package com.telcoware.job_worker_app_demo.service.grpc;

import com.telcoware.job_worker_app_demo.data.dto.pod.WorkerInfo;
import com.telcoware.job_worker_app_demo.data.dto.redis.KvPair;
import com.telcoware.job_worker_app_demo.service.aws.elasticache.AwsValKeyService;
import com.telcoware.job_worker_app_demo.util.GsonUtil;
import com.telcoware.job_worker_app_demo.util.NetworkUtil;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.telcoware.job_worker_app_demo.data.definition.STRING_CONSTANTS.WORKER_PREFIX_PATTERN;
import static com.telcoware.job_worker_app_demo.util.NetworkUtil.ANY_HOST;
import static com.telcoware.job_worker_app_demo.util.NetworkUtil.DEFAULT_GRPC_PORT;

/**
 * packageName    : com.telcoware.job_worker_app_demo.service.grpc
 * fileName       : GrpcServerService
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : GRPC Server 서비스 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcServerService {

    private final Map<Integer, Server> serverMap = new ConcurrentHashMap<>();

    // gRPC 서비스/인터셉터를 스프링 빈에서 자동 수집
    private final ObjectProvider<BindableService> bindableServices;
    private final ObjectProvider<ServerInterceptor> serverInterceptors;

    private final AwsValKeyService awsValKeyService;

    private final GsonUtil gsonUtil;

    @Retryable(
            retryFor = {IOException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 2000)
    )
    public Server enableGrpcServer() throws IOException {
        Server server = null;
        int lastGrpcPort = getLastGrpcPort();
        if (lastGrpcPort > 0) {
            server = buildGrpcServer(
                    NetworkUtil.pickAvailablePort(lastGrpcPort)
            );
            startGrpcServer(server);
        }
        return server;
    }

    /**
     * 모든 재시도가 실패했을 때 호출되는 회복 로직.
     * 1) 다음 사용 가능한 포트 재시도
     * 2) 실패 시 ephemeral 포트(0)로 바인딩
     * 3) 그래도 실패하면 예외 throw
     */
    @Recover
    public Server recover(IOException e) {
        log.error("->SVC::gRPC server start failed after retries. Try fallback. cause={}", e.toString());

        // 1) '마지막 포트 + 1' 등으로 한 번 더 시도
        try {
            int last = getLastGrpcPort();
            int candidate = NetworkUtil.pickAvailablePort(last + 1);
            log.warn("->SVC::Fallback try with next port {}", candidate);
            Server fallback = buildGrpcServer(candidate);
            startGrpcServer(fallback);
            log.warn("->SVC::Fallback succeeded on port {}", candidate);
            return fallback;
        } catch (Exception ex1) {
            log.warn("->SVC::Fallback(next port) failed: {}", ex1.toString());
        }

        // 2) OS가 정해주는 ephemeral 포트(0)로 바인딩 시도
        try {
            log.warn("->SVC::Fallback try with ephemeral port (0)");
            Server ephemeral = buildGrpcServer(0);
            startGrpcServer(ephemeral);

            int actualPort = ((InetSocketAddress) ephemeral.getListenSockets().iterator().next()).getPort();
            log.warn("->SVC::Ephemeral fallback succeeded on port {}", actualPort);

            return ephemeral;
        } catch (Exception ex2) {
            log.error("->SVC::Fallback(ephemeral) failed: {}", ex2.toString());
        }

        // 3) 최종 실패 처리: 알림 후 중단
        log.error("->SVC::All fallback attempts failed. gRPC server not started.");
        throw new IllegalStateException("Failed to start gRPC server in recover()", e);
    }

    public Server buildGrpcServer(int port) {
        // 리플렉션/헬스
        HealthStatusManager healthStatusManager = new HealthStatusManager();

        NettyServerBuilder builder = NettyServerBuilder
                .forAddress(new InetSocketAddress(ANY_HOST, port))
                .maxInboundMessageSize(16 * 1024 * 1024)
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .permitKeepAliveTime(10, TimeUnit.SECONDS)
                .permitKeepAliveWithoutCalls(true);

        // 서비스 등록(빈 자동 수집)
        bindableServices.stream().forEach(builder::addService);

        // 리플렉션/헬스 서비스
        builder.addService(ProtoReflectionService.newInstance());
        builder.addService(healthStatusManager.getHealthService());

        // 인터셉터 등록
        serverInterceptors.stream().forEach(builder::intercept);

        return builder.build();
    }

    public void startGrpcServer(Server server) throws IOException {
        server.start();
        serverMap.put(server.getPort(), server);
        log.info("->SVC::gRPC server started on port {}",
                ((InetSocketAddress) server.getListenSockets()
                        .iterator().next())
                        .getPort()
        );
    }

    public void stopGrpcServer(Server server) {
        try {
            if (server != null) {
                server.shutdown();
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }

                int port = server.getPort();
                serverMap.remove(port);
                log.info("->SVC::gRPC server stopped (port={})", port);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("->SVC::gRPC server interrupted ({})", ie.getMessage());
        }
    }

    public Server getFirstGrpcServer() {
        return serverMap.values().stream().findFirst().orElse(null);
    }

    private int getLastGrpcPort() {
        List<KvPair> fetchedKeyValues = awsValKeyService.fetchKeyValues(WORKER_PREFIX_PATTERN.getValue());
        if (fetchedKeyValues == null) return DEFAULT_GRPC_PORT;
        if (fetchedKeyValues.isEmpty()) return DEFAULT_GRPC_PORT;

        KvPair kvPair = fetchedKeyValues.get(fetchedKeyValues.size() - 1);
        if (kvPair == null) return DEFAULT_GRPC_PORT;

        String value = kvPair.value();
        if (!StringUtils.hasText(value)) return DEFAULT_GRPC_PORT;

        WorkerInfo workerInfo = gsonUtil.deserialize(value, WorkerInfo.class);
        return (workerInfo != null && workerInfo.getGrpcPort() > 0) ? workerInfo.getGrpcPort() : DEFAULT_GRPC_PORT;
    }

}
