package io.github.mangi.ai.runtime;

import java.net.URI;
import java.util.Objects;

/** Immutable one-session endpoint. Credentials never appear in toString(). */
public final class AiEndpoint {
    private final URI baseUrl;
    private final String bearerToken;

    public AiEndpoint(String baseUrl, String bearerToken) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(bearerToken, "bearerToken");
        URI uri = URI.create(baseUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getRawUserInfo() != null ||
            uri.getRawQuery() != null || uri.getRawFragment() != null ||
            !uri.isAbsolute()) {
            throw new IllegalArgumentException("Endpoint must be an absolute URL without credentials/query");
        }
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!"https".equalsIgnoreCase(scheme) && !(loopback && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Only HTTPS or local loopback HTTP is permitted");
        }
        if (bearerToken.isBlank() || bearerToken.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Missing or unsafe bearer token");
        }
        this.baseUrl = uri;
        this.bearerToken = bearerToken;
    }

    public URI baseUrl() { return baseUrl; }
    /** Trusted in-process provider transports only; never expose through exported Android IPC. */
    public String bearerToken() { return bearerToken; }
    @Override public String toString() { return "AiEndpoint{baseUrl=" + baseUrl + ", bearerToken=[REDACTED]}"; }
}
