"""Sound effects of the support cannon, the flares and the fire supports (original synthesis, mono Ogg Vorbis).

Each function returns a mono numpy array at 44.1 kHz. The loud ones carry their own outdoor echo (delayed, darker copies)
and a reverb tail, so a blast keeps rolling around the base. Run:
  python tools/audio/sfx.py <assets/arsenal_beacon/sounds/sfx> [name ...]
"""
import os
import sys
import numpy as np
import synth as S

n_of, t_of, noise, lp, hp, bp, decay, sine = S.n_of, S.t_of, S.noise, S.lp, S.hp, S.bp, S.decay, S.sine


def place(buf, at, x, gain=1.0):
    i = n_of(at) if at > 0 else 0
    seg = x[:max(0, len(buf) - i)] * gain
    buf[i:i + len(seg)] += seg
    return buf


def metal(n, freqs, tau, jitter=0.02):
    t = t_of(n)
    return sum(np.sin(2 * np.pi * f * (1 + jitter * (S.RNG.random() - .5)) * t) * decay(n, tau * (0.6 + 0.8 * S.RNG.random())) for f in freqs) / len(freqs)


def boom(dur, f0, f1, tau, body_cut=900, crack=1.0, body=0.6):
    n = n_of(dur)
    t = t_of(n)
    f = f1 + (f0 - f1) * np.exp(-t / 0.12)
    low = sine(f, n) * decay(n, tau)
    rumble = lp(noise(n), 180, 2) * decay(n, tau * 1.3)
    mid = bp(noise(n), 120, body_cut, 2) * decay(n, tau * 0.45)
    snap = hp(noise(n), 1800, 2) * decay(n, 0.012)
    return low * 1.0 + rumble * 0.9 + mid * body + snap * crack


def debris(dur, rate=35, start=0.15):
    n = n_of(dur); out = np.zeros(n)
    k = int(rate * dur)
    for _ in range(k):
        at = start + S.RNG.random() ** 1.6 * (dur - start - 0.1)
        m = n_of(0.03); click = hp(noise(m), 1200 + 2500 * S.RNG.random(), 2) * decay(m, 0.006) * (0.2 + 0.8 * S.RNG.random()) * (1 - at / dur)
        place(out, at, click)
    return out


def mono_reverb(x, rt60=2.2, mix=0.3):
    ir = S.reverb_ir(rt60 * 1.2, rt60, 0.4)[0]
    from scipy import signal
    wet = signal.fftconvolve(x, ir)[:len(x)]
    return x * (1 - mix) + wet * mix * 2.2


def trim(x, floor_db=-58.0):
    """Cuts the silent end (below floor_db for good) so a long echo does not hold a sound channel for nothing."""
    env = np.sqrt(np.convolve(x ** 2, np.ones(n_of(0.05)) / n_of(0.05), 'same'))
    loud = np.nonzero(env > 10 ** (floor_db / 20) * np.max(np.abs(x)))[0]
    end = min(len(x), (loud[-1] if len(loud) else len(x)) + n_of(0.05))
    return x[:end]


def finish(x, peak=0.95, tail=0.02):
    x = x - np.mean(x)
    x = S.limit(x, 1.2, peak)
    x = trim(x)
    return S.fade(x, 0.001, max(tail, 0.04))


def pad(x, seconds):
    return np.concatenate([x, np.zeros(n_of(seconds))])


# ---- the cannon ---------------------------------------------------------------------------------------------------------------
def cannon_fire():
    S.seed(1)
    out = pad(boom(1.4, 70, 34, 0.45, 2600, 1.9, 1.1), 2.6)
    place(out, 0.07, metal(n_of(0.6), (412, 977, 1630, 2440, 3310), 0.28), 0.5)    # the recoil clank of the mount
    place(out, 0.42, metal(n_of(0.4), (520, 1240, 2010), 0.15), 0.25)              # the breech running back
    out = S.echoes(out, [(0.38, 0.34), (0.85, 0.22), (1.5, 0.13), (2.3, 0.07)], 1800)
    return finish(mono_reverb(out, 1.8, 0.28))


