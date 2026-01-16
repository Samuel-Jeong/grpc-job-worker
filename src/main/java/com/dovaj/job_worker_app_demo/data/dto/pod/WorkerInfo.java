package com.dovaj.job_worker_app_demo.data.dto.pod;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * packageName    : com.dovaj.job_worker_app_demo.data.dto.pod
 * fileName       : WorkerInfo
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : WORKER 정보
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
@Data
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class WorkerInfo {

    private int grpcPort;
    private Short status;
    private LocalDateTime updateDatetime;

}
