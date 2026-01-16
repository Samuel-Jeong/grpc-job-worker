package com.telcoware.job_worker_app_demo.job.dto.status;

import lombok.Builder;
import lombok.Data;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.dto
 * fileName       : JobStatusInfoDto
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 작업 상태 정보 DTO 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Data
@Builder
public class JobStatusInfoDto {

    private String workerId;
    private String jobId;
    private String jobName;
    private Short status; // JOB_STATUS_TYPE.code
    private String updateDatetime;

}