def cannon_traverse():
    S.seed(2)
    n = n_of(0.5); t = t_of(n)
    hum = lp(S.saw(55, n) + 0.6 * S.saw(110, n) + 0.3 * S.saw(165, n), 700, 2) * (0.75 + 0.25 * np.sin(2 * np.pi * 12 * t))
    ticks = np.zeros(n)
    for k in range(int(0.5 * 22)):
        m = n_of(0.025); place(ticks, k / 22 + 0.004 * S.RNG.random(), metal(m, (2100, 2900, 3700), 0.008) * (0.5 + 0.5 * S.RNG.random()))
    grind = bp(noise(n), 600, 2400, 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t + 1.3))
    x = hum * 0.7 + ticks * 0.8 + grind * 0.35
    env = np.minimum(1, np.minimum(t / 0.05, (0.5 - t) / 0.08))
    return finish(x * env, 0.8, 0.02)


def cannon_clank():
    S.seed(3)
    out = np.zeros(n_of(0.7))
    place(out, 0, metal(n_of(0.5), (280, 700, 1210, 1900), 0.2), 0.8)
    place(out, 0.06, metal(n_of(0.4), (330, 820, 1450), 0.14), 0.5)
    place(out, 0, sine(90, n_of(0.3)) * decay(n_of(0.3), 0.08), 0.6)
    return finish(mono_reverb(out, 0.9, 0.2), 0.85)


def cannon_lock():
    S.seed(4)
    out = np.zeros(n_of(1.4))
    place(out, 0, metal(n_of(0.4), (640, 1500, 2600, 3900), 0.09) + 0.6 * hp(noise(n_of(0.4)), 2500) * decay(n_of(0.4), 0.01), 0.7)
    place(out, 0.09, metal(n_of(0.6), (210, 530, 980, 1700), 0.22), 0.9)
    place(out, 0.09, sine(70, n_of(0.5)) * decay(n_of(0.5), 0.12), 0.9)
    place(out, 0.14, bp(noise(n_of(0.9)), 2000, 8000, 2) * decay(n_of(0.9), 0.35), 0.22)   # the hydraulics settle
    return finish(mono_reverb(out, 1.1, 0.22), 0.9)


# ---- flares --------------------------------------------------------------------------------------------------------------------
def flare_signal():
    """A flare lands: a sharp pop, a piercing rising whistle, then a loud sizzling burn. Meant to turn every head in the chunk."""
    S.seed(5)
    out = np.zeros(n_of(3.6))
    pop = hp(noise(n_of(0.2)), 900, 2) * decay(n_of(0.2), 0.02) + sine(900, n_of(0.2)) * decay(n_of(0.2), 0.03)
    place(out, 0, pop, 1.0)
    n = n_of(1.1); t = t_of(n)
    f = 1100 * (3.3) ** (t / 1.1)
    whistle = sine(f, n) * 0.8 + bp(noise(n), 1500, 4500, 2) * 0.25
    whistle *= np.minimum(1, t / 0.08) * (0.7 + 0.3 * np.sin(2 * np.pi * 9 * t)) * np.minimum(1, (1.1 - t) / 0.15)
    place(out, 0.05, whistle, 0.75)
    n = n_of(2.8); t = t_of(n)
    crackle = np.zeros(n)
    for _ in range(140):
        at = S.RNG.random() * 2.6; m = n_of(0.01)
        place(crackle, at, hp(noise(m), 3000, 2) * decay(m, 0.002) * (0.3 + 0.7 * S.RNG.random()))
    sizzle = (hp(noise(n), 3500, 2) * 0.35 + crackle) * np.minimum(1, t / 0.1) * np.exp(-t / 1.6)
    place(out, 0.35, sizzle, 0.8)
    return finish(mono_reverb(out, 2.0, 0.3), 0.97)


