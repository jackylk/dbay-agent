#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

RELEASE_NAME="${RELEASE_NAME:-dbay-agent}"
NAMESPACE="${NAMESPACE:-dbay-agent}"
KUBECONFIG="${KUBECONFIG:-$HOME/.kube/cce-dbay-control-plane-config}"
IMAGE_TAG="${IMAGE_TAG:-$(cd "$PROJECT_DIR" && git rev-parse --short=8 HEAD)}"
LAKEBASE_API_BASE_URL="${LAKEBASE_API_BASE_URL:-https://api.dbay.cloud:8443/api/v1}"

helm upgrade --install "$RELEASE_NAME" "$PROJECT_DIR/deploy/helm/dbay-agent" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --set-string image.tag="$IMAGE_TAG" \
  --set-string lakebase.apiBaseUrl="$LAKEBASE_API_BASE_URL" \
  --timeout 5m

kubectl -n "$NAMESPACE" rollout status "deployment/$RELEASE_NAME" --timeout=180s
