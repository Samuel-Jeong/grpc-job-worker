package com.telcoware.job_worker_app_demo;

import com.telcoware.job_worker_app_demo.config.MonitoringJobProperties;
import com.telcoware.job_worker_app_demo.config.MonitoringSystemProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        exclude = {
                // yidongnan server 자동 구성 관련 오토설정 제거
                net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration.class,
                net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration.class
        }
)
@EnableScheduling
@EnableConfigurationProperties({MonitoringSystemProperties.class, MonitoringJobProperties.class})
public class JobWorkerAppDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobWorkerAppDemoApplication.class, args);
    }

}
