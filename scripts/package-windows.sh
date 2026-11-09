#!/usr/bin/env bash
# Builds a portable Windows x64 folder of Jaitsizazu, for manual testing:
#
#   dist/jaitsizazu-windows-x64/      double-click Jaitsizazu.bat
#   dist/jaitsizazu-windows-x64.zip
#
# Run it inside the build container, never on the host:
#
#   docker compose run --rm build scripts/package-windows.sh
#
# Downloads are cached in build/windows-downloads/ and checked against the
# pinned SHA-256 and the publisher's checksum file on every run.
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
FFMPEG_COMMIT="7adfbe40dc"

# ---------------------------------------------------------------------------

TEMURIN_BASE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${TEMURIN_RELEASE//+/%2B}"
FFMPEG_BASE_URL="https://github.com/FFmpeg-Builds/FFmpeg-Builds/releases/download/${FFMPEG_TAG}"

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

# Downloads $1/$2 into the cache unless it is there, then checks it against
# the pinned hash $3 and against the publisher's checksum file $4.
fetch_verified() {
    local base_url="$1" asset="$2" pinned="$3" checksum_url="$4"
    local file="$downloads/$asset"
    if [[ ! -f "$file" ]]; then
        echo "Downloading $asset"
        curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
            --output "$file.part" "$base_url/$asset"
        mv "$file.part" "$file"
    fi

    local actual published
    actual="$(sha256sum "$file" | cut -d' ' -f1)"
    published="$(curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 "$checksum_url" \
        | awk -v asset="$asset" '$2 == asset || $2 == "*" asset { print $1 }')"

    if [[ "$actual" != "$pinned" ]]; then
        rm -f "$file"
        fail "SHA-256 mismatch for $asset: got $actual, pinned $pinned. The file was deleted."
    fi
    if [[ "$published" != "$pinned" ]]; then
        fail "Published checksum for $asset is '${published:-missing}', pinned $pinned ($checksum_url)."
    fi
    echo "SHA-256 OK: $asset ($actual)"
}

mkdir -p "$downloads"

fetch_verified "$TEMURIN_BASE_URL" "$TEMURIN_ASSET" "$TEMURIN_SHA256" \
    "$TEMURIN_BASE_URL/$TEMURIN_ASSET.sha256.txt"
fetch_verified "$FFMPEG_BASE_URL" "$FFMPEG_ASSET" "$FFMPEG_SHA256" \
    "$FFMPEG_BASE_URL/checksums.sha256"

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
cp "$ffmpeg_dir/bin/ffmpeg.exe" "$out/bin/"
cp "$ffmpeg_dir/LICENSE.txt" "$out/licenses/FFmpeg-LICENSE.txt"

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

bin/      FFmpeg ${FFMPEG_ASSET%.zip} (static GPL build, commit ${FFMPEG_COMMIT}).
          License: GNU GPL v3 or later (GPL build with GPL components).
          Build: ${FFMPEG_BASE_URL}/${FFMPEG_ASSET}
          Build scripts: https://github.com/FFmpeg-Builds/FFmpeg-Builds
          FFmpeg source: https://github.com/FFmpeg/FFmpeg/commit/${FFMPEG_COMMIT}
          Full license: licenses/FFmpeg-LICENSE.txt

app/      The Jaitsizazu JARs and their Java libraries: Kotlin, kotlinx.coroutines,
          Compose Multiplatform and Skiko (Apache License 2.0). Skiko includes Skia
          (BSD 3-Clause).
EOF

(cd "$dist" && zip -qr "$name.zip" "$name")

echo "Done:"
echo "  $out"
echo "  $dist/$name.zip"
