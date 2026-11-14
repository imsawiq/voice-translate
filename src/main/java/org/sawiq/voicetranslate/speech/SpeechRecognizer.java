package org.sawiq.voicetranslate.speech;

import org.sawiq.voicetranslate.net.ServiceException;

/**
 * Turns speech into text. Implementations block, and are called from the
 * translation pipeline's worker threads, never from the game or audio thread.
 */
public interface SpeechRecognizer {
    Transcript transcribe(RecognitionRequest request) throws ServiceException;

    /**
     * How many recognitions can usefully run at once: one for a local GPU,
     * more for a cloud service.
     */
    int parallelism();

    /** Whether drafts of unfinished sentences are cheap enough to send by default. */
    boolean isCheapPerRequest();
}
