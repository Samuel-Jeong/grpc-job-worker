package com.dovaj.job_worker_app_demo.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;

/**
 * packageName    : com.capshome.iotgw.utils
 * fileName       : GsonUtil
 * author         : samuel
 * date           : 24. 8. 5.
 * description    : Gson 라이브러리 커스텀 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 24. 8. 5.        samuel       최초 생성
 */
@Slf4j
@Component
public class GsonUtil {

    private final Gson gsonPretty;
    private final Gson gsonNotPretty;
    private final Type listStringType = new TypeToken<List<String>>() {
    }.getType();

    public GsonUtil() {
        GsonBuilder gsonBuilderPretty = new GsonBuilder().setPrettyPrinting();
        this.gsonPretty = gsonBuilderPretty
                .disableHtmlEscaping()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .serializeNulls()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
                .create();
        GsonBuilder gsonBuilderNotPretty = new GsonBuilder();
        this.gsonNotPretty = gsonBuilderNotPretty
                .disableHtmlEscaping()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .serializeNulls()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
                .create();
    }

    public <T> T deserialize(JsonElement jsonElement, Type type) {
        try {
            return gsonPretty.fromJson(jsonElement, type);
        } catch (Exception e) {
            log.warn("deserialize json error", e);
            return null;
        }
    }

    public <T> T deserialize(String data, Class<T> type) {
        try {
            return gsonPretty.fromJson(data, type);
        } catch (Exception e) {
            log.warn("deserialize json error", e);
            return null;
        }
    }

    public String serialize(Object object) {
        try {
            return gsonPretty.toJson(object);
        } catch (Exception e) {
            log.warn("serialize json error", e);
            return null;
        }
    }

    public JsonObject convertObjectToJsonObject(Object object) {
        if (object instanceof String) {
            JsonElement element = JsonParser.parseString((String) object);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            } else {
                return null;
            }
        }

        JsonElement element = gsonPretty.toJsonTree(object);
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        } else {
            return null;
        }
    }

    public List<String> parseArrayStringToStringArray(String data) {
        return gsonPretty.fromJson(data, listStringType);
    }

    public String deserializeNotPretty(Object object) {
        return gsonNotPretty.toJson(object);
    }

}