package com.dovaj.job_worker_app_demo.scheduler.schedule.unit;

import com.dovaj.job_worker_app_demo.scheduler.job.Job;
import com.dovaj.job_worker_app_demo.scheduler.schedule.handler.JobScheduler;

/**
 * packageName    : com.dovaj.job_worker_app_demo.scheduler.schedule.unit
 * fileName       : ScheduleUnit
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : 작업 실행 단위 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
public class ScheduleUnit {

    /// /////////////////////////////////////////////////////////////////////////////
    public static final int DEFAULT_THREAD_COUNT = 5;
    private final long createdTime = System.currentTimeMillis();

    private final String scheduleUnitKey;

    private final int poolSize; // Thread pool size
    private final JobScheduler jobScheduler;
    ////////////////////////////////////////////////////////////////////////////////

    /// /////////////////////////////////////////////////////////////////////////////
    public ScheduleUnit(String key, int poolSize, int queueSize) {
        this.scheduleUnitKey = key;

        if (poolSize > 0) {
            this.poolSize = poolSize;
        } else {
            this.poolSize = DEFAULT_THREAD_COUNT;
        }

        jobScheduler = new JobScheduler(scheduleUnitKey, poolSize, queueSize);
    }
    ////////////////////////////////////////////////////////////////////////////////

    /// /////////////////////////////////////////////////////////////////////////////
    public boolean start(Job job) {
        if (job == null) {
            return false;
        }
        job.setScheduleUnitKey(scheduleUnitKey);
        return jobScheduler.schedule(job);
    }

    public void stop(Job job) {
        if (job == null) {
            return;
        }
        job.setScheduleUnitKey(null);
        jobScheduler.cancel(job);
    }

    public void stopAll() {
        jobScheduler.stop();
    }
    ////////////////////////////////////////////////////////////////////////////////

    /// /////////////////////////////////////////////////////////////////////////////
    public int getJobListSize() {
        return jobScheduler.getScheduledJobCount();
    }

    public JobScheduler getJobScheduler() {
        return jobScheduler;
    }

    public String getScheduleUnitKey() {
        return scheduleUnitKey;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    @Override
    public String toString() {
        return "ScheduleUnit{" +
                "key='" + scheduleUnitKey + '\'' +
                ", threadCount=" + poolSize +
                '}';
    }
    ////////////////////////////////////////////////////////////////////////////////

}
