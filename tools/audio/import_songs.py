#!/usr/bin/env python3
"""Converts the owner's raid songs (MP3) into the OGG Vorbis files the game plays.

Usage: python3 import_songs.py <the owner's upload folder> <out_dir>

Every song of the table below that is in the folder is converted (the owner's uploads: 0.0.17 had Boss/, Normal/, Special raid/;
0.0.19 had New songs/, with four more ordinary songs and the victory fanfare). Each song is cut free of silence at both ends (a looping song must not pause at its seam), brought to the same loudness
(-16 LUFS, two-pass linear EBU R128, so its dynamics are untouched), stripped of its cover picture and tags, and written as
48 kHz stereo Vorbis (quality 4). Needs ffmpeg with libvorbis. The table below is the whole mapping: file name, the
resource name in sounds/music/, and the pool ("victory" is the fanfare); RaidPlaylist.SONGS must list the same resource names
and lengths, and the lang file the titles (the owner's file names; the MP3 title tags of three 0.0.19 songs differ).
"""
import json, os, re, subprocess, sys

SONGS = [
    ("Normal/Barren Gap.mp3", "barren_gap", "normal"),
    ("Normal/Line Holder.mp3", "line_holder", "normal"),
    ("Normal/Phase One Assault.mp3", "phase_one_assault", "normal"),
    ("Normal/Silent Trigger.mp3", "silent_trigger", "normal"),
    ("Boss/Boss Battle.mp3", "boss_battle", "boss"),
    ("Special raid/Anomaly Protocol.mp3", "anomaly_protocol", "special"),
    ("New songs/Acid Redeemer.mp3", "acid_redeemer", "normal"),
    ("New songs/Assault Loop.mp3", "assault_loop", "normal"),
    ("New songs/Breach Core.mp3", "breach_core", "normal"),
    ("New songs/Rolling Wave.mp3", "rolling_wave", "normal"),
    ("New songs/Victory Fanfare.mp3", "victory", "victory"),
]
TARGET_LUFS = -16.0
TRIM = "silenceremove=start_periods=1:start_threshold=-60dB:start_silence=0.05,areverse,silenceremove=start_periods=1:start_threshold=-60dB:start_silence=0.3,areverse"


def measure(src):
    out = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-nostats", "-i", src, "-map", "0:a",
                          "-af", f"{TRIM},loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=20:print_format=json", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    return json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", out, re.S).group(0))


def convert(src, dst):
    m = measure(src)
    norm = (f"loudnorm=I={TARGET_LUFS}:TP=-1.5:LRA=20:linear=true:measured_I={m['input_i']}:measured_TP={m['input_tp']}"
            f":measured_LRA={m['input_lra']}:measured_thresh={m['input_thresh']}:offset={m['target_offset']}")
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-hide_banner", "-loglevel", "error", "-i", src, "-map", "0:a", "-map_metadata", "-1",
                    "-af", f"{TRIM},{norm},aresample=48000", "-ar", "48000", "-ac", "2", "-c:a", "libvorbis", "-q:a", "4", dst], check=True)
    length = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", dst],
                                  capture_output=True, text=True).stdout)
    return m, length


def main():
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    for rel, name, pool in SONGS:
        if not os.path.exists(os.path.join(src_dir, rel)):
            continue
        m, length = convert(os.path.join(src_dir, rel), os.path.join(out_dir, name + ".ogg"))
        print(f"{name:20s} {pool:8s} {length:7.2f} s  input {m['input_i']} LUFS -> {TARGET_LUFS}")


if __name__ == "__main__":
    main()
