package com.dovaj.job_worker_app_demo.data.dto.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * packageName    : com.dovaj.job_master_app_demo.job.dto.impl
 * fileName       : JobInfoDto
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : 작업 정보 DTO
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobInfoDto {

    private String jobId;
    private String jobName;

}
