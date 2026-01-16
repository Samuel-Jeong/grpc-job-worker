# job-worker-app-demo

CAPSHOME Job Worker 애플리케이션 데모 프로젝트입니다. 단독으로 동작하는 gRPC 기반 워커 프로세스로, 자체 스케줄러를 통해 주기 작업을 실행하고, AWS ElastiCache(Valkey/Redis 호환)를 사용해 워커 상태를 공유/보고합니다.

## 주요 기능
- gRPC 서버 제공: `worker.WorkerService/SendWork` RPC 수신
- 자체 스케줄러 내장: 스레드 풀 기반 작업 실행 및 주기 작업 관리
- HA/상태 보고: Redis(ElastiCache)에 파드/프로세스 상태 저장(`wpm-apps:<ip>:<pid>`) 및 TTL 관리
- 동적 gRPC 포트 할당: 마지막 사용 포트 기준 가용 포트 선택(기본 9091)
- 워터마크 기반 수용량(Active Worker) 판단

## 아키텍처 개요
- Spring Boot 애플리케이션(Web 서버 비활성, gRPC 전용)
- gRPC 서버(Netty) 직접 구동: `GrpcServerService`
- 작업 실행기: `JobMaster` + `ThreadPoolTaskExecutor`
- 스케줄러: `ScheduleManager` + `Job/JobBuilder`
- 주기 작업 예시: `ApplicationInfoReportJob`(5초 간격) → 현재 워커 정보(포트/상태/시간) Redis 등록
- 초기화: `InitService`, `HaHandler`가 컨텍스트 시작 시 gRPC 서버 및 스케줄러 초기화

## 기술 스택
- Java 17, Spring Boot 3.5.x
- gRPC, protobuf
- Lettuce(Valkey/Redis 클라이언트)
- Lombok, MapStruct, Guava

## 디렉터리 구조(요약)
```
/ (project root)
├─ build.gradle, settings.gradle
├─ src/main/java/com/dovaj/job_worker_app_demo
│  ├─ JobWorkerAppDemoApplication.java
│  ├─ config
│  │  ├─ AwsElasticacheConfig.java
│  │  ├─ JobConfig.java                # 워커 스레드풀/워터마크 설정
│  │  └─ ScheduleConfig.java           # 스케줄러 스레드풀 설정
│  ├─ controller/ha/HAController.java  # (향후 HA 제어용)
│  ├─ data/definition, dto             # PodInfo, POD_STATUS_TYPE 등
│  ├─ job/handler                      # JobMaster, JobWorker
│  ├─ redis                            # Lettuce 클라이언트/스캐너
│  ├─ scheduler                        # ScheduleManager, Job, JobBuilder ...
│  ├─ service
│  │  ├─ aws/elasticache/AwsValKeyService.java
│  │  ├─ grpc/GrpcServerService.java   # gRPC 서버 빌드/기동/중지
│  │  ├─ grpc/WorkerGrpcServiceImpl.java
│  │  ├─ init/InitService.java         # 초기 gRPC 포트/PodInfo 등록
│  │  └─ job/HaHandler.java            # 스케줄러 init 및 Job 등록
│  └─ util                             # Network/Process/Pod/Time/Gson 유틸
├─ src/main/proto/worker.proto         # gRPC 인터페이스 정의
├─ src/main/resources
│  ├─ application.yml                  # 공통 설정(HTTP 미사용, 프로파일)
│  ├─ application-local.yml            # 로컬 기본값(스레드풀, Redis 엔드포인트)
│  ├─ bin/run.sh                       # 운영 배포 실행 스크립트
│  └─ logback-spring.xml
└─ HELP.md
```

## 구성/환경 변수
- Spring 프로파일
  - `application.yml`: `spring.profiles.active: ${PROFILE}`
  - run.sh 사용 시: 환경변수 `PROFILES_ACTIVE`로 제어(기본 `local`)
- Redis/Valkey(ElastiCache) 엔드포인트
  - `aws.elasticache.valkey.endpoint.host`
  - `aws.elasticache.valkey.endpoint.port`
  - `AwsElasticacheConfig#getEndpoint()`는 `rediss://<host>:<port>` URI 생성(TLS 엔드포인트가 권장)
- 워커 스레드풀
  - `job.worker.thread-pool.core-size`, `max-size`, `queue-capacity`, `watermark`
- 스케줄러 스레드풀
  - `schedule.application-info-report.thread.pool-size`, `queue-size`

로컬 기본값은 `src/main/resources/application-local.yml`을 참고하세요.

