package org.sawiq.voicetranslate.client;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.sawiq.voicetranslate.VoiceTranslate;
import org.sawiq.voicetranslate.audio.SegmenterSettings;
import org.sawiq.voicetranslate.audio.VoiceIntake;
import org.sawiq.voicetranslate.caption.Caption;
import org.sawiq.voicetranslate.caption.CaptionBoard;
import org.sawiq.voicetranslate.client.compat.ClientAccess;
import org.sawiq.voicetranslate.client.ui.EngineStatusText;
import org.sawiq.voicetranslate.client.ui.SettingsScreen;
import org.sawiq.voicetranslate.config.ConfigStore;
import org.sawiq.voicetranslate.config.RecognitionService;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.EnginePaths;
import org.sawiq.voicetranslate.dub.DubPlayer;
import org.sawiq.voicetranslate.dub.DubbingService;
import org.sawiq.voicetranslate.dub.DubVoices;
import org.sawiq.voicetranslate.dub.PiperSynthesizer;
import org.sawiq.voicetranslate.dub.VoiceCopier;
import org.sawiq.voicetranslate.dub.OwnSpeechTranslator;
import org.sawiq.voicetranslate.dub.VoiceSamples;
import org.sawiq.voicetranslate.engine.LocalEngine;
import org.sawiq.voicetranslate.engine.VoiceEngine;
import org.sawiq.voicetranslate.language.SpeakerLanguages;
import org.sawiq.voicetranslate.net.ServiceException;
import org.sawiq.voicetranslate.pipeline.FinishedSentence;
import org.sawiq.voicetranslate.pipeline.PipelineServices;
import org.sawiq.voicetranslate.pipeline.TranslationPipeline;
import org.sawiq.voicetranslate.voice.VoiceBridge;

/**
 * The translator on the client: owns the settings, the pipeline, the local
 * engine and the captions, and is driven by the loader's tick and lifecycle
 * events.
 */
public final class VoiceTranslateClient {
    public static final VoiceTranslateClient INSTANCE = new VoiceTranslateClient();

    private static final long FAILURE_NOTICE_INTERVAL_MILLIS = 30_000L;
    private static final int PLAYER_REFRESH_TICKS = 10;
    /** A player whose speech was translated this recently counts as speaking another language. */
    private static final long FOREIGN_SPEAKER_MILLIS = 120_000L;

    private final CaptionBoard captions = new CaptionBoard();
    private final SpeakerLanguages languages = new SpeakerLanguages();
    private final OnlinePlayers players = new OnlinePlayers();
    private final GlossaryLoader glossary = new GlossaryLoader();
    private final Queue<Caption> finishedForChat = new ConcurrentLinkedQueue<>();
    /** Other windows showing these settings, such as Plasmo Voice's; told after every change. */
    private final List<Runnable> settingsListeners = new CopyOnWriteArrayList<>();
    private final VoiceSamples voiceSamples = new VoiceSamples();
    private final DubPlayer dubPlayer = new DubPlayer();
    private final Map<UUID, Long> lastTranslatedAt = new ConcurrentHashMap<>();
    private VoiceEngine voiceEngine;
    private DubbingService dubbing;
    private OwnSpeechTranslator ownSpeech;
    private volatile PipelineServices services;
    private volatile ServiceException pendingVoiceFailure;
    private long lastVoiceFailureNoticeAt;
    private ConfigStore store;
    private volatile TranslateConfig config = new TranslateConfig();
    private volatile SegmenterSettings segmenterSettings = SegmenterSettings.DEFAULT;
    /** A copy for the audio threads; the config's own set is changed on the game thread. */
    private volatile Set<UUID> mutedPlayers = Set.of();
    private LocalEngine engine;
    private TranslationPipeline pipeline;
    private VoiceIntake intake;
    private KeyBindings keys;
    private volatile ServiceException pendingFailure;
    private long lastFailureNoticeAt;
    private String lastGameLanguage = "";
    private boolean wasInWorld;
    private int tickCount;

    private VoiceTranslateClient() {
    }

