# Build image: JDK 21, ffmpeg, yt-dlp and the Android SDK. Gradle itself comes
# from the wrapper, and its downloads live in the ~/.gradle volume declared in
# compose.yaml.
FROM eclipse-temurin:21.0.13_9-jdk-noble

# ffmpeg from Ubuntu: the engine-desktop integration tests run it for real.
# file, unzip and zip: scripts/package-windows.sh (curl is in the base image).
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ffmpeg file unzip zip \
    && rm -rf /var/lib/apt/lists/*

# yt-dlp standalone Linux binary, pinned and checked against its SHA-256:
# the engine-desktop integration tests download from a local HTTP server with
# it, never from the internet. Keep the version in step with YTDLP_VERSION in
# scripts/package-windows.sh. Owned by root, so the tests see a yt-dlp that
# cannot update itself.
ARG YTDLP_VERSION=2026.10.26
ARG YTDLP_SHA256=767ba9dce7052aa196f8b12652f433d76de128ba168966fa84b6394d5e6c6795
RUN curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
        --output /usr/local/bin/yt-dlp \
        "https://github.com/yt-dlp/yt-dlp/releases/download/${YTDLP_VERSION}/yt-dlp_linux" \
    && echo "${YTDLP_SHA256}  /usr/local/bin/yt-dlp" | sha256sum --check --strict \
    && chmod 0755 /usr/local/bin/yt-dlp

# Run as a non-root user. UID/GID default to 1000 so that files written
# into a bind-mounted checkout on Linux belong to the usual first user.
# compose.yaml passes HOST_UID/HOST_GID here when they are set.
ARG UID=1000
ARG GID=1000
RUN userdel --remove ubuntu 2>/dev/null || true \
    && groupadd --gid "${GID}" builder \
    && useradd --uid "${UID}" --gid "${GID}" --create-home builder \
    && mkdir -p /home/builder/.gradle /workspace \
    && chown builder:builder /home/builder/.gradle /workspace

# Android SDK for :androidApp, :engine-android and the Android targets of :core
# and :ui. The command-line tools are pinned and checked against their SHA-256
# (from dl.google.com/android/repository/repository2-3.xml). The SDK belongs to
# builder, so the build can read it and install nothing else as root.
# compileSdk 37 (androidx.compose.ui 1.12 needs it) and the build tools that
# AGP 9.3 uses by default.
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools"
ARG ANDROID_CMDLINE_TOOLS=commandlinetools-linux-15859902_latest.zip
ARG ANDROID_CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
RUN mkdir -p "${ANDROID_HOME}" && chown builder:builder "${ANDROID_HOME}"

USER builder
RUN cd /tmp \
    && curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
        --output cmdline-tools.zip \
        "https://dl.google.com/android/repository/${ANDROID_CMDLINE_TOOLS}" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  cmdline-tools.zip" | sha256sum --check --strict \
    && unzip -q cmdline-tools.zip -d cmdline-tools-unpacked \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && mv cmdline-tools-unpacked/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf cmdline-tools.zip cmdline-tools-unpacked \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "platforms;android-37.0" "build-tools;36.0.0" "platform-tools" > /dev/null

WORKDIR /workspace
