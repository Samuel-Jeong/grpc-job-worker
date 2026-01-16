package com.dovaj.job_worker_app_demo.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * packageName    : com.dovaj.job_worker_app_demo.util
 * fileName       : ReflectionUtil
 * author         : samuel
 * date           : 25. 10. 29.
 * description    : 리플렉션 유틸 클래스
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 25. 10. 29.        samuel       최초 생성
 */
@Slf4j
public class ReflectionUtil {

    private ReflectionUtil() {
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class
    );

    public static Class<?> getClassByName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.warn("클래스를 찾을 수 없음 - className={}", className, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(String className, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            if (args == null || args.length == 0) {
                return (T) clazz.getDeclaredConstructor().newInstance();
            }

            Constructor<?> best = selectBestConstructor(clazz, args);
            if (best == null) {
                log.warn("호출 가능한 생성자를 찾지 못함 - className={}, argsTypes={}",
                        className, argTypes(args));
                throw new IllegalArgumentException("호출 가능한 생성자 없음");
            }

            best.setAccessible(true);
            return (T) best.newInstance(args);
        } catch (ClassNotFoundException e) {
            log.warn("클래스를 찾을 수 없음 - className={}", className, e);
            throw new IllegalArgumentException("클래스 로드 실패: " + className, e);
        } catch (InvocationTargetException e) {
            log.warn("생성자 내부 예외 - className={}, cause={}",
                    className, e.getTargetException().toString(), e);
            throw new RuntimeException("생성자 실행 중 예외 발생", e.getTargetException());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            log.warn("인스턴스 생성 실패 - className={}", className, e);
            throw new RuntimeException("인스턴스 생성 실패", e);
        }
    }

    private static Constructor<?> selectBestConstructor(Class<?> clazz, Object[] args) {
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        List<Constructor<?>> candidates = new ArrayList<>();
        for (Constructor<?> c : ctors) {
            if (c.getParameterCount() != args.length) continue;
            if (isCompatible(c.getParameterTypes(), args)) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() > 1) {
            // 모호성 경고
            log.warn("여러 생성자와 매칭됨 - className={}, candidatesCount={}, argsTypes={}",
                    clazz.getName(), candidates.size(), argTypes(args));
        }
        // 일단 첫 번째를 선택(필요시 더 정교한 점수화 로직으로 개선)
        return candidates.get(0);
    }

    private static boolean isCompatible(Class<?>[] paramTypes, Object[] args) {
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> p = paramTypes[i];
            Object a = args[i];
            if (a == null) {
                // 원시타입은 null 불가
                if (p.isPrimitive()) return false;
                continue;
            }
            Class<?> aType = a.getClass();
            if (p.isPrimitive()) {
                Class<?> wrapper = PRIMITIVE_TO_WRAPPER.get(p);
                if (!wrapper.isAssignableFrom(aType)) return false;
            } else {
                if (!p.isAssignableFrom(aType)) return false;
            }
        }
        return true;
    }

    private static String argTypes(Object[] args) {
        return Arrays.toString(Arrays.stream(args)
                .map(a -> a == null ? "null" : a.getClass().getName())
                .toArray(String[]::new));
    }

}
