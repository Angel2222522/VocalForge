#include "TuneEngine.h"
extern "C" {
void* vela_create(int rate,int offline){return new vela::TuneEngine(rate,offline!=0);}
void vela_free(void*p){delete static_cast<vela::TuneEngine*>(p);}
void vela_settings(void* h,float speed,float humanize,int hard,int scale,int tonic,float strength,float wet,int bypass){vela::Parameters p;p.retuneMs=speed;p.humanize=humanize;p.hard=hard;p.scale=scale;p.tonic=tonic;p.strength=strength;p.wet=wet;p.bypass=bypass;p.outputDb=0;static_cast<vela::TuneEngine*>(h)->setParameters(p);}
void vela_process(void* h,const float* input,float* output,int n){static_cast<vela::TuneEngine*>(h)->process(input,output,n);}
int vela_delay(void* h){return static_cast<vela::TuneEngine*>(h)->latencyFrames();}
float vela_pitch(void* h){return static_cast<vela::TuneEngine*>(h)->meters().hz;}
float vela_target(void* h){return static_cast<vela::TuneEngine*>(h)->meters().targetHz;}
}
