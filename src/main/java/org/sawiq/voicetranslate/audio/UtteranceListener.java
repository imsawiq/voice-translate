package org.sawiq.voicetranslate.audio;

import java.util.UUID;

/** Receives speech cut into utterances. Called on audio threads; must not block. */
public interface UtteranceListener {
    void onUtterance(Utterance utterance);

    /** The drafts already sent for this utterance turned out not to be speech. */
    void onDiscarded(UUID speakerId, long utteranceId);
}
