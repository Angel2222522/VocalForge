#include <jni.h>
#include <oboe/Oboe.h>
#include "TuneEngine.h"
#include <array>
#include <atomic>
#include <memory>
#include <chrono>
#include <algorithm>
#include <mutex>
namespace {
static_assert(std::atomic<float>::is_always_lock_free, "Realtime floats must be lock free");
static_assert(std::atomic<uint32_t>::is_always_lock_free, "Realtime counters must be lock free");
vela::Parameters decode(const float* a){vela::Parameters p;p.tonic=int(a[0]);p.scale=int(a[1]);p.retuneMs=a[2];p.humanize=a[3];p.strength=a[4];p.wet=a[5];p.inputDb=a[6];p.outputDb=a[7];p.hard=a[8]>.5;p.bypass=a[9]>.5;return p;}
struct Live:oboe::AudioStreamDataCallback,oboe::AudioStreamErrorCallback {
    std::shared_ptr<oboe::AudioStream> input,output;
    std::unique_ptr<vela::TuneEngine> dsp;
    std::array<std::atomic<float>,10> params;
    std::array<std::atomic<float>,14> stats;
    static constexpr uint32_t size=1<<20;
    std::unique_ptr<float[]> fifo{new float[size]};
    std::atomic<uint32_t> write{0},read{0},lost{0};
    std::atomic<bool> monitor{false},record{false},fault{false};
    std::array<float,2048> scratch{};
    int lastXrun=0;
    Live(){float a[]={0,0,35,.35,1,1,0,-3,0,0};for(int i=0;i<10;i++)params[i]=a[i];for(auto&s:stats)s=0;}
    ~Live(){stop();}
    void stop(){
        if(output){output->requestStop();output->close();output.reset();}
        if(input){input->requestStop();input->close();input.reset();}
        // Capture delayed DSP tail only after callbacks have stopped.
        if(record.exchange(false) && dsp){
            std::array<float,2048> zero{},tail{};
            for(int left=dsp->latencyFrames();left>0;){
                int n=std::min(left,2048);dsp->process(zero.data(),tail.data(),n);
                uint32_t w=write.load(std::memory_order_relaxed),r=read.load(std::memory_order_acquire);
                if(size-(w-r)<uint32_t(n)){lost.fetch_add(n);fault=true;break;}
                for(int j=0;j<n;j++)fifo[(w+j)&(size-1)]=tail[j];
                write.store(w+n,std::memory_order_release);left-=n;
            }
        }
    }
    oboe::Result start(){
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)->setFormat(oboe::AudioFormat::Float)->setChannelCount(1)
          ->setPerformanceMode(oboe::PerformanceMode::LowLatency)->setSharingMode(oboe::SharingMode::Exclusive)
          ->setUsage(oboe::Usage::Game)->setContentType(oboe::ContentType::Music)->setDataCallback(this)->setErrorCallback(this);
        auto r=b.openStream(output);if(r!=oboe::Result::OK){b.setSharingMode(oboe::SharingMode::Shared);r=b.openStream(output);}if(r!=oboe::Result::OK)return r;
        int sr=output->getSampleRate();
        if(sr<8000||sr>96000){stop();return oboe::Result::ErrorInvalidRate;}
        oboe::AudioStreamBuilder ib;
        ib.setDirection(oboe::Direction::Input)->setFormat(oboe::AudioFormat::Float)->setChannelCount(1)
          ->setSampleRate(sr)->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
          ->setInputPreset(oboe::InputPreset::Unprocessed)->setPerformanceMode(oboe::PerformanceMode::LowLatency)
          ->setSharingMode(oboe::SharingMode::Exclusive)->setErrorCallback(this);
        r=ib.openStream(input);if(r!=oboe::Result::OK){ib.setSharingMode(oboe::SharingMode::Shared)->setInputPreset(oboe::InputPreset::VoiceRecognition);r=ib.openStream(input);}if(r!=oboe::Result::OK){stop();return r;}
        if(input->getSampleRate()!=sr){stop();return oboe::Result::ErrorInvalidRate;}
        dsp=std::make_unique<vela::TuneEngine>(sr);
        output->setBufferSizeInFrames(output->getFramesPerBurst()*2);
        stats[0]=float(sr);stats[1]=float(dsp->latencyFrames());stats[2]=float(output->getBufferSizeInFrames());
        r=input->requestStart();if(r==oboe::Result::OK)r=output->requestStart();if(r!=oboe::Result::OK)stop();return r;
    }
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*,void* data,int32_t n)override {
        auto started=std::chrono::steady_clock::now();auto*dest=static_cast<float*>(data);
        float a[10];for(int i=0;i<10;i++)a[i]=params[i].load(std::memory_order_relaxed);dsp->setParameters(decode(a));
        for(int pos=0;pos<n;){
            int count=std::min(n-pos,2048);auto result=input->read(scratch.data(),count,0);
            int got=result?result.value():0;if(got<0)got=0;
            std::fill(scratch.begin()+got,scratch.begin()+count,0);
            if(got<count)stats[12].store(stats[12].load()+1);
            dsp->process(scratch.data(),dest+pos,count);
            if(record.load(std::memory_order_relaxed)){
                uint32_t w=write.load(std::memory_order_relaxed),r=read.load(std::memory_order_acquire);
                if(size-(w-r)>=uint32_t(count)){
                    for(int j=0;j<count;j++)fifo[(w+j)&(size-1)]=dest[pos+j];
                    write.store(w+count,std::memory_order_release);
                }else{lost.fetch_add(count);fault=true;}
            }
            if(!monitor.load(std::memory_order_relaxed))std::fill(dest+pos,dest+pos+count,0);
            pos+=count;
        }
        auto m=dsp->meters();stats[3]=m.hz;stats[4]=m.targetHz;stats[5]=m.confidence;stats[6]=m.inputPeak;stats[7]=m.outputPeak;stats[8]=float(m.clips);
        float load=float(std::chrono::duration<double>(std::chrono::steady_clock::now()-started).count()*dsp->sampleRate()/n);
        stats[9]=load;stats[10]=std::max(stats[10].load(),load);
        return fault.load()?oboe::DataCallbackResult::Stop:oboe::DataCallbackResult::Continue;
    }
    void onErrorAfterClose(oboe::AudioStream*,oboe::Result)override{fault=true;}
    void poll(){if(output){auto x=output->getXRunCount();if(x){stats[11]=float(x.value());if(x.value()>lastXrun){int burst=output->getFramesPerBurst();output->setBufferSizeInFrames(std::min(output->getBufferSizeInFrames()+burst,burst*8));lastXrun=x.value();}stats[2]=float(output->getBufferSizeInFrames());}}stats[13]=lost.load()>0?2:(fault?1:0);}
};
std::mutex mutex;
std::unique_ptr<Live> live;
void error(JNIEnv* e,const char*m){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),m);}
}
extern "C" JNIEXPORT jint JNICALL Java_gr_anelix_velatune_NativeAudio_start(JNIEnv*e,jclass){std::lock_guard<std::mutex>g(mutex);try{live.reset();live=std::make_unique<Live>();auto r=live->start();if(r!=oboe::Result::OK){live.reset();error(e,oboe::convertToText(r));return 0;}return int(live->stats[0].load());}catch(const std::exception&x){live.reset();error(e,x.what());return 0;}}
extern "C" JNIEXPORT void JNICALL Java_gr_anelix_velatune_NativeAudio_stop(JNIEnv*,jclass){std::lock_guard<std::mutex>g(mutex);if(live)live->stop();}
extern "C" JNIEXPORT void JNICALL Java_gr_anelix_velatune_NativeAudio_release(JNIEnv*,jclass){std::lock_guard<std::mutex>g(mutex);live.reset();}
extern "C" JNIEXPORT void JNICALL Java_gr_anelix_velatune_NativeAudio_configure(JNIEnv*e,jclass,jfloatArray v,jboolean mon,jboolean rec){std::lock_guard<std::mutex>g(mutex);if(!live||e->GetArrayLength(v)!=10)return;float a[10];e->GetFloatArrayRegion(v,0,10,a);for(int i=0;i<10;i++)live->params[i]=a[i];live->monitor=mon;live->record=rec;}
extern "C" JNIEXPORT jfloatArray JNICALL Java_gr_anelix_velatune_NativeAudio_stats(JNIEnv*e,jclass){std::lock_guard<std::mutex>g(mutex);auto a=e->NewFloatArray(14);float v[14]={};if(live){live->poll();for(int i=0;i<14;i++)v[i]=live->stats[i];}e->SetFloatArrayRegion(a,0,14,v);return a;}
extern "C" JNIEXPORT jint JNICALL Java_gr_anelix_velatune_NativeAudio_drain(JNIEnv*e,jclass,jfloatArray a){std::lock_guard<std::mutex>g(mutex);if(!live)return 0;uint32_t r=live->read.load(std::memory_order_relaxed),w=live->write.load(std::memory_order_acquire);int n=std::min(uint32_t(e->GetArrayLength(a)),w-r);auto*v=e->GetFloatArrayElements(a,nullptr);if(!v)return 0;for(int j=0;j<n;j++)v[j]=live->fifo[(r+j)&(Live::size-1)];live->read.store(r+n,std::memory_order_release);e->ReleaseFloatArrayElements(a,v,0);return n;}
extern "C" JNIEXPORT jlong JNICALL Java_gr_anelix_velatune_NativeAudio_createProcessor(JNIEnv*e,jclass,jint sr,jfloatArray a){try{if(e->GetArrayLength(a)!=10){error(e,"Invalid parameters");return 0;}auto*p=new vela::TuneEngine(sr,true);float v[10];e->GetFloatArrayRegion(a,0,10,v);p->setParameters(decode(v));return reinterpret_cast<jlong>(p);}catch(const std::exception&x){error(e,x.what());return 0;}}
extern "C" JNIEXPORT jint JNICALL Java_gr_anelix_velatune_NativeAudio_processorDelay(JNIEnv*,jclass,jlong h){return reinterpret_cast<vela::TuneEngine*>(h)->latencyFrames();}
extern "C" JNIEXPORT void JNICALL Java_gr_anelix_velatune_NativeAudio_process(JNIEnv*e,jclass,jlong h,jfloatArray a,jint n){if(!h||n<0||n>e->GetArrayLength(a)){error(e,"Invalid audio block");return;}auto*v=e->GetFloatArrayElements(a,nullptr);if(!v)return;reinterpret_cast<vela::TuneEngine*>(h)->process(v,v,n);e->ReleaseFloatArrayElements(a,v,0);}
extern "C" JNIEXPORT void JNICALL Java_gr_anelix_velatune_NativeAudio_destroyProcessor(JNIEnv*,jclass,jlong h){delete reinterpret_cast<vela::TuneEngine*>(h);}
