"""A small deterministic synthesizer for the mod's own music and sound effects (numpy + scipy, no samples).

Everything here is original synthesis: oscillators (band-limited saw/pulse, sine), envelopes, filters, a few instruments
(brass, strings, choir, bass, drums, timpani, bells, distorted power chords), a stereo mixer with a convolution reverb,
echo taps and a soft limiter. `compose.py` writes the raid music and the victory fanfare with it, `sfx.py` the effects.
Output is written as WAV and encoded to Ogg Vorbis with ffmpeg (libvorbis), which is what Minecraft plays.
"""
import math
import subprocess
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
RNG = np.random.default_rng(16)


def seed(n):
    """Re-seed the noise source so every file is reproducible on its own."""
    global RNG
    RNG = np.random.default_rng(n)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12.0)


NOTE = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def m(name):
    """'C4', 'Bb3', 'F#5' -> MIDI number."""
    letter = name[0].upper(); rest = name[1:]; acc = 0
    while rest and rest[0] in 'b#':
        acc += -1 if rest[0] == 'b' else 1; rest = rest[1:]
    return 12 * (int(rest) + 1) + NOTE[letter] + acc


def n_of(seconds):
    return max(1, int(round(seconds * SR)))


def t_of(n):
    return np.arange(n) / SR


# ---- oscillators ----------------------------------------------------------------------------------------------------------------
def _blep(ph, dt):
    out = np.zeros_like(ph)
    a = ph < dt
    x = ph[a] / dt[a]; out[a] = x + x - x * x - 1
    b = ph > 1 - dt
    x = (ph[b] - 1) / dt[b]; out[b] = x * x + x + x + 1
    return out


def phase(freq, n, start=None):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    dt = f / SR
    p0 = RNG.random() if start is None else start
    return (p0 + np.cumsum(dt)) % 1.0, dt


def saw(freq, n, start=None):
    ph, dt = phase(freq, n, start)
    return 2 * ph - 1 - _blep(ph, dt)


def pulse(freq, n, width=0.5, start=None):
    ph, dt = phase(freq, n, start)
    a = 2 * ph - 1 - _blep(ph, dt)
    ph2 = (ph + width) % 1.0
    b = 2 * ph2 - 1 - _blep(ph2, dt)
    return (a - b) * 0.5


def sine(freq, n, start=0.0):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return np.sin(2 * np.pi * (start + np.cumsum(f) / SR))


def noise(n):
    return RNG.standard_normal(n)


def vibrato(base, n, rate=5.2, cents=9.0, delay=0.25):
    t = t_of(n)
    depth = np.clip((t - delay) / 0.4, 0, 1) * cents
    return base * 2 ** (depth * np.sin(2 * np.pi * rate * t + RNG.random() * 6.28) / 1200.0)


# ---- envelopes and filters ------------------------------------------------------------------------------------------------------
def adsr(n, a=0.01, d=0.1, s=0.8, r=0.1):
    """Attack, decay to the sustain level, hold, and release over the last r seconds (the note's own tail)."""
    nr = min(n, n_of(r)); nb = n - nr
    na = min(nb, n_of(a)); nd = min(nb - na, n_of(d))
    e = np.empty(n)
    e[:na] = np.linspace(0, 1, na, endpoint=False)
    e[na:na + nd] = np.linspace(1, s, nd, endpoint=False)
    e[na + nd:nb] = s
    level = e[nb - 1] if nb > 0 else 0.0
    e[nb:] = level * np.linspace(1, 0, nr) ** 1.5
    return e


def decay(n, tau):
    return np.exp(-t_of(n) / tau)


def _sos(kind, fc, order=2):
    if kind == 'band':
        lo, hi = fc
        return signal.butter(order, [max(20, lo) / (SR / 2), min(SR / 2 - 100, hi) / (SR / 2)], 'bandpass', output='sos')
    return signal.butter(order, min(SR / 2 - 100, max(20, fc)) / (SR / 2), kind, output='sos')


def lp(x, fc, order=2):
    return signal.sosfilt(_sos('low', fc, order), x)


def hp(x, fc, order=2):
    return signal.sosfilt(_sos('high', fc, order), x)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos('band', (lo, hi), order), x)


def sweep_lp(x, f0, f1, order=2, blocks=64):
    """A low-pass whose cutoff glides from f0 to f1 over the sound (block-wise, with filter state carried over)."""
    out = np.zeros_like(x); edges = np.linspace(0, len(x), blocks + 1).astype(int); zi = None
    for k in range(blocks):
        a, b = edges[k], edges[k + 1]
        if b <= a: continue
        fc = f0 * (f1 / f0) ** (k / max(1, blocks - 1))
        sos = _sos('low', fc, order)
        if zi is None: zi = np.zeros((sos.shape[0], 2))
        out[a:b], zi = signal.sosfilt(sos, x[a:b], zi=zi)
    return out


