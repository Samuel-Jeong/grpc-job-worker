package com.dovaj.job_worker_app_demo.service.job;

import com.dovaj.job_worker_app_demo.config.ScheduleConfig;
import com.dovaj.job_worker_app_demo.job.dto.impl.JobMonitoringWork;
import com.dovaj.job_worker_app_demo.job.handler.JobMaster;
import com.dovaj.job_worker_app_demo.scheduler.job.Job;
import com.dovaj.job_worker_app_demo.scheduler.job.JobBuilder;
import com.dovaj.job_worker_app_demo.scheduler.schedule.ScheduleManager;
import com.dovaj.job_worker_app_demo.util.GsonUtil;
import com.dovaj.job_worker_app_demo.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.job
 * fileName       : JobMonitoringService
 * author         : samuel
 * date           : 25. 10. 29.
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "monitoring.job.enable-scheduler",
        havingValue = "true"
)
public class JobMonitoringService implements ApplicationListener<ContextRefreshedEvent> {

    private ScheduleManager scheduleManager;
    private boolean isSchedulerEnabled = false;
    private String scheduleKey;

    private final ScheduleConfig scheduleConfig;

    private final GsonUtil gsonUtil;

    private final JobMaster jobMaster;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // ScheduleManager init
        scheduleManager = new ScheduleManager();
        scheduleKey = "JOB_ALLOCATOR_SCHEDULE_KEY:" + UUID.randomUUID();
        isSchedulerEnabled = scheduleManager.initJob(
                scheduleKey,
                scheduleConfig.getScheduleMonitoringThreadPoolSize(),
                scheduleConfig.getScheduleMonitoringThreadPoolQueueSize()
        );
        if (isSchedulerEnabled) {
            log.info("Success to init job scheduler. ({})", scheduleKey);
        }

        assignJobMonitoringWork();
    }

    private void assignJobMonitoringWork() {
        String className = JobMonitoringWork.class.getSimpleName();
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
        JobMonitoringWork jobTargetSelectionWork = new JobMonitoringWork(
                job,
                jobMaster,
                gsonUtil
        );
        jobTargetSelectionWork.start();
        if (scheduleManager.startJob(scheduleKey, jobTargetSelectionWork.getJob())) {
            log.info("Success to start [{}].", className);
        } else {
            log.warn("Fail to start [{}].", className);
        }
    }

}
