package com.telcoware.job_worker_app_demo.job.handler;

import com.telcoware.job_worker_app_demo.config.JobConfig;
import com.telcoware.job_worker_app_demo.job.dto.inf.JobInfo;
import com.telcoware.job_worker_app_demo.job.dto.task.JobTaskInfoDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.handler
 * fileName       : JobMaster
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : JOB 마스터
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobMaster {

    private final JobConfig jobConfig;
    private ThreadPoolTaskExecutor executor;

    private final Map<String, JobTaskInfoDto> jobTaskMap = new ConcurrentHashMap<>();
    private final Map<String, JobInfo<?>> jobInfoMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        executor = jobConfig.threadPoolTaskExecutor();
    }

    // Thread-pool 에 job 할당
    public <T> void assignJob(JobInfo<T> jobInfo) {
        JobWorker<T> jobWorker = new JobWorker<>(jobInfo);
        String jobId = jobInfo.getJobId();

        CompletableFuture<Void> future = CompletableFuture.runAsync(jobWorker, executor)
                .whenComplete((response, exception) -> {
                    // 무조건 정리 (성공, 예외 관계없이)
                    jobTaskMap.remove(jobId);
                    jobInfoMap.remove(jobId);
                    if (exception != null) {
                        log.warn("[JobMaster] {} failed: {}", jobId, exception.getMessage());
                    } else {
                        log.info("[JobMaster] {} completed. removed from task map.", jobId);
                    }
                });

        jobTaskMap.put(
                jobId,
                JobTaskInfoDto.builder()
                        .worker(jobWorker)
                        .future(future)
                        .build()
        );
        jobInfoMap.put(
                jobId,
                jobInfo
        );
    }

    public void stopJob(String jobId) {
        JobTaskInfoDto jobTaskInfoDto = jobTaskMap.remove(jobId);
        if (jobTaskInfoDto == null) {
            return;
        }

        jobInfoMap.remove(jobId);

        // 1) 큐에 대기 중인 동일 Runnable 제거 시도
        JobWorker<?> worker = jobTaskInfoDto.getWorker();
        Future<?> future = jobTaskInfoDto.getFuture();
        boolean removedFromQueue = executor.getThreadPoolExecutor().remove(worker);
        if (removedFromQueue) {
            // 큐에서 빠졌으니 Future도 취소 시도 (이미 실행 안 됨이 보장되지만 안전차원)
            future.cancel(false);
            log.info("[JobMaster] removed from queue. jobId={}", jobId);
            return;
        }

        // 2) 이미 실행 중이면 인터럽트 + 협력적 취소
        worker.cancel(); // 협력적 종료 신호
        boolean cancelled = future.cancel(true); // 인터럽트 신호
        log.info("[JobMaster] cancel invoked. jobId={}, futureCancelled={}", jobId, cancelled);
    }

    public boolean isActive() {
        int activeCount = executor.getActiveCount();
        int maxPoolSize = executor.getMaxPoolSize();
        int watermark = jobConfig.getJobWorkerWatermark();
        return (((double) activeCount / (double) maxPoolSize)) * 100 <= watermark;
    }

    public List<JobInfo<?>> getJobInfos() {
        return new ArrayList<>(jobInfoMap.values());
    }

}
