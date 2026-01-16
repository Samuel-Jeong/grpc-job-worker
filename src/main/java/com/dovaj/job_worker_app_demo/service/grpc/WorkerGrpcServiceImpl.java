package com.dovaj.job_worker_app_demo.service.grpc;

import com.dovaj.job_worker_app_demo.data.dto.job.JobInfoDto;
import com.dovaj.job_worker_app_demo.job.definition.JOB_STATUS_TYPE;
import com.dovaj.job_worker_app_demo.job.dto.inf.JobInfo;
import com.dovaj.job_worker_app_demo.job.handler.JobMaster;
import com.dovaj.job_worker_app_demo.proto.*;
import com.dovaj.job_worker_app_demo.service.job.JobInfoReflectionFactoryService;
import com.dovaj.job_worker_app_demo.service.job.JobReporter;
import com.dovaj.job_worker_app_demo.util.NetworkUtil;
import com.dovaj.job_worker_app_demo.util.ProcessUtil;
import com.dovaj.job_worker_app_demo.util.TextUtil;
import com.dovaj.job_worker_app_demo.util.WorkerInfoUtil;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.grpc
 * fileName       : WorkerGrpcServiceImpl
 * author         : samuel
 * date           : 25. 10. 21.
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class WorkerGrpcServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {

    private static final String JOB_CLASS_PREFIX = "com.dovaj.job_worker_app_demo.job.dto.scenario.";
    private static final String JOB_CLASS_POSTFIX = "Job";
    private final JobMaster jobMaster;
    private final JobReporter jobReporter;
    private final JobInfoReflectionFactoryService jobInfoReflectionFactoryService;

    @Override
    public void addWork(AddWorkReq request, StreamObserver<AddWorkRes> responseObserver) {
        String currentIp = NetworkUtil.getCurrentIp();
        long pid = ProcessUtil.getPid();
        String workerId = WorkerInfoUtil.makeWorkerId(currentIp, pid);

        String jobId = request.getId();
        String jobName = request.getName();
        log.info("->SVC::[ADD JOB] [ID={} / NAME={}]", jobId, jobName);

        JobInfo<JobInfoDto> targetJob = jobInfoReflectionFactoryService.newJobInfo(
                JOB_CLASS_PREFIX + TextUtil.snakeToPascal(jobName) + JOB_CLASS_POSTFIX,
                workerId,
                jobId,
                jobName,
                0L,
                jobReporter
        );

        AddWorkRes sendWorkRes;
        if (targetJob != null) {
            if (jobMaster.isActive()) {
                // Job 상태 천이 (HODLING > ALLOCATED)
                boolean updateJobStatusInfoResult = jobReporter.updateJobStatusInfoDto(workerId, jobId, jobName, JOB_STATUS_TYPE.ALLOCATED);
                if (updateJobStatusInfoResult) {
                    jobMaster.assignJob(targetJob);
                    sendWorkRes = AddWorkRes.newBuilder()
                            .setMessage("SUCCESS")
                            .build();
                } else {
                    sendWorkRes = AddWorkRes.newBuilder()
                            .setMessage("FAIL")
                            .build();
                }
            } else {
                sendWorkRes = AddWorkRes.newBuilder()
                        .setMessage("FAIL")
                        .build();
            }
        } else {
            sendWorkRes = AddWorkRes.newBuilder()
                    .setMessage("FAIL")
                    .build();
        }
        responseObserver.onNext(sendWorkRes);
        responseObserver.onCompleted();
    }

    @Override
    public void stopWork(StopWorkReq request, StreamObserver<StopWorkRes> responseObserver) {
        String jobId = request.getId();
        String jobName = request.getName();
        log.info("->SVC::[STOP JOB] [ID={} / NAME={}]", jobId, jobName);

        jobMaster.stopJob(jobId);

        responseObserver.onNext(
                StopWorkRes.newBuilder()
                        .setMessage("SUCCESS")
                        .build()
        );
        responseObserver.onCompleted();
    }

}
