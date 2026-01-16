package com.dovaj.job_worker_app_demo.data.dto.job;

import com.dovaj.job_worker_app_demo.data.definition.WORKER_STATUS_TYPE;
import com.dovaj.job_worker_app_demo.data.dto.pod.WorkerInfo;
import com.dovaj.job_worker_app_demo.job.handler.JobMaster;
import com.dovaj.job_worker_app_demo.scheduler.job.Job;
import com.dovaj.job_worker_app_demo.scheduler.job.JobContainer;
import com.dovaj.job_worker_app_demo.service.aws.elasticache.AwsValKeyService;
import com.dovaj.job_worker_app_demo.service.grpc.GrpcServerService;
import com.dovaj.job_worker_app_demo.util.GsonUtil;
import com.dovaj.job_worker_app_demo.util.NetworkUtil;
import com.dovaj.job_worker_app_demo.util.WorkerInfoUtil;
import com.dovaj.job_worker_app_demo.util.ProcessUtil;
import io.grpc.Server;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * packageName    : com.dovaj.job_worker_app_demo.data.dto.job
 * fileName       : JobTargetSelectionWork
 * author         : samuel
 * date           : 25. 10. 21.
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
public class WorkerInfoReportJob extends JobContainer {

    private final GsonUtil gsonUtil;
    private final GrpcServerService grpcServerService;
    private final AwsValKeyService awsValKeyService;
    private final JobMaster jobMaster;

    public WorkerInfoReportJob(Job job,
                               GsonUtil gsonUtil,
                               GrpcServerService grpcServerService,
                               AwsValKeyService awsValKeyService,
                               JobMaster jobMaster) {
        setJob(job);

        this.gsonUtil = gsonUtil;
        this.grpcServerService = grpcServerService;
        this.awsValKeyService = awsValKeyService;
        this.jobMaster = jobMaster;
    }

    public void start() {
        getJob().setRunnable(() -> {
            try {
                String currentIp = NetworkUtil.getCurrentIp();
                long pid = ProcessUtil.getPid();

                String workerId = WorkerInfoUtil.makeWorkerId(currentIp, pid);

                int port = -1;
                Server firstGrpcServer = grpcServerService.getFirstGrpcServer();
                if (firstGrpcServer != null) {
                    port = firstGrpcServer.getPort();
                }

                WORKER_STATUS_TYPE currentWorkerStatus;
                boolean isJobSystemActive = jobMaster.isActive();
                if (isJobSystemActive) {
                    currentWorkerStatus = WORKER_STATUS_TYPE.IDLE;
                } else {
                    currentWorkerStatus = WORKER_STATUS_TYPE.BUSY;
                }

                WorkerInfo workerInfo = WorkerInfoUtil.makeWorkerInfo(port, currentWorkerStatus);

                boolean setResult = awsValKeyService.setValue(
                        workerId,
                        gsonUtil.serialize(workerInfo),
                        Duration.of(10, ChronoUnit.SECONDS)
                );
                if (!setResult) {
                    log.warn("->SVC::Worker Info set failed ({}, {})", workerId, gsonUtil.serialize(workerInfo));
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        });
    }

}
