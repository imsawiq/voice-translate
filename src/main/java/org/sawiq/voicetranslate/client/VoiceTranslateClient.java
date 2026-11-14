package org.sawiq.voicetranslate.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
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
import org.sawiq.voicetranslate.client.ui.SettingsScreen;
import org.sawiq.voicetranslate.config.ConfigStore;
import org.sawiq.voicetranslate.config.RecognitionService;
import org.sawiq.voicetranslate.config.TranslateConfig;
import org.sawiq.voicetranslate.engine.EngineInstaller;
import org.sawiq.voicetranslate.engine.EnginePaths;
import org.sawiq.voicetranslate.engine.LocalEngine;
import org.sawiq.voicetranslate.language.SpeakerLanguages;
import org.sawiq.voicetranslate.net.ServiceException;
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

    private final CaptionBoard captions = new CaptionBoard();
    private final SpeakerLanguages languages = new SpeakerLanguages();
    private final OnlinePlayers players = new OnlinePlayers();
    private final GlossaryLoader glossary = new GlossaryLoader();
    private final Queue<Caption> finishedForChat = new ConcurrentLinkedQueue<>();
    /** Other windows showing these settings, such as Plasmo Voice's; told after every change. */
    private final List<Runnable> settingsListeners = new CopyOnWriteArrayList<>();
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
                this::onCaptionFinished, failure -> this.pendingFailure = failure);
        this.intake = new VoiceIntake(this.pipeline, () -> this.segmenterSettings, this::isTranslated);
        VoiceBridge.connect(this.intake);
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
        handleKeys();
        handleWorldChange(client);
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
        boolean wantsLocal = current.enabled && current.recognition.service == RecognitionService.LOCAL;
        this.engine.want(wantsLocal, current.recognition.localModel, current.recognition.localDevice);
        this.glossary.request(ServiceFactory.targetLanguage(current, this.lastGameLanguage), this::applySettings);
        this.pipeline.use(ServiceFactory.create(current, this.engine, this.lastGameLanguage, this.glossary.current()));
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
        String name = this.players.nameOf(caption.speakerId());
        if (!name.isEmpty()) {
            return name;
        }
        return caption.speakerName().isEmpty() ? caption.speakerId().toString().substring(0, 8) : caption.speakerName();
    }

    // --- Internals --------------------------------------------------------------

    /** Audio thread: whether this voice is worth cutting into sentences at all. */
    private boolean isTranslated(UUID speaker) {
        TranslateConfig current = this.config;
        return current.enabled && this.players.isOtherPlayer(speaker) && !this.mutedPlayers.contains(speaker);
    }

    private void onCaptionFinished(Caption caption) {
        if (this.config.display.chatLog) {
            this.finishedForChat.add(caption);
        }
    }

    private void handleKeys() {
        while (this.keys.openSettings().consumeClick()) {
            ClientAccess.openScreen(new SettingsScreen(ClientAccess.currentScreen()));
        }
        while (this.keys.openHistory().consumeClick()) {
            ClientAccess.openScreen(new SettingsScreen(ClientAccess.currentScreen(), SettingsScreen.Page.HISTORY));
        }
        while (this.keys.toggle().consumeClick()) {
            setEnabled(!this.config.enabled);
            ClientAccess.actionBar(Component.translatable(this.config.enabled
                    ? "voice-translate.toggle.on" : "voice-translate.toggle.off"));
        }
    }

    private void handleWorldChange(Minecraft client) {
        boolean isInWorld = client.player != null;
        if (isInWorld && !this.wasInWorld) {
            showWelcomeOnce();
        }
        if (!isInWorld && this.wasInWorld) {
            this.intake.clear();
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
        EngineInstaller.Progress outcome = this.engine.installer().takeOutcome();
        if (outcome == null) {
            return;
        }
        ClientAccess.chat(Component.translatable(outcome.state() == EngineInstaller.State.INSTALLED
                ? "voice-translate.engine.installed_chat" : "voice-translate.engine.failed_chat"));
    }
}
