#!/usr/bin/env python3
"""Converts the owner's hand-tuned sound effects into the OGG files the game plays (0.0.18).

Usage: python3 import_sfx.py <folder with the SFX-*.wav/mp3/flac files> <out_dir>

Every positional sound is made mono (Minecraft only places mono sounds in the world; a stereo file plays at full volume
everywhere), resampled to 48 kHz, trimmed where noted below and limited to a -1 dBFS peak when it was hotter (the turning
"done" clunk decodes at +8 dBFS). The cluster bomblet is then lowered 6 dB: the game plays many at once, and a volume
above 1 in the code only makes a sound carry farther (Minecraft caps its loudness at 1), so a quieter sound has to be quieter in
the file. The turning grind becomes a seamless loop (its end is crossfaded into its start). The
attack alarm stays stereo: it is a warning played to the player, not a sound in the world. Needs numpy and ffmpeg (libvorbis).

The trim points matter to the code: the cannon fires the moment cannon_turning_done.ogg ends (SupportCannon.DONE_TICKS) and a
round lands when ordnance_incoming.ogg reaches its impact (SupportRules.BLAST_FLIGHT_TICKS); OwnerSoundsTest checks both
against the files.
"""
import os, subprocess, sys
import numpy as np

RATE = 48000
# file, output name, mono, (start, end) seconds or None, fade-out seconds at the end, loop crossfade seconds, gain dB after the limit
SOUNDS = [
    ("SFX-Cannon-Fire.mp3", "cannon_fire", True, None, 0.0, 0.0, 0),
    ("SFX-CannonTurning-Start.wav", "cannon_turning", True, None, 0.0, 0.10, 0),
    ("SFX-CannonTurning-Done.mp3", "cannon_turning_done", True, (0.28, 1.15), 0.05, 0.0, 0),
    ("SFX-Bomb-Incoming.mp3", "ordnance_incoming", True, (0.0, 5.35), 0.15, 0.0, 0),
    ("SFX-Bunker-Buster.mp3", "bunker_buster", True, None, 0.0, 0.0, 0),
    ("SFX-Cluster-Strike.flac", "cluster_strike", True, (0.0, 0.45), 0.2, 0.0, -6),
    ("SFX-Shockwave.wav", "shockwave", True, (0.065, None), 0.0, 0.0, 0),
    ("SFX-Beacon-Attacked.wav", "beacon_attacked", False, None, 0.0, 0.0, 0),
]


def load(path, mono):
    raw = subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", path, "-ac", "1" if mono else "2", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).astype(np.float64)
    return x if mono else x.reshape(-1, 2)


def write(path, x, mono):
    data = x.astype(np.float32).tobytes()
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-v", "error", "-f", "f32le", "-ar", str(RATE), "-ac", "1" if mono else "2", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "5", path], input=data, check=True)


def process(src, mono, span, fade, loop, gain):
    x = load(src, mono)
    if span:
        a = int(span[0] * RATE); b = len(x) if span[1] is None else int(span[1] * RATE)
        x = x[a:b]
    if fade > 0:
        n = int(fade * RATE); ramp = np.linspace(1, 0, n)
        x[-n:] = (x[-n:].T * ramp).T
    if loop > 0:
        n = int(loop * RATE); fin = np.linspace(0, 1, n)
        head, tail = x[:n].copy(), x[-n:].copy()
        x = x[:-n].copy()
        x[:n] = (head.T * fin + tail.T * (1 - fin)).T
    peak = np.abs(x).max()
    limit = 10 ** (-1 / 20)
    if peak > limit:
        x = x * (limit / peak)
    return x * 10 ** (gain / 20)


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    for name, target, mono, span, fade, loop, gain in SOUNDS:
        x = process(os.path.join(src, name), mono, span, fade, loop, gain)
        write(os.path.join(out, target + ".ogg"), x, mono)
        print(f"{target:22s} {'mono' if mono else 'stereo':6s} {len(x) / RATE:6.3f} s  peak {20 * np.log10(np.abs(x).max()):5.1f} dBFS")


if __name__ == "__main__":
    main()
