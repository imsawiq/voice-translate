package org.sawiq.voicetranslate.translation;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Remembers recent translations. Voice chat repeats itself ("go go go",
 * "wait", "come here"), and drafts of a sentence often end up with the
 * same text as the final version, so a small cache saves many requests.
 */
public final class CachingTranslator implements Translator {
    private static final int CAPACITY = 512;

    private final Translator delegate;
    private final Map<String, TranslationResult> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TranslationResult> eldest) {
            return size() > CAPACITY;
        }
    };

    public CachingTranslator(Translator delegate) {
        this.delegate = delegate;
    }

    @Override
    public TranslationResult translate(TranslationRequest request) throws ServiceException {
        // Context changes the translation, but only slightly; keying on it
        // too would make the cache useless for exactly the repeated phrases.
        String key = request.sourceLanguage() + '\u0000' + request.targetLanguage() + '\u0000' + request.text();
        synchronized (this.cache) {
            TranslationResult cached = this.cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        TranslationResult result = this.delegate.translate(request);
        synchronized (this.cache) {
            this.cache.put(key, result);
        }
        return result;
    }
}
