package com.telcoware.job_worker_app_demo.scheduler.schedule.handler;

import com.telcoware.job_worker_app_demo.scheduler.job.Job;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * packageName    : package com.capshome.iotgw.scheduler.schedule.handler
 * fileName       : JobExecutor
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : 작업 실행 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
public class JobExecutor {

    /// /////////////////////////////////////////////////////////////////////////////
    private final int index;

    private final PriorityBlockingQueue<Job> priorityQueue;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    ////////////////////////////////////////////////////////////////////////////////

    /// /////////////////////////////////////////////////////////////////////////////
    public JobExecutor(String scheduleUnitKey, int index, int queueSize) {
        this.index = index;

        priorityQueue = new PriorityBlockingQueue<>(
                queueSize,
                Comparator.comparing(Job::getPriority)
        );

        ThreadFactory threadFactory = new BasicThreadFactory
                .Builder()
                .namingPattern(scheduleUnitKey + "_JobExecutor" + "-" + index)
                .daemon(true)
                .build();

        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, threadFactory);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(
                new Worker(),
                0,
                1,
                TimeUnit.MILLISECONDS
        );
    }

    /// /////////////////////////////////////////////////////////////////////////////

    public void stop() {
        scheduledThreadPoolExecutor.shutdown();
        priorityQueue.clear();
    }

    public boolean addJob(Job job) {
        return priorityQueue.offer(job);
    }

    public int getIndex() {
        return index;
    }

    /// /////////////////////////////////////////////////////////////////////////////

    private class Worker implements Runnable {

        @Override
        public void run() {
            try {
                // poll(): dequeue 후 객체 null 여부에 상관없이 기다리지 않음
                // take(): dequeue 후 객체가 null 이 아닐 때까지 기다림
                Job job = priorityQueue.poll();
                if (job == null) {
                    return;
                }

                Runnable runnable = job.getRunnable();
                if (runnable == null) {
                    return;
                }

                if (!job.isLasted()) {
                    if (job.getCurRemainRunCount() < 0) {
                        job.setIsFinished(true);
                    }
                }

                runnable.run();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

}
