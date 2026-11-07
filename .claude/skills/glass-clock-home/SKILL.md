---
name: glass-clock-home
description: Set up this fork's couch-tv style home on an Android TV or Google TV that runs the Clean Your TV launcher - the big glass clock with scenery cut out over it, the weather corner (tv-weather add-on, Open-Meteo), pictures that follow the weather and time of day, the on-TV wallpaper picker and the compact app row. Also for adding a person's own picture as a wallpaper with clock depth. Use when someone asks for the glass clock, couch-tv's Night look, the weather home, or a new wallpaper with depth on their TV.
---

# Glass-clock home

The human guide is [`docs/glass-clock-home.md`](../../../docs/glass-clock-home.md); this skill is the same work as
steps for an agent. Read the repo's [`AGENTS.md`](../../../AGENTS.md) first: its safety rules (disable never
uninstall, close ADB afterwards, no identifiers in commits) apply here too.

## Before you start

- The TV must already run the Clean Your TV launcher as Home. If it does not, follow [`PROMPT.md`](../../../PROMPT.md)
  first, then come back.
- You need the TV's address (`TV_HOST`) and network debugging on. Check with
  `TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py --probe`.
- Ask the person three things, in one question: their city (for the weather and the sun's times), which apps go
  first in the row, and whether they want the weather corner at all.

## Steps

1. **Launcher.** `TV_HOST=<ip> launcher/build-and-install.sh` (Windows: `launcher\build-and-install.ps1 -TvHost <ip>`).
   It is over upstream's 576 KB size gate because of the fonts; that is expected in this fork.
2. **Weather add-on** (skip if they said no). Put `tvweather.place`, `tvweather.latitude` and `tvweather.longitude` in
   the person's `~/.gradle/gradle.properties`, never in the repo. Build `addons/tv-weather` (`./gradlew --no-daemon
   assembleRelease`), push the APK with `tools/b64push.py` to `/data/local/tmp/` and `pm install -r` it. Check it
   answers: `content query --uri content://app.tvweather.now/now` (the first query starts a fetch; the second, a few
   seconds later, has a row).
3. **Theme.** Copy `fork/glass-depth` outside the checkout. In its `config.json` set `dock_apps` (and `hidden_apps` if the person wants apps out of the row); drop `"weather"` if there is no add-on. For their own sunrise and sunset, set `latitude` and `longitude` in
   `config.xml` before step 1 (a build setting, `docs/sky.md`), and keep that edit out of commits.
   Push with `tools/theme.sh <folder>`.
4. **Check on the TV.** Screenshot Home (`tools/tv.py --screenshot shots/home.png HOME --wait 6`). The weather, the
   date and the round buttons share one line; the clock is centred; the scenery overlaps the bottom of the digits.
5. **Show the person the picker**: Up, the picture button first, OK. *Automatic* follows the weather and the time.

## Adding the person's own picture

Only with a picture they give you (a file path). Keep the person's own photos out of any commit: they stay in their
theme copy, outside the checkout.

1. Look at it first. Decide how to cut it out:
   - landscape with a calm sky: couch-tv's `make-scene.py --depth` (clone https://github.com/Seagey/couch-tv; needs
     numpy, Pillow, scipy; pymatting for exact edges). Streaks of sky in the mountains: `--step 4 --far 22 --spread 3 --fill 70`;
   - a subject over a busy background (a plane, a person): `rembg` with `isnet-general-use`, locally;
   - forest against a blue and white sky: a colour rule (green below, blue or white above), smoothed per column.
2. Frame it: crop a 16:9 window from the original so the cut-out's top edge at the colon (columns 862-906 of 1920)
   lands at y 452, then resize to exactly 1920x1080 with Lanczos. Save JPEG quality 94, no chroma subsampling.
3. Make `NAME-glass.jpg` (Gaussian blur 16, brightness x1.3, colour x1.25; brighter for a very dark night sky) and
   `NAME-fg.webp` (the picture with the cut-out as alpha, quality 92, alpha quality 100).
4. Check the cut-out over magenta. If the edge is broken, ship the picture without `-fg.webp` rather than with a bad one.
5. Add a `captions.txt` line (`NAME.jpg<TAB>Name`), a `wallpaper_styles` entry (`{"clock_y": 0}`; `"text": "dark"`
   only for a very pale sky) and the right `wallpaper_moods` groups; push the theme; pick it on the TV and screenshot it.

## Finish

Close network debugging as `AGENTS.md` says, and tell the person how to switch pictures and how to undo
(`tools/theme.sh --reset`).
