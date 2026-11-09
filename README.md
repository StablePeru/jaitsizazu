# Jaitsizazu

Jaitsizazu will download audio and video from YouTube and similar sites, and convert your own audio and video files. One minimal app for desktop (Windows and Linux) and Android, with no ads, no telemetry and no accounts. For example, it will turn a WhatsApp voice note (Ogg Opus) into a WAV file you can drop into Ableton Live. It is part of DanonAA.

> **Status:** the desktop app converts files and downloads from web addresses, in Basque. Conversions are tested against real ffmpeg, and downloads against real yt-dlp with a local test server, inside Docker. The downloader has not been tried against YouTube from the app yet. Android and installers are not done yet.

## Language

The app speaks Basque (euskara), whatever the language of your system. English is built in as a second language, but there is no setting to choose it yet. The Basque texts are proposals still under review: [docs/translations-eu.md](docs/translations-eu.md) lists every text in both languages.

The steps below name the buttons in English, with the Basque text in brackets.

## Convert a file (desktop)

1. Click **Choose file** (*Aukeratu fitxategia*) and pick an audio file, for example a WhatsApp voice note.
2. Pick the format: WAV (the default), MP3, FLAC or Ogg Vorbis.
3. Click **Convert** (*Bihurtu*). While it runs, **Cancel** (*Utzi*) stops it and removes the unfinished file.

The new file is saved next to the original. Nothing is ever overwritten: if `memo.wav` exists, you get `memo (1).wav`.

## Download from a web address (desktop)

Open the **Download** tab (*Deskargatu*).

1. Paste the web address of a video or a song.
2. Pick what to save. **Audio (MP3)** is the default.
3. The file goes to your `Downloads` folder. **Change folder** (*Aldatu karpeta*) picks another one.
4. Click **Download** (*Deskargatu*). A bar shows the progress, the speed and the time left. **Cancel** (*Utzi*) stops it and removes the unfinished files.

The choices go from the most compatible file to the best quality:

| Choice | You get | Good for |
|--------|---------|----------|
| Audio (MP3) | An MP3 file. | Playing anywhere. |
| Audio (WAV) | A WAV file, with no further loss. | Ableton Live and other audio editors. |
| Audio (original) | The sound as the site serves it, not encoded again: usually `.opus` or `.m4a`. | Keeping the best sound in the smallest file. Ableton Live on Windows may not open these files: pick WAV for Ableton. |
| Video (MP4, up to 1080p) | An MP4 file, at most 1080p high. | Playing anywhere. |
| Video (best quality, up to 4K) | The best video up to 2160p: MP4 when its codecs allow it, otherwise MKV. | The best picture. Files can be very large, and VP9 or AV1 video may need a modern player. |

Only single videos are downloaded, never whole playlists. Nothing is ever overwritten: if the file is already in the folder, the app says so and leaves it alone. While a download runs, its partial files sit in a `.jaitsizazu-…` folder inside the chosen folder, and that folder is removed when the download ends.

Sites such as YouTube change often, and an old yt-dlp stops working with them. **Update yt-dlp** (*Eguneratu yt-dlp*) replaces the yt-dlp that the app uses with the latest stable release, in place. If that yt-dlp is read-only for you (for example, installed system-wide), the app says so and changes nothing: update it the way you installed it.

## External tools

The app runs these programs. It does not install them: only **Update yt-dlp** downloads something, a newer yt-dlp, and only when you click it.

