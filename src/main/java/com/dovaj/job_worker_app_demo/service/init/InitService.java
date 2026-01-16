package com.dovaj.job_worker_app_demo.service.init;

import com.dovaj.job_worker_app_demo.data.definition.WORKER_STATUS_TYPE;
import com.dovaj.job_worker_app_demo.data.dto.pod.WorkerInfo;
import com.dovaj.job_worker_app_demo.service.aws.elasticache.AwsValKeyService;
import com.dovaj.job_worker_app_demo.service.grpc.GrpcServerService;
import com.dovaj.job_worker_app_demo.util.GsonUtil;
import com.dovaj.job_worker_app_demo.util.NetworkUtil;
import com.dovaj.job_worker_app_demo.util.ProcessUtil;
import com.dovaj.job_worker_app_demo.util.WorkerInfoUtil;
import io.grpc.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.init
 * fileName       : InitService
 * author         : samuel
 * date           : 25. 10. 21.
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitService implements ApplicationListener<ContextRefreshedEvent> {

    private final AwsValKeyService awsValKeyService;

    private final GrpcServerService grpcServerService;
    private final GsonUtil gsonUtil;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 파드 기동되면 어플리케이션에서 ElastiCache 에 파드 정보 등록
        String currentIp = NetworkUtil.getCurrentIp();
        long pid = ProcessUtil.getPid();

        // gRPC 서버 생성
        Server server = null;
        try {
            server = grpcServerService.enableGrpcServer();
        } catch (Exception e) {
            log.warn("Failed to start grpc server", e);
            System.exit(1);
        }
        if (server == null) {
            log.warn("Failed to start grpc server");
            System.exit(1);
        }

        // 서버 빈 등록 (singleton)
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) event.getApplicationContext();
        ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory();
        beanFactory.registerSingleton("grpcServer", server);

        // 종료 HOOK (컨텍스트 종료 시 graceful down)
        Server finalServer = server;
        ctx.addApplicationListener((ApplicationListener<ContextClosedEvent>) e -> grpcServerService.stopGrpcServer(finalServer));

        String workerId = WorkerInfoUtil.makeWorkerId(currentIp, pid);
        WorkerInfo workerInfo = WorkerInfoUtil.makeWorkerInfo(server.getPort(), WORKER_STATUS_TYPE.IDLE);

        boolean setResult = awsValKeyService.setValue(
                workerId,
                gsonUtil.serialize(workerInfo),
                Duration.of(10, ChronoUnit.SECONDS)
        );
        if (setResult) {
            log.info("->SVC::Worker Info set successfully ({}, {})", workerId, gsonUtil.serialize(workerInfo));
        } else {
            log.warn("->SVC::Worker Info set failed ({}, {})", workerId, gsonUtil.serialize(workerInfo));
        }
    }

}
