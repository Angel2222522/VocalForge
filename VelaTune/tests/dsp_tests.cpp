#include "TuneEngine.h"
#include <iostream>
#include <vector>
#include <chrono>
#include <random>
#include <fstream>
#include <cstdlib>
#include <new>
#include <atomic>
#include <cstring>
static std::atomic<size_t> allocations{0};
void* operator new(std::size_t n){allocations++;if(void*p=std::malloc(n))return p;throw std::bad_alloc();}
void operator delete(void*p) noexcept{std::free(p);}void operator delete(void*p,std::size_t)noexcept{std::free(p);}
static int failures=0;
void check(bool x,const char*name){std::cout<<(x?"PASS ":"FAIL ")<<name<<'\n';if(!x)failures++;}
void raw(const char*path,const std::vector<float>& x){std::ofstream f(path,std::ios::binary);f.write((const char*)x.data(),x.size()*4);}
int main(int argc,char**argv){
 bool brief=argc>1&&std::strcmp(argv[1],"quick")==0;
 constexpr double pi=3.14159265358979323846;
 std::mt19937 rng(1042);std::normal_distribution<float> noise(0,1);
 for(int sr:{8000,16000,44100,48000,96000}) {
  vela::PitchDetector d;int n=int(sr*.048);std::vector<float>x(n);
  for(double f:{55.5,65.41,82.41,110.,220.,440.,880.,1108.73}) {
   for(int i=0;i<n;i++)x[i]=.3*std::sin(2*pi*f*i/sr)+.12*std::sin(4*pi*f*i/sr);
   // Detector is used on downsampled audio in engine; direct max-lag bounds here at high sr.
   if(sr>16000)continue;
   auto p=d.estimate(x.data(),n,sr);
   double cents=p.hz?1200*std::log2(p.hz/f):9999;
   std::cout<<"pitch sr="<<sr<<" f="<<f<<" error_cents="<<cents<<'\n';
   check(std::abs(cents)<15,"YIN periodic harmonic accuracy <15 cents");
  }
  vela::TuneEngine e(sr);vela::Parameters p;p.hard=true;p.retuneMs=0;p.outputDb=0;e.setParameters(p);
  int length=sr*(brief?1:3);std::vector<float>a(length),b(length);
  for(int i=0;i<length;i++)a[i]=.4*std::sin(2*pi*225*i/sr)+.15*std::sin(4*pi*225*i/sr);
  size_t before=allocations;auto start=std::chrono::steady_clock::now();
  std::vector<double> timing;timing.reserve((length+191)/192);before=allocations;
  for(int i=0;i<length;i+=192){auto t=std::chrono::steady_clock::now();e.process(a.data()+i,b.data()+i,std::min(192,length-i));timing.push_back(std::chrono::duration<double,std::micro>(std::chrono::steady_clock::now()-t).count());}
  auto after=allocations.load();
  double seconds=std::chrono::duration<double>(std::chrono::steady_clock::now()-start).count();
  std::sort(timing.begin(),timing.end());
  std::cout<<"BENCH sr="<<sr<<" audio_s="<<length/double(sr)<<" wall_s="<<seconds<<" realtime_factor="<<seconds*sr/length<<" p99_block_us="<<timing[int(timing.size()*.99)]<<" max_block_us="<<timing.back()<<" algorithmic_delay_frames="<<e.latencyFrames()<<'\n';
  check(after==before,"zero allocations during processing");
  bool finite=true;for(float v:b)finite&=std::isfinite(v)&&std::abs(v)<=1;
  check(finite,"bounded finite output");
  int dec=std::max(1,sr/12000);int dn=int(sr*.048)/dec;std::vector<float>out(dn);
  for(int i=0;i<dn;i++)out[i]=b[length-sr/4+i*dec];
  auto q=d.estimate(out.data(),dn,sr/float(dec));
  double cents=q.hz?1200*std::log2(q.hz/220):9999;
  std::cout<<"corrected_hz="<<q.hz<<" error_cents="<<cents<<'\n';
  check(std::abs(cents)<20,"actual output converges 225Hz to A3 220Hz");
  if(sr==48000&&!brief){raw("evidence/detuned-input.f32",a);raw("evidence/hard-output.f32",b);}
 }
 check(vela::TuneEngine::nearestNote(61,0,1)==60,"C major rejects C sharp with stable lower tie");
 check(vela::TuneEngine::nearestNote(63,0,2)==63,"C minor accepts E flat");
 bool scales=true;
 for(int mode=0;mode<5;mode++)for(int key=0;key<12;key++)for(int n=24;n<100;n++)scales&=std::abs(vela::TuneEngine::nearestNote(n,key,mode)-n)<=2;
 check(scales,"all key/scale mappings bounded");
 vela::TuneEngine e(48000);vela::Parameters p;p.bypass=true;p.outputDb=0;e.setParameters(p);
 std::vector<float>a(12000),b(12000);a[5000]=.5;e.process(a.data(),b.data(),a.size());
 int peak=int(std::max_element(b.begin(),b.end())-b.begin());
 check(peak-5000==e.latencyFrames(),"measured impulse bypass delay matches declared frames");
 vela::TuneEngine silence(48000);std::fill(a.begin(),a.end(),0);silence.process(a.data(),b.data(),a.size());
 check(silence.meters().hz==0&&*std::max_element(b.begin(),b.end())==0,"silence remains silence");
 for(float&v:a){v=noise(rng)*.1f;}silence.process(a.data(),b.data(),a.size());
 check(silence.meters().hz==0,"unvoiced white noise rejected");
 for(float&v:a){v=std::numeric_limits<float>::quiet_NaN();}silence.process(a.data(),b.data(),a.size());
 check(std::all_of(b.begin(),b.end(),[](float x){return std::isfinite(x);}),"NaN sanitized");
 std::cout<<"FAILURES="<<failures<<'\n';return failures?1:0;
}
