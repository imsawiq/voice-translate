package org.sawiq.voicetranslate.speech;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Throws away what Whisper invents.
 *
 * <p>Whisper was trained on subtitled video, so on breathing, keyboard noise
 * or a cough it confidently "hears" the lines that end videos: "Thanks for
 * watching", "Субтитры сделал DimaTorzok", "Продолжение следует...". It also
 * gets stuck repeating a word. A subtitle that shows those lines over a
 * player who said nothing is worse than no subtitle, so segments are judged
 * by Whisper's own confidence and checked against the phrases it is known to
 * make up.</p>
 */
public final class HallucinationFilter {
    private static final double MAX_NO_SPEECH_PROBABILITY = 0.6;
    private static final double MIN_AVERAGE_LOG_PROBABILITY = -1.0;
    private static final double MAX_COMPRESSION_RATIO = 2.4;
    private static final Pattern NOT_A_LETTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern REPEATED_WORD = Pattern.compile("(?iu)\\b(\\p{L}+)(?:[\\s,.!?-]+\\1\\b){4,}");

    /** Normalised: lower case, letters and digits only, single spaces. */
    private static final Set<String> KNOWN_INVENTIONS = Set.of(
            "thanks for watching",
            "thank you for watching",
            "thank you so much for watching",
            "please subscribe",
            "subscribe to my channel",
            "like and subscribe",
            "see you in the next video",
            "see you next time",
            "thank you",
            "you",
            "bye",
            "music",
            "applause",
            "subtitles by the amara org community",
            "продолжение следует",
            "спасибо за просмотр",
            "благодарю за внимание",
            "подписывайтесь на канал",
            "ставьте лайки",
            "субтитры сделал dimatorzok",
            "субтитры создавал dimatorzok",
            "редактор субтитров а синецкая корректор а егорова",
            "редактор субтитров асинецкая корректор аегорова",
            "дякую за перегляд",
            "субтитри зроблені спільнотою amara org",
            "vielen dank fürs zuschauen",
            "untertitel im auftrag des zdf",
            "untertitel der amara org community",
            "merci d avoir regardé",
            "sous titres réalisés par la communauté d amara org",
            "gracias por ver",
            "subtítulos realizados por la comunidad de amara org",
            "obrigado por assistir",
            "dziękuję za uwagę",
            "napisy stworzone przez społeczność amara org",
            "ご視聴ありがとうございました",
            "字幕由amara org社区提供",
            "请不吝点赞 订阅 转发 打赏支持明镜与点点栏目"
    );

    /** Words that are a real answer when someone says only that. */
    private static final Set<String> SHORT_BUT_REAL = Set.of("thank you", "bye", "you");

    private HallucinationFilter() {
    }

    /**
     * The text worth showing, or empty if nothing in the transcript can be
     * trusted.
     *
     * @param voicedSeconds how long the speaker was actually audible; short
     *                      sounds are the ones Whisper fills with inventions
     */
    public static String clean(Transcript transcript, double voicedSeconds) {
        String text = keptText(transcript);
        if (text.isBlank()) {
            return "";
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return "";
        }
        if (KNOWN_INVENTIONS.contains(normalized)) {
            // "Thank you" is a fine sentence when someone clearly said it;
            // a second of mumbling that comes back as "Thank you" is not.
            boolean isPlausible = SHORT_BUT_REAL.contains(normalized) && voicedSeconds >= 0.6 && isConfident(transcript);
            if (!isPlausible) {
                return "";
            }
        }
        if (REPEATED_WORD.matcher(text).find()) {
            return "";
        }
        return text.strip();
    }

    private static String keptText(Transcript transcript) {
        if (transcript.segments().isEmpty()) {
            return transcript.text();
        }
        List<String> kept = new ArrayList<>();
        for (Transcript.Segment segment : transcript.segments()) {
            boolean isSilence = segment.noSpeechProbability() > MAX_NO_SPEECH_PROBABILITY
                    && segment.averageLogProbability() < MIN_AVERAGE_LOG_PROBABILITY;
            boolean isLoop = segment.compressionRatio() > MAX_COMPRESSION_RATIO;
            boolean isGuess = segment.averageLogProbability() < -1.4;
            if (!isSilence && !isLoop && !isGuess && !segment.text().isBlank()) {
                kept.add(segment.text().strip());
            }
        }
        return String.join(" ", kept);
    }

    private static boolean isConfident(Transcript transcript) {
        return transcript.segments().stream().allMatch(segment ->
                segment.averageLogProbability() > -0.5 && segment.noSpeechProbability() < 0.3);
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return NOT_A_LETTER.matcher(decomposed).replaceAll(" ").strip();
    }
}
