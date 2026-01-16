package com.telcoware.job_worker_app_demo.util;

/**
 * packageName    : com.telcoware.job_worker_app_demo.util
 * fileName       : TextUtil
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : TEXT 유틸 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
public class TextUtil {

    private TextUtil() {}

    /**
     * snake_case 문자열을 PascalCase 문자열로 변환
     * ex) hello_world -> HelloWorld
     */
    public static String snakeToPascal(String snake) {
        if (snake == null || snake.isEmpty()) {
            return snake;
        }

        StringBuilder result = new StringBuilder();
        boolean toUpper = true;

        for (char c : snake.toCharArray()) {
            if (c == '_') {
                toUpper = true; // 다음 문자를 대문자로
            } else {
                if (toUpper) {
                    result.append(Character.toUpperCase(c));
                    toUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }

        return result.toString();
    }

}
