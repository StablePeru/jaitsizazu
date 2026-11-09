# Build image: JDK 21, ffmpeg and yt-dlp. Gradle itself comes from the wrapper,
# and its downloads live in the ~/.gradle volume declared in compose.yaml.
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

USER builder
WORKDIR /workspace
