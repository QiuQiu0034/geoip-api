# ---------------------------------------------------------------------
# (1) build stage
# ---------------------------------------------------------------------
FROM observabilitystack/graalvm-maven-builder:21.0.1-ol9 AS builder
ARG MAXMIND_LICENSE_KEY

ADD . /build
WORKDIR /build

# Build application
RUN mvn -B native:compile -P native --no-transfer-progress -DskipTests=true && \
    chmod +x /build/target/geoip-api

# Download recent geoip data


# ---------------------------------------------------------------------
# (2) run stage
# ---------------------------------------------------------------------
FROM debian:bookworm-slim
ARG CREATED_AT
ARG VERSION
ARG GIT_REVISION
#
## Add labels to identify release
LABEL org.opencontainers.image.authors="Torsten B. Köster <tbk@thiswayup.de>" \
      org.opencontainers.image.url="https://github.com/observabilitystack/geoip-api" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.title="Geoip-API" \
      org.opencontainers.image.description="A JSON REST API for Maxmind GeoIP databases" \
      org.opencontainers.image.created="${CREATED_AT}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_REVISION}"

## install curl for healthcheck
RUN apt-get update && \
    apt-get install -y curl && \
    apt-get clean

## place app and data
COPY --from=builder "/build/target/geoip-api" /srv/geoip-api


CMD ["sleep","99999"]
