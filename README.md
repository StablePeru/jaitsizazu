# Jaitsizazu

Jaitsizazu will download audio and video from YouTube and similar sites, and convert your own audio and video files. One minimal app for desktop (Windows and Linux) and Android, with no ads, no telemetry and no accounts. For example, it will turn a WhatsApp voice note (Ogg Opus) into a WAV file you can drop into Ableton Live. It is part of DanonAA.

> **Status:** the desktop converter is in place. Its conversions are tested against real ffmpeg inside Docker. Downloads, Android and installers are not done yet.

## Convert a file (desktop)

1. Click **Choose file** and pick an audio file, for example a WhatsApp voice note.
2. Pick the format: WAV (the default), MP3, FLAC or Ogg Vorbis.
3. Click **Convert**.

The new file is saved next to the original. Nothing is ever overwritten: if `memo.wav` exists, you get `memo (1).wav`.

### ffmpeg is required

The app runs [FFmpeg](https://ffmpeg.org/) to convert. It looks for it in this order:

| Order | Where |
|-------|-------|
| 1 | The path in the `JAITSIZAZU_FFMPEG` environment variable. |
| 2 | `bin/ffmpeg` (`bin\ffmpeg.exe` on Windows) in the folder of the installed app launcher. |
| 3 | `ffmpeg` on your `PATH`. |

If none is found, the window says so and explains these options.

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
docker compose build build                        # build image (JDK 21 and ffmpeg)
docker compose run --rm build ./gradlew check     # compile and run the tests
docker compose run --rm build ./gradlew build     # full build
```

Gradle downloads are cached in the `gradle-home` Docker volume. The tests in `engine-desktop` run the image's ffmpeg for real.

**Linux: match your user.** The image runs as a user with UID and GID 1000. If your own user has other IDs, the bind-mounted checkout is not writable from the container. Build the image with yours:

```sh
HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose build build
```

The variables are called `HOST_UID` and `HOST_GID` because bash does not let you set `UID`. On Windows and macOS with Docker Desktop you can skip this.

## Architecture

Hexagonal. The `core` module holds pure domain code: formats, requests, the `ConvertMedia` use case and two ports, `MediaConverter` and `MediaDownloader`. Each platform plugs in an engine adapter: on desktop it runs ffmpeg as a subprocess today, and will run yt-dlp and Deno the same way; on Android it will use youtubedl-android. The shared Compose UI only talks to the use cases.

| Module | Content |
|--------|---------|
| `core` | Kotlin Multiplatform domain code, no I/O and no UI. |
| `ui` | Kotlin Multiplatform Compose UI: plain state holders and stateless screens. JVM target only for now. |
| `engine-desktop` | Desktop engine adapter: finds and runs ffmpeg. |
| `desktopApp` | Compose desktop app for Windows and Linux. It wires the engine into the UI and owns the file dialog. |

Android will add its own engine module and app module. `core` and `ui` gain an Android target without moving code.

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
