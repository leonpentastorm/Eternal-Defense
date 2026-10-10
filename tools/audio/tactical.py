"""Sounds of the Command Table and the hunting missions (0.0.20; original synthesis, mono Ogg Vorbis).

Radio and console sounds, short and dry, so they read as an interface and never as a weapon. Peaks are set so each sits near
-17 dB mean, level with the owner's radio chatter. Run:
  python tools/audio/tactical.py <assets/arsenal_beacon/sounds/sfx> [name ...]
"""
import os
import sys
import numpy as np
import synth as S
from sfx import place, finish, mono_reverb

n_of, t_of, noise, lp, hp, bp, decay, sine = S.n_of, S.t_of, S.noise, S.lp, S.hp, S.bp, S.decay, S.sine


def beep(f, dur, vel=1.0, square=0.25):
    """A console beep: a sine with a little odd-harmonic edge and soft ends."""
    n = n_of(dur); t = t_of(n)
    tone = sine(f, n) + square * sine(3 * f, n) / 3 + square * sine(5 * f, n) / 5
    env = np.minimum(1, t / 0.004) * np.minimum(1, (dur - t) / 0.02)
    return tone * env * vel


def squelch(dur, vel=0.5):
    """The hiss of a radio channel opening or closing."""
    n = n_of(dur); t = t_of(n)
    return bp(noise(n), 900, 5200, 2) * np.minimum(1, t / 0.01) * np.exp(-t / (dur * 0.45)) * vel


def radio(x):
    """Narrow band, a touch of drive: it sounds like it came over a handset."""
    return np.tanh(bp(x, 380, 3600, 2) * 1.6) / 1.2


def tactical_accept():
    """Orders confirmed: squelch, two rising beeps, squelch out."""
    S.seed(31)
    out = np.zeros(n_of(1.0))
    place(out, 0.0, squelch(0.12, 0.45))
    place(out, 0.08, beep(988, 0.09))
    place(out, 0.20, beep(1319, 0.16))
    place(out, 0.40, squelch(0.18, 0.35))
    return finish(mono_reverb(radio(out), 0.6, 0.12), 0.4)


def tactical_refused():
    """Denied: two short low buzzes."""
    S.seed(32)
    out = np.zeros(n_of(0.6))
    for at in (0.0, 0.17):
        n = n_of(0.12); t = t_of(n)
        buzz = np.sign(sine(196, n)) * 0.5 + sine(196, n) * 0.5
        place(out, at, lp(buzz, 2200, 2) * np.minimum(1, t / 0.004) * np.minimum(1, (0.12 - t) / 0.015), 0.8)
    return finish(radio(out), 0.34)


def tactical_scan():
    """The satellite sweep starts: a long sonar ping falling away, data chirps under it."""
    S.seed(33)
    out = np.zeros(n_of(2.6))
    n = n_of(1.8); t = t_of(n)
    ping = (sine(1480, n) * 0.8 + sine(2960, n) * 0.15) * np.minimum(1, t / 0.005) * np.exp(-t / 0.45)
    place(out, 0.0, ping, 0.9)
    for i in range(14):
        at = 0.25 + i * 0.11 + 0.03 * S.RNG.random()
        place(out, at, beep(2200 + 900 * S.RNG.random(), 0.025, 0.25, 0.4))
    n = n_of(2.2); t = t_of(n)
    place(out, 0.1, bp(noise(n), 2500, 7000, 2) * np.sin(np.pi * t / 2.2) ** 2 * 0.08)
    return finish(mono_reverb(out, 1.6, 0.35), 0.75)


def tactical_scan_done():
    """The scan is in: three quick rising pings."""
    S.seed(34)
    out = np.zeros(n_of(1.1))
    for at, f in ((0.0, 1175), (0.09, 1480), (0.18, 1976)):
        n = n_of(0.5); t = t_of(n)
        place(out, at, sine(f, n) * np.minimum(1, t / 0.003) * np.exp(-t / 0.12), 0.7)
    return finish(mono_reverb(out, 0.9, 0.25), 0.55)


def tactical_contact():
    """Contact: the warband is out. A hard squelch, an urgent two-tone alert three times, static."""
    S.seed(35)
    out = np.zeros(n_of(1.6))
    place(out, 0.0, squelch(0.15, 0.7))
    for k in range(3):
        at = 0.1 + k * 0.26
        place(out, at, beep(1046, 0.1, 0.9, 0.5))
        place(out, at + 0.11, beep(784, 0.12, 0.9, 0.5))
    n = n_of(1.0); t = t_of(n)
    place(out, 0.6, bp(noise(n), 700, 4000, 2) * np.exp(-t / 0.35) * 0.25)
    return finish(mono_reverb(radio(out), 0.7, 0.15), 0.5)


EFFECTS = {f.__name__: f for f in (tactical_accept, tactical_refused, tactical_scan, tactical_scan_done, tactical_contact)}

if __name__ == '__main__':
    out_dir = sys.argv[1]; os.makedirs(out_dir, exist_ok=True)
    for name in sys.argv[2:] or list(EFFECTS):
        data = EFFECTS[name]()
        size = S.write(os.path.join(out_dir, name + '.ogg'), data, quality=5, stereo=False)
        print(f'{name}: {len(data) / S.SR:.2f} s, {size // 1024} KiB')
