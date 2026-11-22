package org.sawiq.voicetranslate.translation;

import org.sawiq.voicetranslate.net.ServiceException;

/** Translates one line of speech. Blocks; called from pipeline workers only. */
public interface Translator {
    /**
     * @param request what to translate and the lines said just before it
     * @return the translation, and the source language if the translator detected it
     */
    TranslationResult translate(TranslationRequest request) throws ServiceException;
}
