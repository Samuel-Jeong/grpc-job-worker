package com.dovaj.job_worker_app_demo.data.dto.system;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.system
 * fileName       : SystemMetrics
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 시스템 메트릭스 정보 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Value
@Builder
public class SystemMetrics {
    Instant timestamp;

    Cpu cpu;
    Memory memory;
    Jvm jvm;
    List<Disk> disks;

    @Value
    @Builder
    public static class Cpu {
        /**
         * 0~100 (%)
         */
        double systemCpuLoadPercent;
        /**
         * 0~100 (%)
         */
        double processCpuLoadPercent;
        /**
         * 1,5,15분 LoadAvg (OS별 비가용 가능)
         */
        double systemLoadAverage1m;
    }

    @Value
    @Builder
    public static class Memory {
        /**
         * 바이트
         */
        long totalPhysical;
        long freePhysical;
        long usedPhysical;
        /**
         * 0~100 (%)
         */
        double usedPercent;
    }

    @Value
    @Builder
    public static class Jvm {
        long heapUsed;
        long heapCommitted;
        long heapMax;
        long nonHeapUsed;
        long nonHeapCommitted;
        int threadCount;
        /**
         * 0~100 (%)
         */
        double heapUsedPercent;
    }

    @Value
    @Builder
    public static class Disk {
        String mount;
        String type;
        long total;
        long usable;
        long used;
        /**
         * 0~100 (%)
         */
        double usedPercent;
    }
}