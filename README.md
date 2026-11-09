# Jaitsizazu

Jaitsizazu will download audio and video from YouTube and similar sites, and convert your own audio and video files. One minimal app for desktop (Windows and Linux) and Android, with no ads, no telemetry and no accounts. For example, it will turn a WhatsApp voice note (Ogg Opus) into a WAV file you can drop into Ableton Live. It is part of DanonAA.

> **Status:** early skeleton. Nothing usable yet.

## Why

- **Trust.** Most "YouTube to MP3" sites are full of ads and you cannot see what they do. This app is open source and runs on your own machine.
- **Your own copies.** Online content disappears. Keeping a copy of what matters to you helps avoid lost media.
- **Your own library.** Big private platforms should not be the only place your music and videos live.
- **Offline access.** Files on your device work without a connection.
- **Learning.** It is a teaching project: the code and its history are meant to be read.

## Intended use and legal note

Use Jaitsizazu for:

- your own content;
- public domain or freely licensed works;
- personal copies, where the law allows them.

Keep in mind:

| Topic | What the source says |
|-------|----------------------|
| YouTube Terms of Service | Downloading is not allowed unless the service allows it, YouTube and the rights holders authorise it in writing, or the law allows it. Using this app on YouTube may breach these terms. [Terms of Service](https://www.youtube.com/static?template=terms) |
| Spain: private copy | The private copy exception requires a lawful source (art. 31.2 TRLPI). [BOE text](https://www.boe.es/buscar/act.php?id=BOE-A-1996-8930#a31) |

This is not legal advice. You are responsible for how you use the app.

## Build

Everything builds and tests inside Docker. You only need Docker with Compose.

```sh
docker compose build build                        # build image (JDK 21)
docker compose run --rm build ./gradlew check     # compile and run the tests
docker compose run --rm build ./gradlew build     # full build
```

Gradle downloads are cached in the `gradle-home` Docker volume.

## Architecture

Hexagonal. The `core` module holds pure domain code: formats, requests and two ports, `MediaConverter` and `MediaDownloader`. Each platform plugs in an engine adapter: on desktop it will run the yt-dlp, ffmpeg and Deno binaries as subprocesses; on Android it will use youtubedl-android. The Compose Multiplatform UI only talks to the ports.

| Module | Content |
|--------|---------|
| `core` | Kotlin Multiplatform domain code, no I/O and no UI. |
| `desktopApp` | Compose desktop app for Windows and Linux. |

## Credits

Jaitsizazu builds on:

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) for downloads;
- [FFmpeg](https://ffmpeg.org/) for conversions.

It studies these apps as references:

- [Seal](https://github.com/JunkFood02/Seal)
- [YTDLnis](https://github.com/deniscerri/ytdlnis)
- [Parabolic](https://github.com/NickvisionApps/Parabolic)

## License

[GPL-3.0](LICENSE).