def whistle(dur, f0, f1, rough=0.0, sub=0.0):
    n = n_of(dur); t = t_of(n)
    f = f1 + (f0 - f1) * np.exp(-t / (dur * 0.55))
    tone = sine(f * (1 + 0.004 * np.sin(2 * np.pi * 7 * t)), n)
    air = bp(noise(n), 500, 5000, 2) * 0.18
    x = tone + air + rough * bp(noise(n), 400, 2200, 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 31 * t))
    if sub: x += sub * lp(noise(n), 120, 2) * (t / dur)
    env = (0.18 + 0.82 * (t / dur) ** 1.8) * np.minimum(1, (dur - t) / 0.06)
    return x * env


def shell_whistle():
    S.seed(6)
    return finish(whistle(1.55, 2600, 780), 0.9, 0.05)


def bomb_whistle():
    S.seed(7)
    return finish(whistle(1.55, 1500, 340, rough=0.55, sub=0.8), 0.95, 0.05)


# ---- impacts -------------------------------------------------------------------------------------------------------------------
def shell_explosion():
    S.seed(8)
    out = pad(boom(1.8, 62, 28, 0.75, 2500, 1.6, 0.9), 3.4)
    place(out, 0, debris(2.0, 40, 0.2), 0.5)
    out = S.echoes(out, [(0.42, 0.45), (0.95, 0.3), (1.6, 0.18), (2.4, 0.1), (3.3, 0.05)], 1500)
    return finish(mono_reverb(out, 3.0, 0.35))


def bunker_impact():
    """The bomb hits: a huge low hit, the ground shaking, two more blasts as it bores in, debris, and a long echo off the hills."""
    S.seed(9)
    n = n_of(7.5)
    out = np.zeros(n)
    place(out, 0, boom(2.8, 46, 22, 1.4, 1200, 1.8, 1.0), 1.0)
    m = n_of(4.5); t = t_of(m)
    place(out, 0.1, lp(noise(m), 110, 2) * decay(m, 1.6) * (0.7 + 0.3 * np.sin(2 * np.pi * 5.5 * t)), 0.9)
    place(out, 0.35, boom(1.5, 70, 30, 0.5, 900, 0.8, 0.7), 0.55)
    place(out, 0.8, boom(1.5, 60, 28, 0.45, 800, 0.6, 0.6), 0.4)
    place(out, 0, debris(3.6, 55, 0.25), 0.55)
    out = S.echoes(out, [(0.55, 0.5), (1.2, 0.35), (2.0, 0.22), (3.0, 0.12), (4.2, 0.06)], 1200)
    return finish(mono_reverb(out, 3.5, 0.4))


def bunker_dig():
    S.seed(10)
    out = pad(lp(boom(1.2, 50, 26, 0.5, 500, 0.0, 0.8), 450, 2), 0.6)
    m = n_of(0.5)
    place(out, 0, bp(noise(m), 200, 1300, 2) * decay(m, 0.12), 0.9)     # the thud of rock giving way, audible on small speakers
    place(out, 0.05, debris(1.2, 30, 0.05), 0.35)
    return finish(mono_reverb(out, 1.4, 0.25), 0.8)


def cluster_pop():
    S.seed(11)
    out = np.zeros(n_of(2.2))
    for k in range(7):
        place(out, 0.06 * k + 0.05 * S.RNG.random(), boom(0.6, 120, 60, 0.12, 2200, 1.4, 0.7), 0.5 + 0.4 * S.RNG.random())
    out = S.echoes(out, [(0.35, 0.3), (0.8, 0.15)], 2200)
    return finish(mono_reverb(out, 1.6, 0.25))


def cryo_burst():
    S.seed(12)
    out = np.zeros(n_of(2.4))
    place(out, 0, boom(0.8, 90, 50, 0.15, 1500, 1.0, 0.4), 0.6)
    for _ in range(60):
        at = S.RNG.random() ** 2 * 0.45; m = n_of(0.08)
        place(out, at, metal(m, (3000 + 6000 * S.RNG.random(), 4500 + 5000 * S.RNG.random()), 0.025), 0.25 + 0.2 * S.RNG.random())
    m = n_of(1.8); t = t_of(m)
    place(out, 0.05, hp(noise(m), 4000, 2) * np.exp(-t / 0.7), 0.35)
    return finish(mono_reverb(out, 1.8, 0.3))


