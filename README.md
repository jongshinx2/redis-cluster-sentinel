# Redis Cluster performance application

Spring Boot 애플리케이션이 같은 Kubernetes 클러스터의 OSS Redis Cluster
(`redis-cluster.redis.svc.cluster.local:6379`)에 직접 연결한다. Redis Cluster는 3개 샤드로
구성되어 있다고 가정하며, 애플리케이션에는 Sentinel/standalone/password 설정이 없다.

## API

- `POST /api/redis/dataset`: 단일 키에 4,600개 객체로 구성된 정확히 1,400,000 UTF-8 byte JSON 배열을 `SETNX`로 저장한다. TTL은 설정하지 않는다. 이미 키가 있으면 `409`를 반환한다.
- `GET /api/redis/dataset`: 전체 값을 조회하고 Logback에 소요 시간과 byte 수를 기록한다.
- `POST /api/redis/dataset/query`: `text/plain` 본문으로 정확히 1,400,000 UTF-8 byte인 JSONPath Query를 받아 Redis 데이터에 실행한다. Query는 전체 4,600개 항목을 조회한다.
- `GET /api/redis/health`: Kubernetes probe용 애플리케이션 상태 API다.

```sh
curl -X POST http://localhost:8080/api/redis/dataset
curl http://localhost:8080/api/redis/dataset
# 1,400,000 byte JSONPath 본문은 k6/redis-read.js에서 생성한다.
```

## 원격 배포

원격 호스트의 Docker daemon에 이미지를 load한 뒤 그 호스트의 `kubectl`로 Deployment를
적용한다. 원격 Docker daemon의 이미지가 Kubernetes 노드에서 보이는 환경(예: Docker
runtime 기반 단일 노드)에 적합하다. containerd 클러스터는 환경에 맞는 import 명령을
`REMOTE_LOAD_COMMAND`에 지정하고 모든 스케줄 대상 노드에 이미지를 넣거나 registry를
사용해야 한다.

```sh
REMOTE_HOST=user@host ./scripts/deploy-remote.sh

# containerd 예시(환경별 namespace/path 확인 필요)
REMOTE_HOST=user@host \
REMOTE_LOAD_COMMAND='sudo ctr -n k8s.io images import' \
./scripts/deploy-remote.sh
```

`IMAGE_NAME`, `IMAGE_TAG`, `NAMESPACE`, `REMOTE_DIR`도 환경변수로 변경할 수 있다.

## k6

먼저 데이터 생성 API를 한 번 호출한 뒤 애플리케이션 Service에 접근 가능한 위치에서 실행한다.
기본값은 GET 1,500 TPS + 1,400,000 byte JSONPath Query POST 1,500 TPS로 총 3,000 TPS, 1분이다.

```sh
kubectl -n redis-performance port-forward service/redis-cluster-app 8080:8080
k6 run -e BASE_URL=http://localhost:8080 -e TPS=3000 -e DURATION=1m k6/redis-read.js
```

1.4MB 응답을 3,000 TPS로 받으면 애플리케이션 응답 기준 약 4.2GB/s(약 33.6Gbps)가
필요하다. 또한 단일 Redis 키는 Redis Cluster의 한 hash slot/한 primary shard에만
배치되므로, 목표 TPS는 네트워크·단일 shard·JSON 파싱 CPU 한계를 고려해 단계적으로
올려 측정해야 한다.
