package com.telcoware.job_worker_app_demo.job.handler;

import com.telcoware.job_worker_app_demo.job.dto.inf.JobInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.handler
 * fileName       : JobWorker
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : JOB 작업자 (인터럽트/취소/타임아웃 대응 업그레이드)
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 * 25. 10. 30.        samuel       인터럽트 전파/초기지연 인터럽트/타임아웃/정리 보강
 * 25. 10. 30.        samuel       busy-wait 제거 및 InterruptedException 경고 제거
 */
@Slf4j
public class JobWorker<T> implements Runnable {

    private final JobInfo<T> jobInfo;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** 최대 실행 시간(옵션). null이면 무제한 */
    private final Long maxRunMillis;
    /** 타임아웃 후 강제 인터럽트까지 허용하는 추가 유예 시간 */
    private final long shutdownGraceMillis;

    /** 실행 스레드 레퍼런스(취소 시 interrupt 전달용) */
    private volatile Thread runnerThread;

    /** 타임아웃 워처용 스케줄러 (필요 시에만 생성) */
    private ScheduledThreadPoolExecutor timeoutScheduler;

    /** 기본 생성자: 타임아웃 없이 즉시 취소/인터럽트만 지원 */
    public JobWorker(JobInfo<T> jobInfo) {
        this(jobInfo, null, Duration.ofSeconds(5));
    }

    /** 확장 생성자: 타임아웃 + 그레이스 기간 지정 가능 */
    public JobWorker(JobInfo<T> jobInfo, Duration maxRun, Duration shutdownGrace) {
        this.jobInfo = Objects.requireNonNull(jobInfo, "jobInfo must not be null");
        this.maxRunMillis = (maxRun == null ? null : maxRun.toMillis());
        this.shutdownGraceMillis = (shutdownGrace == null ? 0L : Math.max(0L, shutdownGrace.toMillis()));
    }

    /**
     * 외부에서 취소 신호 (협조적 취소 + 실행 스레드 인터럽트)
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            try {
                jobInfo.cancel(); // JobInfo에 협조적 취소 전달
            } catch (Throwable t) {
                log.warn("[JobWorker] failed to call jobInfo.cancel(). (jobId={})", jobInfo.getJobId(), t);
            }
            // 실행 스레드가 살아있다면 인터럽트로 즉시 깨우기
            Thread t = runnerThread;
            if (t != null) t.interrupt();
        }
    }

    /**
     * 취소 여부
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void run() {
        this.runnerThread = Thread.currentThread();
        final String id = jobInfo.getJobId();

        // 사전 상태 점검
        if (shouldStop()) {
            log.warn("[JobWorker] already cancelled or interrupted before start. (jobId={})", id);
            return;
        }

        // 타임아웃 워처 기동 (옵션)
        startTimeoutWatcherIfNeeded(id);

        try {
            // [개선] 초기 지연: 단일 차단 대기 + 인터럽트 처리 (busy-wait 제거)
            Long initialDelayMillis = jobInfo.getInitialDelayMillis();
            if (initialDelayMillis != null && initialDelayMillis > 0) {
                try {
                    Thread.sleep(initialDelayMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // 상태 복원
                    log.warn("[JobWorker] interrupted during initial delay. (jobId={})", id, ie);
                    return; // 즉시 종료
                }
            }

            // 실행 직전 재확인
            if (shouldStop()) {
                log.warn("[JobWorker] stopped just before process() start. (jobId={})", id);
                return;
            }

            // 구현체는 변경 없이 process()만 구현하면 됨
            try {
                jobInfo.runGracefully();
            } catch (RuntimeException e) {
                // 실행 중 인터럽트가 발생한 상태라면 정상 중단으로 취급
                if (Thread.currentThread().isInterrupted() || isCancelled()) {
                    log.warn("[JobWorker] interrupted during process(). (jobId={})", id, e);
                    Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                    return;
                }
                // 인터럽트가 아닌 런타임 예외는 로깅 후 반환
                log.warn("[JobWorker] run exception (jobId={})", id, e);
                return;
            }

            if (shouldStop()) {
                log.warn("[JobWorker] cancelled after process() returned. (jobId={})", id);
            } else {
                log.info("[JobWorker] finished normally. (jobId={})", id);
            }
        } finally {
            stopTimeoutWatcherIfNeeded();
        }
    }

    /**
     * 현재 중단해야 하는지 판정
     */
    private boolean shouldStop() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    /**
     * 타임아웃 워처: maxRunMillis가 지나면 cancel()+interrupt(), 그레이스 기간은 join으로 대기
     */
    private void startTimeoutWatcherIfNeeded(String jobId) {
        if (maxRunMillis == null || maxRunMillis <= 0) return;

        timeoutScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "job-timeout-watcher-" + jobId);
            t.setDaemon(true);
            return t;
        });

        timeoutScheduler.schedule(() -> {
            Thread t = runnerThread;
            if (!isCancelled() && t != null && t.isAlive()) {
                log.warn("[JobWorker] timeout exceeded ({} ms). Requesting cancel... (jobId={})",
                        maxRunMillis, jobId);
                cancel(); // 협조적 취소 + 인터럽트

                // [개선] busy-wait 제거: join으로 유예 기간 대기
                if (shutdownGraceMillis > 0) {
                    try {
                        t.join(shutdownGraceMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // 상태 복원
                        return;
                    }
                }

                // 그래도 살아있다면 마지막으로 한 번 더 인터럽트 전파
                if (t.isAlive()) {
                    log.warn("[JobWorker] still running after grace {} ms, interrupt again. (jobId={})",
                            shutdownGraceMillis, jobId);
                    t.interrupt();
                }
            }
        }, maxRunMillis, TimeUnit.MILLISECONDS);
    }

    private void stopTimeoutWatcherIfNeeded() {
        if (timeoutScheduler != null) {
            try {
                timeoutScheduler.shutdownNow();
            } catch (Throwable ignored) {
            } finally {
                timeoutScheduler = null;
            }
        }
    }

}