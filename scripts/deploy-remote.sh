#!/usr/bin/env sh
set -eu

REMOTE_HOST="${1:-${REMOTE_HOST:-}}"
if [ -z "$REMOTE_HOST" ]; then
  echo "Usage: REMOTE_HOST=user@host SSH_KEY=/path/to/private_key $0 [user@host]" >&2
  exit 1
fi
IMAGE_HOSTS="${IMAGE_HOSTS:-$REMOTE_HOST}"

SSH_BIN="${SSH_BIN:-ssh}"
SCP_BIN="${SCP_BIN:-scp}"
SSH_KEY="${SSH_KEY:-}"
SSH_PORT="${SSH_PORT:-22}"
IMAGE_NAME="${IMAGE_NAME:-docker.io/library/redis-cluster-app}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
NAMESPACE="${NAMESPACE:-redis-performance}"
REMOTE_DIR="${REMOTE_DIR:-/tmp/redis-cluster-app}"
REMOTE_LOAD_COMMAND="${REMOTE_LOAD_COMMAND:-}"
REMOTE_IMAGE_LIST_COMMAND="${REMOTE_IMAGE_LIST_COMMAND:-}"
REMOTE_KUBECTL="${REMOTE_KUBECTL:-}"
K8S_NODE_NAME="${K8S_NODE_NAME:-}"
PIN_TO_REMOTE_NODE="${PIN_TO_REMOTE_NODE:-true}"

ssh_exec_on() {
  target_host="$1"
  remote_command="$2"
  set -- -p "$SSH_PORT" -o StrictHostKeyChecking=accept-new
  if [ -n "$SSH_KEY" ]; then
    set -- "$@" -o BatchMode=yes -i "$SSH_KEY"
  fi
  "$SSH_BIN" "$@" "$target_host" "$remote_command"
}

ssh_exec() {
  remote_command="$1"
  ssh_exec_on "$REMOTE_HOST" "$remote_command"
}

scp_copy_to() {
  target_host="$1"
  local_path="$2"
  remote_path="$3"
  set -- -P "$SSH_PORT" -o StrictHostKeyChecking=accept-new
  if [ -n "$SSH_KEY" ]; then
    set -- "$@" -o BatchMode=yes -i "$SSH_KEY"
  fi
  "$SCP_BIN" "$@" "$local_path" "$target_host:$remote_path"
}

scp_copy() {
  local_path="$1"
  remote_path="$2"
  scp_copy_to "$REMOTE_HOST" "$local_path" "$remote_path"
}

if [ -z "$REMOTE_KUBECTL" ]; then
  REMOTE_K8S_TYPE="$(ssh_exec "if command -v k3s >/dev/null 2>&1; then echo k3s; elif command -v microk8s >/dev/null 2>&1; then echo microk8s; elif sudo -n test -f /etc/rancher/rke2/rke2.yaml; then echo rke2; elif sudo -n test -f /etc/kubernetes/admin.conf; then echo kubeadm; elif test -f \"\$HOME/.kube/config\"; then echo user; else echo unknown; fi")"
  case "$REMOTE_K8S_TYPE" in
    k3s) REMOTE_KUBECTL="sudo -n k3s kubectl" ;;
    microk8s) REMOTE_KUBECTL="sudo -n microk8s kubectl" ;;
    rke2) REMOTE_KUBECTL="sudo -n /var/lib/rancher/rke2/bin/kubectl --kubeconfig=/etc/rancher/rke2/rke2.yaml" ;;
    kubeadm) REMOTE_KUBECTL="sudo -n kubectl --kubeconfig=/etc/kubernetes/admin.conf" ;;
    user) REMOTE_KUBECTL="kubectl --kubeconfig=\$HOME/.kube/config" ;;
    *)
      echo "Could not detect a usable remote Kubernetes kubeconfig. Set REMOTE_KUBECTL explicitly." >&2
      exit 1
      ;;
  esac
  echo "Detected remote Kubernetes type: $REMOTE_K8S_TYPE"
fi