    public void initialize(Path configDirectory, KeyBindings keyBindings) {
        this.store = new ConfigStore(configDirectory);
        this.config = this.store.load();
        this.keys = keyBindings;
        this.engine = new LocalEngine();
        this.pipeline = new TranslationPipeline(this.captions, this.languages,
                this::onSentenceFinished, failure -> this.pendingFailure = failure);
        this.intake = new VoiceIntake(this.pipeline, () -> this.segmenterSettings, this::isTranslated);
        this.voiceEngine = new VoiceEngine();
        DubVoices voices = new DubVoices(new PiperSynthesizer(this.engine::voiceAddress),
                new VoiceCopier(this.voiceEngine::address), this.voiceSamples,
                () -> this.config.dubbing.cloneVoices);
        this.dubbing = new DubbingService(voices, this.dubPlayer, this::dubbingSettings,
                failure -> this.pendingVoiceFailure = failure);
        this.ownSpeech = new OwnSpeechTranslator(() -> this.services, voices, this.voiceSamples, this.languages,
                this.players::self, this::ownSpeechSettings, this.captions::publish,
                failure -> this.pendingVoiceFailure = failure);
        VoiceBridge.connect(this.intake);
        VoiceBridge.connectDubbing(this.dubPlayer, this.ownSpeech);
        VoiceBridge.setOriginalGain(this::originalGain);
        VoiceBridge.setEntityLookup(this.players::entityOf);
        VoiceTranslate.LOGGER.info("Voice Translate ready; local engine root {}", EnginePaths.root());
    }

    public void shutdown() {
        VoiceBridge.disconnect();
        if (this.intake != null) {
            this.intake.close();
        }
        if (this.pipeline != null) {
            this.pipeline.close();
        }
        if (this.engine != null) {
            this.engine.close();
        }
        if (this.voiceEngine != null) {
            this.voiceEngine.close();
            this.dubbing.close();
            this.ownSpeech.close();
            this.dubPlayer.close();
        }
    }

    public void tick(Minecraft client) {
        if (this.engine == null) {
            return;
        }
        if (this.tickCount++ % PLAYER_REFRESH_TICKS == 0) {
            this.players.refresh(client);
        }
        String gameLanguage = client.options.languageCode;
        if (!gameLanguage.equals(this.lastGameLanguage)) {
            this.lastGameLanguage = gameLanguage;
            applySettings();
        }
        VoiceBridge.tick();
        handleKeys();
        handleWorldChange(client);
        reportVoiceFailure();
        postFinishedToChat();
        reportFailure();
        reportInstallOutcome();
    }

    // --- Settings ---------------------------------------------------------------

    public TranslateConfig config() {
        return this.config;
    }

    /** Puts the current settings into effect and saves them. Game thread. */
    public void applySettings() {
        TranslateConfig current = this.config;
        this.segmenterSettings = new SegmenterSettings(current.recognition.pauseMillis, SegmenterSettings.DEFAULT.maxSeconds(),
                current.display.showDrafts ? SegmenterSettings.DEFAULT.draftIntervalMillis() : 0,
                current.recognition.sensitivityDb);
        this.mutedPlayers = Set.copyOf(current.mutedPlayers);
        boolean recognisesLocally = current.recognition.service == RecognitionService.LOCAL;
        boolean speaks = current.dubbing.speaksAnything();
        this.engine.want(current.enabled && (recognisesLocally || speaks),
                recognisesLocally ? current.recognition.localModel : LocalEngine.NO_MODEL, current.recognition.localDevice);
        this.engine.wantVoices(spokenLanguages(current));
        this.glossary.request(ServiceFactory.targetLanguage(current, this.lastGameLanguage), this::applySettings);
        this.services = ServiceFactory.create(current, this.engine, this.lastGameLanguage, this.glossary.current());
        this.pipeline.use(this.services);
        this.voiceEngine.want(current.enabled && current.dubbing.needsVoiceEngine());
        if (!current.dubbing.translateMySpeech) {
            this.ownSpeech.reset();
        }
        this.store.save(current);
        this.settingsListeners.forEach(Runnable::run);
    }

    public void addSettingsListener(Runnable listener) {
        this.settingsListeners.add(listener);
    }

    public void setEnabled(boolean enabled) {
        this.config.enabled = enabled;
        if (!enabled) {
            this.captions.clearActive();
        }
        applySettings();
    }

    public boolean isPlayerMuted(UUID player) {
        return this.config.mutedPlayers.contains(player);
    }

