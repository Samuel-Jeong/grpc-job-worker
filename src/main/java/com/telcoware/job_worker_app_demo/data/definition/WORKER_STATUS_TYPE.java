package com.telcoware.job_worker_app_demo.data.definition;

import lombok.Getter;

/**
 * packageName    : com.telcoware.job_worker_app_demo.job.definition
 * fileName       : WORKER_STATUS_TYPE
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : WORKER 상태 유형 enum 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Getter
public enum WORKER_STATUS_TYPE {

    IDLE((short) 0),
    BUSY((short) 1),

    ;

    private final Short value;

    WORKER_STATUS_TYPE(Short value) {
        this.value = value;
    }

}
