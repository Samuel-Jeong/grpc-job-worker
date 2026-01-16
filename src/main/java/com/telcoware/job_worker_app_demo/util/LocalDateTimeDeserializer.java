package com.telcoware.job_worker_app_demo.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * packageName    : com.telcoware.job_worker_app_demo.util
 * fileName       : LocalDateTimeDeserializer
 * author         : samuel
 * date           : 25. 3. 19.
 * description    : LocalDateTime Deserializer Class
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 3. 19.        samuel       최초 생성
 */
public class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> implements com.google.gson.JsonDeserializer<LocalDateTime> {

    public static final DateTimeFormatter FORMATTER_1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter FORMATTER_2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    public static final DateTimeFormatter FORMATTER_3 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext context) throws IOException {
        return tryParse(p.getText());
    }

    @Override
    public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return tryParse(json.getAsString());
    }

    private LocalDateTime tryParse(String str) {
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{FORMATTER_1, FORMATTER_2, FORMATTER_3}) {
            try {
                return LocalDateTime.parse(str, formatter);
            } catch (Exception ignored) {
            }
        }
        throw new JsonParseException("형식이 맞지 않아 JSON 파싱에 실패 했습니다. : " + str);
    }

}
