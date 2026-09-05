"""Independent signal analysis. Synthetic fixtures only; not a listening-panel claim."""
from pathlib import Path
import ctypes as C
import json, time, platform
import numpy as np
from scipy import signal
from scipy.io import wavfile
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
root=Path(__file__).resolve().parents[1]
lib=C.CDLL(str(root/'build/host/libvela.so'))
lib.vela_create.argtypes=[C.c_int,C.c_int];lib.vela_create.restype=C.c_void_p
lib.vela_free.argtypes=[C.c_void_p]
lib.vela_settings.argtypes=[C.c_void_p,C.c_float,C.c_float,C.c_int,C.c_int,C.c_int,C.c_float,C.c_float,C.c_int]
lib.vela_process.argtypes=[C.c_void_p,C.POINTER(C.c_float),C.POINTER(C.c_float),C.c_int]
lib.vela_delay.argtypes=[C.c_void_p];lib.vela_delay.restype=C.c_int
for name in ['vela_pitch','vela_target']:
    getattr(lib,name).argtypes=[C.c_void_p];getattr(lib,name).restype=C.c_float
sr=48000
rng=np.random.default_rng(1609)

def run(x,hard=True,humanize=0,speed=0,offline=False,bypass=False,chunk=192):
    x=np.ascontiguousarray(x,dtype=np.float32);h=lib.vela_create(sr,offline)
    lib.vela_settings(h,speed,humanize,hard,0,0,1,1,bypass)
    delay=lib.vela_delay(h);padded=np.r_[x,np.zeros(delay+chunk,dtype=np.float32)];y=np.empty_like(padded);tracks=[]
    start=time.perf_counter()
    for pos in range(0,len(padded),chunk):
        count=min(chunk,len(padded)-pos)
        lib.vela_process(h,padded[pos:].ctypes.data_as(C.POINTER(C.c_float)),y[pos:].ctypes.data_as(C.POINTER(C.c_float)),count)
        tracks.append([pos/sr,lib.vela_pitch(h),lib.vela_target(h)])
    elapsed=time.perf_counter()-start;lib.vela_free(h)
    return y[delay:delay+len(x)],np.array(tracks),elapsed

def harmonics(f,breath=0):
    f=np.asarray(f);phase=np.cumsum(2*np.pi*f/sr)
    x=sum(np.sin(k*phase)/(k**1.4) for k in range(1,10))*.23
    x+=rng.normal(0,breath,len(f));return x

def f0(x,rate=sr,low=50,high=1300):
    # Independent FFT autocorrelation / peak interpolation (not the DSP detector).
    x=signal.detrend(x);x=x*signal.windows.hann(len(x));n=2**int(np.ceil(np.log2(len(x)*2)))
    a=np.fft.irfft(abs(np.fft.rfft(x,n))**2,n)[:len(x)];a/=max(a[0],1e-12)
    lo=int(rate/high);hi=min(int(rate/low),len(a)-2)
    peaks,_=signal.find_peaks(a[lo:hi]);peaks+=lo
    if len(peaks)==0:return 0
    best=peaks[np.flatnonzero(a[peaks]>=.98*np.max(a[peaks]))[0]];den=a[best-1]-2*a[best]+a[best+1]
    frac=.5*(a[best-1]-a[best+1])/den if abs(den)>1e-12 else 0
    return rate/(best+frac)

