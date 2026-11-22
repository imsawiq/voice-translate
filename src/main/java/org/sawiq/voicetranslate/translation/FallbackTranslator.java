package org.sawiq.voicetranslate.translation;

import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Tries the translator the player chose and, if it fails, Google. A subtitle
 * from the second-best translator beats a missing one in the middle of a
 * conversation; the failure is still reported so the player can fix it.
 */
public final class FallbackTranslator implements Translator {
    private final Translator primary;
    private final Translator fallback;
    private volatile ServiceException lastPrimaryFailure;

    public FallbackTranslator(Translator primary, Translator fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public TranslationResult translate(TranslationRequest request) throws ServiceException {
        try {
            TranslationResult result = this.primary.translate(request);
            this.lastPrimaryFailure = null;
            return result;
        } catch (ServiceException exception) {
            if (this.lastPrimaryFailure == null) {
                VoiceTranslate.LOGGER.warn("Translator failed, using Google instead: {}", exception.getMessage());
            }
            this.lastPrimaryFailure = exception;
            try {
                return this.fallback.translate(request);
            } catch (ServiceException fallbackFailure) {
                exception.addSuppressed(fallbackFailure);
                throw exception;
            }
        }
    }

    /** Why the chosen translator is not being used right now, or null while it works. */
    public ServiceException lastPrimaryFailure() {
        return this.lastPrimaryFailure;
    }
}