def fade(x, a=0.003, r=0.01):
    na, nr = min(len(x), n_of(a)), min(len(x), n_of(r))
    x = x.copy(); x[:na] *= np.linspace(0, 1, na); x[len(x) - nr:] *= np.linspace(1, 0, nr)
    return x


# ---- instruments (mono, peak about 1) -------------------------------------------------------------------------------------------
def brass(note, dur, vel=0.8, bright=1.0, voices=3):
    n = n_of(dur + 0.18)
    f = vibrato(hz(note), n, 5.0, 7.0, 0.3)
    x = sum(saw(f * 2 ** (c / 1200.0), n) for c in np.linspace(-7, 7, voices)) / voices
    env = adsr(n, 0.035, 0.18, 0.78, 0.16)
    swell = np.clip(t_of(n) / 0.09, 0, 1)
    dark = lp(x, 700 + 300 * vel, 2); light = lp(x, (1800 + 2600 * vel) * bright, 2)
    y = dark * (1 - 0.75 * swell * vel) + light * (0.75 * swell * vel)
    return fade(y * env * (0.55 + 0.45 * vel))


def horn(note, dur, vel=0.7):
    return brass(note, dur, vel * 0.85, bright=0.55, voices=4)


def strings(note, dur, vel=0.7, attack=0.22, bright=2600):
    n = n_of(dur + 0.45)
    f = vibrato(hz(note), n, 5.6, 10, 0.2)
    x = sum(saw(f * 2 ** (c / 1200.0), n) for c in (-14, -6, 0, 6, 14)) / 5
    y = lp(x, bright, 2) * adsr(n, attack, 0.3, 0.85, 0.45)
    return fade(y * vel)


def staccato(note, dur, vel=0.7):
    n = n_of(dur + 0.12)
    x = (saw(hz(note) * 2 ** (-8 / 1200.0), n) + saw(hz(note) * 2 ** (8 / 1200.0), n)) / 2
    return fade(lp(x, 3200, 2) * adsr(n, 0.006, 0.12, 0.35, 0.08) * vel)


def tremolo(note, dur, vel=0.5):
    y = strings(note, dur, vel, attack=0.08, bright=3600)
    t = t_of(len(y))
    return y * (0.65 + 0.35 * np.abs(np.sin(2 * np.pi * 8.5 * t)))


FORMANTS = {'a': ((700, 1100, 2600), (1.0, 0.5, 0.25)), 'o': ((450, 800, 2830), (1.0, 0.35, 0.1)),
            'e': ((400, 1900, 2600), (1.0, 0.45, 0.25)), 'u': ((330, 700, 2700), (1.0, 0.3, 0.08))}


def choir(note, dur, vel=0.6, vowel='a', voices=4):
    n = n_of(dur + 0.6)
    fs, gains = FORMANTS[vowel]
    acc = np.zeros(n)
    for k in range(voices):
        f = vibrato(hz(note) * 2 ** ((k - (voices - 1) / 2) * 6 / 1200.0), n, 4.6 + 0.4 * k, 14, 0.15)
        src = saw(f, n) + 0.08 * noise(n)
        acc += sum(g * bp(src, fc * 0.82, fc * 1.18, 2) for fc, g in zip(fs, gains))
    y = acc / voices * adsr(n, 0.35, 0.3, 0.9, 0.6)
    return fade(y / (np.max(np.abs(y)) + 1e-9) * vel)


def bass(note, dur, vel=0.8, bright=900):
    n = n_of(dur + 0.08)
    x = 0.6 * saw(hz(note), n) + 0.55 * sine(hz(note), n)
    y = lp(x, bright, 2) * adsr(n, 0.005, 0.12, 0.75, 0.06)
    return fade(y * vel)


def pluck(note, dur, vel=0.6, width=0.3, bright=4200):
    n = n_of(dur + 0.2)
    x = pulse(hz(note), n, width)
    y = sweep_lp(x, bright, 500, 2, 24) * adsr(n, 0.002, 0.18, 0.25, 0.12)
    return fade(y * vel)


def lead(note, dur, vel=0.6, glide_from=None):
    n = n_of(dur + 0.12)
    base = hz(note)
    if glide_from is not None:
        g = np.minimum(t_of(n) / 0.09, 1)
        f = hz(glide_from) * (base / hz(glide_from)) ** g
    else:
        f = np.full(n, base)
    f = vibrato(f, n, 5.8, 12, 0.18)
    x = 0.6 * pulse(f, n, 0.25) + 0.4 * saw(f * 1.003, n)
    return fade(lp(x, 3400, 2) * adsr(n, 0.01, 0.1, 0.8, 0.1) * vel)


