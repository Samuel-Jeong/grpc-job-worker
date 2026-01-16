package com.dovaj.job_worker_app_demo.redis;

import com.dovaj.job_worker_app_demo.data.dto.redis.KvPair;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.models.partitions.Partitions;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * packageName    : com.dovaj.job_worker_app_demo.redis
 * fileName       : RedisKeyValueScanner
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : Redis KeyValue 스캐너 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 * 25. 10. 22.        chatgpt      클러스터 CROSSSLOT 방지를 위한 슬롯별 MGET 적용
 */

/**
 * Redis(싱글/클러스터)에서 주어진 패턴으로 키를 SCAN하여 수집하고,
 * 값을 조회해 (키, 값) 목록을 반환하는 유틸리티.
 *
 * 특징:
 * - 커서 기반 SCAN 반복 (MATCH + COUNT 힌트)
 * - 최대 결과 개수 limit로 조기 종료
 * - 싱글 노드: 배치 MGET으로 RTT 감소
 * - 클러스터: 슬롯 단위 그룹핑 후 MGET으로 CROSSSLOT 방지
 * - 재시도 + 백오프 + 연결 재오픈으로 안정성 확보
 * - 클러스터: 마스터 노드들만 순회
 *
 * 주의:
 * - SCAN은 정합성 보장 탐색이 아니며, 운영 중 키 추가/삭제가 있을 수 있다.
 * - 매우 큰 데이터셋에서는 limit/배치 크기 조정이 필수.
 */
