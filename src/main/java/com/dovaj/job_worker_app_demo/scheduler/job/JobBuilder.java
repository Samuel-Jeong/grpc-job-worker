package com.dovaj.job_worker_app_demo.scheduler.job;

import com.dovaj.job_worker_app_demo.scheduler.schedule.ScheduleManager;
import com.dovaj.job_worker_app_demo.scheduler.schedule.handler.callback.JobFinishCallBack;

import java.util.concurrent.TimeUnit;

/**
 * packageName    : com.dovaj.job_worker_app_demo.scheduler.job
 * fileName       : JobBuilder
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : 작업 빌더 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
public class JobBuilder {

    private final Job job;

    public JobBuilder() {
        this.job = new Job();
    }

    public JobBuilder setScheduleManager(ScheduleManager scheduleManager) {
        job.setScheduleManager(scheduleManager);
        return this;
    }

    public JobBuilder setName(String name) {
        job.setName(name);
        return this;
    }

    public JobBuilder setInitialDelay(int initialDelay) {
        job.setInitialDelay(initialDelay);
        return this;
    }

    public JobBuilder setInterval(int interval) {
        job.setInterval(interval);
        return this;
    }

    public JobBuilder setTimeUnit(TimeUnit timeUnit) {
        job.setTimeUnit(timeUnit);
        return this;
    }

    public JobBuilder setPriority(int priority) {
        job.setPriority(priority);
        return this;
    }

    public JobBuilder setTotalRunCount(int totalRunCount) {
        job.setTotalRunCount(totalRunCount);
        job.setCurRemainRunCount(totalRunCount);
        return this;
    }

    public JobBuilder setIsLasted(boolean isLasted) {
        job.setLasted(isLasted);
        return this;
    }

    public JobBuilder setJobFinishCallBack(JobFinishCallBack jobFinishCallBack) {
        job.setJobFinishCallBack(jobFinishCallBack);
        return this;
    }

    public Job build() {
        return job;
    }

}
