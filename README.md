# Redis Cluster performance application

Spring Boot 애플리케이션이 같은 Kubernetes 클러스터의 OSS Redis Cluster
(`redis-cluster.redis.svc.cluster.local:6379`)에 직접 연결한다. Redis Cluster는 3개 샤드로
구성되어 있다고 가정하며, 애플리케이션에는 Sentinel/standalone/password 설정이 없다.

Redis 연결은 `RedisClusterClientConfig`의 Java 설정을 사용한다. 안정적인 Kubernetes Service
DNS만 topology refresh source로 유지하고, 10초 주기 refresh, MOVED/ASK/재연결 기반 adaptive
refresh, stale connection 종료, 자동 재연결을 활성화한다.

Redis Pod 재기동 후에도 오류가 지속되면 애플리케이션이 아니라 Redis Cluster 자체가 이전
Pod IP를 광고하는지 먼저 확인한다.

```sh
kubectl -n redis exec -it <redis-pod> -- redis-cli -p 6379 CLUSTER NODES
kubectl -n redis exec -it <redis-pod> -- redis-cli -p 6379 CLUSTER SHARDS
```

출력에 종료된 Pod IP가 남아 있다면 client refresh만으로 해결할 수 없다. Redis는 StatefulSet과
headless Service의 안정적인 Pod DNS를 사용하고, Redis 7 이상에서는 각 노드에
`cluster-announce-hostname`과 `cluster-preferred-endpoint-type hostname`을 설정해 client에
Pod IP 대신 안정적인 hostname을 광고하는 구성을 권장한다. 모든 Redis Pod에서 광고된
hostname과 cluster bus port가 서로 접근 가능해야 한다.

## API

- `POST /api/redis/dataset`: 단일 Redis List 키에 4,600개 JSON element를 Lua의 `RPUSH`로 원자적으로 저장한다. 전체를 JSON 배열로 조립하면 정확히 1,400,000 UTF-8 byte이며 TTL은 없다. 이미 키가 있으면 `409`를 반환한다.
- `GET /api/redis/dataset`: `LRANGE key 0 -1`로 4,600개 element를 조회하고 JSON 배열로 반환한다. Logback에 소요 시간과 byte 수를 기록한다.
- `POST /api/redis/dataset/query`: `LRANGE`로 데이터를 읽은 뒤 `text/plain` 본문의 정확히 3,000,000 UTF-8 byte JSONPath Query를 실행한다. Query는 `$[?(@.id >= 0 || @.value == '<3MB dummy>')]` 형태의 완전한 조건식이며 4,600개 항목을 모두 조회한다.
- `GET /api/redis/health`: Kubernetes probe용 애플리케이션 상태 API다.

```sh
curl -X POST http://localhost:8080/api/redis/dataset
curl http://localhost:8080/api/redis/dataset
# 3,000,000 byte JSONPath 본문은 k6/redis-read.js에서 생성한다.
```

## 원격 배포

로컬 Docker에서 이미지를 build/save한 뒤 원격 Kubernetes 노드의 containerd에 import하고
그 호스트의 `kubectl`로 Deployment를 적용한다. 접속에는 OpenSSH의 `ssh`와 `scp`를 사용하며
Vagrant/OpenSSH private key를 직접 지정할 수 있다. 기본 import 명령은 kubelet이 사용하는
containerd namespace에 이미지를 넣는 다음 명령이다.

```text
sudo -n ctr --namespace k8s.io images import IMAGE_ARCHIVE
```

이미지 이름은 kubelet의 정규화 규칙과 맞도록 `docker.io/library/redis-cluster-app:<tag>`를
사용하며 Deployment의 `imagePullPolicy`는 `Never`다. 여러 worker node에 Pod가 스케줄될 수
있다면 모든 대상 노드에 같은 이미지를 import하거나 내부 registry를 사용해야 한다.

```sh
# Vagrant의 IdentityFile을 SSH_KEY에 그대로 지정한다.
REMOTE_HOST=user@host \
SSH_KEY='C:/workspace/.vagrant/machines/default/virtualbox/private_key' \
./scripts/deploy-remote.sh

# 세 worker node의 containerd에 동일한 이미지를 import한다.
REMOTE_HOST=vagrant@192.168.41.160 \
IMAGE_HOSTS='vagrant@192.168.41.160 vagrant@192.168.41.161 vagrant@192.168.41.162' \
SSH_KEY='C:/workspace/.vagrant/machines/default/virtualbox/private_key' \
./scripts/deploy-remote.sh

# ssh-agent에 키가 등록되어 있다면 SSH_KEY는 생략한다.
REMOTE_HOST=user@host ./scripts/deploy-remote.sh

# k3s 내장 containerd를 사용하는 경우
REMOTE_HOST=user@host \
SSH_KEY='C:/workspace/.vagrant/machines/default/virtualbox/private_key' \
REMOTE_LOAD_COMMAND='sudo -n k3s ctr images import' \
REMOTE_IMAGE_LIST_COMMAND='sudo -n k3s ctr images list' \
./scripts/deploy-remote.sh
```

