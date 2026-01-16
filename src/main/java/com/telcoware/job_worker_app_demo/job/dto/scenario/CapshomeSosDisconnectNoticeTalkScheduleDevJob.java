package com.telcoware.job_worker_app_demo.job.dto.scenario;

import com.telcoware.job_worker_app_demo.data.dto.job.JobInfoDto;
import com.telcoware.job_worker_app_demo.job.dto.inf.JobInfo;
import com.telcoware.job_worker_app_demo.service.job.JobReporter;
import lombok.extern.slf4j.Slf4j;

/**
 * packageName    : com.telcoware.job_master_app_demo.job.dto.impl
 * fileName       : CapshomeSosDisconnectNoticeTalkScheduleDevJob
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : 캡스홈 SOS 비상버튼 단선 선제적 알림톡 스케쥴 (개발/데모) 작업 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Slf4j
public class CapshomeSosDisconnectNoticeTalkScheduleDevJob extends JobInfo<JobInfoDto> {

    public CapshomeSosDisconnectNoticeTalkScheduleDevJob(String workerId,
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
            log.info("Starting CapshomeSosDisconnectNoticeTalkScheduleDevJob");

            /////////////////////////////////////
            // TODO TEST
            Thread.sleep(100000);
            /////////////////////////////////////

            log.info("SUCCESS to finish CapshomeSosDisconnectNoticeTalkScheduleDevJob");
        } catch (Exception e) {
            log.warn("->SVC::[CapshomeSosDisconnectNoticeTalkScheduleDevJob] [Exception] {}", e.getMessage());
        }
    }

}
