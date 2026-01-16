package com.telcoware.job_worker_app_demo.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * packageName    : com.telcoware.job_worker_app_demo.config
 * fileName       : SqsJobConfig
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : Schedule 설정
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Getter
@Configuration
public class ScheduleConfig {

    @Value("${schedule.application-info-report.thread.pool-size}")
    private Integer scheduleApplicationInfoReportThreadPoolSize;

    @Value("${schedule.application-info-report.thread.queue-size}")
    private Integer scheduleApplicationInfoReportThreadPoolQueueSize;

    @Value("${schedule.monitoring.thread.pool-size}")
    private Integer scheduleMonitoringThreadPoolSize;

    @Value("${schedule.monitoring.thread.queue-size}")
    private Integer scheduleMonitoringThreadPoolQueueSize;

}
