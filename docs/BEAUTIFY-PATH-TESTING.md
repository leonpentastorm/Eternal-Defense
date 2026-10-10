# Beautify Path (0.0.16): testing

## Run in this round

* Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` succeeds; both editions (`arsenal-beacon-0.21.0.jar`, `arsenal-beacon-0.21.0-standalone.jar`) are produced.
* Unit tests: 154 pass, 0 fail (`:arsenal-beacon:test`). New `BeautifyPathTest`: raid music choice (`RaidMusic.cue`), the fall of a round (`Ordnance.Fall.drop`: lands exactly at the flight time, a bomb bores on and stops), screen shake falloff, the scrolling type list (`CannonScreen.visibleRows`, `scrollTo`), the upgrade order (three-level upgrades and Area of effect first), shell counts, damage shares and radii of the six new types, the pull and push helpers (never past the centre, bosses and knockback resistance), and that the Field Guide quotes the new numbers. `LangKeysTest` covers the new names, descriptions and subtitles and the guide's card rules; `SupportTest` now expects an out-of-range fire type to clamp to the Starshell.
* Audio was checked by analysis only (levels per band, loudness, spectrograms, that loops have no gap or click at the seam), not by listening: the author of these files cannot hear them.

## Not run in this round

* A real client: music switching and looping, the fanfare, every sound in the game, the falling rounds and their models, the blasts at a distance, the screen shake, the six new fire supports against real mobs, the exit gate (the gate GameTest was extended but not run), the cannon menu scroll and tooltips at several GUI scales.
* The standalone edition in the game, a dedicated server, multiplayer.
