package io.github.mangi.ai.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** In-process registry. Backends may be replaced without global secrets. */
public final class AiBackendRegistry {
    private final Map<String, AiBackend> registered = new ConcurrentHashMap<>();

    public void register(AiBackend backend) {
        Objects.requireNonNull(backend, "backend");
        String id = Objects.requireNonNull(backend.id(), "backend.id()");
        if (!id.matches("[a-z0-9][a-z0-9._-]{1,63}")) {
            throw new IllegalArgumentException("Unsafe backend id");
        }
        if (registered.putIfAbsent(id, backend) != null) {
            throw new IllegalStateException("Backend already registered: " + id);
        }
    }

    public AiBackend require(String id) {
        AiBackend backend = registered.get(Objects.requireNonNull(id));
        if (backend == null) throw new IllegalArgumentException("Backend not installed: " + id);
        return backend;
    }

    public Collection<String> ids() {
        return Collections.unmodifiableSet(registered.keySet());
    }
}
