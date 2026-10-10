"""Which raid song is audible when, in a capture of the game's audio (OpenAL Soft's wave backend).
For every 2-second window of the capture: loudness, and the song whose waveform matches best (normalised cross-correlation
against every .ogg in music_dir, all at 8 kHz mono), with the position in that song. Below r=0.5 the window is not music.
Usage: python3 which_song.py capture.wav music_dir out.txt   (needs numpy, scipy and ffmpeg)"""
import os, sys, subprocess, numpy as np
from scipy.signal import fftconvolve
RATE=8000
def load(path):
    raw=subprocess.run(["ffmpeg","-nostdin","-v","error","-i",path,"-ac","1","-ar",str(RATE),"-f","f32le","-"],capture_output=True,check=True).stdout
    return np.frombuffer(raw,dtype=np.float32).astype(np.float64)
cap=load(sys.argv[1]);music=sys.argv[2]
ids=sorted(f[:-4] for f in os.listdir(music) if f.endswith(".ogg"))
songs={i:load(f"{music}/{i}.ogg") for i in ids}
win=2*RATE;out=[]
for start in range(0,len(cap)-win,win):
    w=cap[start:start+win];rms=np.sqrt(np.mean(w**2));db=20*np.log10(rms+1e-9)
    best,score,where="-",0.0,0
    if db>-50:
        wn=(w-w.mean());norm=np.linalg.norm(wn)
        for i,s in songs.items():
            if len(s)<win:continue
            c=fftconvolve(s,wn[::-1],mode="valid")
            # local energy of the song under the window, for a normalised score
            e=np.sqrt(np.maximum(fftconvolve(s**2,np.ones(win),mode="valid"),1e-12))
            r=c/(e*norm);k=int(np.argmax(r))
            if r[k]>score:best,score,where=i,float(r[k]),k/RATE
    out.append(f"{start/RATE:7.1f}s {db:6.1f} dB  {best:18s} r={score:.2f} at {where:6.1f}s")
open(sys.argv[3],"w").write("\n".join(out)+"\n")
