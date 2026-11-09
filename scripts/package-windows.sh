#!/usr/bin/env bash
# Builds a portable Windows x64 folder of Jaitsizazu, for manual testing:
#
#   dist/jaitsizazu-windows-x64/      double-click Jaitsizazu.bat
#   dist/jaitsizazu-windows-x64.zip
#
# Run it inside the build container, never on the host:
#
#   docker compose run --rm build bash scripts/package-windows.sh
#
# Downloads are cached in build/windows-downloads/ and checked against the
# pinned SHA-256 on every run, and against the publisher's checksum file when
# the publisher has one (the license texts have none: only the pin checks them).
set -euo pipefail

# --- Pinned versions: change them here, and only here. ---------------------

# Eclipse Temurin 21 JRE for Windows x64 (Adoptium).
TEMURIN_RELEASE="jdk-21.0.13+9"
TEMURIN_ASSET="OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip"
TEMURIN_SHA256="2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628"

# FFmpeg GPL static build for Windows x64 (BtbN/FFmpeg-Builds, now hosted as
# FFmpeg-Builds/FFmpeg-Builds). Pin a month-end autobuild: the last build of
# each month is kept for two years, daily builds only for 14 days.
FFMPEG_TAG="autobuild-2026-10-31-12-59"
FFMPEG_ASSET="ffmpeg-n9.0.2-39-g7adfbe40dc-win64-gpl-9.0.zip"
FFMPEG_SHA256="693f3e224bc184b40524a62ca47526cfebaf2504414021b92fe75f677ad868b9"

# yt-dlp standalone Windows x64 binary. Same version as YTDLP_VERSION in the
# Dockerfile, whose Linux binary the tests run.
YTDLP_VERSION="2026.10.26"
YTDLP_SHA256="f5389d3698ecde4ef06f4e6b1ce0294fe9ac1396228c8990b8d3416d6d177a7d"
YTDLP_LICENSE_SHA256="7e12e5df4bae12cb21581ba157ced20e1986a0508dd10d0e8a4ab9a4cf94e85c"
YTDLP_THIRD_PARTY_SHA256="472aefe951c7db35e1657c1d13fd337140511ed6f2b329205105ad441c5a02b7"

# Deno for Windows x64: the JavaScript runtime yt-dlp needs for YouTube
# (yt-dlp asks for Deno 2.3.0 or later).
DENO_VERSION="v2.9.8"
DENO_ASSET="deno-x86_64-pc-windows-msvc.zip"
DENO_SHA256="cba84b26e0df5ae7f74fc66b86a95b866872a86292ebd84fa1e959927edd93bf"
DENO_LICENSE_SHA256="f62497fffecc0852960c8d3e6934b9db86d16396e9b604072e923892cae3a588"

# ---------------------------------------------------------------------------

