package com.retailid.example;

import com.retailid.api.Json;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Turns SDK results into readable, indented JSON for the results panel. */
public final class JsonText {

    private JsonText() {
    }

    /** The text shown for an API result. */
    public static String format(Object value) {
        if (value == null) {
            return "(empty response)";
        }
        if (value instanceof String) {
            return (String) value;
        }
        return pretty(toJsonable(value), 2);
    }

    /**
     * Converts SDK results into plain JSON values. Response models ({@code ReceiveResult},
     * {@code PackageInfo}, ...) become an object of their getters, with the full response last
     * under {@code raw}.
     */
    public static Object toJsonable(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Optional) {
            return toJsonable(((Optional<?>) value).orElse(null));
        }
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> out.put(String.valueOf(k), toJsonable(v)));
            return out;
        }
        if (value instanceof Collection) {
            List<Object> out = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                out.add(toJsonable(item));
            }
            return out;
        }
        if (value instanceof Iterator) {
            List<Object> out = new ArrayList<>();
            ((Iterator<?>) value).forEachRemaining(item -> out.add(toJsonable(item)));
            return out;
        }
        if (value.getClass().getPackageName().startsWith("com.retailid")) {
            Map<String, Object> model = modelFields(value);
            if (model != null) {
                return model;
            }
        }
        return value.toString();
    }

    private static Map<String, Object> modelFields(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object raw = null;
        Method[] methods = value.getClass().getMethods();
        // getMethods() has no defined order; sort by name so the output is stable.
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())
                    || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = propertyName(method.getName());
            if (name == null) {
                continue;
            }
            try {
                Object result = method.invoke(value);
                if (name.equals("raw")) {
                    raw = result;
                } else {
                    out.put(name, toJsonable(result));
                }
            } catch (ReflectiveOperationException e) {
                // Skip a getter that cannot be read.
            }
        }
        if (out.isEmpty() && raw == null) {
            return null;
        }
        if (raw != null) {
            out.put("raw", toJsonable(raw));
        }
        return out;
    }

    private static String propertyName(String method) {
        for (String prefix : new String[] {"get", "is", "has"}) {
            if (method.startsWith(prefix) && method.length() > prefix.length()
                    && Character.isUpperCase(method.charAt(prefix.length()))) {
                String rest = method.substring(prefix.length());
                String name = Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
                return prefix.equals("get") ? name : prefix + rest;
            }
        }
        // ReceiveResult.requiresVerification() and similar predicates.
        if (method.equals("requiresVerification")) {
            return method;
        }
        return null;
    }

    /** Indented JSON; strings and numbers are written by the SDK's own JSON writer. */
    public static String pretty(Object value, int indent) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, indent, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int indent, int depth) {
        if (value instanceof Map && !((Map<?, ?>) value).isEmpty()) {
            sb.append("{\n");
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) value).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                pad(sb, indent, depth + 1);
                sb.append(Json.stringify(String.valueOf(entry.getKey()))).append(": ");
                write(sb, entry.getValue(), indent, depth + 1);
                sb.append(it.hasNext() ? ",\n" : "\n");
            }
            pad(sb, indent, depth);
            sb.append('}');
        } else if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
            sb.append("[\n");
            Iterator<?> it = ((Collection<?>) value).iterator();
            while (it.hasNext()) {
                pad(sb, indent, depth + 1);
                write(sb, it.next(), indent, depth + 1);
                sb.append(it.hasNext() ? ",\n" : "\n");
            }
            pad(sb, indent, depth);
            sb.append(']');
        } else {
            sb.append(Json.stringify(value));
        }
    }

    private static void pad(StringBuilder sb, int indent, int depth) {
        sb.append(" ".repeat(indent * depth));
    }
}
