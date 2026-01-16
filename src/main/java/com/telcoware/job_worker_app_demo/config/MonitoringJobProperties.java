package com.telcoware.job_worker_app_demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * packageName    : com.telcoware.job_worker_app_demo.service.system
 * fileName       : MonitoringJobProperties
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 작업 모니터링 설정 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "monitoring.job")
public class MonitoringJobProperties {

    /**
     * 스케줄러 사용 여부
     */
    private boolean enableScheduler = true;

}