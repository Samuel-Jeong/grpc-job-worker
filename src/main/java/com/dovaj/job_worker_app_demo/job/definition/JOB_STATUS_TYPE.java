package com.dovaj.job_worker_app_demo.job.definition;

import lombok.Getter;

/**
 * packageName    : com.dovaj.job_worker_app_demo.job.definition
 * fileName       : JOB_STATUS_TYPE
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 작업 상태 유형 enum 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Getter
public enum JOB_STATUS_TYPE {

    HODLING((short) 0, "holding"),
    ALLOCATED((short) 1, "allocated"),
    RUNNING((short) 2, "running"),
    CANCELLED((short) 3, "cancelled"),
    SUCCESS((short) 4, "success"),
    FAILED((short) 5, "failed"),

    ;

    private final Short code;
    private final String name;

    JOB_STATUS_TYPE(Short code, String name) {
        this.code = code;
        this.name = name;
    }

}