results={'boundary':'Linux x86_64 host; synthetic signals; no Android device or subjective listening','host':platform.platform(),'tests':{}}
out=root/'evidence/audio';out.mkdir(parents=True,exist_ok=True)
# Static tones across practical vocal ranges, actual output frequency measured independently.
static=[]
for base in [55.8,65.41,82.41,110,164.81,220,440,880,1046.5]:
    actual=base*2**(.28/12);f=np.full(sr*2,actual);x=harmonics(f);y,track,elapsed=run(x)
    expected=440*2**((round(69+12*np.log2(actual/440))-69)/12)
    measured=f0(y[-sr//3:]);error=1200*np.log2(measured/expected) if measured else 9999
    static.append({'input_hz':actual,'target_hz':expected,'output_hz':measured,'error_cents':error})
results['tests']['steady_vocal_range']=static
# Dynamic melody, glissando, vibrato, breath, fricatives. Generated original fixture.
t=np.arange(sr*9)/sr
freq=np.select([t<1.5,t<3,t<4.5,t<6,t<7.5],[114,171,228,228*2**((t-4.5)/3),452],default=228.)
freq*=2**((.22*np.sin(2*np.pi*5.5*t))/12)
x=harmonics(freq,.004)
x[(t>1.35)&(t<1.5)]=rng.normal(0,.12,np.count_nonzero((t>1.35)&(t<1.5)))
x[(t>3.8)&(t<4.1)]=0
hard,track,_=run(x)
natural,_,_=run(x,hard=False,speed=65,humanize=.75)
for name,arr in [('synthetic-vocal-before',x),('synthetic-vocal-hard',hard),('synthetic-vocal-natural',natural)]:wavfile.write(out/(name+'.wav'),sr,arr.astype(np.float32))
results['tests']['dynamic']={'finite':bool(np.isfinite(hard).all() and np.isfinite(natural).all()),'max_adjacent_jump_hard':float(np.abs(np.diff(hard)).max()),'max_adjacent_jump_input':float(np.abs(np.diff(x)).max())}
# Neutral/bypass exact timing + block-size invariance.
y1,_,_=run(x,bypass=True,chunk=127);y2,_,_=run(x,bypass=True,chunk=1024)
results['tests']['block_invariance_max_error']=float(np.max(abs(y1-y2)))
results['tests']['bypass_max_error_after_gain_settle']=float(np.max(abs(y1[sr:]-x[sr:])))
# Noise rejection and delayed unvoiced preservation.
noise=rng.normal(0,.08,sr*2);ny,nt,_=run(noise)
results['tests']['noise']={'voiced_fraction_after_start':float(np.mean(nt[nt[:,0]>.4,1]>0)),'unvoiced_rmse':float(np.sqrt(np.mean((ny[sr:]-noise[sr:])**2)))}
# Impulse invariance: measure aligned output lag with cross correlation.
imp=np.zeros(sr);imp[12000]=.5;iy,_,_=run(imp)
results['tests']['unvoiced_impulse_aligned_offset_samples']=int(np.argmax(iy)-12000)
# Synthetic source/filter vowel with known resonance peaks, spectral envelope inspection.
tv=np.arange(sr*3)/sr;f=np.full(len(tv),113.0);source=signal.sawtooth(np.cumsum(2*np.pi*f/sr),.65)*.15
vowel=source.copy()
for frequency,bw in [(650,100),(1150,130),(2500,180)]:
    r=np.exp(-np.pi*bw/sr);den=[1,-2*r*np.cos(2*np.pi*frequency/sr),r*r];vowel=signal.lfilter([1-r],den,vowel)
vowel*=.45/max(abs(vowel));vy,_,_=run(vowel)
for name,arr in [('vowel-before',vowel),('vowel-after',vy)]:wavfile.write(out/(name+'.wav'),sr,arr.astype(np.float32))
fig,axs=plt.subplots(3,1,figsize=(11,9),constrained_layout=True)
axs[0].plot(t,freq,label='Input F0',lw=1);mask=(track[:,1]>0)&(track[:,2]>0);axs[0].plot(track[mask,0]-.023,track[mask,2],'.',ms=1,label='Controller target');axs[0].set(ylabel='Hz',title='Synthetic melody / glissando / vibrato — controller trace');axs[0].legend()
freqs,p1=signal.welch(vowel[sr:],sr,nperseg=8192);_,p2=signal.welch(vy[sr:],sr,nperseg=8192)
axs[1].plot(freqs,10*np.log10(p1+1e-12),label='Before');axs[1].plot(freqs,10*np.log10(p2+1e-12),label='After',alpha=.7);axs[1].set(xlim=(0,3500),ylim=(-100,-20),ylabel='PSD dB',title='Synthetic vowel spectrum (not a perceptual score)');axs[1].legend()
_,_,_,im=axs[2].specgram(hard,Fs=sr,NFFT=1024,noverlap=768,cmap='magma');axs[2].set(ylim=(0,3000),xlabel='seconds',ylabel='Hz',title='Hard corrected synthetic vocal spectrogram')
fig.savefig(root/'evidence/signal-analysis.png',dpi=150);plt.close(fig)
# Ten-minute streaming stress, bounded arrays and no file I/O in the processing loop.
h=lib.vela_create(sr,0);lib.vela_settings(h,0,0,1,0,0,1,1,0)
block=harmonics(np.full(2048,225)).astype(np.float32);output=np.empty_like(block);start=time.perf_counter();finite=True
for i in range(int(sr*600/len(block))):
    lib.vela_process(h,block.ctypes.data_as(C.POINTER(C.c_float)),output.ctypes.data_as(C.POINTER(C.c_float)),len(block));finite&=bool(np.isfinite(output).all())
elapsed=time.perf_counter()-start;lib.vela_free(h)
results['tests']['ten_minute_stream']={'audio_seconds':600,'host_wall_seconds':elapsed,'finite':finite,'note':'Repeated 2048-frame block; boundary discontinuities intentional; not ten minutes of a real singer'}
(root/'evidence/objective-analysis.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results,indent=2))
assert all(abs(row['error_cents'])<20 for row in static),'steady pitch error gate'
assert results['tests']['block_invariance_max_error']<1e-7
assert results['tests']['noise']['voiced_fraction_after_start']<.02
assert finite
