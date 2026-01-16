package com.telcoware.job_worker_app_demo.job.dto.inf;

import com.telcoware.job_worker_app_demo.job.definition.JOB_STATUS_TYPE;
import com.telcoware.job_worker_app_demo.service.job.JobReporter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.dto
 * fileName       : JobInfo
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : JOB 정보
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
@Data
@AllArgsConstructor
public abstract class JobInfo<T> {

    private final String workerId;
    private final String jobId;
    private final String jobName;
    private final Class<T> messageClass;
    private final JobReporter jobReporter;

    private Long initialDelayMillis;
    private T message;
    private String gsonName = "";

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public JobInfo(String workerId, String jobId, String jobName,
                   Class<T> messageClass, JobReporter jobReporter) {
        this.workerId = workerId;
        this.jobId = jobId;
        this.jobName = jobName;
        this.messageClass = messageClass;
        this.jobReporter = jobReporter;
    }

    /** 구현체에서 비즈니스 로직을 넣는 기존 메서드 */
    public abstract void process();

    /** 외부에서 호출할 ‘안전 실행’ 진입점 (구현체 수정 불필요) */
    public final void runGracefully() {
        // 시작 전에 이미 취소/인터럽트면 바로 종료
        if (isCancelled() || Thread.currentThread().isInterrupted()) {
            log.warn("Job '{}' is already cancelled or interrupted before start.", jobName);
            jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.CANCELLED);
            onInterrupted(); // 선택적 정리 훅
            onFinally(); // 공통 정리 훅
            return;
        }

        // Job 상태 천이 (ALLOCATED > RUNNING)
        jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.RUNNING);

        try {
            process(); // 구현체 코드 호출 (변경 없음)

            // Job 상태 천이 (RUNNING > SUCCESS)
            jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.SUCCESS);
        } catch (RuntimeException e) {
            // 실행 중 인터럽트 플래그가 세워졌다면 ‘정상적인 중단’으로 간주
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Job '{}' interrupted during process().", jobName, e);
                jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.CANCELLED);
                cancel(); // 취소 플래그 세팅
                onInterrupted();
                // 인터럽트 상태 복원
                Thread.currentThread().interrupt();
                return;
            }

            // 인터럽트가 아니라 진짜 런타임 오류면 그대로 전파
            // Job 상태 천이 (RUNNING > FAILED)
            jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.FAILED);

            throw e;
        } catch (Error error) {
            // Error도 인터럽트 상태면 안전 종료로 간주 가능
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Job '{}' interrupted with Error.", jobName, error);
                jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.CANCELLED);
                cancel();
                onInterrupted();
                Thread.currentThread().interrupt();
                return;
            }

            // Job 상태 천이 (RUNNING > FAILED)
            jobReporter.updateJobStatusInfoDto(workerId, getJobId(), getJobName(), JOB_STATUS_TYPE.FAILED);

            throw error;
        } finally {
            onFinally(); // 항상 호출되는 공통 정리 훅
        }
    }

    /** 외부에서 호출 가능한 안전 취소 요청 (실행 스레드를 인터럽트하는 것은 실행자 측 역할) */
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** 인터럽트 시 선택적으로 정리 동작이 필요하다면 구현체가 오버라이드 (미오버라이드 시 no-op) */
    protected void onInterrupted() {
        // no-op by default
    }

    /** 성공/실패/중단과 무관한 공통 정리 훅 (미오버라이드 시 no-op) */
    protected void onFinally() {
        // no-op by default
    }

}
