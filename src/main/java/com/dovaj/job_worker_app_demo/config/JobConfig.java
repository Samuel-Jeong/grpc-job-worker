package com.dovaj.job_worker_app_demo.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * packageName    : com.sks.wpm.job.config
 * fileName       : JobConfig
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : JOB 설정
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Getter
@Configuration
public class JobConfig {

    @Value("${job.worker.thread-pool.core-size}")
    private Integer jobWorkerCorePoolSize;

    @Value("${job.worker.thread-pool.max-size}")
    private Integer jobWorkerMaxPoolSize;

    @Value("${job.worker.thread-pool.queue-capacity}")
    private Integer jobWorkerQueueCapacity;

    @Value("${job.worker.thread-pool.watermark}")
    private Integer jobWorkerWatermark;

    @Bean
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        return new ThreadPoolTaskExecutorBuilder()
                .maxPoolSize(jobWorkerMaxPoolSize)
                .corePoolSize(jobWorkerCorePoolSize)
                .queueCapacity(jobWorkerQueueCapacity)
                .threadNamePrefix("WORKER-Runner-")
                .build();
    }

}
