package org.sawiq.voicetranslate.pipeline;

import org.sawiq.voicetranslate.net.ServiceException;

/**
 * How the pipeline is doing, for the settings screen and the occasional
 * warning above the hotbar.
 *
 * @param lastFailure            the most recent failure, or null if the last job worked
 * @param lastFailureAt          epoch millis of that failure, 0 without one
 * @param averageLatencyMillis   from the end of a sentence to its subtitle, recent average
 * @param translatedCount        finished sentences since the game started
 * @param droppedCount           sentences thrown away because the pipeline fell behind
 */
public record PipelineHealth(ServiceException lastFailure, long lastFailureAt, long averageLatencyMillis,
                             long translatedCount, long droppedCount) {
}
