# Build image: JDK 21 plus ffmpeg. Gradle itself comes from the wrapper,
# and its downloads live in the ~/.gradle volume declared in compose.yaml.
FROM eclipse-temurin:21.0.13_9-jdk-noble

# ffmpeg from Ubuntu: the engine-desktop integration tests run it for real.
# file, unzip and zip: scripts/package-windows.sh (curl is in the base image).
RUN apt-get update \
    && apt-get install --yes --no-install-recommends ffmpeg file unzip zip \
    && rm -rf /var/lib/apt/lists/*

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
