package com.telcoware.job_worker_app_demo.util;

import lombok.extern.slf4j.Slf4j;

/**
 * packageName    : com.telcoware.job_worker_app_demo.util
 * fileName       : ProcessUtil
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : 프로세스 유틸 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Slf4j
public class ProcessUtil {

    private ProcessUtil() {
    }

    public static long getPid() {
        return ProcessHandle.current().pid();
    }

}
