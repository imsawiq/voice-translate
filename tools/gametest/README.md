Starts a real Minecraft 26.3 client with Simple Voice Chat and the built Fabric jar, creates a world, puts sample subtitles on screen, and takes a screenshot of the HUD, every settings tab and the history screen. Screenshots land in `build/run/clientGameTest/screenshots`.

```
cd tools/gametest
..\..\gradlew.bat runClientGameTest
```

Run `gradlew --stop` first if other Gradle daemons are alive: 26.3 has crashed natively at startup with many of them around. Closing the test world can hang inside Fabric's gametest threading after the screenshots are taken; the screenshots are already written by then.
