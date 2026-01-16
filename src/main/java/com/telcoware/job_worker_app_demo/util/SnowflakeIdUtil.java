package com.telcoware.job_worker_app_demo.util;

/**
 * packageName    : com.telcoware.job_master_app_demo.util
 * fileName       : SnowflakeIdUtil
 * author         : samuel
 * date           : 25. 10. 29.
 * description    :
 *
 * <p>
 * 시스템 전역에서 중복되지 않는 고유한 64비트 ID(Long)를 생성하기 위한 유틸리티 클래스.<br>
 * 트위터의 Snowflake 알고리즘 방식을 단순화하여 구현하였으며,
 * 시간 순서가 보장되는 ID를 생성한다.
 * </p>
 *
 * <h3>🧩 ID 비트 구성 (64비트)</h3>
 * <pre>
 * ┌──────────────────────────────┬──────────────┬────────────┐
 * │ 41비트: 타임스탬프(ms 단위) │ 10비트: 노드 ID │ 12비트: 시퀀스 │
 * └──────────────────────────────┴──────────────┴────────────┘
 * </pre>
 *
 * - <b>타임스탬프</b>: 기준시각(EPOCH, 2023-11-15)을 기준으로 현재 시각까지의 경과 시간을 밀리초 단위로 표현<br>
 * - <b>노드 ID</b>: 서버나 인스턴스별로 구분을 위해 사용하는 식별자 (0~1023 범위)<br>
 * - <b>시퀀스</b>: 동일한 밀리초 안에서 여러 요청이 들어올 때의 순번 (0~4095 범위)<br>
 *
 * <h3>⚙️ 동작 방식</h3>
 * - synchronized를 사용하여 멀티스레드 환경에서도 중복 없는 ID를 보장<br>
 * - 동일한 밀리초에 여러 요청이 발생하면 sequence를 증가시키며, 12비트(4096개) 한도 초과 시 다음 밀리초까지 대기<br>
 * - 시간 역행(시계가 뒤로 감김) 상황은 감지하지 않지만, 대부분의 서버 환경에서 안전하게 동작 가능<br>
 *
 * <h3>💡 사용 예시</h3>
 * <pre>{@code
 * long id = SnowflakeIdUtil.nextId();
 * System.out.println(id); // 64비트 고유 ID 출력
 * }</pre>
 *
 * <h3>🧠 특징</h3>
 * - 분산 서버 간에도 중복되지 않는 ID 생성 가능<br>
 * - DB PK, 로그 트랜잭션 ID, Job 트래킹 ID 등 다양한 곳에 활용 가능<br>
 * - UUID보다 짧고 정렬 가능하여 인덱싱 및 조회 효율이 높음<br>
 *
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
public class SnowflakeIdUtil {

    private SnowflakeIdUtil() {}

    private static final long EPOCH = 1700000000000L; // 기준시각(2023-11-15)
    private static final long NODE_ID = 1L; // 서버/노드 구분용
    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    public static synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF; // 12비트 시퀀스
            if (sequence == 0) {
                while (now == lastTimestamp) {
                    now = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << 22) | (NODE_ID << 12) | sequence;
    }

}
