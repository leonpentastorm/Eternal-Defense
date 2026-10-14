#!/usr/bin/env python3
"""Converts the owner's flare radio chatter into the OGG files the game plays.

Usage: python3 import_chatter.py <folder with the owner's MP3s> <out_dir>

Three groups, each converted when its files are in the folder (so a folder with only the new lines converts only those):
  "Fire support chatter (1..6).mp3"   -> chatter_1.ogg ... chatter_6.ogg          (0.0.19, fire support flares)
  "Supplies flare chatter (1..2).mp3" -> supply_chatter_1.ogg, supply_chatter_2.ogg (0.0.22, supply flares)
  "Recall flare chatter (1..3).mp3"   -> recall_chatter_1.ogg ... _3.ogg           (0.0.22, return flares)
  "FOB Order Chatter (1..4).mp3"      -> order_chatter_1.ogg ... _4.ogg            (0.0.34, a specialty seller purchase)
  "Radio static coming in.mp3"        -> radio_static.ogg                          (0.0.34, Contact Specialty Seller)
  "Loadout chatter (1..4).mp3"        -> loadout_chatter_1.ogg ... _4.ogg          (1.1.1, Loadout flares)

Each line is brought to about the same loudness (-18 LUFS, or less where the -1 dBFS peak limit stops it first; one linear gain
per file, so the radio sound of the recording is untouched), made mono (the line follows the player who threw the flare, a placed sound) and written as
48 kHz Vorbis (quality 5) into sounds/sfx. Nothing is trimmed: the lines start at once. Needs ffmpeg with libvorbis. Which line plays
when is SupportChatter (a random line of the flare's own group, never the same one twice in a row for one player).
"""
import json, os, re, subprocess, sys

TARGET_LUFS = -18.0
PEAK_DBFS = -1.0
GROUPS = [("Fire support chatter", "chatter", 6), ("Supplies flare chatter", "supply_chatter", 2), ("Recall flare chatter", "recall_chatter", 3),
          ("FOB Order Chatter", "order_chatter", 4), ("Radio static coming in", "radio_static", 0), ("Loadout chatter", "loadout_chatter", 4)]   # 0: one file, no number


def measure(src):
    out = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-nostats", "-i", src, "-af",
                          f"loudnorm=I={TARGET_LUFS}:TP={PEAK_DBFS}:LRA=11:print_format=json", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    return json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", out, re.S).group(0))


def main():
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for title, name, lines in GROUPS:
        sources = [os.path.join(src_dir, f"{title} ({n}).mp3") for n in range(1, lines + 1)] if lines else [os.path.join(src_dir, f"{title}.mp3")]
        if not all(os.path.exists(s) for s in sources):
            print(f"{title}: not in this folder, skipped")
            continue
        for n, src in enumerate(sources, 1):
            m = measure(src)
            gain = min(TARGET_LUFS - float(m["input_i"]), PEAK_DBFS - float(m["input_tp"]))
            out = f"{name}_{n}" if lines else name
            dst = os.path.join(out_dir, f"{out}.ogg")
            subprocess.run(["ffmpeg", "-nostdin", "-y", "-hide_banner", "-loglevel", "error", "-i", src, "-map", "0:a", "-map_metadata", "-1",
                            "-af", f"volume={gain:.2f}dB,aresample=48000", "-ar", "48000", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
            length = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", dst],
                                          capture_output=True, text=True).stdout)
            print(f"{out}  {length:5.2f} s  input {m['input_i']} LUFS, peak {m['input_tp']} dBTP, gain {gain:+.1f} dB")


if __name__ == "__main__":
    main()
