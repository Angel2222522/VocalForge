#include "TuneEngine.h"
#include <stdexcept>
namespace vela {
static constexpr double pi=3.14159265358979323846;
TuneEngine::TuneEngine(int sampleRate,bool offline):sr(sampleRate) {
    if(sr<8000||sr>96000) throw std::invalid_argument("Supported sample rates: 8–96 kHz");
    maxPeriod=int(std::ceil(sr/55.0));
    delay=int(std::ceil(sr*(offline?0.080:0.048)));
    capacity=delay+maxPeriod*6+8192;
    history.resize(capacity);overlap.resize(capacity);weight.resize(capacity);voicing.resize(capacity);periodHistory.resize(capacity,sr/150.f);correctionHistory.resize(capacity);
    decimation=std::max(1,sr/12000);trackLength=int(std::ceil((sr/double(decimation))*0.046));
    analysisDelay=int(sr*.023);
    track.resize(trackLength);analysis.resize(trackLength);
    hop=std::max(1,int(sr*.005));
    lowpassA=1-std::exp(-2*pi*2200/sr);
    for(int i=0;i<=2048;i++) window[i]=float(.5+.5*std::cos(pi*i/2048));
    period=sr/150.f;nextCentre=maxPeriod;
}
void TuneEngine::setParameters(const Parameters& p) noexcept {
    param=p;param.tonic=std::clamp(p.tonic,0,11);param.scale=std::clamp(p.scale,0,4);
    auto finite=[](float v,float def){return std::isfinite(v)?v:def;};
    param.retuneMs=std::clamp(finite(p.retuneMs,35),0.f,300.f);
    param.humanize=std::clamp(finite(p.humanize,.35),0.f,1.f);
    param.strength=std::clamp(finite(p.strength,1),0.f,1.f);
    param.wet=std::clamp(finite(p.wet,1),0.f,1.f);
    param.inputDb=std::clamp(finite(p.inputDb,0),-24.f,24.f);
    param.outputDb=std::clamp(finite(p.outputDb,-3),-24.f,6.f);
}
int TuneEngine::nearestNote(float midi,int tonic,int scale,int previous,bool hard) noexcept {
    static constexpr int masks[]={0xfff,0xab5,0x5ad,0x9ad,0x4a9};
    int mask=masks[std::clamp(scale,0,4)],best=int(std::round(midi));float dist=100;
    for(int n=int(std::floor(midi))-12;n<=int(std::ceil(midi))+12;n++) {
        int pc=((n-tonic)%12+12)%12;if(!(mask&(1<<pc))) continue;
        float d=std::abs(n-midi);if(d<dist){best=n;dist=d;}
    }
    int pc=((previous-tonic)%12+12)%12;
    if(previous>-100 && (mask&(1<<pc)) && std::abs(previous-midi)<dist+(hard?.04f:.18f)) return previous;
    return best;
}
float TuneEngine::read(double p)const noexcept {
    int64_t a=int64_t(std::floor(p));float frac=float(p-a);
    if(a<0||a+1>clock) return 0;
    return history[index(a)]*(1-frac)+history[index(a+1)]*frac;
}
void TuneEngine::analyse() noexcept {
    if(trackFilled<trackLength) return;
    for(int j=0;j<trackLength;j++)analysis[j]=track[(trackPos+j)%trackLength];
    auto p=detector.estimate(analysis.data(),trackLength,sr/float(decimation));
    meter.hz=p.hz;meter.confidence=p.confidence;
    voiceTarget=p.hz>0?1:0;
    if(p.hz<=0){meter.targetHz=0;stableHops=0;previousHz=0;return;}
    // Delay a likely octave outlier for 15 ms; sustained genuine jumps are accepted.
    if(previousHz>0) {
        float jump=std::abs(12*std::log2(p.hz/previousHz));
        if(jump>10.5f&&jump<13.5f&&octaveWait++<2) p.hz=previousHz;else octaveWait=0;
    }
    previousHz=p.hz;period=sr/p.hz;
    float midi=69+12*std::log2(p.hz/440);
    if(stableHops==0) slowMidi=midi;
    slowMidi+=.08f*(midi-slowMidi);
    int note=nearestNote(midi,param.tonic,param.scale,previousNote,param.hard);
    stableHops=note==previousNote?stableHops+1:1;previousNote=note;
    float vibrato=(!param.hard && stableHops>20)?param.humanize*(midi-slowMidi):0;
    float desired=std::clamp((note-midi+vibrato)*param.strength,-7.f,7.f);
    float speed=param.hard?std::max(1.f,param.retuneMs*.2f):std::max(2.f,param.retuneMs);
    if(!param.hard && stableHops>20) speed*=1+3*param.humanize;
    float alpha=1-std::exp(-1000*hop/(sr*speed));
    correction+=alpha*(desired-correction);
    meter.targetHz=p.hz*std::exp2(correction/12);
}
void TuneEngine::grain() noexcept {
    double desired=nextCentre-delay;
    float sourcePeriod=periodHistory[index(int64_t(desired))];
    float sourceCorrection=correctionHistory[index(int64_t(desired))];
    int radius=std::clamp(int(sourcePeriod),2,maxPeriod);
    double mark=desired;
    // Same-polarity peak marking, constrained near the predicted epoch.
    if(lastMark>=0) mark=lastMark+std::round((desired-lastMark)/sourcePeriod)*sourcePeriod;
    int search=std::max(2,int(sourcePeriod*(lastMark<0?.5:.18)));
    int64_t best=int64_t(std::round(mark));float peak=-1e20f;
    for(int64_t j=best-search;j<=int64_t(std::round(mark))+search;j++) {
        if(j<1||j+radius+1>clock) continue;
        float v=history[index(j)];if(v>peak){peak=v;best=j;}
    }
    if(peak>-1e19f) {
        float a=history[index(best-1)],b=history[index(best)],c=history[index(best+1)];
        float denom=a-2*b+c;float frac=std::abs(denom)>1e-8?std::clamp(.5f*(a-c)/denom,-.5f,.5f):0;
        lastMark=best+frac;
        int64_t from=int64_t(std::ceil(nextCentre-radius)),to=int64_t(std::floor(nextCentre+radius));
        for(int64_t j=from;j<=to;j++) {
            if(j<clock)continue;
            double offset=j-nextCentre;
            int wi=std::clamp(int(std::abs(offset)*2048/radius),0,2048);
            float w=window[wi];int k=index(j);
            overlap[k]+=w*read(lastMark+offset);weight[k]+=w;
        }
    }
    float ratio=std::exp2(sourceCorrection/12);
    nextCentre+=sourcePeriod/std::clamp(ratio,.667f,1.5f);
}
void TuneEngine::process(const float* in,float* out,int count) noexcept {
    float desiredIn=std::pow(10.f,param.inputDb/20),desiredOut=std::pow(10.f,param.outputDb/20);
    for(int j=0;j<count;j++,clock++) {
        inGain+=.002f*(desiredIn-inGain);outGain+=.002f*(desiredOut-outGain);
        float x=std::isfinite(in[j])?in[j]*inGain:0;x=std::clamp(x,-4.f,4.f);
        int k=index(clock);history[k]=x;
        meter.inputPeak=std::max(std::abs(x),meter.inputPeak*.9998f);
        if(std::abs(x)>=1)meter.clips++;
        lp1+=lowpassA*(x-lp1);lp2+=lowpassA*(lp1-lp2);lp3+=lowpassA*(lp2-lp3);lp4+=lowpassA*(lp3-lp4);
        if(++decCount>=decimation){decCount=0;track[trackPos]=lp4;trackPos=(trackPos+1)%trackLength;trackFilled=std::min(trackFilled+1,trackLength);}
        if(clock%hop==0)analyse();
        voiceMix+=.004f*(voiceTarget-voiceMix);
        int aligned=index(clock-analysisDelay);voicing[aligned]=voiceMix;periodHistory[aligned]=period;correctionHistory[aligned]=correction;
        while(clock>=nextCentre-maxPeriod)grain();
        float dry=clock>=delay?history[index(clock-delay)]:0;
        float wet=weight[k]>.05f?overlap[k]/weight[k]:dry;
        wetSmooth+=.002f*((param.bypass?0:param.wet)-wetSmooth);
        float mix=wetSmooth*(clock>=delay?voicing[index(clock-delay)]:0);
        float y=(dry+(wet-dry)*mix)*outGain;
        if(!std::isfinite(y))y=0;
        if(std::abs(y)>1)meter.clips++;
        out[j]=std::clamp(y,-1.f,1.f);
        meter.outputPeak=std::max(std::abs(out[j]),meter.outputPeak*.9998f);
        overlap[k]=0;weight[k]=0;
    }
}
}
