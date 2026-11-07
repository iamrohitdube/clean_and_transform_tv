# TV Weather

The weather for the launcher's `weather` theme key: the temperature now, the condition (clear, cloudy, rain, fog,
snow), today's high and low and the chance of rain, for one place. Home shows it at the top left and uses the
condition to pick the picture's weather group (see [docs/glass-clock-home.md](../../docs/glass-clock-home.md)).

It is not a module card and has no screen of its own: it is a read-only content provider
(`content://app.tvweather.now/now`) that the launcher reads, so the launcher itself stays offline. (The separate
[`weather`](../weather/) add-on is a full weather card; this one only feeds Home.)

- **When it goes online**: only when Home asks and the saved reading is over 30 minutes old, so nothing runs while
  Home is not on screen and the TV is never woken. A failed fetch keeps the last reading, and the launcher hides a
  reading that is over three hours old.
- **What it sends**: the latitude and longitude you build it with, to [Open-Meteo](https://open-meteo.com) (free, no
  account, no key). Nothing else.

## Permissions

`INTERNET` only, to ask Open-Meteo. The provider is exported read-only: it holds a temperature, a condition and
the place's name, which Home shows anyway.

## Build and install

Set the place in `~/.gradle/gradle.properties` (outside the repo):

```properties
tvweather.place=Lisbon
tvweather.latitude=38.72
tvweather.longitude=-9.14
```

```sh
./gradlew --no-daemon assembleRelease
../../tools/venv/bin/python -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvweather.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/tvweather.apk; rm -f /data/local/tmp/tvweather.apk'
```

Then set `"weather": "app.tvweather"` in your theme. Tested on a Xiaomi MiTV-MOOR2 (Google TV, Android 11).

Weather data by [Open-Meteo.com](https://open-meteo.com), CC BY 4.0.
