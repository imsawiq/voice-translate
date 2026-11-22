package org.sawiq.voicetranslate.client;

import org.sawiq.voicetranslate.net.ServiceException;

/** What to tell the player about a failure, by kind; the details go to the log. */
public final class FailureText {
    private FailureText() {
    }

    public static String keyFor(ServiceException.Kind kind) {
        return switch (kind) {
            case NOT_READY -> "voice-translate.failure.not_ready";
            case CONFIGURATION -> "voice-translate.failure.configuration";
            case UNAUTHORIZED -> "voice-translate.failure.unauthorized";
            case RATE_LIMITED -> "voice-translate.failure.rate_limited";
            case NETWORK -> "voice-translate.failure.network";
            case BAD_RESPONSE -> "voice-translate.failure.bad_response";
        };
    }
}
