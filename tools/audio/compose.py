"""The raid music and the victory fanfare of Eternal Defense, written as code (original compositions).

  raid_normal   "Hold the Line"    D minor, 128 BPM, 40 bars: taiko, string ostinato, a horn theme that the trumpets and choir take up.
  raid_boss     "Iron Tyrant"      C minor, 144 BPM, 40 bars: phrygian chug, choir, brass stabs, double-time drums.
  raid_special  "Strange Signals"  E minor, 136 BPM, 36 bars: 3-3-2 rhythm, alarm-like arpeggios, a siren lead.
  victory       "Laurels"          C major fanfare, about 8 seconds: timpani roll, a trumpet hook, full chord with bells.

The three raid tracks loop without a seam (their reverb tails are folded onto their beginnings). Run:
  python tools/audio/compose.py <assets/arsenal_beacon/sounds/music>
"""
import os
import sys
import numpy as np
import synth as S

m = S.m


class Song:
    def __init__(self, bpm, bars, beats=4, tail=6.0):
        self.spb = 60.0 / bpm; self.beats = beats; self.bars = bars
        self.mix = S.Mix(bars * beats * self.spb, tail)

    def t(self, bar, beat=0.0):
        return (bar * self.beats + beat) * self.spb

    def put(self, bar, beat, sound, gain=1.0, pan=0.0, rev=0.25):
        self.mix.add(self.t(bar, beat), sound, gain, pan, rev)

    def line(self, inst, bar, notes, gain=1.0, pan=0.0, rev=0.3, octave=0, **kw):
        """notes: (name or None, beats) in order, starting at the bar's first beat."""
        beat = 0.0
        for name, beats in notes:
            if name:
                self.put(bar, beat, inst(m(name) + 12 * octave, beats * self.spb * 0.98, **kw), gain, pan, rev)
            beat += beats

    def chord(self, inst, bar, beat, names, beats, gain=1.0, spread=0.6, rev=0.35, **kw):
        k = len(names)
        for i, name in enumerate(names):
            p = (i / (k - 1) - 0.5) * 2 * spread if k > 1 else 0
            self.put(bar, beat, inst(m(name), beats * self.spb, **kw), gain / max(1, k) ** 0.5, p, rev)

    def drums(self, bar, pattern, sound_fn, gain=1.0, pan=0.0, rev=0.2, steps=16):
        """pattern: one char per step, 'x' hit, 'X' accent, '.' rest."""
        for i, c in enumerate(pattern):
            if c in 'xX':
                self.put(bar, i * self.beats / steps, sound_fn(1.0 if c == 'X' else 0.72), gain, pan, rev)


CHORD = {'Dm': ('D', 'F', 'A'), 'Bb': ('Bb', 'D', 'F'), 'F': ('F', 'A', 'C'), 'C': ('C', 'E', 'G'), 'A': ('A', 'C#', 'E'),
         'Gm': ('G', 'Bb', 'D'), 'Eb': ('Eb', 'G', 'Bb'), 'Cm': ('C', 'Eb', 'G'), 'Db': ('Db', 'F', 'Ab'), 'G': ('G', 'B', 'D'),
         'Ab': ('Ab', 'C', 'Eb'), 'Fm': ('F', 'Ab', 'C'), 'Em': ('E', 'G', 'B'), 'D': ('D', 'F#', 'A'), 'B': ('B', 'D#', 'F#'),
         'Am': ('A', 'C', 'E')}


def tones(chord, octave):
    """Root-position chord tones from `octave`, each above the previous one."""
    out = []; last = -1
    for name in CHORD[chord]:
        v = m(name + str(octave))
        while v <= last: v += 12
        out.append(v); last = v
    return out