TEMURIN_BASE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${TEMURIN_RELEASE//+/%2B}"
FFMPEG_BASE_URL="https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/${FFMPEG_TAG}"
YTDLP_BASE_URL="https://github.com/yt-dlp/yt-dlp/releases/download/${YTDLP_VERSION}"
YTDLP_SOURCE_URL="https://raw.githubusercontent.com/yt-dlp/yt-dlp/${YTDLP_VERSION}"
DENO_BASE_URL="https://github.com/denoland/deno/releases/download/${DENO_VERSION}"
DENO_SOURCE_URL="https://raw.githubusercontent.com/denoland/deno/${DENO_VERSION}"

root="$(cd "$(dirname "$0")/.." && pwd)"
name="jaitsizazu-windows-x64"
dist="$root/dist"
out="$dist/$name"
downloads="$root/build/windows-downloads"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

# The FFmpeg commit is the hash after "-g" in the asset name, so it has one source.
[[ "$FFMPEG_ASSET" =~ -g([0-9a-f]{7,40})- ]] \
    || fail "Cannot read the FFmpeg commit from $FFMPEG_ASSET (expected -g<hash>-)."
FFMPEG_COMMIT="${BASH_REMATCH[1]}"

# Downloads $1/$2 into the cache as $4 (default: $2) unless it is there, then
# checks it against the pinned hash $3. Prints nothing else on stdout.
fetch_pinned() {
    local base_url="$1" asset="$2" pinned="$3" name="${4:-$2}"
    local file="$downloads/$name"
    if [[ ! -f "$file" ]]; then
        echo "Downloading $asset" >&2
        curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
            --output "$file.part" "$base_url/$asset"
        mv "$file.part" "$file"
    fi

    local actual
    actual="$(sha256sum "$file" | cut -d' ' -f1)"
    if [[ "$actual" != "$pinned" ]]; then
        rm -f "$file"
        fail "SHA-256 mismatch for $asset: got $actual, pinned $pinned. The file was deleted."
    fi
    echo "SHA-256 OK: $name ($actual)" >&2
}

# Like fetch_pinned, and also checks the pinned hash $3 against the
# publisher's checksum file $4. Two formats are read: sha256sum lines
# ("<hash>  <asset>") and PowerShell Get-FileHash output ("Hash : <HASH>"),
# which Deno publishes, one file per asset.
fetch_verified() {
    local base_url="$1" asset="$2" pinned="$3" checksum_url="$4"
    fetch_pinned "$base_url" "$asset" "$pinned"

    local published
    published="$(curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 "$checksum_url" \
        | tr -d '\r' \
        | awk -v asset="$asset" '
            $2 == asset || $2 == "*" asset { print tolower($1) }
            $1 == "Hash" && $2 == ":" { print tolower($3) }')"
    if [[ "$published" != "$pinned" ]]; then
        fail "Published checksum for $asset is '${published:-missing}', pinned $pinned ($checksum_url)."
    fi
    echo "Published checksum OK: $asset"
}

mkdir -p "$downloads"

fetch_verified "$TEMURIN_BASE_URL" "$TEMURIN_ASSET" "$TEMURIN_SHA256" \
    "$TEMURIN_BASE_URL/$TEMURIN_ASSET.sha256.txt"
fetch_verified "$FFMPEG_BASE_URL" "$FFMPEG_ASSET" "$FFMPEG_SHA256" \
    "$FFMPEG_BASE_URL/checksums.sha256"
fetch_verified "$YTDLP_BASE_URL" "yt-dlp.exe" "$YTDLP_SHA256" \
    "$YTDLP_BASE_URL/SHA2-256SUMS"
fetch_verified "$DENO_BASE_URL" "$DENO_ASSET" "$DENO_SHA256" \
    "$DENO_BASE_URL/$DENO_ASSET.sha256sum"
fetch_pinned "$YTDLP_SOURCE_URL" "LICENSE" "$YTDLP_LICENSE_SHA256" "yt-dlp-LICENSE-${YTDLP_VERSION}.txt"
fetch_pinned "$YTDLP_SOURCE_URL" "THIRD_PARTY_LICENSES.txt" "$YTDLP_THIRD_PARTY_SHA256" \
    "yt-dlp-THIRD_PARTY_LICENSES-${YTDLP_VERSION}.txt"
fetch_pinned "$DENO_SOURCE_URL" "LICENSE.md" "$DENO_LICENSE_SHA256" "deno-LICENSE-${DENO_VERSION}.md"

echo "Building the app for windows-x64"
"$root/gradlew" --project-dir "$root" --quiet \
    -Pjaitsizazu.desktopTarget=windows-x64 :desktopApp:stageAppJars
jars="$root/desktopApp/build/app-jars/windows-x64"

# Guard: the Skiko native runtime must be the Windows one, and only that one.
skiko_runtimes=("$jars"/skiko-awt-runtime-*.jar)
[[ ${#skiko_runtimes[@]} -eq 1 && "${skiko_runtimes[0]}" == *-windows-x64-* ]] \
    || fail "Expected exactly one Windows x64 Skiko runtime JAR, found: ${skiko_runtimes[*]##*/}"
unzip -l "${skiko_runtimes[0]}" | grep -q 'skiko-windows-x64\.dll' \
    || fail "${skiko_runtimes[0]##*/} has no skiko-windows-x64.dll"

echo "Assembling $out"
rm -rf "$out" "$dist/$name.zip"
mkdir -p "$out/app" "$out/bin" "$out/licenses"

cp "$jars"/*.jar "$out/app/"

unzip -q "$downloads/$TEMURIN_ASSET" -d "$work/temurin"
temurin_dirs=("$work"/temurin/*/)
[[ ${#temurin_dirs[@]} -eq 1 ]] || fail "Unexpected layout in $TEMURIN_ASSET"
mv "${temurin_dirs[0]}" "$out/runtime"
[[ -f "$out/runtime/bin/javaw.exe" ]] || fail "No bin/javaw.exe in $TEMURIN_ASSET"

ffmpeg_dir="$work/ffmpeg/${FFMPEG_ASSET%.zip}"
unzip -q "$downloads/$FFMPEG_ASSET" -d "$work/ffmpeg"
[[ -f "$ffmpeg_dir/bin/ffmpeg.exe" ]] || fail "No bin/ffmpeg.exe in $FFMPEG_ASSET"
# ffprobe: yt-dlp needs it next to ffmpeg to extract audio.
[[ -f "$ffmpeg_dir/bin/ffprobe.exe" ]] || fail "No bin/ffprobe.exe in $FFMPEG_ASSET"
cp "$ffmpeg_dir/bin/ffmpeg.exe" "$ffmpeg_dir/bin/ffprobe.exe" "$out/bin/"
cp "$ffmpeg_dir/LICENSE.txt" "$out/licenses/FFmpeg-LICENSE.txt"

cp "$downloads/yt-dlp.exe" "$out/bin/"
cp "$downloads/yt-dlp-LICENSE-${YTDLP_VERSION}.txt" "$out/licenses/yt-dlp-LICENSE.txt"
cp "$downloads/yt-dlp-THIRD_PARTY_LICENSES-${YTDLP_VERSION}.txt" "$out/licenses/yt-dlp-THIRD_PARTY_LICENSES.txt"

unzip -q "$downloads/$DENO_ASSET" -d "$work/deno"
[[ -f "$work/deno/deno.exe" ]] || fail "No deno.exe in $DENO_ASSET"
cp "$work/deno/deno.exe" "$out/bin/"
cp "$downloads/deno-LICENSE-${DENO_VERSION}.md" "$out/licenses/Deno-LICENSE.txt"

for notice in LICENSE ASSEMBLY_EXCEPTION ADDITIONAL_LICENSE_INFO; do
    if [[ -f "$out/runtime/legal/java.base/$notice" ]]; then
        cp "$out/runtime/legal/java.base/$notice" "$out/licenses/Temurin-$notice.txt"
    fi
done

cp "$root/LICENSE" "$out/LICENSE"

# The launcher must have CRLF line endings, whatever the checkout has.
sed -e 's/\r$//' -e 's/$/\r/' "$root/scripts/windows/Jaitsizazu.bat" > "$out/Jaitsizazu.bat"

sed -e 's/\r$//' -e 's/$/\r/' > "$out/THIRD-PARTY.txt" <<EOF
Jaitsizazu is licensed under the GNU General Public License v3.0 (LICENSE).
This folder also contains third-party software:

runtime/  Eclipse Temurin JRE ${TEMURIN_RELEASE}, Windows x64, by the Eclipse Foundation (Adoptium).
          License: GNU GPL v2 with the Classpath Exception.
          https://adoptium.net/  https://openjdk.org/legal/gplv2+ce.html
          Full notices: runtime/legal/ and licenses/Temurin-*.txt

bin/ffmpeg.exe, bin/ffprobe.exe
          FFmpeg ${FFMPEG_ASSET%.zip} (static GPL build, commit ${FFMPEG_COMMIT}).
          License: GNU GPL v3 or later (GPL build with GPL components).
          Build: ${FFMPEG_BASE_URL}/${FFMPEG_ASSET}
          Build scripts: https://github.com/FFmpeg-Builds/FFmpeg-Builds
          FFmpeg source: https://github.com/FFmpeg/FFmpeg/commit/${FFMPEG_COMMIT}
          Full license: licenses/FFmpeg-LICENSE.txt

bin/yt-dlp.exe
          yt-dlp ${YTDLP_VERSION}, standalone Windows x64 build (PyInstaller).
          yt-dlp itself is released under the Unlicense, but this binary bundles
          Python and GPLv3+ licensed code, so the binary as a whole is licensed
          under the GNU GPL v3 or later (yt-dlp README, "License").
          Binary: ${YTDLP_BASE_URL}/yt-dlp.exe
          Source: https://github.com/yt-dlp/yt-dlp/tree/${YTDLP_VERSION}
          Licenses: licenses/yt-dlp-LICENSE.txt, licenses/yt-dlp-THIRD_PARTY_LICENSES.txt
          It can replace itself with a newer release ("Update yt-dlp" in the app).

bin/deno.exe
          Deno ${DENO_VERSION}, Windows x64, by the Deno authors.
          License: MIT. Full license: licenses/Deno-LICENSE.txt
          Binary: ${DENO_BASE_URL}/${DENO_ASSET}
          Source: https://github.com/denoland/deno/tree/${DENO_VERSION}

app/      The Jaitsizazu JARs and their Java libraries: Kotlin, kotlinx.coroutines,
          Compose Multiplatform and Skiko (Apache License 2.0). Skiko includes Skia
          (BSD 3-Clause).
EOF

(cd "$dist" && zip -qr "$name.zip" "$name")

echo "Done:"
echo "  $out"
echo "  $dist/$name.zip"