| Tool | Needed for | Variable |
|------|------------|----------|
| [FFmpeg](https://ffmpeg.org/) (with `ffprobe` next to it) | Converting, and finishing downloads (MP3, merging video and audio) | `JAITSIZAZU_FFMPEG` |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Downloading | `JAITSIZAZU_YTDLP` |
| [Deno](https://deno.com/) 2.3.0 or later | Downloading from YouTube ([yt-dlp EJS](https://github.com/yt-dlp/yt-dlp/wiki/EJS)). Other sites work without it. | `JAITSIZAZU_DENO` |

Each tool is looked for in this order:

| Order | Where |
|-------|-------|
| 1 | The path in its environment variable. |
| 2 | `bin/<tool>` (`bin\<tool>.exe` on Windows) in the folder of the installed app launcher. |
| 3 | `<tool>` on your `PATH`. |

If a tool is missing at startup, its tab warns about it and explains these options. The buttons stay available: the app looks for the tools again every time, so you can fix it and try again. A change to `PATH` or to a variable needs a restart, because the app reads its environment when it starts.

yt-dlp always runs with `--ignore-config`, `--no-plugin-dirs` and `--no-remote-components`: a configuration file or a plugin on your machine cannot make it run other commands, and it never fetches extra code.

## Why

- **Trust.** Most "YouTube to MP3" sites are full of ads and you cannot see what they do. This app is open source and runs on your own machine.
- **Your own copies.** Online content disappears. Keeping a copy of what matters to you helps avoid lost media.
- **Your own library.** Big private platforms should not be the only place your music and videos live.
- **Offline access.** Files on your device work without a connection.
- **Learning.** It is a teaching project: the code and its history are meant to be read.

## Intended use and legal note

The download screen always shows this line, in Basque: *Deskargatu zure edukia, jabari publikoko lanak, lizentzia libreko lanak edo, legeak baimentzen duenean, kopia pertsonalak soilik.* In English: *Download only your own content, public domain or freely licensed works, or personal copies where the law allows.*

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
docker compose build build                        # build image (JDK 21, ffmpeg and yt-dlp)
docker compose run --rm build ./gradlew check     # compile and run the tests
docker compose run --rm build ./gradlew build     # full build
```

Gradle downloads are cached in the `gradle-home` Docker volume. The tests in `engine-desktop` run the image's ffmpeg and yt-dlp for real. The yt-dlp tests download from an HTTP server that the test itself starts on `127.0.0.1`: no test reaches the internet. That server has no separate video and audio streams, so merging them (the 4K choice on YouTube) is checked only through the yt-dlp arguments. The Dockerfile pins the yt-dlp version and checks its SHA-256.

**Linux: match your user.** The image runs as a user with UID and GID 1000. If your own user has other IDs, the bind-mounted checkout is not writable from the container. Build the image with yours:

```sh
HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose build build
```

The variables are called `HOST_UID` and `HOST_GID` because bash does not let you set `UID`. On Windows and macOS with Docker Desktop you can skip this.

### Portable Windows build (for testing)

This builds a Windows x64 folder that runs without installing anything. It is a test build, not an installer.

```sh
docker compose build build
docker compose run --rm build bash scripts/package-windows.sh
```

The result is `dist/jaitsizazu-windows-x64/` and `dist/jaitsizazu-windows-x64.zip`. Copy the folder to Windows and double-click `Jaitsizazu.bat`.

| Folder or file | Content |
|----------------|---------|
| `app/` | The app JARs, built for Windows x64. |
| `runtime/` | Eclipse Temurin 21 JRE for Windows x64. |
| `bin/ffmpeg.exe`, `bin/ffprobe.exe` | FFmpeg GPL static build for Windows x64. |
| `bin/yt-dlp.exe` | yt-dlp standalone build for Windows x64. "Update yt-dlp" replaces it in place. |
| `bin/deno.exe` | Deno for Windows x64, for YouTube downloads. |
| `THIRD-PARTY.txt`, `licenses/` | Third-party versions, sources and licenses. |

If the window does not appear, look in `%LOCALAPPDATA%\Jaitsizazu\`: `launch.log` holds the Java output of the last launch, and `crash.log` the errors the app could not handle.

The script pins the Temurin, FFmpeg, yt-dlp and Deno versions at its top, downloads them over HTTPS and stops if a SHA-256 does not match the pinned value or the publisher's checksum file. The yt-dlp and Deno license texts have no published checksum: the script checks them against its pinned SHA-256 only. `-Pjaitsizazu.desktopTarget=windows-x64` is what makes Gradle use the Windows Skiko runtime from the Linux container.

## Architecture

Hexagonal. The `core` module holds pure domain code: formats, requests, the `ConvertMedia` and `DownloadMedia` use cases and three ports, `MediaConverter`, `MediaDownloader` and `DownloaderUpdater`. Failures are typed `Problem` values, not sentences: the UI puts them into words in the app's language and quotes the output of ffmpeg and yt-dlp as it is. Each platform plugs in an engine adapter: on desktop it runs ffmpeg and yt-dlp (with Deno) as subprocesses; on Android it will use youtubedl-android. The shared Compose UI only talks to the use cases.

| Module | Content |
|--------|---------|
| `core` | Kotlin Multiplatform domain code, no I/O and no UI. |
| `ui` | Kotlin Multiplatform Compose UI: plain state holders, stateless screens and every user-facing text, as Compose resources (Basque in `values/`, English in `values-en/`). JVM target only for now. |
| `engine-desktop` | Desktop engine adapters: finds and runs ffmpeg and yt-dlp, and reads yt-dlp's progress. |
| `desktopApp` | Compose desktop app for Windows and Linux, with the Convert and Download tabs. It wires the engine into the UI and owns the file and folder dialogs. |

Android will add its own engine module and app module. `core` and `ui` gain an Android target without moving code.

## Credits

Jaitsizazu builds on:

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) for downloads;
- [Deno](https://deno.com/) to run YouTube's JavaScript for yt-dlp;
- [FFmpeg](https://ffmpeg.org/) for conversions.

It studies these apps as references:

- [Seal](https://github.com/JunkFood02/Seal)
- [YTDLnis](https://github.com/deniscerri/ytdlnis)
- [Parabolic](https://github.com/NickvisionApps/Parabolic)

## License

[GPL-3.0](LICENSE).