def power(root, dur, vel=0.8, palm=False):
    """A distorted power chord (root, fifth, octave) like an overdriven guitar."""
    n = n_of(dur + (0.05 if palm else 0.25))
    x = sum(saw(hz(root + iv) * 2 ** (c / 1200.0), n) for iv in (0, 7, 12) for c in (-6, 5)) / 6
    x = np.tanh(x * 7.0)
    y = bp(x, 90, 3800 if not palm else 1600, 2)
    y = lp(y, 5200, 2) * adsr(n, 0.004, 0.08 if palm else 0.25, 0.25 if palm else 0.7, 0.05 if palm else 0.2)
    return fade(y * vel)


def taiko(vel=0.9, low=1.0):
    n = n_of(1.0)
    t = t_of(n)
    f = (70 * low) * (0.62 + 0.38 * np.exp(-t / 0.05))
    body = sine(f, n) * decay(n, 0.32)
    skin = lp(noise(n), 1400, 2) * decay(n, 0.04)
    return fade((body + 0.35 * skin) * vel)


def kick(vel=0.9):
    n = n_of(0.5)
    t = t_of(n)
    f = 48 + 75 * np.exp(-t / 0.035)
    return fade((sine(f, n) * decay(n, 0.22) + 0.25 * hp(noise(n), 3000) * decay(n, 0.006)) * vel)


def snare(vel=0.7, tone=190):
    n = n_of(0.35)
    rattle = bp(noise(n), 1700, 7500, 2) * decay(n, 0.11)
    body = sine(tone * (1 + 0.3 * np.exp(-t_of(n) / 0.02)), n) * decay(n, 0.06)
    return fade((rattle * 0.9 + body * 0.6) * vel)


def roll(dur, vel0=0.2, vel1=0.9, rate=24.0, tone=190):
    """A snare roll that swells from vel0 to vel1."""
    out = np.zeros(n_of(dur + 0.4)); hits = int(dur * rate)
    for k in range(hits):
        v = vel0 + (vel1 - vel0) * (k / max(1, hits - 1)) ** 1.6
        s = snare(v * (0.85 + 0.15 * RNG.random()), tone); i = n_of(k / rate)
        out[i:i + len(s)] += s[:max(0, len(out) - i)]
    return out


def hat(vel=0.4, open_=False):
    n = n_of(0.35 if open_ else 0.06)
    return fade(hp(noise(n), 7000, 2) * decay(n, 0.12 if open_ else 0.018) * vel)


def crash(vel=0.7, length=2.6):
    n = n_of(length)
    t = t_of(n)
    metal = sum(np.sin(2 * np.pi * f * t + RNG.random() * 6) * decay(n, length * (0.25 + 0.2 * RNG.random())) for f in (3150, 4270, 5480, 6930, 8120, 9950))
    x = hp(noise(n), 4200, 2) * decay(n, length * 0.35) + 0.12 * metal
    return fade(x * vel / 2.0, 0.001, 0.2)


def timpani(note, vel=0.8, length=1.6):
    n = n_of(length)
    t = t_of(n)
    f0 = hz(note) * (1 + 0.02 * np.exp(-t / 0.05))
    x = sine(f0, n) * decay(n, length * 0.45) + 0.45 * sine(f0 * 1.505, n) * decay(n, length * 0.25) + 0.25 * sine(f0 * 1.98, n) * decay(n, length * 0.18)
    strike = lp(noise(n), 900, 2) * decay(n, 0.03)
    return fade((x + 0.5 * strike) * vel)


def timpani_roll(note, dur, vel0=0.2, vel1=0.9):
    out = np.zeros(n_of(dur + 1.6)); rate = 16.0; hits = int(dur * rate)
    for k in range(hits):
        v = vel0 + (vel1 - vel0) * (k / max(1, hits - 1)) ** 1.4
        s = timpani(note, v, 1.2); i = n_of(k / rate)
        out[i:i + len(s)] += s[:len(out) - i]
    return out * 0.6


def bell(note, vel=0.5, length=2.4):
    n = n_of(length)
    t = t_of(n)
    f = hz(note)
    parts = ((1.0, 1.0, 1.0), (2.0, 0.55, 0.6), (2.76, 0.4, 0.45), (5.4, 0.25, 0.3), (8.93, 0.12, 0.2))
    x = sum(a * np.sin(2 * np.pi * f * r * t) * decay(n, length * dk) for r, a, dk in parts)
    return fade(x / 2.3 * vel, 0.001, 0.05)


def riser(dur, f0=300, f1=6000, vel=0.5):
    n = n_of(dur)
    x = sweep_lp(noise(n), f0, f1, 2, 48)
    return fade(x * np.linspace(0, 1, n) ** 2 * vel * 0.5)


