package com.dovaj.job_worker_app_demo.scheduler.queue;

import com.dovaj.job_worker_app_demo.scheduler.job.Job;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * packageName    : com.sks.wpm.core.scheduler.queue
 * fileName       : JobQueueManager
 * author         : samuel
 * date           : 25. 9. 30.
 * description    :
 * - FIFO(선입선출) 방식의 작업 큐 관리자
 * - 내부 저장소는 단일 연결 리스트가 아닌 ArrayDeque(배열 기반 덱)를 사용
 * - 스레드 안전 보장을 위해 ReentrantLock + Condition 조합 채택
 * - 워터마크(임계치)를 최대 수용량으로 사용(0이면 무제한)
 * <p>
 * 주요 특징
 * - 비차단 추가 메서드: 큐가 워터마크 이상이면 즉시 false
 * - 차단 추가 메서드: 여유가 생길 때까지 await(효율 대기, 스핀락 지양)
 * - 삭제/반출/초기화 시 notFull.signal*( ) 호출로 대기 중 생산자 깨움
 * - top/last/get은 boolean만 반환하므로 마지막 조회 Job은 getLastViewed( )로 획득
 * <p>
 * 동시성/정확성 주의사항
 * - Condition.await( )는 spurious wakeup이 가능하므로 반드시 while 조건 재검사 필요
 * - signal( )은 하나만, signalAll( )은 다수 깨움; 상황에 따라 선택 사용
 * - 인터럽트 발생 시 인터럽트 상태 복원 또는 적절히 false/예외 처리
 */

// FIFO Queue 방식
public class JobQueueManager {

    /**
     * 실제 작업이 저장되는 큐 (배열 기반, null 요소 비허용)
     */
    private final Deque<Job> queue = new ArrayDeque<>();

    /**
     * 상호배제를 위한 재진입 락
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 생산자(추가) 대기를 깨우기 위한 조건 변수: "큐가 가득차지 않았다(not full)" 상태 알림
     */
    private final Condition notFull = lock.newCondition();

    /**
     * 소비자(반출) 대기를 깨우기 위한 조건 변수: "큐가 비어있지 않다(not empty)" 상태 알림 (필요 시 사용)
     */
    private final Condition notEmpty = lock.newCondition();
    /**
     * 워터마크 기준치 (0이면 무제한으로 간주)
     */
    private final int watermark;
    /**
     * 최근 조회 성공 시 보관되는 Job (top/last/get 호출 시 설정됨)
     */
    private Job lastViewed;

    /**
     * 생성자
     *
     * @param watermark 큐 최대 수용량(임계치). 0이면 무제한(대기 없이 항상 추가 시도)
     * @throws IllegalArgumentException watermark가 음수인 경우
     */
    public JobQueueManager(int watermark) {
        if (watermark < 0) {
            throw new IllegalArgumentException("워터마크는 음수일 수 없습니다.");
        }
        this.watermark = watermark;
    }

    // ----------------------------------------------------------------------
    // 추가(생산자) 계열
    // ----------------------------------------------------------------------

