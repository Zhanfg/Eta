package io.github.mangi.ai.runtime;

/** Single-use callback; the runtime owns endpoint/session cleanup. */
@FunctionalInterface
public interface AiSessionAction<T> {
    T run(AiEndpoint endpoint) throws Exception;
}