    public void setPlayerMuted(UUID player, boolean muted) {
        if (muted) {
            this.config.mutedPlayers.add(player);
        } else {
            this.config.mutedPlayers.remove(player);
        }
        applySettings();
    }

    // --- Accessors for the HUD and screens ----------------------------------------

    /** Who the voice mods delivered last, or null if nobody was heard yet. */
    public VoiceIntake.Heard lastHeard() {
        return this.intake.lastHeard();
    }

    /** A player's current name, or a short id when they are not on the tab list. */
    public String nameOf(UUID player, String fallback) {
        String name = this.players.nameOf(player);
        if (!name.isEmpty()) {
            return name;
        }
        return fallback.isEmpty() ? player.toString().substring(0, 8) : fallback;
    }

    public CaptionBoard captions() {
        return this.captions;
    }

    /** How many game names the glossary for the current target language holds; 0 while it loads or for English. */
    public int glossarySize() {
        return this.glossary.current().size();
    }

    public LocalEngine engine() {
        return this.engine;
    }

    public TranslationPipeline pipeline() {
        return this.pipeline;
    }

    /** The player's current name, or what the voice mod called them, or a short id. */
    public String displayName(Caption caption) {
        return nameOf(caption.speakerId(), caption.speakerName());
    }

    // --- Internals --------------------------------------------------------------

    /** Audio thread: whether this voice is worth cutting into sentences at all. */
    private boolean isTranslated(UUID speaker) {
        TranslateConfig current = this.config;
        if (!current.enabled || this.mutedPlayers.contains(speaker)) {
            return false;
        }
        // Speech sent translated already gets its own subtitle; running it through here
        // as well would recognise and translate every phrase twice on the same processor.
        boolean isOwnSubtitled = current.translateOwnVoice && !current.dubbing.translateMySpeech;
        return this.players.isOtherPlayer(speaker) || isOwnSubtitled && this.players.isSelf(speaker);
    }

    /** Pipeline worker: a sentence of another player is done. */
    private void onSentenceFinished(FinishedSentence sentence) {
        Caption caption = sentence.caption();
        if (this.config.display.chatLog) {
            this.finishedForChat.add(caption);
        }
        this.voiceSamples.offer(caption.speakerId(), sentence.samples());
        if (caption.isTranslated()) {
            this.lastTranslatedAt.put(caption.speakerId(), System.currentTimeMillis());
            this.dubbing.onSentence(caption, sentence.targetLanguage());
        }
    }

    /**
     * How loud a player's own voice plays: lowered while their speech is
     * dubbed, so the translation is what is heard. Audio threads.
     */
    private double originalGain(UUID speaker) {
        TranslateConfig current = this.config;
        if (!current.enabled || !current.dubbing.dubOthers || this.engine.voiceAddress() == null) {
            return 1.0;
        }
        Long translatedAt = this.lastTranslatedAt.get(speaker);
        boolean isForeign = translatedAt != null && System.currentTimeMillis() - translatedAt < FOREIGN_SPEAKER_MILLIS;
        return isForeign ? current.dubbing.originalVolume / 100.0 : 1.0;
    }

    private DubbingService.Settings dubbingSettings() {
        TranslateConfig current = this.config;
        return new DubbingService.Settings(current.enabled && current.dubbing.dubOthers, current.dubbing.dubVolume / 100f);
    }

    /** The languages something will be spoken in, whose voices are worth having on disk before they are needed. */
    private Set<String> spokenLanguages(TranslateConfig current) {
        Set<String> languages = new HashSet<>();
        if (current.dubbing.dubOthers) {
            languages.add(ServiceFactory.targetLanguage(current, this.lastGameLanguage));
        }
        if (current.dubbing.translateMySpeech) {
            languages.add(current.dubbing.mySpeechLanguage);
        }
        return languages;
    }

    private OwnSpeechTranslator.Settings ownSpeechSettings() {
        TranslateConfig current = this.config;
        return new OwnSpeechTranslator.Settings(current.enabled && current.dubbing.translateMySpeech,
                current.dubbing.mySpeechLanguage, current.dubbing.dubVolume / 100f, current.recognition.pauseMillis);
    }

    public VoiceEngine voiceEngine() {
        return this.voiceEngine;
    }

