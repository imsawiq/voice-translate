Starts a real Minecraft 26.3 client in Russian with Simple Voice Chat and the built Fabric jar, creates a world, adds sample translations to the history, and takes a screenshot of every settings page and of Simple Voice Chat's menu with the Voice Translate button. Screenshots land in `build/run/clientGameTest/screenshots`.

```
cd tools/gametest
..\..\gradlew.bat runClientGameTest
```

Run `gradlew --stop` first if other Gradle daemons are alive: 26.3 has crashed natively at startup with many of them around. Closing the test world can hang inside Fabric's gametest threading after the screenshots are taken; the screenshots are already written by then.
