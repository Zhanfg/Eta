package io.github.mangi.ai.runtime;

import java.util.concurrent.atomic.AtomicInteger;

/** Run without Android SDK or JUnit: java -ea ...AiBackendHostSmoke */
public final class AiBackendHostSmoke {
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        AiEndpoint endpoint = new AiEndpoint("http://127.0.0.1:17863/v1", "FAKE_API_KEY_NOT_REAL");
        check(endpoint.baseUrl().toString().endsWith("/v1"), "Base URL");
        check(!endpoint.toString().contains("FAKE_API_KEY_NOT_REAL"), "token leaked in toString");
        for (String address : new String[] {"http://host.example/v1", "https://me:password@host.example/v1", "file:///tmp/credentials"}) {
            try { new AiEndpoint(address, "fixture"); throw new AssertionError("Accepted " + address); }
            catch (IllegalArgumentException expected) { /* fail closed */ }
        }
        AtomicInteger active = new AtomicInteger();
        AiBackend backend = new AiBackend() {
            public String id() { return "workbuddy.local"; }
            public boolean isInstalled() { return true; }
            public <T> T withSession(AiSessionAction<T> call) throws Exception {
                active.incrementAndGet();
                try { return call.run(endpoint); }
                finally { active.decrementAndGet(); }
            }
        };
        AiBackendRegistry registry = new AiBackendRegistry();
        registry.register(backend);
        check(registry.require("workbuddy.local").isInstalled(), "backend lookup");
        check(registry.require("workbuddy.local").withSession(ep -> ep.baseUrl().getHost()).equals("127.0.0.1"), "session callback");
        check(active.get() == 0, "successful callback leaked lease");
        try { registry.require("workbuddy.local").withSession(ep -> { throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { /* deliberate */ }
        check(active.get() == 0, "failed callback leaked lease");
        try { registry.register(backend); throw new AssertionError("Duplicate accepted"); }
        catch (IllegalStateException expected) { /* deliberate */ }
        System.out.println("PASS: portable AI runtime API: URL policy, redaction, registry, leases");
    }
}
