package io.github.mangi.ai.runtime;

/**
 * Project-independent backend contract for ETA, KELIVO and other hosts.
 * Holds a process lease across a request's whole streaming lifecycle.
 */
public interface AiBackend {
    String id();
    boolean isInstalled();
    <T> T withSession(AiSessionAction<T> operation) throws Exception;
}
