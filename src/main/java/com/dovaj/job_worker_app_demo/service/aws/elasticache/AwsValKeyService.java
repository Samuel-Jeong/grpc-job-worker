package com.dovaj.job_worker_app_demo.service.aws.elasticache;

import com.dovaj.job_worker_app_demo.config.AwsElasticacheConfig;
import com.dovaj.job_worker_app_demo.data.dto.redis.KvPair;
import com.dovaj.job_worker_app_demo.redis.RedisKeyValueClient;
import com.dovaj.job_worker_app_demo.redis.RedisKeyValueScanner;
import io.lettuce.core.RedisURI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.aws.elasticache
 * fileName       : AwsValKeyService
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : AWS ElastiCache ValKey 연동 서비스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwsValKeyService {

    private final AwsElasticacheConfig awsElasticacheConfig;

    /**
     * 키에 해당하는 값을 조회한다.
     *
     * @param key 조회할 Redis 키
     * @return 존재하면 문자열 값, 없거나 실패 시 null
     */
    public String getValue(String key) {
        try (RedisKeyValueClient client = new RedisKeyValueClient(
                List.of(awsElasticacheConfig.getEndpoint()), 2, 300)) {
            return client.getValue(key);
        }
    }

    /**
     * 값 저장 (성공 시 true)
     */
    public boolean setValue(String key, String value) {
        try (RedisKeyValueClient client = new RedisKeyValueClient(
                List.of(awsElasticacheConfig.getEndpoint()), 2, 300)) {
            return client.setValue(key, value);
        }
    }

    /**
     * TTL 포함 저장 (예: 60초)
     */
    public boolean setValue(String key, String value, Duration ttl) {
        try (RedisKeyValueClient client = new RedisKeyValueClient(
                List.of(awsElasticacheConfig.getEndpoint()), 2, 300)) {
            return client.setValue(key, value, ttl);
        }
    }

    /**
     * 키 삭제 (삭제된 개수 > 0 이면 true)
     */
    public boolean removeValue(String key) {
        try (RedisKeyValueClient client = new RedisKeyValueClient(
                List.of(awsElasticacheConfig.getEndpoint()), 2, 300)) {
            return client.removeValue(key);
        }
    }

    /**
     * 존재 여부
     */
    public boolean exists(String key) {
        try (RedisKeyValueClient client = new RedisKeyValueClient(
                List.of(awsElasticacheConfig.getEndpoint()), 2, 300)) {
            return client.exists(key);
        }
    }

    /**
     * 특정 패턴으로 매칭되는 키와 값 목록을 조회한다.
     *
     * @param pattern 예: "user:*"
     * @return List<KvPair> (key → value)
     */
    public List<KvPair> fetchKeyValues(String pattern) {
        List<RedisURI> uris = List.of(awsElasticacheConfig.getEndpoint());

        List<KvPair> list;
        try (var scanner = RedisKeyValueScanner.builder(uris)
                .scanCount(800)      // SCAN COUNT 힌트
                .mgetBatch(500)      // MGET 배치 크기
                .maxRetries(2)       // 실패 시 재시도 횟수
                .backoffMillis(300)  // 재시도 대기
                .build()) {

            int limit = 10_000; // 최대 1만 개
            list = scanner.findByPattern(pattern, limit);
        }
        return list;
    }

}