if [ -z "$REMOTE_LOAD_COMMAND" ]; then
  REMOTE_RUNTIME_TYPE="$(ssh_exec "if command -v k3s >/dev/null 2>&1; then echo k3s; elif command -v microk8s >/dev/null 2>&1; then echo microk8s; elif command -v ctr >/dev/null 2>&1; then echo containerd; else echo unknown; fi")"
  case "$REMOTE_RUNTIME_TYPE" in
    k3s)
      REMOTE_LOAD_COMMAND="sudo -n k3s ctr images import"
      REMOTE_IMAGE_LIST_COMMAND="sudo -n k3s ctr images list"
      ;;
    microk8s)
      REMOTE_LOAD_COMMAND="sudo -n microk8s ctr image import"
      REMOTE_IMAGE_LIST_COMMAND="sudo -n microk8s ctr images list"
      ;;
    containerd)
      REMOTE_LOAD_COMMAND="sudo -n ctr --namespace k8s.io images import"
      REMOTE_IMAGE_LIST_COMMAND="sudo -n ctr --namespace k8s.io images list"
      ;;
    *)
      echo "Could not detect containerd tooling. Set REMOTE_LOAD_COMMAND and REMOTE_IMAGE_LIST_COMMAND." >&2
      exit 1
      ;;
  esac
  echo "Detected remote runtime type: $REMOTE_RUNTIME_TYPE"
fi

if [ -z "$REMOTE_IMAGE_LIST_COMMAND" ]; then
  echo "REMOTE_IMAGE_LIST_COMMAND is required when REMOTE_LOAD_COMMAND is overridden." >&2
  exit 1
fi

IMAGE_HOST_COUNT=0
for image_host in $IMAGE_HOSTS; do
  IMAGE_HOST_COUNT=$((IMAGE_HOST_COUNT + 1))
done
if [ "$IMAGE_HOST_COUNT" -gt 1 ] && [ "$PIN_TO_REMOTE_NODE" = "true" ]; then
  echo "Multiple image hosts configured; disabling single-node pinning."
  PIN_TO_REMOTE_NODE=false
fi

if [ "$PIN_TO_REMOTE_NODE" = "true" ] && [ -z "$K8S_NODE_NAME" ]; then
  K8S_NODE_NAME="$(ssh_exec "hostname")"
fi

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
    -e "s|image: docker.io/library/redis-cluster-app:latest|image: $IMAGE|" \
    k8s/deployment.yaml > "$RENDERED_MANIFEST"

echo "[3/5] Copying image to containerd nodes"
for image_host in $IMAGE_HOSTS; do
  echo "Copying image to $image_host"
  ssh_exec_on "$image_host" "mkdir -p '$REMOTE_DIR'"
  scp_copy_to "$image_host" "$IMAGE_ARCHIVE" "$REMOTE_DIR/redis-cluster-app.tar"
done
scp_copy "$RENDERED_MANIFEST" "$REMOTE_DIR/deployment.yaml"

echo "[4/5] Importing image into containerd on every node"
for image_host in $IMAGE_HOSTS; do
  echo "Importing image on $image_host"
  ssh_exec_on "$image_host" "$REMOTE_LOAD_COMMAND '$REMOTE_DIR/redis-cluster-app.tar'"
  ssh_exec_on "$image_host" "$REMOTE_IMAGE_LIST_COMMAND | grep '$IMAGE'"
done

echo "[5/5] Applying Deployment and waiting for rollout"
if [ "$PIN_TO_REMOTE_NODE" = "true" ]; then
  NODE_PATCH="{\"spec\":{\"template\":{\"spec\":{\"nodeSelector\":{\"kubernetes.io/hostname\":\"$K8S_NODE_NAME\"}}}}}"
  if ! ssh_exec "$REMOTE_KUBECTL get node '$K8S_NODE_NAME' >/dev/null"; then
    echo "Kubernetes node '$K8S_NODE_NAME' was not found. Set K8S_NODE_NAME to one of:" >&2
    ssh_exec "$REMOTE_KUBECTL get nodes -o wide"
    exit 1
  fi
  ssh_exec "$REMOTE_KUBECTL apply --validate=false -f '$REMOTE_DIR/deployment.yaml' && $REMOTE_KUBECTL -n '$NAMESPACE' patch deployment redis-cluster-app --type merge -p '$NODE_PATCH'"
else
  CLEAR_NODE_PATCH='{"spec":{"template":{"spec":{"nodeSelector":null}}}}'
  ssh_exec "$REMOTE_KUBECTL apply --validate=false -f '$REMOTE_DIR/deployment.yaml' && $REMOTE_KUBECTL -n '$NAMESPACE' patch deployment redis-cluster-app --type merge -p '$CLEAR_NODE_PATCH'"
fi

if ! ssh_exec "$REMOTE_KUBECTL -n '$NAMESPACE' rollout status deployment/redis-cluster-app --timeout=300s"; then
  ssh_exec "$REMOTE_KUBECTL -n '$NAMESPACE' get pods -o wide && $REMOTE_KUBECTL -n '$NAMESPACE' describe pods -l app=redis-cluster-app"
  exit 1
fi

echo "Deployed $IMAGE to namespace $NAMESPACE"
