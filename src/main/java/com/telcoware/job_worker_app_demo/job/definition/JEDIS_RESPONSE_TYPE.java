package com.telcoware.job_worker_app_demo.job.definition;

import lombok.Getter;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.definition
 * fileName       : JEDIS_RESPONSE_TYPE
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : Jedis 응답 유형 enum 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Getter
public enum JEDIS_RESPONSE_TYPE {

    OK("OK"),
    PONG("PONG"),
    QUEUED("QUEUED"),

    ;

    private final String value;

    JEDIS_RESPONSE_TYPE(String value) {
        this.value = value;
    }

}
