package com.telcoware.job_worker_app_demo.service.system;

import com.sun.management.OperatingSystemMXBean;
import com.telcoware.job_worker_app_demo.config.MonitoringSystemProperties;
import com.telcoware.job_worker_app_demo.data.dto.system.SystemMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * packageName    : com.telcoware.job_worker_app_demo.service.system
 * fileName       : SystemResourceMonitoringService
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 시스템 리소스 사용량 모니터링 서비스 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "monitoring.system.enable-scheduler",
        havingValue = "true"
)
public class SystemResourceMonitoringService {

    private final MonitoringSystemProperties monitoringSystemProperties;

    // 델타 계산용 상태(프로세스 CPU)
    private long prevProcCpuTimeNanos = -1L;
    private long prevSampleTimeNanos = -1L;

    // 리눅스 시스템 CPU 계산용(/proc/stat)
    private long prevStatTotal = -1L;
    private long prevStatIdle = -1L;

    private record ProcStat(long total, long idle) {
    }

    private ProcStat readLinuxProcStat() {
        File f = new File("/proc/stat");
        if (!f.exists()) return null;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;
            // cpu  user nice system idle iowait irq softirq steal [guest guest_nice]
            String[] t = line.trim().split("\\s+");
            long user = Long.parseLong(t[1]);
            long nice = Long.parseLong(t[2]);
            long system = Long.parseLong(t[3]);
            long idle = Long.parseLong(t[4]);
            long iowait = t.length > 5 ? Long.parseLong(t[5]) : 0L;
            long irq = t.length > 6 ? Long.parseLong(t[6]) : 0L;
            long softirq = t.length > 7 ? Long.parseLong(t[7]) : 0L;
            long steal = t.length > 8 ? Long.parseLong(t[8]) : 0L;

            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            long idleAll = idle + iowait;
            return new ProcStat(total, idleAll);
        } catch (Exception e) {
            return null;
        }
    }

    private static double toPercent(double ratio) {
        if (Double.isNaN(ratio) || ratio < 0) return -1.0;
        double p = ratio * 100.0;
        return p > 100.0 ? 100.0 : Math.max(0.0, p);
    }

    /**
     * 지금 즉시 시스템 지표 수집
     */
    public SystemMetrics collect() {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        // CPU
        final int cpuCores = Runtime.getRuntime().availableProcessors();
        final long nowNanos = System.nanoTime();

        // 1) 프로세스 CPU: 누적 ns 기반 델타 계산
        long procCpuTimeNanos = os.getProcessCpuTime(); // 누적 프로세스 CPU(ns)
        double procCpuPercent;
        if (prevProcCpuTimeNanos >= 0 && prevSampleTimeNanos >= 0) {
            long procDelta = Math.max(0L, procCpuTimeNanos - prevProcCpuTimeNanos);
            long timeDelta = Math.max(1L, nowNanos - prevSampleTimeNanos);
            double ratio = (double) procDelta / (double) (timeDelta * cpuCores);
            procCpuPercent = toPercent(ratio);
        } else {
            procCpuPercent = -1.0; // 첫 샘플은 기준 없음
        }

        // 2) 시스템 CPU: 리눅스면 /proc/stat 델타, 아니면 폴백 시도
        double sysCpuPercent;
        ProcStat stat = readLinuxProcStat();
        if (stat != null) {
            if (prevStatTotal >= 0 && prevStatIdle >= 0) {
                long totalDelta = Math.max(0L, stat.total - prevStatTotal);
                long idleDelta = Math.max(0L, stat.idle - prevStatIdle);
                long usedDelta = Math.max(0L, totalDelta - idleDelta);
                double ratio = totalDelta == 0 ? 0.0 : ((double) usedDelta / (double) totalDelta);
                sysCpuPercent = toPercent(ratio);
            } else {
                sysCpuPercent = -1.0; // 첫 샘플은 기준 없음
            }
        } else {
            // 폴백: 존재하면 getCpuLoad() 사용(없으면 -1 처리)
            try {
                double load = (double) OperatingSystemMXBean.class.getMethod("getCpuLoad").invoke(os);
                sysCpuPercent = toPercent(load);
            } catch (Throwable ignore) {
                sysCpuPercent = -1.0;
            }
        }

        // 상태 업데이트
        prevProcCpuTimeNanos = procCpuTimeNanos;
        prevSampleTimeNanos = nowNanos;
        if (stat != null) {
            prevStatTotal = stat.total;
            prevStatIdle = stat.idle;
        }

        double loadAvg = os.getSystemLoadAverage();

        SystemMetrics.Cpu cpu = SystemMetrics.Cpu.builder()
                .systemCpuLoadPercent(sysCpuPercent)
                .processCpuLoadPercent(procCpuPercent)
                .systemLoadAverage1m(loadAvg)
                .build();

        // Physical Memory (OS)
        long totalPhysical = os.getTotalMemorySize();
        long freePhysical = os.getFreeMemorySize();
        long usedPhysical = Math.max(0, totalPhysical - freePhysical);
        double usedPhysicalPct = pct(usedPhysical, totalPhysical);

        SystemMetrics.Memory memory = SystemMetrics.Memory.builder()
                .totalPhysical(totalPhysical)
                .freePhysical(freePhysical)
                .usedPhysical(usedPhysical)
                .usedPercent(usedPhysicalPct)
                .build();

        // JVM Memory
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        long heapUsed = heap.getUsed();
        long heapCommitted = heap.getCommitted();
        long heapMax = heap.getMax();
        long heapDenom = heapMax > 0 ? heapMax : heapCommitted; // 폴백
        double heapUsedPct = pct(heapUsed, heapDenom);

        SystemMetrics.Jvm jvm = SystemMetrics.Jvm.builder()
                .heapUsed(heapUsed)
                .heapCommitted(heapCommitted)
                .heapMax(heapMax)
                .nonHeapUsed(nonHeap.getUsed())
                .nonHeapCommitted(nonHeap.getCommitted())
                .threadCount(threadBean.getThreadCount())
                .heapUsedPercent(heapUsedPct)
                .build();

        // Disks (루트별)
        File[] roots = File.listRoots();
        List<SystemMetrics.Disk> disks = new ArrayList<>();
        if (roots != null) {
            for (File r : roots) {
                long total = safeTotalSpace(r);
                long usable = safeUsableSpace(r);
                long used = Math.max(0, total - usable);
                double usedPct = pct(used, total);
                disks.add(SystemMetrics.Disk.builder()
                        .mount(r.getAbsolutePath())
                        .type("unknown") // 표준 JDK로는 타입 식별 한계. 필요시 OSHI 등 라이브러리 사용.
                        .total(total)
                        .usable(usable)
                        .used(used)
                        .usedPercent(usedPct)
                        .build());
            }
        }

        return SystemMetrics.builder()
                .timestamp(Instant.now())
                .cpu(cpu)
                .memory(memory)
                .jvm(jvm)
                .disks(disks)
                .build();
    }

    /**
     * 스케줄러: 임계치 초과 시 경고/에러 로그
     */
    @Scheduled(fixedRateString = "${monitoring.system.sample-interval-ms}")
    public void sampleAndLog() {
        if (!monitoringSystemProperties.isEnableScheduler()) return;

        SystemMetrics systemMetrics = collect();

        // 한 줄 요약 문자열
        String line = summarize(systemMetrics);

        // 최대 심각도 계산 후 레벨 한 번만 로깅
        Severity sev = evaluateSeverity(systemMetrics);
        switch (sev) {
            case CRIT -> log.error("[MON][CRIT] {}", line);
            case WARN -> log.warn("[MON][WARN] {}", line);
            default -> log.info("[MON][OK]   {}", line);
        }

        // 상세 필드나 중간값을 보고 싶으면 debug로만 보조 출력
        if (log.isDebugEnabled()) {
            log.debug("[MON][DETAIL] {}", line);
        }
    }

    // ===== 내부 유틸 =====

    private static double safePercent(double v) {
        // com.sun.management 값은 0.0~1.0 또는 미지원 시 음수
        if (v < 0) return -1.0;
        return Math.min(100.0, Math.max(0.0, v * 100.0));
    }

    private static double pct(long part, long total) {
        if (total <= 0) return -1.0;
        return (part * 100.0) / total;
    }

    private static long safeTotalSpace(File f) {
        try {
            return f.getTotalSpace();
        } catch (Exception e) {
            return -1L;
        }
    }

    private static long safeUsableSpace(File f) {
        try {
            return f.getUsableSpace();
        } catch (Exception e) {
            return -1L;
        }
    }

    private void warnIfExceed(String name, double valuePercent, double warn, double crit) {
        if (valuePercent < 0) {
            log.debug("[MON] {}: N/A", name);
            return;
        }
        if (valuePercent >= crit) {
            log.error("[MON][CRIT] {}: {}% (>= {}%)", name, round1(valuePercent), crit);
        } else if (valuePercent >= warn) {
            log.warn("[MON][WARN] {}: {}% (>= {}%)", name, round1(valuePercent), warn);
        } else {
            log.info("[MON][OK]   {}: {}%", name, round1(valuePercent));
        }
    }

    private static String summarize(SystemMetrics systemMetrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("ts=").append(systemMetrics.getTimestamp()).append(' ');
        sb.append("cpu(sys,proc,load1m)=")
                .append(round1(systemMetrics.getCpu().getSystemCpuLoadPercent())).append('%').append(',')
                .append(round1(systemMetrics.getCpu().getProcessCpuLoadPercent())).append('%').append(',')
                .append(systemMetrics.getCpu().getSystemLoadAverage1m()).append(' ');
        sb.append("mem(used%,used/total)=")
                .append(round1(systemMetrics.getMemory().getUsedPercent())).append("%,")
                .append(systemMetrics.getMemory().getUsedPhysical()).append('/')
                .append(systemMetrics.getMemory().getTotalPhysical()).append(' ');
        sb.append("jvm(heapUsed%,heapUsed/heapMax,threads)=")   // << 라벨 변경
                .append(round1(systemMetrics.getJvm().getHeapUsedPercent())).append("%,")   // << 사용률 표시
                .append(systemMetrics.getJvm().getHeapUsed()).append('/')
                .append(systemMetrics.getJvm().getHeapMax()).append(',')
                .append(systemMetrics.getJvm().getThreadCount()).append(' ');
        sb.append("disks=");
        for (SystemMetrics.Disk d : systemMetrics.getDisks()) {
            sb.append('[').append(d.getMount()).append(' ')
                    .append(round1(d.getUsedPercent())).append("% ")
                    .append(d.getUsed()).append('/').append(d.getTotal()).append(']');
        }
        return sb.toString();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private enum Severity {OK, WARN, CRIT}

    private Severity severityFor(double valuePercent, double warn, double crit) {
        // N/A(-1)은 판정 제외 → OK로 간주
        if (valuePercent < 0) return Severity.OK;
        if (valuePercent >= crit) return Severity.CRIT;
        if (valuePercent >= warn) return Severity.WARN;
        return Severity.OK;
    }

    private Severity max(Severity a, Severity b) {
        if (a == Severity.CRIT || b == Severity.CRIT) return Severity.CRIT;
        if (a == Severity.WARN || b == Severity.WARN) return Severity.WARN;
        return Severity.OK;
    }

    /**
     * 수집된 메트릭 전체에서 최대 심각도 계산
     */
    private Severity evaluateSeverity(SystemMetrics systemMetrics) {
        Severity s = Severity.OK;

        // CPU
        s = max(s, severityFor(systemMetrics.getCpu().getSystemCpuLoadPercent(),
                monitoringSystemProperties.getCpuWarn(), monitoringSystemProperties.getCpuCrit()));
        s = max(s, severityFor(systemMetrics.getCpu().getProcessCpuLoadPercent(),
                monitoringSystemProperties.getCpuWarn(), monitoringSystemProperties.getCpuCrit()));

        // Memory (물리)
        s = max(s, severityFor(systemMetrics.getMemory().getUsedPercent(),
                monitoringSystemProperties.getMemWarn(), monitoringSystemProperties.getMemCrit()));

        // JVM Heap
        s = max(s, severityFor(systemMetrics.getJvm().getHeapUsedPercent(),
                monitoringSystemProperties.getJvmWarn(), monitoringSystemProperties.getJvmCrit()));

        // Disks
        for (SystemMetrics.Disk d : systemMetrics.getDisks()) {
            s = max(s, severityFor(d.getUsedPercent(),
                    monitoringSystemProperties.getDiskWarn(), monitoringSystemProperties.getDiskCrit()));
        }
        return s;
    }

}
