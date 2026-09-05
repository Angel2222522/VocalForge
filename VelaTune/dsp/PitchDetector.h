#pragma once
#include <array>
#include <cmath>
#include <algorithm>
namespace vela {
struct Pitch { float hz=0, confidence=0, rms=0; };
// Original implementation of cumulative-mean normalized difference (YIN).
// Caller supplies a contiguous, low-pass/downsampled chronological window.
class PitchDetector {
    std::array<float,1024> diff{};
public:
    Pitch estimate(const float* x, int n, float rate, float low=55, float high=1200) noexcept {
        Pitch p;
        int maxLag=std::min({int(rate/low), n/2-2, 1022});
        int minLag=std::max(2,int(rate/high));
        if(maxLag<=minLag) return p;
        int w=n-maxLag-1;
        double energy=0, mean=0;
        for(int j=0;j<n;j++) mean+=x[j];
        mean/=n;
        for(int j=0;j<n;j++) energy+=(x[j]-mean)*(x[j]-mean);
        p.rms=std::sqrt(energy/n);
        if(p.rms<0.002f) return p;
        double cumulative=0;
        diff[0]=1;
        for(int lag=1;lag<=maxLag;lag++) {
            double d=0;
            for(int j=0;j<w;j++) { double e=x[j]-x[j+lag];d+=e*e; }
            cumulative+=d;
            diff[lag]=cumulative>1e-15?float(d*lag/cumulative):1;
        }
        int best=0;
        for(int lag=minLag;lag<maxLag-1;lag++) {
            if(diff[lag]<0.13f) {
                while(lag+1<maxLag && diff[lag+1]<diff[lag]) ++lag;
                best=lag;break;
            }
        }
        if(!best) {
            best=minLag;
            for(int lag=minLag+1;lag<maxLag;lag++) if(diff[lag]<diff[best]) best=lag;
            if(diff[best]>0.28f) return p;
        }
        if(best<=1||best>=maxLag) return p;
        float denom=diff[best-1]-2*diff[best]+diff[best+1];
        float delta=std::abs(denom)>1e-8f ? 0.5f*(diff[best-1]-diff[best+1])/denom : 0;
        delta=std::clamp(delta,-0.5f,0.5f);
        // Refine short periods against cubic-interpolated waveform differences.
        // CMNDF's parabola is biased when a period spans very few samples.
        if(best<32) {
            auto cost=[&](float lag) {
                int k=int(lag);float t=lag-k;double total=0;
                for(int j=0;j<w;j++) {
                    const float* q=x+j+k;
                    float v=q[0]+.5f*t*(q[1]-q[-1]+t*(2*q[-1]-5*q[0]+4*q[1]-q[2]+t*(3*(q[0]-q[1])+q[2]-q[-1])));
                    double e=x[j]-v;total+=e*e;
                }return total;
            };
            float left=best-.6f,right=best+.6f;
            for(int i=0;i<12;i++){float a=left+(right-left)/3,b=right-(right-left)/3;if(cost(a)<cost(b))right=b;else left=a;}
            delta=(left+right)*.5f-best;
        }
        float f=rate/(best+delta);
        if(f<low||f>high) return p;
        p.hz=f;p.confidence=std::clamp(1-diff[best],0.f,1.f);return p;
    }
};
}
