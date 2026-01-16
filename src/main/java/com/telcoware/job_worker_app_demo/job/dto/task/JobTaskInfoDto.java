package com.telcoware.job_worker_app_demo.job.dto.task;

import com.telcoware.job_worker_app_demo.job.handler.JobWorker;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.Future;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.dto.task
 * fileName       : JobTaskInfoDto
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 작업 수행 정보 DTO 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Data
@Builder
@RequiredArgsConstructor
public class JobTaskInfoDto {

    private final JobWorker<?> worker;
    private final Future<?> future;

}
