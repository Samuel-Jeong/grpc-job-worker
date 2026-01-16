package com.dovaj.job_worker_app_demo.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * packageName    : com.capshome.iotgw.utils
 * fileName       : LocalDateTimeSerializer
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : Gson 라이브러리에서 사용할 LocalDateTime 객체 직렬화 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
public class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime> {

    public static final String DATE_FORMAT = "DATE_FORMAT({0}, '%Y-%m-%d %H:%i:%s')";

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public JsonElement serialize(LocalDateTime localDateTime, Type srcType, JsonSerializationContext context) {
        return new JsonPrimitive(FORMATTER.format(localDateTime));
    }

}
