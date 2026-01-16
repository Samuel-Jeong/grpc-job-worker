package com.telcoware.job_worker_app_demo.redis;

import io.lettuce.core.*;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * packageName    : com.telcoware.job_worker_app_demo.redis
 * fileName       : RedisConnectionFactory
 * author         : samuel
 * date           : 25. 10. 21.
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */

/**
 * Lettuce 클라이언트 생성을 책임지는 팩토리.
 * - 싱글/클러스터를 자동 판별(일반적으로 클러스터는 여러 URI를 전달)
 * - 자동재연결, TCP Keep-Alive, 타임아웃, 토폴로지 리프레시, pingBeforeActivateConnection 등 옵션 활성화
 * - ClientResources의 reconnectDelay를 지수 백오프로 설정
 */
class RedisConnectionFactory implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisConnectionFactory.class);

    @Getter
    private final boolean cluster;
    private final RedisClient redisClient;
    private final RedisClusterClient clusterClient;
    private final ClientResources clientResources;

    private RedisConnectionFactory(boolean cluster,
                                   RedisClient redisClient,
                                   RedisClusterClient clusterClient,
                                   ClientResources clientResources) {
        this.cluster = cluster;
        this.redisClient = redisClient;
        this.clusterClient = clusterClient;
        this.clientResources = clientResources;
    }

    public static RedisConnectionFactory of(List<RedisURI> redisUriList) {
        if (redisUriList == null || redisUriList.isEmpty()) {
            throw new IllegalArgumentException("Redis URIs must not be empty");
        }

        // 일반적으로 클러스터는 여러 호스트 URI 전달. 단일 URI일 때는 싱글로 간주.
        boolean isCluster = redisUriList.size() > 1;

        // ClientResources: 재연결 지연을 지수 백오프로 설정
        ClientResources clientResources = DefaultClientResources.builder()
                .reconnectDelay(
                        Delay.exponential(
                                Duration.ofMillis(100),     // 하한 100ms
                                Duration.ofSeconds(1),      // 상한 1s (원하면 더 키우세요)
                                2,                          // 지수 베이스(2배씩 증가)
                                TimeUnit.MILLISECONDS       // 타임유닛(밀리초)
                        )
                )
                .build();

        SocketOptions socketOptions = SocketOptions.builder()
                .keepAlive(true)                 // TCP Keep-Alive로 유휴 연결 조기 종료 예방
                .tcpNoDelay(true)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        TimeoutOptions timeoutOptions = TimeoutOptions.enabled(Duration.ofSeconds(5));

        if (isCluster) {
            // 클러스터 클라이언트 생성
            RedisClusterClient clusterClient = RedisClusterClient.create(clientResources, redisUriList);

            // 토폴로지 리프레시: 슬롯 이동/연결 이벤트에 반응 + 주기적 갱신
            ClusterTopologyRefreshOptions topologyOptions = ClusterTopologyRefreshOptions.builder()
                    .enableAllAdaptiveRefreshTriggers()
                    .enablePeriodicRefresh(Duration.ofSeconds(30))
                    .build();

            ClusterClientOptions options = ClusterClientOptions.builder()
                    .autoReconnect(true)
                    .pingBeforeActivateConnection(true)  // 커넥션 활성 전 PING으로 검증
                    .socketOptions(socketOptions)
                    .timeoutOptions(timeoutOptions)
                    .topologyRefreshOptions(topologyOptions)
                    .build();

            clusterClient.setOptions(options);
            if (log.isDebugEnabled()) {
                log.debug("Redis cluster client initialized with topology refresh and pingBeforeActivateConnection");
            }
            return new RedisConnectionFactory(true, null, clusterClient, clientResources);
        } else {
            // 싱글 클라이언트 생성
            RedisClient singleClient = RedisClient.create(clientResources, redisUriList.get(0));

            ClientOptions options = ClientOptions.builder()
                    .autoReconnect(true)
                    .pingBeforeActivateConnection(true)  // 커넥션 활성 전 PING으로 검증
                    .socketOptions(socketOptions)
                    .timeoutOptions(timeoutOptions)
                    .build();

            singleClient.setOptions(options);
            if (log.isDebugEnabled()) {
                log.debug("Redis single client initialized with pingBeforeActivateConnection");
            }
            return new RedisConnectionFactory(false, singleClient, null, clientResources);
        }
    }

    public RedisClient single() {
        if (cluster) throw new IllegalStateException("This is a cluster factory");
        return redisClient;
    }

    public RedisClusterClient cluster() {
        if (!cluster) throw new IllegalStateException("This is a single-node factory");
        return clusterClient;
    }

    @Override
    public void close() {
        try {
            if (redisClient != null) redisClient.shutdown();
        } catch (Exception ignore) {
        }
        try {
            if (clusterClient != null) clusterClient.shutdown();
        } catch (Exception ignore) {
        }
        try {
            if (clientResources != null) clientResources.shutdown();
        } catch (Exception ignore) {
        }
    }

}