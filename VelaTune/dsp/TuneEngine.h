#pragma once
#include "PitchDetector.h"
#include <cstdint>
#include <vector>
#include <array>
namespace vela {
struct Parameters {
    int tonic=0, scale=0; // chromatic, major, minor, harmonic minor, pentatonic minor
    float retuneMs=35, humanize=0.35f, strength=1, wet=1, inputDb=0, outputDb=-3;
    bool hard=false, bypass=false;
};
struct Meters { float hz=0,targetHz=0,confidence=0,inputPeak=0,outputPeak=0;uint64_t clips=0; };
class TuneEngine {
public:
    explicit TuneEngine(int sampleRate, bool offline=false);
    void setParameters(const Parameters&) noexcept;
    void process(const float* input,float* output,int count) noexcept;
    int latencyFrames()const noexcept{return delay;}
    int sampleRate()const noexcept{return sr;}
    const Meters& meters()const noexcept{return meter;}
    static int nearestNote(float midi,int tonic,int scale,int previous=-100,bool hard=false) noexcept;
private:
    int sr,delay,capacity,maxPeriod,hop,decimation,decCount=0,trackLength,trackPos=0,trackFilled=0,analysisDelay=0;
    int64_t clock=0;double nextCentre=0,lastMark=-1;
    float period=0,correction=0,slowMidi=0,previousHz=0,voiceMix=0,voiceTarget=0,wetSmooth=1;
    int previousNote=-100,stableHops=0,octaveWait=0;
    float inGain=1,outGain=0.7079f,lowpassA=0,lp1=0,lp2=0,lp3=0,lp4=0;
    Parameters param;Meters meter;PitchDetector detector;
    std::vector<float> history,overlap,weight,voicing,periodHistory,correctionHistory,track,analysis;
    std::array<float,2049> window{};
    int index(int64_t n)const noexcept {auto i=n%capacity;return int(i<0?i+capacity:i);}
    float read(double position)const noexcept;
    void analyse() noexcept;
    void grain() noexcept;
};
}
