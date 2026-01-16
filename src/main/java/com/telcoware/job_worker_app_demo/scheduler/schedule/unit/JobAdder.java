package com.telcoware.job_worker_app_demo.scheduler.schedule.unit;

import com.telcoware.job_worker_app_demo.scheduler.job.Job;
import com.telcoware.job_worker_app_demo.scheduler.schedule.handler.JobScheduler;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * packageName    : com.capshome.iotgw.scheduler.schedule.unit
 * fileName       : JobAdder
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : 작업 예약 워커 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
public class JobAdder implements Runnable {

    private final JobScheduler jobScheduler;
    private final Job job;
    private final int executorIndex;
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);

    public JobAdder(JobScheduler jobScheduler, Job job, int executorIndex) {
        this.jobScheduler = jobScheduler;
        this.job = job;
        this.executorIndex = executorIndex;
    }

    @Override
    public void run() {
        scheduledThreadPoolExecutor.scheduleAtFixedRate(
                !job.isLasted() ?
                        (() -> {
                            if (isJobFinished(job)) {
                                jobScheduler.cancel(job);
                            } else {
                                job.decCurRemainRunCount();
                                jobScheduler.addJobToExecutor(executorIndex, job);
                            }
                        })
                        :
                        (() -> {
                            if (isJobFinished(job)) {
                                jobScheduler.cancel(job);
                            } else {
                                jobScheduler.addJobToExecutor(executorIndex, job);
                            }
                        }),
                job.getInitialDelay(), job.getInterval(), job.getTimeUnit()
        );
    }

    public void stop() {
        scheduledThreadPoolExecutor.shutdown();
    }

    public boolean isJobFinished(Job job) {
        if (job == null) {
            return true;
        }

        return job.getIsFinished() ||
                (!job.isLasted() && (job.decCurRemainRunCount() < 0));
    }

}
