package com.telcoware.job_worker_app_demo.redis;

import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * packageName    : com.telcoware.job_worker_app_demo.redis
 * fileName       : RedisKeyValueClient
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : Redis KeyValue Client
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */

/**
 * 키-값 중심의 간단한 Redis 접근 유틸리티.
 * - 싱글/클러스터 모두 지원 (RedisConnectionFactory 로 자동 분기)
 * - 명령 실행 전 커넥션 오픈 보장(게이트) + 필요 시 재오픈
 * - 재시도/백오프/예열(PING) 로직 포함으로 "Connection is already closed" 예방
 */
@Slf4j
public class RedisKeyValueClient implements AutoCloseable {

    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 명령 실패 시 재시도 최대 횟수 (0 이면 재시도 없음)
     */
    private final int maxRetryCount;

    /**
     * 재시도 사이의 대기 시간(밀리초). 필요 시 외부에서 지수 백오프 적용 가능
     */
    private final long backoffMillisOnRetry;

    /**
     * 싱글 환경에서 사용하는 연결
     */
    private StatefulRedisConnection<String, String> singleNodeConnection;

    /**
     * 클러스터 환경에서 사용하는 연결
     */
    private StatefulRedisClusterConnection<String, String> clusterConnection;

    /**
     * 기본 생성자. 재시도 2회, 백오프 300ms.
     */
    public RedisKeyValueClient(List<RedisURI> redisUriList) {
        this(redisUriList, 2, 300L);
    }

    /**
     * 재시도/백오프를 직접 지정할 수 있는 생성자.
     */
    public RedisKeyValueClient(List<RedisURI> redisUriList, int maxRetryCount, long backoffMillisOnRetry) {
        Objects.requireNonNull(redisUriList, "redisUriList must not be null");
        this.redisConnectionFactory = RedisConnectionFactory.of(redisUriList);
        this.maxRetryCount = Math.max(0, maxRetryCount);
        this.backoffMillisOnRetry = Math.max(0, backoffMillisOnRetry);
        openConnectionsInitially();
    }

    private static void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 최초 연결 열기(싱글/클러스터 자동 분기) 및 PING 예열.
     */
    private void openConnectionsInitially() {
        if (redisConnectionFactory.isCluster()) {
            this.clusterConnection = redisConnectionFactory.cluster().connect();
            this.clusterConnection.sync().ping();
            log.debug("Opened cluster connection and performed PING");
        } else {
            this.singleNodeConnection = redisConnectionFactory.single().connect();
            this.singleNodeConnection.sync().ping();
            log.debug("Opened single-node connection and performed PING");
        }
    }

