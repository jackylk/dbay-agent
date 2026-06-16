#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

SWR_REGION="${SWR_REGION:-cn-north-4}"
SWR_ORG="${SWR_ORG:-flex}"
IMAGE_TAG="${IMAGE_TAG:-$(cd "$PROJECT_DIR" && git rev-parse --short=8 HEAD)}"
IMAGE="swr.${SWR_REGION}.myhuaweicloud.com/${SWR_ORG}/dbay-agent-api:${IMAGE_TAG}"

cd "$PROJECT_DIR/dbay-agent-api"
mvn package -Dmaven.test.skip=true -q

docker build -t "$IMAGE" .

if command -v crane >/dev/null 2>&1; then
  TAR="/tmp/dbay-agent-api-${IMAGE_TAG}.tar"
  docker save "$IMAGE" -o "$TAR"
  env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy \
    NO_PROXY='*' no_proxy='*' crane push --platform linux/amd64 "$TAR" "$IMAGE"
else
  docker push "$IMAGE"
fi

echo "$IMAGE"
