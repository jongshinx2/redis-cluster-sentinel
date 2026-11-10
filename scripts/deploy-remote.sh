#!/usr/bin/env sh
set -eu

REMOTE_HOST="${1:-${REMOTE_HOST:-}}"
if [ -z "$REMOTE_HOST" ]; then
  echo "Usage: REMOTE_HOST=user@host $0 [user@host]" >&2
  exit 1
fi

IMAGE_NAME="${IMAGE_NAME:-redis-cluster-app}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
NAMESPACE="${NAMESPACE:-redis-performance}"
REMOTE_DIR="${REMOTE_DIR:-/tmp/redis-cluster-app}"
REMOTE_LOAD_COMMAND="${REMOTE_LOAD_COMMAND:-docker load -i}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT INT TERM
IMAGE_ARCHIVE="$WORK_DIR/redis-cluster-app.tar"
RENDERED_MANIFEST="$WORK_DIR/deployment.yaml"

echo "[1/5] Building $IMAGE"
docker build -t "$IMAGE" .

echo "[2/5] Saving image"
docker save -o "$IMAGE_ARCHIVE" "$IMAGE"

sed -e "s|name: redis-performance|name: $NAMESPACE|g" \
    -e "s|namespace: redis-performance|namespace: $NAMESPACE|g" \
    -e "s|image: redis-cluster-app:latest|image: $IMAGE|" \
    k8s/deployment.yaml > "$RENDERED_MANIFEST"

echo "[3/5] Copying image and manifest to $REMOTE_HOST"
ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'"
scp "$IMAGE_ARCHIVE" "$REMOTE_HOST:$REMOTE_DIR/redis-cluster-app.tar"
scp "$RENDERED_MANIFEST" "$REMOTE_HOST:$REMOTE_DIR/deployment.yaml"

echo "[4/5] Loading image on remote container runtime"
ssh "$REMOTE_HOST" "$REMOTE_LOAD_COMMAND '$REMOTE_DIR/redis-cluster-app.tar'"

echo "[5/5] Applying Deployment and waiting for rollout"
ssh "$REMOTE_HOST" "kubectl apply -f '$REMOTE_DIR/deployment.yaml' && kubectl -n '$NAMESPACE' rollout status deployment/redis-cluster-app --timeout=300s"

echo "Deployed $IMAGE to namespace $NAMESPACE"