    /**
     * 내부 연결만 닫고 팩토리는 유지.
     */
    private void closeConnectionsOnly() {
        try {
            if (singleNodeConnection != null && singleNodeConnection.isOpen()) {
                singleNodeConnection.close();
            }
        } catch (Exception ignore) {
        }
        try {
            if (clusterConnection != null && clusterConnection.isOpen()) {
                clusterConnection.close();
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * 실패 후 백오프 대기 + 연결 재오픈.
     */
    private void reopenConnectionsAfterFailure(Exception cause) {
        log.warn("Reopening connections after failure: {}", cause.toString());
        closeConnectionsOnly();
        sleepQuietly(backoffMillisOnRetry);
        openConnectionsInitially();
    }

    /**
     * 싱글 노드 커넥션 게이트: 닫혀 있으면 재오픈 후 동기 커맨드를 반환.
     */
    private RedisCommands<String, String> ensureSingleNodeCommandsOpen() {
        if (singleNodeConnection == null || !singleNodeConnection.isOpen()) {
            log.debug("Single-node connection is not open. Reopening...");
            reopenConnectionsAfterFailure(new IllegalStateException("single connection closed"));
        }
        return singleNodeConnection.sync();
    }

    /**
     * 클러스터 커넥션 게이트: 닫혀 있으면 재오픈 후 동기 커맨드를 반환.
     */
    private RedisAdvancedClusterCommands<String, String> ensureClusterCommandsOpen() {
        if (clusterConnection == null || !clusterConnection.isOpen()) {
            log.debug("Cluster connection is not open. Reopening...");
            reopenConnectionsAfterFailure(new IllegalStateException("cluster connection closed"));
        }
        return clusterConnection.sync();
    }

    // ============================================================
    // 공개 API
    // ============================================================

    /**
     * 키에 해당하는 값을 조회한다.
     *
     * @param key 조회할 Redis 키
     * @return 존재하면 문자열 값, 없거나 실패 시 null
     */
    public String getValue(String key) {
        Objects.requireNonNull(key, "key must not be null");
        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            try {
                if (redisConnectionFactory.isCluster()) {
                    RedisAdvancedClusterCommands<String, String> commands = ensureClusterCommandsOpen();
                    return commands.get(key);
                } else {
                    RedisCommands<String, String> commands = ensureSingleNodeCommandsOpen();
                    return commands.get(key);
                }
            } catch (RedisException redisException) {
                log.warn("->SVC::Fail to get value by lettuce, key={}, attempt={}/{}", key, attempt + 1, maxRetryCount + 1, redisException);
                if (attempt == maxRetryCount) return null;
                reopenConnectionsAfterFailure(redisException);
            } catch (Exception unexpected) {
                log.warn("->SVC::Unexpected fail to get value, key={}", key, unexpected);
                return null;
            }
        }
        return null;
    }

    /**
     * 값 저장 (성공 시 true)
     */
    public boolean setValue(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            try {
                if (redisConnectionFactory.isCluster()) {
                    RedisAdvancedClusterCommands<String, String> commands = ensureClusterCommandsOpen();
                    String result = commands.set(key, value);
                    return "OK".equalsIgnoreCase(result);
                } else {
                    RedisCommands<String, String> commands = ensureSingleNodeCommandsOpen();
                    String result = commands.set(key, value);
                    return "OK".equalsIgnoreCase(result);
                }
            } catch (RedisException redisException) {
                log.warn("->SVC::Fail to set value by lettuce, key={}, attempt={}/{}", key, attempt + 1, maxRetryCount + 1, redisException);
                if (attempt == maxRetryCount) return false;
                reopenConnectionsAfterFailure(redisException);
            } catch (Exception unexpected) {
                log.warn("->SVC::Unexpected fail to set value, key={}", key, unexpected);
                return false;
            }
        }
        return false;
    }

    /**
     * TTL 포함 저장 (예: 60초)
     */
    public boolean setValue(String key, String value, Duration timeToLive) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(timeToLive, "timeToLive must not be null");
        long seconds = Math.max(0, timeToLive.toSeconds()); // 음수 방지
        SetArgs setArguments = (seconds > 0)
                ? SetArgs.Builder.ex(seconds)            // TTL 초 단위
                : new SetArgs();                         // TTL 미지정 시 기본 SET

        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            try {
                if (redisConnectionFactory.isCluster()) {
                    RedisAdvancedClusterCommands<String, String> commands = ensureClusterCommandsOpen();
                    String result = commands.set(key, value, setArguments);
                    return "OK".equalsIgnoreCase(result);
                } else {
                    RedisCommands<String, String> commands = ensureSingleNodeCommandsOpen();
                    String result = commands.set(key, value, setArguments);
                    return "OK".equalsIgnoreCase(result);
                }
            } catch (RedisException redisException) {
                log.warn("->SVC::Fail to set value with TTL by lettuce, key={}, attempt={}/{}", key, attempt + 1, maxRetryCount + 1, redisException);
                if (attempt == maxRetryCount) return false;
                reopenConnectionsAfterFailure(redisException);
            } catch (Exception unexpected) {
                log.warn("->SVC::Unexpected fail to set value with TTL, key={}", key, unexpected);
                return false;
            }
        }
        return false;
    }

    /**
     * 키 삭제 (삭제된 개수 > 0 이면 true)
     */
    public boolean removeValue(String key) {
        Objects.requireNonNull(key, "key must not be null");
        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            try {
                if (redisConnectionFactory.isCluster()) {
                    RedisAdvancedClusterCommands<String, String> commands = ensureClusterCommandsOpen();
                    Long deletedCount = commands.del(key);
                    return deletedCount != null && deletedCount > 0;
                } else {
                    RedisCommands<String, String> commands = ensureSingleNodeCommandsOpen();
                    Long deletedCount = commands.del(key);
                    return deletedCount != null && deletedCount > 0;
                }
            } catch (RedisException redisException) {
                log.warn("->SVC::Fail to delete value by lettuce, key={}, attempt={}/{}", key, attempt + 1, maxRetryCount + 1, redisException);
                if (attempt == maxRetryCount) return false;
                reopenConnectionsAfterFailure(redisException);
            } catch (Exception unexpected) {
                log.warn("->SVC::Unexpected fail to delete value, key={}", key, unexpected);
                return false;
            }
        }
        return false;
    }

    /**
     * 존재 여부
     */
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key must not be null");
        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            try {
                if (redisConnectionFactory.isCluster()) {
                    RedisAdvancedClusterCommands<String, String> commands = ensureClusterCommandsOpen();
                    return commands.exists(key) > 0;
                } else {
                    RedisCommands<String, String> commands = ensureSingleNodeCommandsOpen();
                    return commands.exists(key) > 0;
                }
            } catch (RedisException redisException) {
                log.warn("->SVC::Fail to check exists by lettuce, key={}, attempt={}/{}", key, attempt + 1, maxRetryCount + 1, redisException);
                if (attempt == maxRetryCount) return false;
                reopenConnectionsAfterFailure(redisException);
            } catch (Exception unexpected) {
                log.warn("->SVC::Unexpected fail to check exists, key={}", key, unexpected);
                return false;
            }
        }
        return false;
    }

    @Override
    public void close() {
        closeConnectionsOnly();
        redisConnectionFactory.close();
    }

}