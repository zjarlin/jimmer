package org.babyfish.jimmer.impl.util;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * JSON Provider 的内部类型归属判断工具。
 */
public final class JsonCodecProviderUtil {

    private JsonCodecProviderUtil() {
    }

    public static boolean containsType(Type type, String... packagePrefixes) {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            String className = clazz.getName();
            for (String packagePrefix : packagePrefixes) {
                if (className.startsWith(packagePrefix)) {
                    return true;
                }
            }
            return clazz.isArray() && containsType(clazz.getComponentType(), packagePrefixes);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (containsType(parameterizedType.getRawType(), packagePrefixes)) {
                return true;
            }
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsType(argument, packagePrefixes)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof GenericArrayType) {
            return containsType(((GenericArrayType) type).getGenericComponentType(), packagePrefixes);
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type bound : wildcardType.getUpperBounds()) {
                if (containsType(bound, packagePrefixes)) {
                    return true;
                }
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                if (containsType(bound, packagePrefixes)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean containsValue(Object value, String... packagePrefixes) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return containsValue(value, visited, packagePrefixes);
    }

    private static boolean containsValue(
            Object value,
            Set<Object> visited,
            String[] packagePrefixes
    ) {
        if (value == null) {
            return false;
        }
        if (containsType(value.getClass(), packagePrefixes)) {
            return true;
        }
        if (value instanceof Map<?, ?>) {
            if (!visited.add(value)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (
                        containsValue(entry.getKey(), visited, packagePrefixes) ||
                        containsValue(entry.getValue(), visited, packagePrefixes)
                ) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?>) {
            if (!visited.add(value)) {
                return false;
            }
            for (Object element : (Iterable<?>) value) {
                if (containsValue(element, visited, packagePrefixes)) {
                    return true;
                }
            }
            return false;
        }
        if (value.getClass().isArray()) {
            if (!visited.add(value)) {
                return false;
            }
            int size = Array.getLength(value);
            for (int i = 0; i < size; i++) {
                if (containsValue(Array.get(value, i), visited, packagePrefixes)) {
                    return true;
                }
            }
        }
        return false;
    }
}
