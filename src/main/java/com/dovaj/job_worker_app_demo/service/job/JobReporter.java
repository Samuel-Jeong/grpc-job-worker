package com.dovaj.job_worker_app_demo.service.job;

import com.dovaj.job_worker_app_demo.job.definition.JOB_STATUS_TYPE;
import com.dovaj.job_worker_app_demo.job.dto.status.JobStatusInfoDto;
import com.dovaj.job_worker_app_demo.service.aws.elasticache.AwsValKeyService;
import com.dovaj.job_worker_app_demo.util.GsonUtil;
import com.dovaj.job_worker_app_demo.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.job
 * fileName       : JobReporter
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : 작업 보고 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobReporter {

    private final AwsValKeyService awsValKeyService;
    private final GsonUtil gsonUtil;

    public boolean updateJobStatusInfoDto(String workerId, String jobId, String jobName,
                                          JOB_STATUS_TYPE jobStatusType) {
        JobStatusInfoDto jobStatusInfoDto = JobStatusInfoDto.builder()
                .workerId(workerId)
                .jobId(jobId)
                .jobName(jobName)
                .status(jobStatusType.getCode())
                .updateDatetime(TimeUtil.convertLocalDateTimeToString(LocalDateTime.now(ZoneId.of("Asia/Seoul"))))
                .build();

        return awsValKeyService.setValue(
                jobId,
                gsonUtil.serialize(jobStatusInfoDto),
                Duration.of(600, ChronoUnit.SECONDS)
        );
    }

}
