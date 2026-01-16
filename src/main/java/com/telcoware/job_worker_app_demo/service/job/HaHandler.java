package com.telcoware.job_worker_app_demo.service.job;

import com.telcoware.job_worker_app_demo.config.ScheduleConfig;
import com.telcoware.job_worker_app_demo.data.dto.job.WorkerInfoReportJob;
import com.telcoware.job_worker_app_demo.job.handler.JobMaster;
import com.telcoware.job_worker_app_demo.scheduler.job.Job;
import com.telcoware.job_worker_app_demo.scheduler.job.JobBuilder;
import com.telcoware.job_worker_app_demo.scheduler.schedule.ScheduleManager;
import com.telcoware.job_worker_app_demo.service.aws.elasticache.AwsValKeyService;
import com.telcoware.job_worker_app_demo.service.grpc.GrpcServerService;
import com.telcoware.job_worker_app_demo.util.GsonUtil;
import com.telcoware.job_worker_app_demo.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * packageName    : com.telcoware.job_worker_app_demo.service.job
 * fileName       : HaHandler
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
public class HaHandler implements ApplicationListener<ContextRefreshedEvent> {

    private final ScheduleConfig scheduleConfig;
    private final GsonUtil gsonUtil;
    private final GrpcServerService grpcServerService;
    private final AwsValKeyService awsValKeyService;
    private final JobMaster jobMaster;
    private ScheduleManager scheduleManager;
    private boolean isSchedulerEnabled = false;
    private String scheduleKey;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // ScheduleManager init
        scheduleManager = new ScheduleManager();
        scheduleKey = "HA_HANDLER_SCHEDULE_KEY:" + UUID.randomUUID();
        isSchedulerEnabled = scheduleManager.initJob(
                scheduleKey,
                scheduleConfig.getScheduleApplicationInfoReportThreadPoolSize(),
                scheduleConfig.getScheduleApplicationInfoReportThreadPoolQueueSize()
        );
        if (isSchedulerEnabled) {
            log.info("Success to init job scheduler. ({})", scheduleKey);
        }

        assignApplicationInfoReportJob();
    }

    private void assignApplicationInfoReportJob() {
        String className = WorkerInfoReportJob.class.getSimpleName();
        if (!isSchedulerEnabled) {
            log.warn("Fail to start [{}]. Scheduler is not started.", className);
            return;
        }

        Job job = new JobBuilder()
                .setScheduleManager(scheduleManager)
                .setName(className + ":" + UUID.randomUUID())
                .setInitialDelay(0)
                .setInterval(5)
                .setTimeUnit(TimeUtil.convertStringToTimeUnit("s"))
                .setPriority(1)
                .setTotalRunCount(0)
                .setIsLasted(true)
                .setJobFinishCallBack(() -> log.info("[{}] : removedJob", className))
                .build();
        WorkerInfoReportJob workerInfoReportJob = new WorkerInfoReportJob(
                job,
                gsonUtil,
                grpcServerService,
                awsValKeyService,
                jobMaster
        );
        workerInfoReportJob.start();
        if (scheduleManager.startJob(scheduleKey, workerInfoReportJob.getJob())) {
            log.info("Success to start [{}].", className);
        } else {
            log.warn("Fail to start [{}].", className);
        }
    }

}
