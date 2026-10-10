#!/usr/bin/env python3
"""Finds where each sound effect plays in a recording of the game's audio output.

Usage: find_sfx.py <capture.wav> <name=file.ogg[@pitch,pitch...]> ... [--threshold 0.45] [--from S] [--to S]

The capture is downmixed to mono and resampled to 16 kHz; each sound (at each listed pitch: OpenAL plays a pitch by
resampling, so a pitch p shortens the sound by 1/p) is slid along it and the normalised cross-correlation is computed
(1.00 = the capture at that moment is exactly that sound, scaled). Peaks above the threshold, at least half a sound apart, are
printed with their time into the capture and the pitch that matched best (with --skip, a later part of each sound is matched and the time printed is still where the sound began). Only the first `--head` seconds of each sound are matched (default 1.5 s), so a
long sound still matches when something else starts on top of its tail.
"""
import argparse, subprocess
import numpy as np

RATE = 16000


def decode(path, start=None, end=None):
    cmd = ["ffmpeg", "-nostdin", "-v", "error"]
    if start is not None: cmd += ["-ss", str(start)]
    if end is not None: cmd += ["-to", str(end)]
    cmd += ["-i", path, "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"]
    return np.frombuffer(subprocess.run(cmd, capture_output=True, check=True).stdout, dtype=np.float32).astype(np.float64)


def resample(x, factor):
    """Plays x at pitch `factor` (shorter when > 1)."""
    if abs(factor - 1) < 1e-6: return x
    n = int(len(x) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(x)), x)


def ncc(signal, template):
    n = len(template)
    t = template - template.mean()
    tn = np.sqrt((t * t).sum())
    size = 1 << int(np.ceil(np.log2(len(signal) + n)))
    corr = np.fft.irfft(np.fft.rfft(signal, size) * np.conj(np.fft.rfft(t, size)), size)[:len(signal) - n + 1]
    c1 = np.concatenate([[0], np.cumsum(signal)]); c2 = np.concatenate([[0], np.cumsum(signal * signal)])
    s1 = c1[n:] - c1[:-n]; s2 = c2[n:] - c2[:-n]
    var = np.maximum(s2 - s1 * s1 / n, 1e-12)
    return corr / (np.sqrt(var) * tn)


def peaks(score, threshold, gap):
    out = []
    order = np.argsort(score)[::-1]
    taken = np.zeros(len(score), bool)
    for i in order:
        if score[i] < threshold: break
        if taken[max(0, i - gap):i + gap].any(): continue
        taken[i] = True; out.append(i)
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("capture"); ap.add_argument("sounds", nargs="+")
    ap.add_argument("--threshold", type=float, default=0.45); ap.add_argument("--head", type=float, default=1.5)
    ap.add_argument("--skip", type=float, default=0.0, help="match the part of each sound from this many seconds (times still mark where the sound starts)")
    ap.add_argument("--from", dest="start", type=float); ap.add_argument("--to", dest="end", type=float)
    a = ap.parse_args()
    x = decode(a.capture, a.start, a.end); offset = a.start or 0.0
    print(f"# capture {len(x) / RATE:.1f} s from {offset:.1f} s; threshold {a.threshold}; first {a.head} s of each sound matched")
    found = []
    for spec in a.sounds:
        name, rest = spec.split("=", 1)
        path, _, pitches = rest.partition("@")
        base = decode(path)
        best = which = None
        for p in [float(v) for v in pitches.split(",")] if pitches else [1.0]:
            r = resample(base, p); k = int(a.skip / p * RATE); t = r[k:k + int(a.head * RATE)]
            s = ncc(x, t)
            if best is None: best, which = s, np.full(len(s), p)
            else:
                n = min(len(best), len(s)); better = s[:n] > best[:n]
                which[:n][better] = p; best[:n] = np.maximum(best[:n], s[:n])
        for i in peaks(best, a.threshold, max(1, len(t) // 2)):
            found.append((offset + i / RATE - a.skip / which[i], name, best[i], which[i]))
    for when, name, score, pitch in sorted(found):
        print(f"{when:8.2f} s  {name:22s} r={score:.2f}  pitch {pitch:.3f}")


if __name__ == "__main__":
    main()