    /**
     * 작업을 큐에 추가 (고정 지연 후 시도, 비차단)
     * - 지연은 단순 Thread.sleep 기반이며, 인터럽트 시 false 반환
     * - 워터마크(>0) 이상이면 즉시 false
     *
     * @param job         추가할 작업 (null이면 false)
     * @param delayMillis 추가 전 대기할 밀리초(0 이하면 즉시 시도)
     * @return 삽입 성공 여부
     */
    public boolean addJobInQueue(Job job, long delayMillis) {
        if (job == null) return false;

        // 요청된 지연이 있으면 수행 (인터럽트 발생 시 인터럽트 상태 복원 + 실패 반환)
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        lock.lock();
        try {
            // 워터마크가 설정되어 있고 가득 차면 비차단 정책에 따라 즉시 실패
            if (watermark > 0 && queue.size() >= watermark) return false;

            // ArrayDeque.offerLast는 용량 제한이 없으므로 항상 true 반환
            boolean ok = queue.offerLast(job);

            // 소비자(반출 대기 중)가 있을 수 있으므로 알림 (필수는 아님)
            if (ok) notEmpty.signal();
            return ok;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 작업을 큐에 추가 (랜덤 지연 후 시도, 비차단)
     * - minDelay/maxDelay는 음수일 수 없음. 음수 입력 시 0으로 보정
     * - minDelay > maxDelay면 두 값을 교환하여 보정
     * - 워터마크(>0) 이상이면 즉시 false
     *
     * @param job      추가할 작업 (null이면 false)
     * @param minDelay 최소 지연(ms)
     * @param maxDelay 최대 지연(ms)
     * @return 삽입 성공 여부
     */
    public boolean addJobInQueue(Job job, long minDelay, long maxDelay) {
        if (job == null) return false;

        // 경계값 방어: 음수 → 0, 구간 역전 시 스왑
        long min = Math.max(0L, minDelay);
        long max = Math.max(0L, maxDelay);
        if (min > max) {
            long tmp = min;
            min = max;
            max = tmp;
        }

        // min==max인 경우에도 nextLong은 문제 없음(고정 지연)
        long delay = ThreadLocalRandom.current().nextLong(min, max + 1);

        try {
            if (delay > 0) Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        lock.lock();
        try {
            if (watermark > 0 && queue.size() >= watermark) return false;
            boolean ok = queue.offerLast(job);
            if (ok) notEmpty.signal();
            return ok;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 작업을 큐에 추가 (차단형 + 지연 지원)
     * - 삽입 전 delayMillis 만큼 대기
     * - 큐가 워터마크(>0)에 도달해 가득 찼다면, 여유가 생길 때까지 조건 변수에서 await
     * - spinWaitMillis <= 0 : 무기한 대기
     * - spinWaitMillis  > 0 : 해당 간격으로 타임드 대기/재확인(폴링 유사)
     *
     * @param job            추가할 Job (null이면 false)
     * @param delayMillis    삽입 전 대기(ms). 0 이하면 바로 시도
     * @param spinWaitMillis 큐가 가득 찼을 때 대기 간격(ms). 0 이하면 무기한 대기
     * @return 삽입 성공 여부
     */
    public boolean addJobInQueueBlocking(Job job, long delayMillis, long spinWaitMillis) {
        if (job == null) return false;

        // --- 삽입 전 지연 ---
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        lock.lock();
        try {
            // 무제한 모드: 대기 없이 즉시 삽입
            if (watermark == 0) {
                boolean ok = queue.offerLast(job);
                if (ok) notEmpty.signal();
                return ok;
            }

            try {
                if (spinWaitMillis <= 0) {
                    // 무기한 대기: 큐에 여유가 생길 때까지 대기
                    while (queue.size() >= watermark) {
                        notFull.await();
                    }
                } else {
                    // 타임드 대기: 일정 간격마다 깨어나 상태 재확인
                    long nanos = TimeUnit.MILLISECONDS.toNanos(spinWaitMillis);
                    while (queue.size() >= watermark) {
                        long left = notFull.awaitNanos(nanos);
                        // left는 사용하지 않고 주기적 재검사만 수행
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }

            // 여유 확보 후 삽입
            boolean ok = queue.offerLast(job);
            if (ok) notEmpty.signal();
            return ok;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 여러 작업을 입력 순서대로 큐에 추가 (비차단 일괄)
     * - null 항목은 무시
     * - 워터마크(>0) 도달 시 추가 중단
     * - 하나라도 추가되면 true
     *
     * @param jobs 추가할 작업 목록
     * @return 하나라도 추가되면 true, 아니면 false
     */
    public boolean addJobsInQueue(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) return false;

        lock.lock();
        try {
            boolean added = false;

            for (Job job : jobs) {
                if (job == null) continue;

                // 워터마크가 설정되어 있고 가득 찼다면 더 이상 추가하지 않음
                if (watermark > 0 && queue.size() >= watermark) break;

                queue.offerLast(job);
                added = true;
            }

            // 일부라도 추가되었다면 소비자 깨움
            if (added) notEmpty.signalAll();
            return added;
        } finally {
            lock.unlock();
        }
    }

    // ----------------------------------------------------------------------
    // 조회(읽기) 계열
    // ----------------------------------------------------------------------

    /**
     * 큐의 맨 앞(HEAD)을 조회 (삭제하지 않음)
     * - 성공 시 마지막 조회 값(lastViewed)에 보관
     *
     * @return 요소가 있으면 true, 없으면 false
     */
    public boolean topJobInQueue() {
        lock.lock();
        try {
            lastViewed = queue.peekFirst();
            return lastViewed != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 큐의 맨 뒤(TAIL)를 조회 (삭제하지 않음)
     * - 성공 시 마지막 조회 값(lastViewed)에 보관
     *
     * @return 요소가 있으면 true, 없으면 false
     */
    public boolean lastJobInQueue() {
        lock.lock();
        try {
            lastViewed = queue.peekLast();
            return lastViewed != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 0 기반 인덱스로 요소를 조회 (삭제하지 않음)
     * - ArrayDeque는 인덱스 접근이 O(1)이 아니므로 선형 탐색
     * - 성공 시 마지막 조회 값(lastViewed)에 보관
     *
     * @param index 0 이상 정수 (int 범위 초과 불가)
     * @return 요소가 있으면 true, 없으면 false
     */
    public boolean getJobInQueue(long index) {
        if (index < 0 || index > Integer.MAX_VALUE) return false;

        lock.lock();
        try {
            int i = 0;
            for (Job j : queue) {
                if (i == (int) index) {
                    lastViewed = j;
                    return true;
                }
                i++;
            }
            // 범위를 벗어난 경우 null로 초기화
            lastViewed = null;
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 직전 조회(top/last/get) 성공 시점의 Job 반환
     * - 조회 실패 직후에는 null
     * - 읽기 전용 반환이므로 외부에서 불변으로 취급해야 함
     *
     * @return 마지막 조회 성공한 Job 또는 null
     */
    public Job getLastViewed() {
        lock.lock();
        try {
            return lastViewed;
        } finally {
            lock.unlock();
        }
    }

    // ----------------------------------------------------------------------
    // 삭제/반출(쓰기) 계열
    // ----------------------------------------------------------------------

    /**
     * 큐의 맨 앞(HEAD)을 삭제 (반환 없음)
     * - 요소가 실제로 제거되면 notFull.signal()로 생산자 깨움
     *
     * @return 삭제 성공 여부
     */
    public boolean deleteJobInQueue() {
        lock.lock();
        try {
            boolean removed = (queue.pollFirst() != null);
            if (removed) {
                // 여유가 생겼으니 생산자(추가 대기 중) 하나를 깨움
                notFull.signal();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 큐의 모든 작업을 삭제 (초기화)
     * - 기존에 하나 이상 존재했다면 true
     * - 대기 중인 생산자가 많을 수 있으므로 signalAll
     *
     * @return 초기화로 인해 실제로 비워졌다면 true, 이미 비어있었으면 false
     */
    public boolean clearQueue() {
        lock.lock();
        try {
            if (queue.isEmpty()) return false;

            queue.clear();
            lastViewed = null;

            // 여유가 크게 생겼으므로 대기 중 생산자 전부 깨움
            notFull.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 큐의 맨 앞(HEAD)을 팝(pop)하여 반환 (삭제하며 반환)
     * - 실제 요소가 제거되면 notFull.signal()로 생산자 깨움
     *
     * @return 제거된 Job, 없으면 null
     */
    public Job exportJobFromQueue() {
        lock.lock();
        try {
            Job j = queue.pollFirst();
            if (j != null) {
                // 여유 발생 알림
                notFull.signal();
            }
            return j;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 큐에서 작업을 '반출(pop)'하는 블로킹 메서드(무기한 대기).
     * - 큐가 비어 있으면 요소가 들어올 때까지 notEmpty.await()로 대기
     * - 스레드 인터럽트 시 즉시 종료하고 null 반환(인터럽트 상태 복원)
     * - 요소를 꺼낸 뒤에는 notFull.signal()로 대기 중 생산자 깨움
     *
     * @return 꺼낸 Job, 인터럽트 발생 시 null
     */
    public Job exportJobFromQueueBlocking() {
        try {
            return exportJobFromQueueBlocking(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 위 오버로드는 InterruptedException을 던지지만, 여기서는 일관성 있게 null 반환
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 큐에서 작업을 '반출(pop)'하는 블로킹 메서드(타임아웃 지원).
     * - timeout <= 0 이면 무기한 대기
     * - 대기 중 인터럽트 발생 시 InterruptedException을 던짐(호출 측에서 정책 선택 가능)
     * - 요소를 꺼낸 뒤에는 notFull.signal()로 대기 중 생산자 깨움
     * <p>
     * 사용 예)
     * Job j = queue.exportJobFromQueueBlocking(500, TimeUnit.MILLISECONDS); // 500ms 내에 없으면 null
     *
     * @param timeout 최대 대기 시간
     * @param unit    시간 단위
     * @return 꺼낸 Job, 타임아웃 시 null
     * @throws InterruptedException 대기 중 인터럽트되면 발생
     */
    public Job exportJobFromQueueBlocking(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = (timeout <= 0) ? Long.MAX_VALUE : unit.toNanos(timeout);

        // 인터럽트 대응을 위해 lockInterruptibly 사용
        lock.lockInterruptibly();
        try {
            // 큐가 비어 있으면 notEmpty에서 대기
            while (queue.isEmpty()) {
                if (nanos == Long.MAX_VALUE) {
                    // 무기한 대기
                    notEmpty.await(); // spurious wakeup 대비 while 루프
                } else {
                    if (nanos <= 0) {
                        // 타임아웃
                        return null;
                    }
                    nanos = notEmpty.awaitNanos(nanos); // 깨어나면 남은 시간으로 재대기
                }
            }

            // 여기까지 왔다는 것은 큐가 비어있지 않음 → 안전하게 poll
            Job j = queue.pollFirst();

            // 요소를 하나 빼냈으니 생산자(추가 대기) 깨우기
            notFull.signal();

            return j;
        } finally {
            lock.unlock();
        }
    }

    // ----------------------------------------------------------------------
    // 상태/보조
    // ----------------------------------------------------------------------

    /**
     * 현재 큐 크기 반환
     * - 락으로 보호하여 일관된 값을 제공
     *
     * @return 현재 요소 수
     */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 큐가 비어있는지 여부
     *
     * @return 비었으면 true
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * 현재 큐가 워터마크 이상(가득 참)인지 여부
     * - 워터마크가 0(무제한)인 경우 항상 false
     *
     * @return 큐 크기 >= 워터마크이면 true
     */
    public boolean isAboveWatermark() {
        lock.lock();
        try {
            return watermark > 0 && queue.size() >= watermark;
        } finally {
            lock.unlock();
        }
    }
}