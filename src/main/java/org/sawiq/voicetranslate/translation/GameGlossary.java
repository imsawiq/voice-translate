package org.sawiq.voicetranslate.translation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft's own names for its things, in English and in the reader's
 * language, taken from the game's language files.
 *
 * <p>General translators do not know the game: Google turns "the nether"
 * into "the void" and "ender pearl" into "pearl of Ender". The game ships
 * the official name of every item, block, mob and biome in every language,
 * so English speech can be translated with the words players of the target
 * language actually use.</p>
 *
 * <p>Multi-word names ("Crafting Table", "Ender Pearl") are specific enough
 * to match safely. Single words are only taken from a fixed list of words
 * that mean the game's thing when a player says them, so "stone" or "bed"
 * in an ordinary sentence is left to the translator.</p>
 */
public final class GameGlossary {
    public static final GameGlossary EMPTY = new GameGlossary(List.of());

    private static final List<String> NAME_KEY_PREFIXES = List.of(
            "item.minecraft.", "block.minecraft.", "entity.minecraft.", "biome.minecraft.");
    /** Other keys that hold a name players say, such as the Nether, which has no item of its own. */
    private static final Set<String> EXTRA_KEYS = Set.of("advancements.nether.root.title");
    /** Words with an everyday meaning ("stray", "breeze", "husk") are left out. */
    private static final Set<String> GAME_ONLY_WORDS = Set.of(
            "nether", "creeper", "enderman", "endermite", "elytra", "ghast", "blaze", "piglin", "hoglin",
            "zoglin", "shulker", "wither", "warden", "axolotl", "sniffer", "strider", "villager",
            "pillager", "evoker", "vindicator", "ravager", "netherrack", "glowstone", "purpur",
            "armadillo", "phantom", "vex", "illager");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int MAX_WORDS = 4;
    private static final int MAX_TERMS_PER_SENTENCE = 8;

    private record Entry(GlossaryTerm term, String firstWord, Pattern pattern) {
    }

    private final List<Entry> entries;

    private GameGlossary(List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * @param english           the game's en_us strings, key to text
     * @param target            the same keys in the reader's language
     * @param capitalizesNouns  whether the target writes every noun with a capital, as German does;
     *                          otherwise a name said in lower case is written in lower case
     */
    public static GameGlossary from(Map<String, String> english, Map<String, String> target, boolean capitalizesNouns) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> name : english.entrySet()) {
            String key = name.getKey();
            boolean isName = NAME_KEY_PREFIXES.stream().anyMatch(key::startsWith) || EXTRA_KEYS.contains(key);
            String translation = target.get(key);
            String englishName = name.getValue().strip();
            if (!isName || translation == null || !isUseful(englishName, translation)) {
                continue;
            }
            String lower = englishName.toLowerCase(Locale.ROOT);
            if (seen.add(lower)) {
                String firstWord = WHITESPACE.split(lower)[0];
                String inSentence = capitalizesNouns ? translation.strip() : lowerFirst(translation.strip());
                entries.add(new Entry(new GlossaryTerm(englishName, inSentence), firstWord, patternFor(englishName)));
            }
        }
        // Longest first, so "Ender Pearl" is found before a shorter name inside it.
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.term.english().length()).reversed());
        return new GameGlossary(List.copyOf(entries));
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public int size() {
        return this.entries.size();
    }

    /** The game names said in this English text, longest first. */
    public List<GlossaryTerm> find(String text) {
        List<GlossaryTerm> found = new ArrayList<>();
        String remaining = text.toLowerCase(Locale.ROOT);
        for (Entry entry : this.entries) {
            if (found.size() >= MAX_TERMS_PER_SENTENCE) {
                break;
            }
            // A plain search first: almost every name is absent, and it is far cheaper than the pattern.
            if (!remaining.contains(entry.firstWord)) {
                continue;
            }
            Matcher matcher = entry.pattern.matcher(remaining);
            if (matcher.find()) {
                found.add(entry.term);
                // Blank the match out so "Ender Pearl" does not also count as a shorter name inside it.
                remaining = matcher.replaceAll(match -> " ".repeat(match.group().length()));
            }
        }
        return found;
    }

    /** Writes the official translations into the text, for translators that take no glossary. */
    public static String substitute(String text, List<GlossaryTerm> terms) {
        String result = text;
        for (GlossaryTerm term : terms) {
            result = patternFor(term.english()).matcher(result).replaceAll(Matcher.quoteReplacement(term.translation()));
        }
        return result;
    }

    /** "Алмазная кирка" reads as "алмазная кирка" in the middle of a sentence. */
    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }

    private static boolean isUseful(String english, String translation) {
        if (english.isEmpty() || translation.isBlank() || english.equalsIgnoreCase(translation.strip())
                || english.contains("%") || english.contains("(")) {
            return false;
        }
        String[] words = WHITESPACE.split(english);
        if (words.length > MAX_WORDS) {
            return false;
        }
        return words.length > 1 || GAME_ONLY_WORDS.contains(english.toLowerCase(Locale.ROOT));
    }

    /** Whole words in any case; the last one may be plural ("creepers", "ender pearls", "endermen"). */
    private static Pattern patternFor(String english) {
        String[] words = WHITESPACE.split(english.strip());
        StringBuilder regex = new StringBuilder("(?iu)(?<![\\p{L}\\p{N}])");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                regex.append("[\\s-]+");
            }
            String word = words[i];
            boolean isLast = i == words.length - 1;
            if (isLast && word.toLowerCase(Locale.ROOT).endsWith("man")) {
                regex.append(Pattern.quote(word.substring(0, word.length() - 3))).append("m[ae]n");
            } else {
                regex.append(Pattern.quote(word));
                if (isLast) {
                    regex.append("(?:e?s)?");
                }
            }
        }
        regex.append("(?![\\p{L}\\p{N}])");
        return Pattern.compile(regex.toString());
    }
}