    /** Tells the player, at most every half minute, that speaking translations fails and why. */
    private void reportVoiceFailure() {
        ServiceException failure = this.pendingVoiceFailure;
        if (failure == null) {
            return;
        }
        this.pendingVoiceFailure = null;
        long now = System.currentTimeMillis();
        if (now - this.lastVoiceFailureNoticeAt < FAILURE_NOTICE_INTERVAL_MILLIS) {
            return;
        }
        this.lastVoiceFailureNoticeAt = now;
        VoiceTranslate.LOGGER.warn("Speaking a translation failed: {}", failure.getMessage());
        ClientAccess.actionBar(Component.translatable("voice-translate.failure.voice",
                Component.translatable(FailureText.keyFor(failure.kind()))).withStyle(ChatFormatting.RED));
    }

    private void handleKeys() {
        while (this.keys.openSettings().consumeClick()) {
            ClientAccess.openScreen(SettingsScreen.open(ClientAccess.currentScreen()));
        }
        while (this.keys.openHistory().consumeClick()) {
            ClientAccess.openScreen(new SettingsScreen(ClientAccess.currentScreen(), SettingsScreen.Page.HISTORY));
        }
        while (this.keys.toggleMySpeech().consumeClick()) {
            this.config.dubbing.translateMySpeech = !this.config.dubbing.translateMySpeech;
            applySettings();
            ClientAccess.actionBar(Component.translatable(this.config.dubbing.translateMySpeech
                    ? "voice-translate.my_speech.on" : "voice-translate.my_speech.off"));
        }
        while (this.keys.toggle().consumeClick()) {
            setEnabled(!this.config.enabled);
            ClientAccess.actionBar(Component.translatable(this.config.enabled
                    ? "voice-translate.toggle.on" : "voice-translate.toggle.off"));
        }
    }

    private void handleWorldChange(Minecraft client) {
        boolean isInWorld = client.player != null;
        VoiceBridge.setOwnId(isInWorld ? client.player.getUUID() : null);
        if (isInWorld && !this.wasInWorld) {
            showWelcomeOnce();
        }
        if (!isInWorld && this.wasInWorld) {
            this.intake.clear();
            this.ownSpeech.reset();
            this.dubPlayer.clear();
            this.voiceSamples.clear();
            this.lastTranslatedAt.clear();
            this.captions.clearActive();
            this.languages.clear();
        }
        this.wasInWorld = isInWorld;
    }

    private void showWelcomeOnce() {
        if (this.config.hasSeenWelcome) {
            return;
        }
        this.config.hasSeenWelcome = true;
        this.store.save(this.config);
        ClientAccess.chat(Component.translatable("voice-translate.welcome",
                Component.keybind(this.keys.openSettings().getName()).withStyle(ChatFormatting.YELLOW)));
    }

    private void postFinishedToChat() {
        Caption caption;
        while ((caption = this.finishedForChat.poll()) != null) {
            ClientAccess.chat(Component.literal("[" + displayName(caption) + "] ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(caption.translation()).withStyle(ChatFormatting.WHITE)));
        }
    }

    /** Tells the player, at most every half minute, that subtitles are failing and why. */
    private void reportFailure() {
        ServiceException failure = this.pendingFailure;
        if (failure == null) {
            return;
        }
        this.pendingFailure = null;
        long now = System.currentTimeMillis();
        if (now - this.lastFailureNoticeAt < FAILURE_NOTICE_INTERVAL_MILLIS) {
            return;
        }
        this.lastFailureNoticeAt = now;
        VoiceTranslate.LOGGER.warn("Translation failed: {}", failure.getMessage());
        ClientAccess.actionBar(Component.translatable("voice-translate.failure",
                Component.translatable(FailureText.keyFor(failure.kind()))).withStyle(ChatFormatting.RED));
    }

    private void reportInstallOutcome() {
        reportInstallOutcome(this.engine.installer());
        reportInstallOutcome(this.voiceEngine.installer());
    }

    private static void reportInstallOutcome(EngineInstaller installer) {
        EngineInstaller.Progress outcome = installer.takeOutcome();
        if (outcome == null) {
            return;
        }
        ClientAccess.chat(Component.translatable(EngineStatusText.key(installer.component(),
                outcome.state() == EngineInstaller.State.INSTALLED ? "installed_chat" : "failed_chat")));
    }
}
