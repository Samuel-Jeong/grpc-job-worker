package com.telcoware.job_worker_app_demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * packageName    : com.telcoware.job_worker_app_demo.service.system
 * fileName       : MonitoringSystemProperties
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 시스템 모니터링 설정 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitoring.system")
public class MonitoringSystemProperties {

    /**
     * 수집 주기(ms). 스케줄링 미사용 시 무시됨
     */
    private long sampleIntervalMs = 5000;

    /**
     * CPU 사용률 경고/치명 임계(%)
     */
    private double cpuWarn = 80.0;
    private double cpuCrit = 90.0;

    /**
     * 시스템 메모리 사용률 경고/치명 임계(%)
     */
    private double memWarn = 80.0;
    private double memCrit = 90.0;

    /**
     * 디스크 사용률 경고/치명 임계(%)
     */
    private double diskWarn = 80.0;
    private double diskCrit = 90.0;

    /**
     * 스케줄러 사용 여부
     */
    private boolean enableScheduler = true;

    private double jvmWarn = 80.0;
    private double jvmCrit = 90.0;

}