public class RedisKeyValueScanner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisKeyValueScanner.class);

    /**
     * SCAN COUNT 힌트 기본값
     */
    private static final int DEFAULT_SCAN_COUNT = 500;

    /**
     * 싱글 노드에서 사용할 MGET 배치 크기 기본값
     */
    private static final int DEFAULT_BATCH_MGET = 500;

    private final RedisConnectionFactory redisConnectionFactory;

    private StatefulRedisConnection<String, String> singleNodeConnection;
    private StatefulRedisClusterConnection<String, String> clusterConnection;

    private final int scanCountHint;
    private final int mgetBatchSize;
    private final int maxRetryCount;
    private final long backoffMillisOnRetry;

    /**
     * 빌더 진입점
     */
    public static Builder builder(List<RedisURI> redisUriList) {
        return new Builder(redisUriList);
    }

    /**
     * 빌더
     */
    public static final class Builder {
        private final List<RedisURI> redisUriList;
        private int scanCountHint = DEFAULT_SCAN_COUNT;
        private int mgetBatchSize = DEFAULT_BATCH_MGET;
        private int maxRetryCount = 2;
        private long backoffMillisOnRetry = 300L;

        private Builder(List<RedisURI> redisUriList) {
            this.redisUriList = Objects.requireNonNull(redisUriList, "redisUriList must not be null");
        }

        public Builder scanCount(int scanCountHint) {
            if (scanCountHint <= 0) {
                throw new IllegalArgumentException("scanCountHint must be > 0");
            }
            this.scanCountHint = scanCountHint;
            return this;
        }

        public Builder mgetBatch(int mgetBatchSize) {
            if (mgetBatchSize <= 0) {
                throw new IllegalArgumentException("mgetBatchSize must be > 0");
            }
            this.mgetBatchSize = mgetBatchSize;
            return this;
        }

        public Builder maxRetries(int maxRetryCount) {
            if (maxRetryCount < 0) {
                throw new IllegalArgumentException("maxRetryCount must be >= 0");
            }
            this.maxRetryCount = maxRetryCount;
            return this;
        }

        public Builder backoffMillis(long backoffMillisOnRetry) {
            if (backoffMillisOnRetry < 0) {
                throw new IllegalArgumentException("backoffMillisOnRetry must be >= 0");
            }
            this.backoffMillisOnRetry = backoffMillisOnRetry;
            return this;
        }

        public RedisKeyValueScanner build() {
            return new RedisKeyValueScanner(
                    redisUriList,
                    scanCountHint,
                    mgetBatchSize,
                    maxRetryCount,
                    backoffMillisOnRetry
            );
        }
    }

    private RedisKeyValueScanner(List<RedisURI> redisUriList,
                                 int scanCountHint,
                                 int mgetBatchSize,
                                 int maxRetryCount,
                                 long backoffMillisOnRetry) {
        this.redisConnectionFactory = RedisConnectionFactory.of(redisUriList);
        this.scanCountHint = scanCountHint;
        this.mgetBatchSize = mgetBatchSize;
        this.maxRetryCount = maxRetryCount;
        this.backoffMillisOnRetry = backoffMillisOnRetry;

        openConnectionsInitially();
    }

    /**
     * 최초 연결 열기(싱글/클러스터 자동 분기).
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
     * 실패 시 백오프 후 연결을 재오픈.
     */
    private void reopenConnectionsAfterFailure(Exception cause) {
        log.warn("Reopening connections after failure: {}", cause.toString());
        closeConnectionsOnly();
        sleepQuietly(backoffMillisOnRetry);
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
     * 패턴과 일치하는 키·값을 최대 limit 개까지 조회.
     */
    public List<KvPair> findByPattern(String keyPattern, int maximumResultSize) {
        Objects.requireNonNull(keyPattern, "keyPattern must not be null");

        final String trimmedPattern = keyPattern.trim();
        if (trimmedPattern.isEmpty()) {
            throw new IllegalArgumentException("keyPattern must not be blank");
        }

        final int normalizedLimit = (maximumResultSize <= 0) ? Integer.MAX_VALUE : maximumResultSize;

        for (int attempt = 0; attempt <= maxRetryCount; attempt++) {
            try {
                if (redisConnectionFactory.isCluster()) {
                    return findByPatternInCluster(trimmedPattern, normalizedLimit);
                } else {
                    return findByPatternInSingleNode(trimmedPattern, normalizedLimit);
                }
            } catch (RedisException redisException) {
                log.warn("Redis operation failed on attempt {}/{}: {}",
                        attempt + 1, maxRetryCount + 1, redisException.toString());

                if (attempt == maxRetryCount) {
                    throw redisException;
                }
                reopenConnectionsAfterFailure(redisException);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted during scanning", interrupted);
            } catch (Exception unexpected) {
                log.error("Unexpected exception during scanning", unexpected);
                throw unexpected;
            }
        }
        return List.of();
    }

    /**
     * 싱글/레플리카셋 환경에서의 키·값 조회.
     */
    private List<KvPair> findByPatternInSingleNode(String keyPattern, int maximumResultSize) throws InterruptedException {
        RedisCommands<String, String> redisCommands = singleNodeConnection.sync();
        List<String> collectedKeys = scanKeysWithCursor(redisCommands, keyPattern, scanCountHint, maximumResultSize);
        return executeBatchMGetSingleNode(redisCommands, collectedKeys);
    }

    /**
     * 클러스터 환경에서의 키·값 조회.
     * 마스터 노드들만 순회하며, 노드별 예외는 로깅 후 다른 노드 진행(부분 성공 허용).
     * SCAN으로 수집 후, 슬롯별로 그룹핑하여 MGET(CROSSSLOT 방지).
     */
    private List<KvPair> findByPatternInCluster(String keyPattern, int maximumResultSize) throws InterruptedException {
        RedisAdvancedClusterCommands<String, String> clusterCommands = clusterConnection.sync();
        Partitions partitionsSnapshot = clusterConnection.getPartitions();

        List<String> collectedKeys = new ArrayList<>(Math.min(maximumResultSize, scanCountHint * Math.max(1, partitionsSnapshot.size())));
        ScanArgs scanArguments = ScanArgs.Builder.matches(keyPattern).limit(scanCountHint);

        for (RedisClusterNode partitionNode : partitionsSnapshot) {
            if (collectedKeys.size() >= maximumResultSize) {
                break;
            }
            if (!partitionNode.getRole().isMaster()) {
                continue;
            }

            try (StatefulRedisConnection<String, String> nodeConnection = clusterConnection.getConnection(partitionNode.getNodeId())) {
                RedisCommands<String, String> nodeCommands = nodeConnection.sync();

                KeyScanCursor<String> cursor = nodeCommands.scan(scanArguments);
                addKeysUpToLimit(collectedKeys, cursor.getKeys(), maximumResultSize);

                while (!cursor.isFinished() && collectedKeys.size() < maximumResultSize) {
                    cursor = nodeCommands.scan(cursor, scanArguments);
                    addKeysUpToLimit(collectedKeys, cursor.getKeys(), maximumResultSize);
                }
            } catch (RedisException redisException) {
                log.warn("SCAN failed on node {} ({}). Continuing other nodes. Cause: {}",
                        partitionNode.getNodeId(), partitionNode.getUri(), redisException.toString());
            }
        }

        // 슬롯 단위로 그룹핑하여 MGET 실행(CROSSSLOT 방지)
        return executeClusterMGetBySlot(clusterCommands, collectedKeys);
    }

    /**
     * Cursor 기반 SCAN으로 패턴 키를 수집한다(싱글/노드 단위 공용).
     */
    private static List<String> scanKeysWithCursor(RedisCommands<String, String> redisCommands,
                                                   String keyPattern,
                                                   int scanCountHint,
                                                   int maximumResultSize) throws InterruptedException {
        List<String> accumulatedKeys = new ArrayList<>(Math.min(maximumResultSize, scanCountHint));
        ScanArgs scanArguments = ScanArgs.Builder.matches(keyPattern).limit(scanCountHint);

        KeyScanCursor<String> cursor = redisCommands.scan(scanArguments);
        addKeysUpToLimit(accumulatedKeys, cursor.getKeys(), maximumResultSize);

        while (!cursor.isFinished() && accumulatedKeys.size() < maximumResultSize) {
            cursor = redisCommands.scan(cursor, scanArguments);
            addKeysUpToLimit(accumulatedKeys, cursor.getKeys(), maximumResultSize);

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while scanning keys");
            }
        }
        return accumulatedKeys;
    }

    /**
     * 누적 리스트에 limit까지 키를 추가.
     */
    private static void addKeysUpToLimit(List<String> accumulatedKeys, List<String> incomingKeys, int maximumResultSize) {
        if (incomingKeys == null || incomingKeys.isEmpty()) return;
        for (String key : incomingKeys) {
            accumulatedKeys.add(key);
            if (accumulatedKeys.size() >= maximumResultSize) break;
        }
    }

    /**
     * 싱글 환경: 파이프라인 GET 실행.
     */
    private List<KvPair> executeBatchMGetSingleNode(RedisCommands<String, String> redisCommands, List<String> targetKeys) throws InterruptedException {
        if (targetKeys == null || targetKeys.isEmpty()) return List.of();

        // 커넥션 & async 핸들
        StatefulRedisConnection<String, String> conn = this.singleNodeConnection;
        io.lettuce.core.api.async.RedisAsyncCommands<String, String> async = conn.async();

        List<KvPair> out = new ArrayList<>(targetKeys.size());
        List<io.lettuce.core.RedisFuture<String>> futures = new ArrayList<>(targetKeys.size());

        try {
            // 파이프라인 시작 (커넥션 단위)
            conn.setAutoFlushCommands(false);

            // 큐잉: 단건 GET을 잔뜩 쌓는다 (CROSSSLOT 없음)
            for (String k : targetKeys) {
                futures.add(async.get(k));
            }

            // 전송
            conn.flushCommands();

            // 수집
            for (int i = 0; i < targetKeys.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted while executing pipelined GET");
                }
                try {
                    String v = futures.get(i).get(); // 필요시 get(timeout, unit) 사용
                    if (v != null) out.add(new KvPair(targetKeys.get(i), v));
                } catch (Exception e) {
                    log.warn("GET failed for key {}: {}", targetKeys.get(i), e.toString());
                }
            }
        } finally {
            // 반드시 복구
            conn.setAutoFlushCommands(true);
        }
        return out;
    }

    /**
     * 클러스터 환경: 슬롯 단위로 그룹핑하여 MGET 실행(CROSSSLOT 방지).
     */
    private List<KvPair> executeClusterMGetBySlot(RedisAdvancedClusterCommands<String, String> clusterCommands,
                                                  List<String> targetKeys) throws InterruptedException {
        if (targetKeys == null || targetKeys.isEmpty()) return List.of();

        // 1) 슬롯 단위 그룹핑
        Map<Integer, List<String>> slotToKeys = new LinkedHashMap<>();
        for (String key : targetKeys) {
            int slot = SlotHash.getSlot(key);
            slotToKeys.computeIfAbsent(slot, s -> new ArrayList<>()).add(key);
        }

        // 2) 같은 슬롯끼리만 MGET
        List<KvPair> resultPairs = new ArrayList<>(targetKeys.size());
        for (Map.Entry<Integer, List<String>> entry : slotToKeys.entrySet()) {
            List<String> keysInSlot = entry.getValue();
            if (keysInSlot.isEmpty()) continue;

            // 배치 크기가 큰 경우, 슬롯 그룹 내에서도 분할 MGET 수행
            for (int start = 0; start < keysInSlot.size(); start += mgetBatchSize) {
                int end = Math.min(start + mgetBatchSize, keysInSlot.size());
                List<String> batch = keysInSlot.subList(start, end);

                List<KeyValue<String, String>> resp = clusterCommands.mget(batch.toArray(new String[0]));
                for (int i = 0; i < batch.size(); i++) {
                    KeyValue<String, String> kv = resp.get(i);
                    if (kv != null && kv.hasValue()) {
                        resultPairs.add(new KvPair(batch.get(i), kv.getValue()));
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted while executing clustered MGET");
                }
            }
        }
        return resultPairs;
    }

    /**
     * 공용 배치 MGET 구현(싱글 노드 전용 경로에서 사용).
     */
    private List<KvPair> doBatchMGet(List<String> targetKeys, MGetInvoker invoker) throws InterruptedException {
        if (targetKeys == null || targetKeys.isEmpty()) return List.of();

        List<KvPair> resultPairs = new ArrayList<>(targetKeys.size());

        for (int startIndex = 0; startIndex < targetKeys.size(); startIndex += mgetBatchSize) {
            int endExclusive = Math.min(startIndex + mgetBatchSize, targetKeys.size());
            List<String> batchKeys = targetKeys.subList(startIndex, endExclusive);

            List<KeyValue<String, String>> mgetResult = invoker.invoke(batchKeys);

            for (int i = 0; i < batchKeys.size(); i++) {
                KeyValue<String, String> redisKeyValue = mgetResult.get(i);
                if (redisKeyValue != null && redisKeyValue.hasValue()) {
                    resultPairs.add(new KvPair(batchKeys.get(i), redisKeyValue.getValue()));
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while executing batch MGET");
            }
        }
        return resultPairs;
    }

    @FunctionalInterface
    private interface MGetInvoker {
        List<KeyValue<String, String>> invoke(List<String> batch);
    }

    /**
     * 내부 커넥션만 닫는다(팩토리는 유지).
     */
    private void closeConnectionsOnly() {
        try {
            if (singleNodeConnection != null && singleNodeConnection.isOpen()) {
                singleNodeConnection.close();
            }
        } catch (Exception ignore) { }

        try {
            if (clusterConnection != null && clusterConnection.isOpen()) {
                clusterConnection.close();
            }
        } catch (Exception ignore) { }
    }

    /**
     * 외부에서 close 호출 시 모든 리소스를 정리한다.
     */
    @Override
    public void close() {
        closeConnectionsOnly();
        redisConnectionFactory.close();
    }

}