def name_of(midi):
    names = ['C', 'C#', 'D', 'Eb', 'E', 'F', 'F#', 'G', 'Ab', 'A', 'Bb', 'B']
    return names[midi % 12] + str(midi // 12 - 1)


# ---- 1. Hold the Line ----------------------------------------------------------------------------------------------------------
def hold_the_line():
    S.seed(101)
    s = Song(128, 40)
    plan = (['Dm', 'Dm', 'Bb', 'C'] + ['Dm', 'Bb', 'F', 'C', 'Dm', 'Bb', 'C', 'A'] * 2 + ['Gm', 'Dm', 'Bb', 'A', 'Gm', 'Dm', 'Eb', 'A']
            + ['Dm', 'Bb', 'F', 'C', 'Dm', 'Bb', 'C', 'A'] + ['Dm', 'Bb', 'C', 'A'])
    theme_a = [[('D5', 1.5), ('A4', .5), ('D5', 1), ('E5', 1)], [('F5', 2), ('E5', 1), ('D5', 1)], [('C5', 1.5), ('A4', .5), ('C5', 1), ('D5', 1)], [('E5', 3), (None, 1)],
               [('D5', 1.5), ('A4', .5), ('D5', 1), ('E5', 1)], [('F5', 1), ('G5', 1), ('F5', 1), ('D5', 1)], [('E5', 1), ('F5', 1), ('G5', 1), ('E5', 1)], [('A5', 4)]]
    theme_b = [[('G5', 2), ('F5', 1), ('D5', 1)], [('A5', 3), ('F5', 1)], [('Bb5', 1), ('A5', 1), ('G5', 1), ('F5', 1)], [('E5', 2), ('C#5', 2)],
               [('D5', 1), ('G5', 1), ('Bb5', 2)], [('A5', 1.5), ('G5', .5), ('F5', 1), ('D5', 1)], [('G5', 2), ('Bb5', 1), ('G5', 1)], [('A5', 2), ('E5', 1), ('C#5', 1)]]
    for bar, ch in enumerate(plan):
        r, third, fifth = tones(ch, 4)
        section = 'intro' if bar < 4 else 'a1' if bar < 12 else 'a2' if bar < 20 else 'b' if bar < 28 else 'a3' if bar < 36 else 'out'
        loud = {'intro': .6, 'a1': .8, 'a2': .9, 'b': .95, 'a3': 1.0, 'out': .65}[section]
        # string ostinato in 16ths
        pat = [r, r, fifth - 12, r, third, r, fifth - 12, r] * 2
        for i, note in enumerate(pat):
            s.put(bar, i * .25, S.staccato(note, .22 * s.spb, .9 if i % 4 == 0 else .7), .30 * loud, .35, .25)
            if section in ('a2', 'b', 'a3'): s.put(bar, i * .25, S.staccato(note - 12, .22 * s.spb, .7), .18 * loud, -.35, .25)
        # bass in 8ths, octave up on the last eighth
        for i in range(8):
            s.put(bar, i * .5, S.bass(r - 24 + (12 if i == 7 else 0), .45 * s.spb, .9 if i % 2 == 0 else .7), .32 * loud, 0, .05)
        # drums
        s.drums(bar, 'X.....x.X.x.....', lambda v: S.taiko(v, 1.0), .55 * loud, -.1, .3)
        s.drums(bar, '....x.......x...' if section != 'intro' else '................', lambda v: S.taiko(v * .8, 1.6), .45 * loud, .2, .3)
        if section in ('a2', 'b', 'a3'):
            s.drums(bar, '....X.......X..x', lambda v: S.snare(v * .9), .30, .1, .25)
            s.drums(bar, 'x.x.x.x.x.x.x.x.', lambda v: S.hat(v * .8), .10, .3, .1)
        if bar in (4, 12, 20, 28): s.put(bar, 0, S.crash(.9), .32, -.2, .3)
        if bar == 3: s.put(bar, 0, S.roll(4 * s.spb, .1, .95), .32, .1, .3); s.put(bar, 2, S.riser(2 * s.spb, 400, 7000), .35, 0, .2)
        if bar == 27: s.put(bar, 2, S.roll(2 * s.spb, .2, .9), .28, .1, .3)
        # sustained strings and horn chords
        if section in ('a2', 'b', 'a3', 'out'):
            s.chord(S.strings, bar, 0, [name_of(v) for v in tones(ch, 3)], 4, .30 * loud, .7, .45)
        if section in ('intro', 'out'):
            s.chord(S.horn, bar, 0, [name_of(v) for v in tones(ch, 3)], 4, .22 * loud, .4, .4, vel=.45)
        # melodies
        if section == 'a1':
            s.line(S.horn, bar, theme_a[bar - 4], .80, -.15, .35, octave=-1, vel=.8)
        if section == 'a2':
            s.line(S.brass, bar, theme_a[bar - 12], .70, .15, .35, vel=.85)
            s.line(S.horn, bar, theme_a[bar - 12], .30, -.25, .35, octave=-1, vel=.6)
        if section == 'b':
            s.line(S.choir, bar, theme_b[bar - 20], .75, 0, .45, vowel='a')
            s.line(S.strings, bar, theme_b[bar - 20], .28, .3, .4, octave=-1, vel=.6, attack=.06)
            s.drums(bar, '..x...x...x...x.', lambda v: S.brass(tones(ch, 3)[0], .18 * s.spb, v * .7), .22, -.3, .2)
        if section == 'a3':
            s.line(S.brass, bar, theme_a[bar - 28], .78, .15, .35, vel=.95)
            s.line(S.brass, bar, theme_a[bar - 28], .32, -.2, .35, octave=-1, vel=.8)
            s.line(S.choir, bar, theme_a[bar - 28], .32, 0, .5, vowel='o')
            if bar in (28, 32): s.put(bar, 0, S.crash(.8), .28, .2, .3)
    return s.mix.render(S.reverb_ir(2.8, 2.4), loop=True)


# ---- 2. Iron Tyrant -------------------------------------------------------------------------------------------------------------
def iron_tyrant():
    S.seed(202)
    s = Song(144, 40)
    a = ['Cm', 'Db', 'Cm', 'G', 'Cm', 'Db', 'Ab', 'G']
    plan = ['Cm'] * 4 + a + a + ['Fm', 'Db', 'Eb', 'G', 'Fm', 'Db', 'Ab', 'G'] + a + ['Cm', 'Db', 'Cm', 'G']
    theme = [[('C5', 2), ('Eb5', 1), ('D5', 1)], [('Db5', 3), ('C5', 1)], [('G4', 1), ('Ab4', 1), ('Bb4', 1), ('C5', 1)], [('B4', 2), ('D5', 2)],
             [('C5', 1), ('Eb5', 1), ('G5', 2)], [('F5', 1.5), ('Eb5', .5), ('Db5', 1), ('C5', 1)], [('Eb5', 1), ('C5', 1), ('Ab4', 1), ('C5', 1)], [('B4', 2), ('G4', 2)]]
    theme_b = [[('Ab5', 2), ('G5', 1), ('F5', 1)], [('F5', 2), ('Eb5', 1), ('Db5', 1)], [('G5', 2), ('Bb5', 2)], [('B5', 3), (None, 1)],
               [('C6', 2), ('Ab5', 1), ('F5', 1)], [('Ab5', 1.5), ('G5', .5), ('F5', 1), ('Db5', 1)], [('Eb5', 2), ('C5', 1), ('Eb5', 1)], [('D5', 2), ('B4', 2)]]
    for bar, ch in enumerate(plan):
        r = tones(ch, 3)[0]
        section = 'intro' if bar < 4 else 'a' if bar < 12 else 'a2' if bar < 20 else 'b' if bar < 28 else 'a3' if bar < 36 else 'out'
        loud = {'intro': .7, 'a': .85, 'a2': .95, 'b': 1.0, 'a3': 1.0, 'out': .75}[section]
        # the chug: root, root, root, half-step up, root, root, root, whole-step down
        if section != 'intro' or bar >= 2:
            up = 1 if ch in ('Cm', 'G', 'Fm') else 2
            for i, off in enumerate((0, 0, 0, up, 0, 0, 0, -2)):
                s.put(bar, i * .5, S.staccato(r + off, .4 * s.spb, .95 if i in (0, 3) else .75), .34 * loud, .3, .2)
                s.put(bar, i * .5, S.bass(r + off - 12, .42 * s.spb, .9, bright=650), .32 * loud, 0, .05)
                if section in ('a2', 'b', 'a3') and i in (0, 3): s.put(bar, i * .5, S.brass(r + off - 12, .3 * s.spb, .9, .7), .26 * loud, -.3, .25)
        # choir pads (low "oh", high "ah" in the big sections)
        s.chord(S.choir, bar, 0, [name_of(v) for v in tones(ch, 3)], 4, .42 * loud, .6, .5, vowel='o')
        if section in ('b', 'a3'):
            s.chord(S.choir, bar, 0, [name_of(v) for v in tones(ch, 4)], 4, .25, .8, .5, vowel='a')
        if section in ('a2', 'b', 'a3'):
            for v in tones(ch, 4): s.put(bar, 0, S.tremolo(v + 12, 4 * s.spb, .45), .12, .45, .4)
        # drums
        if section == 'intro':
            if bar == 0: s.put(bar, 0, S.timpani_roll(m('C2'), 8 * s.spb, .1, 1.0), .55, 0, .35)
            if bar == 2: s.put(bar, 0, S.roll(8 * s.spb, .1, 1.0, 26, 160), .25, .1, .3)
            if bar == 3: s.put(bar, 2, S.riser(2 * s.spb, 200, 8000), .4, 0, .2)
        else:
            dbl = section in ('a2', 'b', 'a3')
            s.drums(bar, 'X.xxX.xxX.xxX.xx' if dbl else 'X...x.x.X...x.x.', lambda v: S.kick(v), .40 * loud, 0, .1)
            s.drums(bar, 'X.......X.......', lambda v: S.taiko(v, .8), .50 * loud, -.1, .35)
            s.drums(bar, '....X.......X...', lambda v: S.snare(v, 160), .36, .1, .3)
            s.drums(bar, 'x.x.x.x.x.x.x.x.', lambda v: S.hat(v * .7, False), .08, .35, .1)
            if bar % 4 == 3:
                for i, note in enumerate(('A3', 'F3', 'D3', 'A2', 'F2', 'D2')):
                    s.put(bar, 2.5 + i * .25, S.tom(m(note), .9), .45, -.4 + i * .16, .25)
            if bar in (4, 12, 20, 28, 36): s.put(bar, 0, S.crash(1.0), .35, -.2, .35); s.put(bar, 0, S.timpani(m('C2'), 1.0), .6, 0, .3)
        if section == 'b':
            for beat in (1.5, 2.5, 3.5):
                for v in tones(ch, 4): s.put(bar, beat, S.brass(v, .2 * s.spb, .95, 1.1), .16, (v % 3 - 1) * .4, .3)
        # melodies
        if section == 'a':
            s.line(S.horn, bar, theme[bar - 4], .82, -.1, .35, octave=-1, vel=.9)
        if section == 'a2':
            s.line(S.brass, bar, theme[bar - 12], .70, .15, .35, vel=.9)
            s.line(S.horn, bar, theme[bar - 12], .35, -.2, .35, octave=-1, vel=.75)
        if section == 'b':
            s.line(S.choir, bar, theme_b[bar - 20], .75, 0, .5, vowel='a')
            s.line(S.strings, bar, theme_b[bar - 20], .30, .3, .4, vel=.65, attack=.05)
        if section == 'a3':
            s.line(S.brass, bar, theme[bar - 28], .72, .15, .35, octave=1, vel=1.0)
            s.line(S.brass, bar, theme[bar - 28], .42, -.15, .35, vel=.9)
            s.line(S.choir, bar, theme[bar - 28], .35, 0, .5, vowel='a')
    return s.mix.render(S.reverb_ir(3.0, 2.8, .4), loop=True)


# ---- 3. Strange Signals ---------------------------------------------------------------------------------------------------------
def strange_signals():
    S.seed(303)
    s = Song(136, 36)
    a = ['Em', 'C', 'D', 'B', 'Em', 'C', 'Am', 'B']
    plan = ['Em'] * 4 + a + ['C', 'D', 'Em', 'Em', 'C', 'D', 'B', 'B'] + a + ['Am', 'C', 'Em', 'B'] + ['Em', 'C', 'D', 'B']
    theme = [[('E5', 1), ('G5', 1), ('F#5', 1), ('B4', 1)], [('E5', 1), ('G5', 1), ('A5', 1), ('B5', 1)], [('C6', 1.5), ('B5', .5), ('A5', 1), ('F#5', 1)], [('D#5', 2), ('F#5', 2)],
             [('G5', 1.5), ('F#5', .5), ('E5', 1), ('B4', 1)], [('C5', 1), ('E5', 1), ('G5', 1), ('C6', 1)], [('B5', 1), ('A5', 1), ('G5', 1), ('E5', 1)], [('F#5', 2), ('D#5', 2)]]
    theme_b = [[('G5', 2), ('E5', 2)], [('F#5', 2), ('A5', 2)], [('B5', 3), ('G5', 1)], [('E5', 4)],
               [('C6', 2), ('B5', 1), ('A5', 1)], [('A5', 2), ('F#5', 1), ('D5', 1)], [('D#5', 2), ('F#5', 2)], [('B5', 4)]]
    for bar, ch in enumerate(plan):
        t3 = tones(ch, 4); r = t3[0]
        section = 'intro' if bar < 4 else 'a' if bar < 12 else 'b' if bar < 20 else 'a2' if bar < 28 else 'break' if bar < 32 else 'out'
        loud = {'intro': .65, 'a': .85, 'b': .95, 'a2': 1.0, 'break': .7, 'out': .75}[section]
        bright = {'intro': 1200, 'a': 2600, 'b': 3600, 'a2': 4800, 'break': 1800, 'out': 2000}[section]
        arp = [t3[0], t3[2], t3[0] + 12, t3[2], t3[1] + 12, t3[2], t3[0] + 12, t3[1]] * 2
        for i, note in enumerate(arp):
            s.put(bar, i * .25, S.pluck(note, .24 * s.spb, .9 if i % 3 == 0 else .65, .3, bright), .26 * loud, (.4 if i % 2 else -.4), .3)
        # 3-3-2 bass and kick
        for step, octave, beats in ((0, 0, 1.4), (3, 0, 1.4), (6, 12, .9)):
            s.put(bar, step * .5, S.bass(r - 24 + octave, beats * s.spb, .9, 800), .34 * loud, 0, .05)
        if section != 'intro':
            s.drums(bar, 'X.....x.....x...' if section != 'a2' else 'X.....x.X.....x.', lambda v: S.kick(v), .42 * loud, 0, .1)
            s.drums(bar, '........X.......' if section in ('a', 'break') else '....X.......X...', lambda v: S.snare(v, 210), .32, .1, .3)
            s.drums(bar, 'xxXxxXxxXxxXxxXx', lambda v: S.hat(v * .6), .07, .35, .1)
            s.drums(bar, '..x..x....x..x..', lambda v: S.tom(m('E3'), v * .7), .22, -.35, .25)
        if bar in (4, 12, 20, 28, 32): s.put(bar, 0, S.crash(.9), .3, .25, .3)
        # the siren: an alarm glide every eight bars
        if bar % 8 == 2:
            s.put(bar, 0, S.lead(m('B4'), 1.6 * s.spb, .55, glide_from=m('E4')), .22, -.2, .45)
            s.put(bar, 2, S.lead(m('E4'), 1.6 * s.spb, .5, glide_from=m('B4')), .2, .2, .45)
        if section in ('b', 'a2', 'break'):
            s.chord(S.strings, bar, 0, [name_of(v) for v in tones(ch, 3)], 4, .26 * loud, .7, .45, attack=.3)
        if section == 'break':
            s.chord(S.choir, bar, 0, [name_of(v) for v in tones(ch, 4)], 4, .3, .7, .55, vowel='o')
        if section == 'a':
            s.line(S.lead, bar, theme[bar - 4], .50, .1, .35, vel=.75)
        if section == 'b':
            s.line(S.brass, bar, theme_b[bar - 12], .70, -.1, .35, vel=.9)
            for beat in (0, 1.5, 3):
                for v in tones(ch, 3): s.put(bar, beat, S.power(v, .3 * s.spb, .8, palm=True), .07, 0, .1)
        if section == 'a2':
            s.line(S.lead, bar, theme[bar - 20], .30, .2, .35, vel=.75)
            s.line(S.brass, bar, theme[bar - 20], .60, -.15, .35, vel=.9)
            s.put(bar, 0, S.power(r - 12, 1.8 * s.spb, .85), .12, 0, .15); s.put(bar, 2, S.power(r - 12, 1.8 * s.spb, .8), .1, 0, .15)
    return s.mix.render(S.reverb_ir(2.4, 2.0, .5), loop=True)


# ---- 4. Laurels (victory fanfare) -----------------------------------------------------------------------------------------------
def laurels():
    S.seed(404)
    s = Song(132, 6, tail=0.5)
    lead_in = 1.0
    mix = S.Mix(8.6, 0.4)
    spb = s.spb

    def at(beat):
        return lead_in + beat * spb

    mix.add(0, S.timpani_roll(m('G2'), lead_in, .15, 1.0), .6, 0, .3)
    mix.add(0, S.roll(lead_in, .1, 1.0, 26), .3, .1, .3)
    mix.add(0, S.riser(lead_in, 300, 9000), .35, 0, .2)
    # the hook: trumpets, horns a third below, power chords and taiko under it
    hook = [('C5', 1, 'G4'), ('G4', .5, 'E4'), ('C5', .5, 'G4'), ('E5', 1, 'C5'), ('G5', 1, 'E5'), ('F5', .5, 'C5'), ('E5', .5, 'C5'), ('F5', .5, 'C5'), ('A5', .5, 'F5'),
            ('G5', 2, 'E5'), ('F5', .5, 'D5'), ('G5', .5, 'B4')]
    beat = 0.0
    for name, beats, under in hook:
        mix.add(at(beat), S.brass(m(name), beats * spb * .97, 1.0, 1.15), .62, .15, .3)
        mix.add(at(beat), S.brass(m(name) + 12, beats * spb * .97, .8, 1.2), .2, .3, .35)
        mix.add(at(beat), S.horn(m(under), beats * spb * .97, .85), .4, -.2, .3)
        mix.add(at(beat), S.taiko(.9 if beats >= 1 else .6, 1.2), .35, -.1, .3)
        beat += beats
    for k, root in enumerate(['C3'] * 8 + ['F3'] * 4 + ['C3'] * 4 + ['G3'] * 2):
        mix.add(at(k * .5), S.power(m(root), .45 * spb, .85, palm=True), .14, 0, .1)
    mix.add(at(0), S.crash(1.0), .35, -.2, .3)
    mix.add(at(4), S.crash(.8), .25, .2, .3)
    # the final chord
    end = at(beat)
    for name in ('C4', 'E4', 'G4', 'C5', 'E5', 'G5'):
        mix.add(end, S.brass(m(name), 2.6, 1.0, 1.1), .26, (m(name) % 7 / 3.5 - 1) * .6, .4)
    mix.add(end, S.brass(m('C6'), 2.6, 1.0, 1.25), .2, .2, .45)
    for name in ('C3', 'G3', 'C4', 'E4'):
        mix.add(end, S.strings(m(name), 2.8, .8, .03), .25, 0, .45)
    mix.add(end, S.choir(m('C5'), 2.6, .7, 'a'), .3, 0, .5); mix.add(end, S.choir(m('E5'), 2.6, .6, 'a'), .22, .3, .5)
    mix.add(end, S.power(m('C3'), 2.4, 1.0), .22, 0, .2)
    mix.add(end, S.crash(1.0, 3.2), .45, 0, .35)
    mix.add(end, S.timpani(m('C2'), 1.0, 2.2), .7, 0, .3); mix.add(end + .02, S.timpani(m('G2'), .8, 2.0), .45, .1, .3)
    mix.add(end, S.taiko(1.0, .9), .6, 0, .35)
    for i, name in enumerate(('C6', 'E6', 'G6', 'C7')):
        mix.add(end + .12 + i * .09, S.bell(m(name), .7, 2.6), .22, (i - 1.5) * .3, .5)
    out = mix.render(S.reverb_ir(2.6, 2.2), loop=False)
    k = S.n_of(1.2); out[:, -k:] *= np.linspace(1, 0, k) ** 2
    return out


TRACKS = {'raid_normal': (hold_the_line, -17.0), 'raid_boss': (iron_tyrant, -16.5), 'raid_special': (strange_signals, -17.0), 'victory': (laurels, -15.0)}

if __name__ == '__main__':
    out_dir = sys.argv[1]; os.makedirs(out_dir, exist_ok=True)
    only = sys.argv[2:] or list(TRACKS)
    for name in only:
        fn, loud = TRACKS[name]
        data = S.master(fn(), loud)
        size = S.write(os.path.join(out_dir, name + '.ogg'), data, quality=4)
        print(f'{name}: {data.shape[1] / S.SR:.1f} s, {size // 1024} KiB')
