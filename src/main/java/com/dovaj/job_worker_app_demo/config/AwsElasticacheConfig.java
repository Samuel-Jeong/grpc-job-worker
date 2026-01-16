package com.dovaj.job_worker_app_demo.config;

import io.lettuce.core.RedisURI;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * packageName    : com.dovaj.job_worker_app_demo.config
 * fileName       : AwsElasticacheConfig
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : SQS JOB 설정
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Slf4j
@Getter
@Configuration
public class AwsElasticacheConfig {

    @Value("${aws.elasticache.valkey.endpoint.host}")
    private String awsElasticacheValkeyEndpointHost;

    @Value("${aws.elasticache.valkey.endpoint.port}")
    private Integer awsElasticacheValkeyEndpointPort;

    public RedisURI getEndpoint() {
        return RedisURI.create("rediss://" + awsElasticacheValkeyEndpointHost + ":" + awsElasticacheValkeyEndpointPort);
    }

}