스크립트는 원격 Kubernetes 환경을 다음 순서로 자동 감지한다.

- k3s: `sudo -n k3s kubectl`
- MicroK8s: `sudo -n microk8s kubectl`
- RKE2: `/etc/rancher/rke2/rke2.yaml`
- kubeadm: `/etc/kubernetes/admin.conf`
- 일반 사용자: `$HOME/.kube/config`

자동 감지할 수 없는 환경은 다음처럼 직접 지정한다.

```sh
# 일반 사용자 kubeconfig
REMOTE_KUBECTL='kubectl --kubeconfig=/home/deploy/.kube/config' \
REMOTE_HOST=user@host SSH_KEY='C:/path/to/private_key' ./scripts/deploy-remote.sh

# k3s
REMOTE_KUBECTL='sudo -n kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml' \
REMOTE_HOST=user@host SSH_KEY='C:/path/to/private_key' ./scripts/deploy-remote.sh
```

`sudo -n`은 암호 프롬프트 대신 즉시 실패하므로 배포 계정에 필요한 명령의 `NOPASSWD`
권한이 있어야 한다. 최초 접속의 host key는 `StrictHostKeyChecking=accept-new`로
`known_hosts`에 등록되며, 이후 서버 키가 변경되면 접속을 거부한다. `IMAGE_NAME`,
`IMAGE_TAG`, `NAMESPACE`, `REMOTE_DIR`, `SSH_PORT`, `SSH_BIN`, `SCP_BIN`도 환경변수로 변경할 수 있다.

`IMAGE_HOSTS`에는 이미지를 import할 SSH 대상 노드를 공백으로 구분해 지정한다. `REMOTE_HOST`는
kubectl을 실행할 대표 노드이며 `IMAGE_HOSTS`에도 포함해야 한다. 기본값
`PIN_TO_REMOTE_NODE=true`는 이미지를 import한 SSH 대상 노드의 hostname을 Kubernetes
node 이름으로 사용해 Pod를 해당 노드에 고정한다. Kubernetes node 이름이 hostname과 다르면
`K8S_NODE_NAME=<kubectl get nodes에 표시되는 이름>`을 지정한다. 모든 worker에 이미지를
import했거나 registry를 사용한다면 `PIN_TO_REMOTE_NODE=false`로 비활성화할 수 있다.
`IMAGE_HOSTS`가 2개 이상이면 스크립트가 단일 노드 고정을 자동으로 해제한다.

기본 이미지 태그는 `<git-commit>-<빌드시각>` 형식이다. 커밋하지 않은 코드 변경도 새로운
Deployment image 값으로 반영되어 Kubernetes가 반드시 새 Pod를 생성한다. 재현 가능한 고정
태그가 필요할 때만 `IMAGE_TAG`를 직접 지정한다.

## k6

먼저 데이터 생성 API를 한 번 호출한 뒤 애플리케이션 Service에 접근 가능한 위치에서 실행한다.
기본값은 GET 1,500 TPS + 3,000,000 byte JSONPath Query POST 1,500 TPS로 총 3,000 TPS, 1분이다.

```sh
kubectl -n redis-performance port-forward service/redis-cluster-app 8080:8080
k6 run -e BASE_URL=http://localhost:8080 -e TPS=3000 -e DURATION=1m k6/redis-read.js
```

1.4MB 응답을 3,000 TPS로 받으면 애플리케이션 응답 기준 약 4.2GB/s(약 33.6Gbps)가
필요하다. 또한 단일 Redis 키는 Redis Cluster의 한 hash slot/한 primary shard에만
배치되므로, 목표 TPS는 네트워크·단일 shard·JSON 파싱 CPU 한계를 고려해 단계적으로
올려 측정해야 한다. JSONPath 시나리오는 여기에 3MB 요청 본문을 1,500 TPS로 전송하므로
추가로 약 4.5GB/s의 업로드 대역폭이 필요하다.