def tom(note, vel=0.8):
    n = n_of(0.6)
    t = t_of(n)
    f = hz(note) * (1 + 0.35 * np.exp(-t / 0.04))
    return fade((sine(f, n) * decay(n, 0.2) + 0.2 * lp(noise(n), 2000) * decay(n, 0.03)) * vel)


# ---- mixing ---------------------------------------------------------------------------------------------------------------------
def pan_gains(p):
    a = (p + 1) * math.pi / 4
    return math.cos(a), math.sin(a)


class Mix:
    """A stereo bus with a reverb send. Times are in seconds; notes that run past the end are kept for the loop wrap."""

    def __init__(self, seconds, tail=6.0):
        self.length = n_of(seconds)
        self.n = self.length + n_of(tail)
        self.dry = np.zeros((2, self.n))
        self.send = np.zeros((2, self.n))

    def add(self, at, mono, gain=1.0, pan=0.0, rev=0.25):
        i = n_of(at) if at > 0 else 0
        if i >= self.n: return
        seg = mono[:self.n - i] * gain
        gl, gr = pan_gains(pan)
        self.dry[0, i:i + len(seg)] += seg * gl; self.dry[1, i:i + len(seg)] += seg * gr
        if rev > 0:
            self.send[0, i:i + len(seg)] += seg * gl * rev; self.send[1, i:i + len(seg)] += seg * gr * rev

    def render(self, ir, loop=True):
        wet = np.stack([signal.fftconvolve(self.send[c], ir[c])[:self.n] for c in (0, 1)])
        out = self.dry + wet
        if loop:
            # everything that rings past the loop point is folded back onto its start: the file loops without a seam
            head = out[:, :self.length].copy(); tailpart = out[:, self.length:]
            k = min(tailpart.shape[1], self.length); head[:, :k] += tailpart[:, :k]
            return head
        return out


def reverb_ir(seconds=2.6, rt60=2.2, damp=0.45, early=True, stereo=True):
    n = n_of(seconds)
    t = t_of(n)
    chans = []
    for c in range(2):
        x = noise(n)
        lowband = lp(x, 2500, 2) * np.exp(-t * 6.9 / rt60)
        highband = hp(x, 2500, 2) * np.exp(-t * 6.9 / (rt60 * damp))
        ir = lowband + 0.6 * highband
        ir *= np.clip(t / 0.012, 0, 1)
        if early:
            for d, g in ((0.011, 0.6), (0.019, 0.45), (0.027, 0.4), (0.041, 0.3), (0.057, 0.25)):
                k = n_of(d + 0.003 * c); ir[k] += g * (1 if (k + c) % 2 else -1) * 4
        chans.append(ir / np.sqrt(np.sum(ir ** 2)))
    return np.stack(chans) * 0.8


def echoes(x, taps, cutoff=2400):
    """Delayed, darker copies of a mono sound: (delay seconds, gain) pairs, like a blast bouncing off hills."""
    out = np.concatenate([x, np.zeros(n_of(max(d for d, _ in taps) + 0.2))])
    for d, g in taps:
        i = n_of(d); seg = lp(x, cutoff * (1 - 0.15 * d), 2) * g
        out[i:i + len(seg)] += seg[:len(out) - i]
    return out


def limit(x, drive=1.4, peak=0.89):
    y = np.tanh(x * drive / (np.max(np.abs(x)) + 1e-9)) / math.tanh(drive)
    return y * peak


def eq(stereo, low_cut=0.5, presence=0.45):
    """Mastering EQ: thins the low end below about 120 Hz and lifts the 1.5 - 5 kHz presence band, so melodies carry on small speakers."""
    out = []
    for ch in stereo:
        ch = hp(ch, 28, 2)
        ch = ch - low_cut * lp(ch, 120, 2) + presence * bp(ch, 1500, 5000, 2)
        out.append(ch)
    return np.stack(out)


def master(stereo, target_rms_db=-17.0, peak=0.89):
    stereo = eq(stereo)
    x = stereo / (np.max(np.abs(stereo)) + 1e-9)
    rms = np.sqrt(np.mean(x ** 2))
    gain = 10 ** (target_rms_db / 20) / (rms + 1e-9)
    x = x * gain
    # soft knee limiter: keeps the loudness, rounds the few peaks above the ceiling
    return np.tanh(x / peak) * peak


def write(path_ogg, data, quality=5, stereo=True):
    """Writes 16-bit WAV next to the target and encodes it to Ogg Vorbis with ffmpeg; returns the encoded size."""
    wav = path_ogg[:-4] + '.wav'
    arr = data.T if stereo else data
    wavfile.write(wav, SR, (np.clip(arr, -1, 1) * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', str(quality), path_ogg], check=True)
    import os
    os.remove(wav)
    return os.path.getsize(path_ogg)
