package org.thinkingstudio.foxifiedlibrary.impl.loader;

import org.thinkingstudio.foxifiedlibrary.api.loader.ObjectShare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

final class ObjectShareImpl implements ObjectShare {
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, List<BiConsumer<String, Object>>> pendingMap = new HashMap<>();

    @Override
    public synchronized Object get(String key) {
        validateKey(key);

        return values.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "null value");

        List<BiConsumer<String, Object>> pending;

        synchronized (this) {
            Object prev = values.put(key, value);
            if (prev != null) return prev; // no new entry -> can't have pending entries for it

            pending = pendingMap.remove(key);
        }

        if (pending != null) invokePending(key, value, pending);

        return null;
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        validateKey(key);
        Objects.requireNonNull(value, "null value");

        List<BiConsumer<String, Object>> pending;

        synchronized (this) {
            Object prev = values.putIfAbsent(key, value);
            if (prev != null) return prev; // no new entry -> can't have pending entries for it

            pending = pendingMap.remove(key);
        }

        if (pending != null) invokePending(key, value, pending);

        return null;
    }

    @Override
    public synchronized Object remove(String key) {
        validateKey(key);

        return values.remove(key);
    }

    @Override
    public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
        validateKey(key);

        Object value;

        synchronized (this) {
            value = values.get(key);

            if (value == null) { // value doesn't exist yet, queue invocation for when it gets added
                pendingMap.computeIfAbsent(key, ignore -> new ArrayList<>()).add(consumer);
                return;
            }
        }

        // value exists already, invoke directly
        consumer.accept(key, value);
    }

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "null key");

        int pos = key.indexOf(':');
        if (pos <= 0 || pos >= key.length() - 1) throw new IllegalArgumentException("invalid key, must be modid:subkey");
    }

    private static void invokePending(String key, Object value, List<BiConsumer<String, Object>> pending) {
        for (BiConsumer<String, Object> consumer : pending) {
            consumer.accept(key, value);
        }
    }
}
