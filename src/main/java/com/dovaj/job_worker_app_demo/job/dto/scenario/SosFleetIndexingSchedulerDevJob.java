package com.dovaj.job_worker_app_demo.job.dto.scenario;

import com.dovaj.job_worker_app_demo.data.dto.job.JobInfoDto;
import com.dovaj.job_worker_app_demo.job.dto.inf.JobInfo;
import com.dovaj.job_worker_app_demo.service.job.JobReporter;
import lombok.extern.slf4j.Slf4j;

/**
 * packageName    : com.dovaj.job_master_app_demo.job.dto.impl
 * fileName       : SosFleetIndexingSchedulerDevJob
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : SOS 비상버튼 단선체크 람다 스케줄러 (개발) 작업 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Slf4j
public class SosFleetIndexingSchedulerDevJob extends JobInfo<JobInfoDto> {

    public SosFleetIndexingSchedulerDevJob(String workerId,
                                           String jobId,
                                           String jobName,
                                           Long initialDelayMillis,
                                           JobReporter jobReporter) {
        super(workerId, jobId, jobName, JobInfoDto.class, jobReporter);

        this.setInitialDelayMillis(initialDelayMillis);
    }

    @Override
    public void process() {
        try {
            log.info("Starting SosFleetIndexingSchedulerDevJob");

            /////////////////////////////////////
            // TODO TEST
            Thread.sleep(5000);
            /////////////////////////////////////

            log.info("SUCCESS to finish SosFleetIndexingSchedulerDevJob");
        } catch (Exception e) {
            log.warn("->SVC::[SosFleetIndexingSchedulerDevJob] [Exception] {}", e.getMessage());
        }
    }

}
