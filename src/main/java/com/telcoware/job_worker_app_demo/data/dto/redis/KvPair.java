package com.telcoware.job_worker_app_demo.data.dto.redis;

/**
 * packageName    : com.telcoware.job_worker_app_demo.data.dto.redis
 * fileName       : KvPair
 * author         : samuel
 * date           : 25. 10. 21.
 * description    : Redis KeyValue Pair class
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 21.        samuel       최초 생성
 */
public record KvPair(String key, String value) {
}
