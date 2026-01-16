package com.dovaj.job_worker_app_demo.service.job;

import com.dovaj.job_worker_app_demo.job.dto.inf.JobInfo;
import com.dovaj.job_worker_app_demo.util.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * packageName    : com.dovaj.job_worker_app_demo.service.job
 * fileName       : JobInfoReflectionFactoryService
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 작업 정보 리플렉션 팩토리 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobInfoReflectionFactoryService {

    private <X extends JobInfo<?>> Class<X> asJobInfoSubclass(Class<?> raw) {
        @SuppressWarnings("unchecked")
        Class<X> sub = (Class<X>) raw.asSubclass(JobInfo.class);
        return sub;
    }

    @SuppressWarnings("unchecked")
    public <T, J extends JobInfo<T>> J newJobInfo(String className, Object... args) {
        try {
            Class<?> raw = Class.forName(className);
            Class<? extends JobInfo<?>> sub = asJobInfoSubclass(raw);
            Object obj = ReflectionUtil.newInstance(className, args);
            return (J) sub.cast(obj);
        } catch (ClassNotFoundException e) {
            log.warn("클래스를 찾을 수 없음 - className={} ({})", className, e.getMessage());
            return null;
        } catch (ClassCastException e) {
            log.warn("JobInfo 서브타입이 아님 - className={} ({})", className, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("알 수 없는 오류 - className={} ({})", className, e.getMessage());
            return null;
        }
    }

}
