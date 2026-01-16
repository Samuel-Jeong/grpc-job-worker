package com.dovaj.job_worker_app_demo.job.dto.impl;

import com.dovaj.job_worker_app_demo.job.dto.inf.JobInfo;
import com.dovaj.job_worker_app_demo.job.handler.JobMaster;
import com.dovaj.job_worker_app_demo.scheduler.job.Job;
import com.dovaj.job_worker_app_demo.scheduler.job.JobContainer;
import com.dovaj.job_worker_app_demo.util.GsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * packageName    : com.dovaj.job_master_app_demo.data.dto.job
 * fileName       : CronJobAllocationWork
 * author         : samuel
 * date           : 25. 10. 21.
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
public class JobMonitoringWork extends JobContainer {

    private final JobMaster jobMaster;
    private final GsonUtil gsonUtil;

    public JobMonitoringWork(Job job,
                             JobMaster jobMaster,
                             GsonUtil gsonUtil) {
        setJob(job); // 부모 컨테이너에 Job 주입

        this.jobMaster = jobMaster;
        this.gsonUtil = gsonUtil;
    }

    public void start() {
        getJob().setRunnable(() -> {
            try {
                List<JobInfo<?>> jobInfoList = jobMaster.getJobInfos();
                if (jobInfoList.isEmpty()) {
                    return;
                }

                for (int i = 0; i < jobInfoList.size(); i++) {
                    JobInfo<?> jobInfo = jobInfoList.get(i);
                    if (jobInfo == null) {
                        continue;
                    }

                    String jobId = jobInfo.getJobId();
                    String jobName = jobInfo.getJobName();
                    log.info("\t->SVC::[JOB-{}] id={}, name={}", i, jobId, jobName);
                }
            } catch (Exception e) {
                log.warn("->SVC::Monitoring job info failed.", e);
            }
        });
    }

}