## 빌드
- 사전 요구: JDK 17, Gradle Wrapper 포함
```
./gradlew clean build
```
빌드 산출물: `build/libs/job-worker-app-demo-0.0.1-SNAPSHOT.jar`

## 실행 방법
### 1) Gradle/Java 로컬 실행
- 프로파일 지정(예: local)
```
PROFILE=local java -jar build/libs/job-worker-app-demo-0.0.1-SNAPSHOT.jar
# 또는
java -Dspring.profiles.active=local -jar build/libs/job-worker-app-demo-0.0.1-SNAPSHOT.jar
```
- 부팅 시 동작
  - `InitService`: 마지막 gRPC 포트 후보(기본 9091) 기준 사용 가능한 포트 선택 및 gRPC 서버 기동
  - `HaHandler`: 스케줄러 초기화 후 `ApplicationInfoReportJob`을 5초 간격으로 등록/실행

### 2) 운영 배포 스크립트 실행
`src/main/resources/bin/run.sh` 복사 후 환경변수로 경로/프로파일을 지정합니다.
- 필수 경로 구조 예시
```
$SERVICE_HOME/
 ├─ libs/job-worker-app-demo-0.0.1-SNAPSHOT.jar
 ├─ run/          # PID 파일 저장
 └─ config/       # 외부 설정(yml) 위치
```
- 환경 변수 예시
```
export SERVICE_HOME=/home/ec2-user/capshome/job-system/job-worker-app
export PROFILES_ACTIVE=live   # 또는 dev/stg/local
```
- 실행/중지/상태
```
$SERVICE_HOME/run.sh start
$SERVICE_HOME/run.sh status
$SERVICE_HOME/run.sh stop
```

## gRPC 인터페이스
- proto: `src/main/proto/worker.proto`
```
service WorkerService {
  rpc SendWork(SendWorkReq) returns (SendWorkRes);
}

message SendWorkReq { string id = 1; string name = 2; }
message SendWorkRes { string message = 1; }
```
- 서버 구현: `WorkerGrpcServiceImpl#sendWork`
- 로컬 테스트(grpcurl)
```
# 서버 포트는 로그 또는 Redis의 PodInfo 참조(기본 9091부터 가용 포트 사용)
grpcurl -plaintext localhost:9091 list worker.WorkerService
grpcurl -plaintext -d '{"id":"job-1","name":"demo"}' localhost:9091 worker.WorkerService/SendWork
```

## Redis/ElastiCache 상태 보고
- 키 규칙: `wpm-apps:<ip>:<pid>`
- 값: `PodInfo` JSON(`grpcPort`, `status`, `updateDatetime`)
- TTL: 60초
- 등록 시점
  - 애플리케이션 부팅 시(`InitService`)
  - 이후 5초마다 `ApplicationInfoReportJob`이 갱신
- 상태 판단
  - `JobMaster#isActive()`: 워터마크(%) 이하일 때 `IDLE`, 초과 시 `BUSY`

## 로그/포트
- HTTP 서버 비활성: `spring.main.web-application-type: NONE`
- gRPC 서버 바인딩: `0.0.0.0:<port>`
- 포트 선택: 기본 9091, 점유 시 인접 포트 탐색(최대 50 포트), 모두 실패 시 ephemeral(0)
- 로그 설정: `logback-spring.xml` 및 일부 access log 설정(application.yml) 포함

## 개발 팁
- Proto 재생성은 Gradle 플러그인이 자동 수행(build 시). 필요 시:
```
./gradlew generateProto
```
- 의존성 주요 버전은 `build.gradle`의 `ext` 섹션 참조(`grpcVersion`, `protobufVersion`, `grpcSpringStarter`).

## 트러블슈팅
- gRPC 접속 불가
  - 실제 리슨 포트를 로그에서 확인: `->SVC::gRPC server started on port ...`
  - 방화벽/보안그룹/인바운드 규칙 확인
- Redis 연결 실패
  - ElastiCache 엔드포인트(host/port) 및 TLS(rediss) 사용 여부 확인
  - 네트워크 접근권한 및 보안그룹 확인
- 포트 충돌
  - 9091 사용 중이면 자동으로 인접 포트로 대체. 운영 모니터링에 해당 포트 반영 필요
- 워커가 BUSY 상태로 유지됨
  - `job.worker.thread-pool.*` 및 `watermark` 조정

## 라이선스
본 저장소는 데모 목적의 예제 코드이며, 사내 표준 및 배포 환경에 맞춰 적절히 수정하여 사용하세요.
