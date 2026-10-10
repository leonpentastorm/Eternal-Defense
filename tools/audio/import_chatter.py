#!/usr/bin/env python3
"""Converts the owner's fire support radio chatter (0.0.19) into the OGG files the game plays.

Usage: python3 import_chatter.py <folder with "Fire support chatter (1..6).mp3"> <out_dir>

Each line is brought to about the same loudness (-18 LUFS, or less where the -1 dBFS peak limit stops it first; one linear gain
per file, so the radio sound of the recording is untouched), made mono (the line follows the player who threw the flare, a placed sound) and written as
48 kHz Vorbis (quality 5): sounds/sfx/chatter_1.ogg ... chatter_6.ogg. Nothing is trimmed: the lines start at once. Needs ffmpeg
with libvorbis. Which line plays when is SupportChatter (a random line, never the same one twice in a row for one player).
"""
import json, os, re, subprocess, sys

TARGET_LUFS = -18.0
PEAK_DBFS = -1.0
LINES = 6


def measure(src):
    out = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-nostats", "-i", src, "-af",
                          f"loudnorm=I={TARGET_LUFS}:TP={PEAK_DBFS}:LRA=11:print_format=json", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    return json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", out, re.S).group(0))


def main():
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for n in range(1, LINES + 1):
        src = os.path.join(src_dir, f"Fire support chatter ({n}).mp3")
        m = measure(src)
        gain = min(TARGET_LUFS - float(m["input_i"]), PEAK_DBFS - float(m["input_tp"]))
        dst = os.path.join(out_dir, f"chatter_{n}.ogg")
        subprocess.run(["ffmpeg", "-nostdin", "-y", "-hide_banner", "-loglevel", "error", "-i", src, "-map", "0:a", "-map_metadata", "-1",
                        "-af", f"volume={gain:.2f}dB,aresample=48000", "-ar", "48000", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
        length = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", dst],
                                      capture_output=True, text=True).stdout)
        print(f"chatter_{n}  {length:5.2f} s  input {m['input_i']} LUFS, peak {m['input_tp']} dBTP, gain {gain:+.1f} dB")


if __name__ == "__main__":
    main()
