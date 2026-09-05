#include "TuneEngine.h"
#include <random>
#include <array>
#include <iostream>
int main(){
 std::mt19937 rng(246);std::uniform_real_distribution<float> value(-2,2);
 for(int rate:{8000,11025,16000,22050,32000,44100,48000,88200,96000})for(bool offline:{false,true}){
  vela::TuneEngine e(rate,offline);std::array<float,1024>x{},y{};
  for(int block=0;block<1600;block++){
   int n=1+rng()%1024;for(int i=0;i<n;i++)x[i]=value(rng);
   if(block%9==0){vela::Parameters p;p.tonic=rng()%12;p.scale=rng()%5;p.retuneMs=rng()%250;p.humanize=value(rng);p.wet=value(rng);p.hard=block%2;p.bypass=block%3==0;e.setParameters(p);}
   e.process(x.data(),y.data(),n);for(int i=0;i<n;i++)if(!std::isfinite(y[i])||std::abs(y[i])>1)return 1;
  }
 }
 std::cout<<"PASS randomized block sizes, 9 sample rates, both modes, controls, finite output\n";
}
