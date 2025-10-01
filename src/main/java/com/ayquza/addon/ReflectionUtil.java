package com.ayquza.addon;

import java.lang.reflect.Field;


public final class ReflectionUtil {
    private ReflectionUtil() {}



    public static Field findFieldByType(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(f.getType())) return f;
        }
        return null;
    }
}
