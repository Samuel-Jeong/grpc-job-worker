package com.telcoware.job_worker_app_demo.data.definition;

import lombok.Getter;

/**
 * packageName    : com.telcoware.job_worker_app_demo.data.definition
 * fileName       : STRING_CONSTANTS
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 문자열 상수 enum 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Getter
public enum STRING_CONSTANTS {

    WORKER_PREFIX("wpm-apps:"),
    WORKER_PREFIX_PATTERN("wpm-apps:*"),
    JOB_PREFIX("job:"),
    JOB_PREFIX_PATTERN("job:*"),

    ;

    private final String value;

    STRING_CONSTANTS(String value) {
        this.value = value;
    }

}
