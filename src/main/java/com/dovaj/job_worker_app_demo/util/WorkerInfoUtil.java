package com.dovaj.job_worker_app_demo.util;

import com.dovaj.job_worker_app_demo.data.definition.WORKER_STATUS_TYPE;
import com.dovaj.job_worker_app_demo.data.dto.pod.WorkerInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.dovaj.job_worker_app_demo.data.definition.STRING_CONSTANTS.WORKER_PREFIX;

/**
 * packageName    : com.dovaj.job_worker_app_demo.util
 * fileName       : WorkerInfoUtil
 * author         : samuel
 * date           : 25. 10. 22.
 * description    : WORKER 정보 유틸 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 22.        samuel       최초 생성
 */
@Slf4j
public class WorkerInfoUtil {

    private WorkerInfoUtil() {
    }

    public static String makeWorkerId(String ip, long pid) {
        return WORKER_PREFIX.getValue() + ip + ":" + pid;
    }

    public static WorkerInfo makeWorkerInfo(int grpcPort, WORKER_STATUS_TYPE podStatus) {
        return WorkerInfo.builder()
                .grpcPort(grpcPort)
                .status(podStatus.getValue())
                .updateDatetime(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
                .build();
    }

}
