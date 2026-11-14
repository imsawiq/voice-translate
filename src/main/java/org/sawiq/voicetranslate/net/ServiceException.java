package org.sawiq.voicetranslate.net;

/**
 * A speech or translation service could not do its job. The kind decides
 * what the player is told and whether trying again soon makes sense.
 */
public final class ServiceException extends Exception {
    public enum Kind {
        /** The service is starting or loading a model; try again shortly. */
        NOT_READY,
        /** Missing or wrong settings: a key, an address, a model name. */
        CONFIGURATION,
        /** The key was refused. */
        UNAUTHORIZED,
        /** Too many requests or the quota is used up. */
        RATE_LIMITED,
        /** No connection, a timeout, a server error. */
        NETWORK,
        /** The service answered, but not in a way that can be read. */
        BAD_RESPONSE
    }

    private final Kind kind;

    public ServiceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ServiceException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    static ServiceException fromStatus(String service, int status, String body) {
        Kind kind = switch (status) {
            case 401, 403 -> Kind.UNAUTHORIZED;
            case 429, 456 -> Kind.RATE_LIMITED;
            case 400, 404, 413, 415, 422 -> Kind.CONFIGURATION;
            case 503 -> Kind.NOT_READY;
            default -> Kind.NETWORK;
        };
        return new ServiceException(kind, service + " answered HTTP " + status + ": " + body);
    }
}
