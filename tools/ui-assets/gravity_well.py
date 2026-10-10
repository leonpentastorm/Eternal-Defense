#!/usr/bin/env python3
"""Draws the Gravity Well's vortex sprite (0.0.18): textures/entity/gravity_well.png, 128 x 128 with transparency.

A four-armed logarithmic spiral in violet over a dark core, with a bright rim at the very edge: the renderer lays it flat on the
ground exactly as wide as the well's area, so the rim shows where the pull reaches. Run: python3 tools/ui-assets/gravity_well.py
"""
import os
import numpy as np
from PIL import Image

N = 128
OUT = os.path.join(os.path.dirname(__file__), "../../custom-mods/arsenal-beacon/src/main/resources/assets/arsenal_beacon/textures/entity/gravity_well.png")


def draw():
    y, x = np.mgrid[0:N, 0:N]
    u = (x + .5) / N * 2 - 1
    v = (y + .5) / N * 2 - 1
    r = np.sqrt(u * u + v * v)
    a = np.arctan2(v, u)
    arms = .5 + .5 * np.cos(4 * (a + 2.6 * np.log(r + .06)))          # four arms winding inward
    arms = arms ** 2.2
    fine = .5 + .5 * np.cos(11 * (a + 2.6 * np.log(r + .06)) + 1.3)    # thin streaks between them
    body = np.clip(.75 * arms + .25 * fine ** 3, 0, 1)
    fall = np.clip((r - .12) / .5, 0, 1) * np.clip((1.0 - r) / .25, 0, 1)   # dark in the eye, fading toward the rim
    core = np.clip(1 - r / .22, 0, 1)                                    # the eye: almost black, mostly opaque
    rim = np.exp(-((r - .955) / .022) ** 2)                              # the edge of the well's reach
    light = np.clip(body * fall, 0, 1)
    deep = np.array([28, 4, 52]); mid = np.array([150, 60, 255]); pale = np.array([232, 205, 255])
    rgb = deep[None, None, :] * (1 - light[..., None]) + mid[None, None, :] * light[..., None]
    rgb = rgb * (1 - rim[..., None]) + pale[None, None, :] * rim[..., None]
    rgb = rgb + (pale - rgb) * (np.clip(light - .7, 0, 1) / .3)[..., None] * .6
    alpha = np.clip(.28 + .66 * light + .9 * rim + .55 * core, 0, 1)
    alpha[r > 1] = 0
    img = np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)
    return Image.fromarray(img, "RGBA")


if __name__ == "__main__":
    draw().save(os.path.abspath(OUT))
    print(os.path.abspath(OUT))
