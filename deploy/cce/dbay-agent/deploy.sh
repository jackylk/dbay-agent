#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

RELEASE_NAME="${RELEASE_NAME:-dbay-agent}"
NAMESPACE="${NAMESPACE:-dbay-agent}"
KUBECONFIG="${KUBECONFIG:-$HOME/.kube/cce-dbay-agent-config}"
IMAGE_TAG="${IMAGE_TAG:-$(cd "$PROJECT_DIR" && git rev-parse --short=8 HEAD)}"
LAKEBASE_API_BASE_URL="${LAKEBASE_API_BASE_URL:-https://api.dbay.cloud:8443/api/v1}"
DBAY_AGENT_DB_URL="${DBAY_AGENT_DB_URL:-}"
DBAY_AGENT_DB_USER="${DBAY_AGENT_DB_USER:-}"
DBAY_AGENT_DB_PASSWORD="${DBAY_AGENT_DB_PASSWORD:-}"
DBAY_AGENT_DB_DRIVER="${DBAY_AGENT_DB_DRIVER:-}"
DBAY_AGENT_JPA_DDL_AUTO="${DBAY_AGENT_JPA_DDL_AUTO:-update}"
export KUBECONFIG

helm upgrade --install "$RELEASE_NAME" "$PROJECT_DIR/deploy/helm/dbay-agent" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --set-string image.tag="$IMAGE_TAG" \
  --set-string lakebase.apiBaseUrl="$LAKEBASE_API_BASE_URL" \
  --set-string database.url="$DBAY_AGENT_DB_URL" \
  --set-string database.user="$DBAY_AGENT_DB_USER" \
  --set-string database.password="$DBAY_AGENT_DB_PASSWORD" \
  --set-string database.driver="$DBAY_AGENT_DB_DRIVER" \
  --set-string database.ddlAuto="$DBAY_AGENT_JPA_DDL_AUTO" \
  --timeout 5m

kubectl -n "$NAMESPACE" rollout status "deployment/$RELEASE_NAME" --timeout=180s