def napalm_whoosh():
    S.seed(13)
    n = n_of(2.6); t = t_of(n)
    whoosh = S.sweep_lp(noise(n), 300, 3000, 2, 48) * np.sin(np.pi * np.minimum(1, t / 0.9)) ** 2 * 0.6
    roar = lp(noise(n), 900, 2) * np.minimum(1, t / 0.2) * np.exp(-t / 1.6)
    crackle = np.zeros(n)
    for _ in range(120):
        place(crackle, S.RNG.random() * 2.4, hp(noise(n_of(0.012)), 2500, 2) * decay(n_of(0.012), 0.003) * S.RNG.random())
    x = whoosh + roar * 0.8 + crackle * 0.7
    return finish(mono_reverb(x, 1.6, 0.25))


def gravity_hum():
    S.seed(14)
    n = n_of(3.0); t = t_of(n)
    hum = (sine(55, n) + 0.7 * sine(82.5, n) + 0.4 * sine(110 * (1 + 0.01 * np.sin(2 * np.pi * .7 * t)), n)) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * t))
    suck = sine(600 * (0.33) ** (t / 3.0), n) * 0.25
    swell = S.sweep_lp(noise(n), 200, 2000, 2, 32) * (t / 3.0) ** 2 * 0.5
    x = (hum * 0.7 + suck + swell) * np.minimum(1, t / 0.3) * np.minimum(1, (3.0 - t) / 0.1)
    return finish(mono_reverb(x, 2.0, 0.3), 0.85)


def gravity_implode():
    S.seed(15)
    n = n_of(0.9); t = t_of(n)
    rise = S.sweep_lp(noise(n), 200, 6000, 2, 32) * (t / 0.9) ** 3
    out = pad(rise * 0.7, 1.6)
    place(out, 0.9, boom(1.2, 80, 34, 0.35, 1200, 1.2, 0.7), 1.0)
    return finish(mono_reverb(out, 1.8, 0.3))


def shockwave_blast():
    S.seed(16)
    out = pad(boom(1.0, 75, 40, 0.25, 3000, 2.0, 0.5), 1.8)
    n = n_of(1.5); t = t_of(n)
    place(out, 0.02, S.sweep_lp(noise(n), 3000, 300, 2, 32) * np.exp(-t / 0.5), 0.8)
    out = S.echoes(out, [(0.4, 0.35), (0.9, 0.2), (1.5, 0.1)], 1800)
    return finish(mono_reverb(out, 2.0, 0.3))


def starshell_pop():
    S.seed(17)
    out = np.zeros(n_of(4.0))
    place(out, 0, lp(boom(0.8, 140, 70, 0.2, 1800, 1.0, 0.6), 2500, 2), 0.8)
    n = n_of(3.4); t = t_of(n)
    burn = (hp(noise(n), 2500, 2) * 0.4 + debris(3.4, 60, 0.0) * 0.8) * np.minimum(1, t / 0.15) * np.exp(-t / 2.2)
    place(out, 0.25, burn, 0.7)
    return finish(mono_reverb(out, 2.4, 0.35))


EFFECTS = {f.__name__: f for f in (cannon_fire, cannon_traverse, cannon_clank, cannon_lock, flare_signal, shell_whistle, bomb_whistle, shell_explosion,
                                    bunker_impact, bunker_dig, cluster_pop, cryo_burst, napalm_whoosh, gravity_hum, gravity_implode, shockwave_blast, starshell_pop)}

if __name__ == '__main__':
    out_dir = sys.argv[1]; os.makedirs(out_dir, exist_ok=True)
    for name in sys.argv[2:] or list(EFFECTS):
        data = EFFECTS[name]()
        size = S.write(os.path.join(out_dir, name + '.ogg'), data, quality=5, stereo=False)
        print(f'{name}: {len(data) / S.SR:.2f} s, {size // 1024} KiB')
