#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <vector>

namespace {

constexpr int kPackedHeader = 2;
constexpr int kFrameFields = 5;
constexpr int kEditFields = 7;
constexpr float kPi = 3.14159265358979323846f;

struct Frame {
    float time = 0.0f;
    float f0 = 0.0f;
    float confidence = 0.0f;
    bool voiced = false;
    float energy = 0.0f;
};

float clamp01(float value) {
    return std::max(0.0f, std::min(1.0f, value));
}

float linearAt(const std::vector<float>& samples, float position) {
    if (samples.empty() || position < 0.0f || position >= static_cast<float>(samples.size() - 1)) return 0.0f;
    const int index = static_cast<int>(position);
    const float fraction = position - static_cast<float>(index);
    return samples[index] * (1.0f - fraction) + samples[index + 1] * fraction;
}

float correlationAt(const std::vector<float>& window, int lag) {
    if (lag <= 0 || lag >= static_cast<int>(window.size()) - 2) return 0.0f;
    double numerator = 0.0;
    double left = 0.0;
    double right = 0.0;
    for (int i = 0; i + lag < static_cast<int>(window.size()); ++i) {
        numerator += static_cast<double>(window[i]) * window[i + lag];
        left += static_cast<double>(window[i]) * window[i];
        right += static_cast<double>(window[i + lag]) * window[i + lag];
    }
    if (left <= 1e-12 || right <= 1e-12) return 0.0f;
    return static_cast<float>(numerator / std::sqrt(left * right));
}

std::vector<Frame> analyzeSamples(const std::vector<float>& input, int sampleRate, int& hopOut) {
    const int frameSize = sampleRate >= 88'000 ? 4096 : 2048;
    const int hop = sampleRate >= 88'000 ? 512 : 256;
    const int frameCount = std::max(1, static_cast<int>((input.size() + hop - 1) / hop));
    hopOut = hop;
    std::vector<Frame> frames(frameCount);
    std::vector<float> rawF0(frameCount, 0.0f);
    std::vector<float> rawConfidence(frameCount, 0.0f);
    std::vector<bool> rawVoiced(frameCount, false);
    std::vector<float> rawEnergy(frameCount, 0.0f);

    const int minLag = std::max(2, sampleRate / 1100);
    const int maxLag = std::min(frameSize / 2 - 2, sampleRate / 55);
    std::vector<float> window(frameSize);
    constexpr int decimation = 4;
    std::vector<float> coarseWindow(frameSize / decimation);

    for (int frame = 0; frame < frameCount; ++frame) {
        const int center = frame * hop;
        double energy = 0.0;
        for (int i = 0; i < frameSize; ++i) {
            const int source = center - frameSize / 2 + i;
            const float sample = source >= 0 && source < static_cast<int>(input.size()) ? input[source] : 0.0f;
            const float taper = 0.5f - 0.5f * std::cos(2.0f * kPi * static_cast<float>(i) / static_cast<float>(frameSize - 1));
            window[i] = sample * taper;
            energy += static_cast<double>(window[i]) * window[i];
        }
        const float rms = std::sqrt(static_cast<float>(energy / frameSize));
        rawEnergy[frame] = rms;
        if (rms < 0.004f) continue;

        for (int i = 0; i < static_cast<int>(coarseWindow.size()); ++i) coarseWindow[i] = window[i * decimation];
        float coarseBest = 0.0f;
        int coarseLag = 0;
        const int coarseMinLag = std::max(2, minLag / decimation);
        const int coarseMaxLag = std::min(static_cast<int>(coarseWindow.size()) / 2 - 2, maxLag / decimation);
        // Search coarsely, then refine only around the best period. This keeps
        // the robustness of autocorrelation without an O(frame*lag*window)
        // penalty that makes long offline renders needlessly slow.
        for (int lag = coarseMinLag; lag <= coarseMaxLag; ++lag) {
            const float value = correlationAt(coarseWindow, lag);
            if (value > coarseBest) { coarseBest = value; coarseLag = lag; }
        }
        const int coarseBestLag = coarseLag * decimation;
        float best = 0.0f;
        int bestLag = 0;
        for (int lag = std::max(minLag, coarseBestLag - 8); lag <= std::min(maxLag, coarseBestLag + 8); ++lag) {
            const float value = correlationAt(window, lag);
            if (value > best) { best = value; bestLag = lag; }
        }
        if (bestLag == 0 || best < 0.22f) continue;

        // Prefer a plausible fundamental when the second harmonic wins by a
        // small margin. The confidence gate prevents silence from becoming F0.
        const float doubledPeriod = correlationAt(window, bestLag * 2);
        if (bestLag * 2 <= maxLag && doubledPeriod > best * 0.88f) {
            bestLag *= 2;
            best = doubledPeriod;
        }
        const float left = correlationAt(window, bestLag - 1);
        const float right = correlationAt(window, bestLag + 1);
        const float denominator = 2.0f * (left - 2.0f * best + right);
        const float offset = std::abs(denominator) > 1e-6f ? 0.5f * (left - right) / (left - 2.0f * best + right) : 0.0f;
        const float refinedLag = std::max(1.0f, static_cast<float>(bestLag) + std::max(-0.45f, std::min(0.45f, offset)));
        const float f0 = static_cast<float>(sampleRate) / refinedLag;
        const float confidence = clamp01((best - 0.22f) / 0.68f) * clamp01(rms / 0.025f);
        rawF0[frame] = f0;
        rawConfidence[frame] = confidence;
        rawVoiced[frame] = confidence > 0.18f && f0 >= 55.0f && f0 <= 1100.0f;
    }

    // Reject octave jumps only when the neighboring voiced evidence supports
    // continuity. Quick real note changes remain untouched.
    for (int i = 1; i < frameCount; ++i) {
        if (!rawVoiced[i] || !rawVoiced[i - 1]) continue;
        const float ratio = rawF0[i] / std::max(1.0f, rawF0[i - 1]);
        if (ratio > 1.72f && rawConfidence[i - 1] > 0.35f) rawF0[i] *= 0.5f;
        else if (ratio < 0.58f && rawConfidence[i - 1] > 0.35f) rawF0[i] *= 2.0f;
    }

    for (int i = 0; i < frameCount; ++i) {
        std::vector<float> neighborhood;
        for (int j = std::max(0, i - 2); j <= std::min(frameCount - 1, i + 2); ++j) {
            if (rawVoiced[j]) neighborhood.push_back(rawF0[j]);
        }
        if (neighborhood.size() >= 3) {
            std::sort(neighborhood.begin(), neighborhood.end());
            rawF0[i] = neighborhood[neighborhood.size() / 2];
        }
        frames[i] = Frame{
            static_cast<float>(i * hop) / static_cast<float>(sampleRate),
            rawVoiced[i] ? rawF0[i] : 0.0f,
            rawVoiced[i] ? rawConfidence[i] : 0.0f,
            rawVoiced[i],
            rawEnergy[i]
        };
    }
    return frames;
}

float frameValue(const std::vector<Frame>& frames, float framePosition, int field) {
    if (frames.empty()) return 0.0f;
    const int left = std::max(0, std::min(static_cast<int>(frames.size()) - 1, static_cast<int>(std::floor(framePosition))));
    const int right = std::min(static_cast<int>(frames.size()) - 1, left + 1);
    const float mix = framePosition - static_cast<float>(left);
    const float a = field == 0 ? frames[left].f0 : field == 1 ? frames[left].confidence : frames[left].voiced ? 1.0f : 0.0f;
    const float b = field == 0 ? frames[right].f0 : field == 1 ? frames[right].confidence : frames[right].voiced ? 1.0f : 0.0f;
    return a + (b - a) * mix;
}

int nearestPermitted(float sourceMidi, int mask, int previous) {
    const int rounded = static_cast<int>(std::round(sourceMidi));
    int best = rounded;
    float bestDistance = std::numeric_limits<float>::max();
    for (int midi = rounded - 12; midi <= rounded + 12; ++midi) {
        if ((mask & (1 << (midi % 12 + 12) % 12)) == 0) continue;
        const float distance = std::abs(static_cast<float>(midi) - sourceMidi) + (previous != -1 && midi != previous ? 0.012f : 0.0f);
        if (distance < bestDistance) { bestDistance = distance; best = midi; }
    }
    return best;
}

struct EditValues {
    bool found = false;
    bool disabled = false;
    float target = -1.0f;
    float amount = -1.0f;
    float vibrato = -1.0f;
};

EditValues editAt(const std::vector<float>& edits, float time) {
    EditValues result;
    for (size_t i = 0; i + kEditFields <= edits.size(); i += kEditFields) {
        if (time < edits[i] || time > edits[i + 1]) continue;
        result.found = true;
        result.target = edits[i + 2];
        result.amount = edits[i + 3];
        result.disabled = edits[i + 4] > 0.5f;
        result.vibrato = edits[i + 6];
    }
    return result;
}

std::vector<float> renderSamples(
    const std::vector<float>& input,
    int sampleRate,
    const std::vector<Frame>& frames,
    int scaleMask,
    float correctionAmount,
    float retuneMs,
    float humanize,
    float formantPreservation,
    float vibratoPreservation,
    float transition,
    float dryWet,
    const std::vector<float>& edits,
    JNIEnv* env,
    jobject listener
) {
    const int n = static_cast<int>(input.size());
    std::vector<float> correctedMidi(frames.size(), 0.0f);
    std::vector<float> sourceMidi(frames.size(), 0.0f);
    std::vector<float> targetF0(frames.size(), 0.0f);
    int lockedNote = -1;
    int candidateNote = -1;
    int candidateRun = 0;
    float smoothedMidi = 0.0f;
    const float hopSeconds = frames.size() > 1 ? frames[1].time - frames[0].time : 256.0f / sampleRate;

    for (size_t i = 0; i < frames.size(); ++i) {
        if (!frames[i].voiced || frames[i].f0 <= 0.0f) continue;
        const float midi = 69.0f + 12.0f * std::log2(frames[i].f0 / 440.0f);
        sourceMidi[i] = midi;
        const EditValues edit = editAt(edits, frames[i].time);
        const int candidate = edit.found && edit.target >= 0.0f ? static_cast<int>(std::round(edit.target)) : nearestPermitted(midi, scaleMask, lockedNote);
        if (lockedNote == -1) lockedNote = candidate;
        if (candidate != candidateNote) { candidateNote = candidate; candidateRun = 1; } else ++candidateRun;
        if (candidateRun >= 3 || std::abs(candidate - lockedNote) >= 3) lockedNote = candidate;

        float amount = edit.found && edit.amount >= 0.0f ? edit.amount : correctionAmount;
        if (edit.found && edit.disabled) amount = 0.0f;
        amount = clamp01(amount);
        const int radius = 8;
        float localBase = midi;
        int count = 0;
        for (int j = std::max<int>(0, static_cast<int>(i) - radius); j <= std::min<int>(static_cast<int>(frames.size()) - 1, static_cast<int>(i) + radius); ++j) {
            if (sourceMidi[j] > 0.0f) { localBase += sourceMidi[j]; ++count; }
        }
        if (count > 0) localBase /= static_cast<float>(count + 1);
        const float vibrato = edit.found && edit.vibrato >= 0.0f ? edit.vibrato : vibratoPreservation;
        float desired = localBase + (midi - localBase) * clamp01(vibrato) + (static_cast<float>(lockedNote) - localBase) * amount;
        if (i == 0 || smoothedMidi == 0.0f) smoothedMidi = desired;
        const float timeConstant = std::max(0.0f, retuneMs) * (0.35f + 0.65f * clamp01(transition));
        const float alpha = timeConstant < 1.0f ? 1.0f : 1.0f - std::exp(-hopSeconds * 1000.0f / timeConstant);
        smoothedMidi += (desired - smoothedMidi) * alpha;
        correctedMidi[i] = smoothedMidi;
        targetF0[i] = 440.0f * std::pow(2.0f, (smoothedMidi - 69.0f) / 12.0f);
    }

    // Fill short gaps so an unvoiced detector glitch does not produce a click.
    for (size_t i = 1; i + 1 < targetF0.size(); ++i) {
        if (targetF0[i] == 0.0f && targetF0[i - 1] > 0.0f && targetF0[i + 1] > 0.0f) targetF0[i] = 0.5f * (targetF0[i - 1] + targetF0[i + 1]);
    }

    std::vector<float> sum(n, 0.0f);
    std::vector<float> weights(n, 0.0f);
    const float safeFormant = clamp01(formantPreservation);
    const int hop = frames.size() > 1 ? std::max(1, static_cast<int>(std::round(hopSeconds * sampleRate))) : 256;
    const int progressEvery = std::max(1, n / 100);
    jclass listenerClass = listener ? env->GetObjectClass(listener) : nullptr;
    jmethodID progressMethod = listenerClass ? env->GetMethodID(listenerClass, "onProgress", "(F)V") : nullptr;

    for (int center = 0; center < n; center += std::max(8, hop / 2)) {
        const float framePosition = static_cast<float>(center) / static_cast<float>(hop);
        const float sourceF0 = frameValue(frames, framePosition, 0);
        const float confidence = frameValue(frames, framePosition, 1);
        const bool voiced = frameValue(frames, framePosition, 2) > 0.5f && sourceF0 > 0.0f && confidence > 0.16f;
        if (!voiced) continue;
        const size_t frameIndex = std::min(frames.size() - 1, static_cast<size_t>(std::round(framePosition)));
        const float target = targetF0[frameIndex] > 0.0f ? targetF0[frameIndex] : sourceF0;
        const float sourcePeriod = static_cast<float>(sampleRate) / std::max(55.0f, sourceF0);
        const float targetPeriod = static_cast<float>(sampleRate) / std::max(55.0f, target);
        const float ratio = sourcePeriod / std::max(1.0f, targetPeriod);
        const float cycles = 2.0f + safeFormant * 2.0f + clamp01(transition) * 0.75f;
        const int halfGrain = std::max(32, std::min(4096, static_cast<int>(std::round(targetPeriod * cycles))));
        for (int offset = -halfGrain; offset <= halfGrain; ++offset) {
            const int destination = center + offset;
            if (destination < 0 || destination >= n) continue;
            const float sourcePosition = static_cast<float>(center) + static_cast<float>(offset) * ratio;
            const float window = 0.5f + 0.5f * std::cos(kPi * static_cast<float>(offset) / static_cast<float>(halfGrain));
            sum[destination] += linearAt(input, sourcePosition) * window;
            weights[destination] += window;
        }
        if (listener && progressMethod && (center % progressEvery == 0)) {
            env->CallVoidMethod(listener, progressMethod, static_cast<jfloat>(0.1f + 0.82f * center / std::max(1.0f, static_cast<float>(n))));
            if (env->ExceptionCheck()) { env->ExceptionClear(); }
        }
    }

    std::vector<float> output(n);
    for (int i = 0; i < n; ++i) {
        const float wet = weights[i] > 1e-4f ? sum[i] / weights[i] : input[i];
        output[i] = input[i] * (1.0f - clamp01(dryWet)) + wet * clamp01(dryWet);
        output[i] = std::max(-1.0f, std::min(1.0f, output[i]));
    }
    if (listener && progressMethod) env->CallVoidMethod(listener, progressMethod, static_cast<jfloat>(1.0f));
    if (listenerClass) env->DeleteLocalRef(listenerClass);
    return output;
}

std::vector<float> toVector(JNIEnv* env, jfloatArray array) {
    if (!array) return {};
    const jsize length = env->GetArrayLength(array);
    std::vector<float> result(static_cast<size_t>(length));
    env->GetFloatArrayRegion(array, 0, length, result.data());
    return result;
}

jfloatArray toJavaArray(JNIEnv* env, const std::vector<float>& values) {
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (result && !values.empty()) env->SetFloatArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}

std::vector<Frame> unpackAnalysis(const std::vector<float>& packed) {
    std::vector<Frame> frames;
    if (packed.size() < kPackedHeader) return frames;
    const int count = std::max(0, std::min(static_cast<int>(packed[0]), static_cast<int>((packed.size() - kPackedHeader) / kFrameFields)));
    frames.reserve(count);
    for (int i = 0; i < count; ++i) {
        const int index = kPackedHeader + i * kFrameFields;
        frames.push_back(Frame{packed[index], packed[index + 1], packed[index + 2], packed[index + 3] > 0.5f, packed[index + 4]});
    }
    return frames;
}

} // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_angel_vocalforge_dsp_NativeAudioEngine_analyze(JNIEnv* env, jclass, jfloatArray inputArray, jint sampleRate) {
    const std::vector<float> input = toVector(env, inputArray);
    int hop = 256;
    const std::vector<Frame> frames = analyzeSamples(input, sampleRate, hop);
    std::vector<float> packed(kPackedHeader + frames.size() * kFrameFields);
    packed[0] = static_cast<float>(frames.size());
    packed[1] = static_cast<float>(hop);
    for (size_t i = 0; i < frames.size(); ++i) {
        const int index = kPackedHeader + static_cast<int>(i) * kFrameFields;
        packed[index] = frames[i].time;
        packed[index + 1] = frames[i].f0;
        packed[index + 2] = frames[i].confidence;
        packed[index + 3] = frames[i].voiced ? 1.0f : 0.0f;
        packed[index + 4] = frames[i].energy;
    }
    return toJavaArray(env, packed);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_angel_vocalforge_dsp_NativeAudioEngine_render(
    JNIEnv* env,
    jclass,
    jfloatArray inputArray,
    jint sampleRate,
    jfloatArray analysisArray,
    jint scaleMask,
    jint,
    jfloat correctionAmount,
    jfloat retuneSpeedMs,
    jfloat humanize,
    jfloat formantPreservation,
    jfloat vibratoPreservation,
    jfloat transition,
    jfloat dryWet,
    jfloatArray editsArray,
    jobject listener
) {
    const std::vector<float> input = toVector(env, inputArray);
    const std::vector<Frame> frames = unpackAnalysis(toVector(env, analysisArray));
    const std::vector<float> edits = toVector(env, editsArray);
    const std::vector<float> output = renderSamples(input, sampleRate, frames, scaleMask, correctionAmount, retuneSpeedMs, humanize, formantPreservation, vibratoPreservation, transition, dryWet, edits, env, listener);
    return toJavaArray(env, output);
}
