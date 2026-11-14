[![GitHub](https://cdn.modrinth.com/data/cached_images/53263a70bb7a7689208e40d465e4377be013ccca.png)](https://github.com/imsawiq)
[![Discord](https://cdn.modrinth.com/data/cached_images/be7e1b5fe7e280c38c5b29dd81c453ddfdad25c2.png)](https://discord.gg/UTxEy4PtSU)

# EN

**Voice Translate** is a client mod for **Fabric and NeoForge** that translates what other players say in **Simple Voice Chat** or **Plasmo Voice** into live subtitles. Their speech is recognised with Whisper, translated into your language, and shown above their heads and in a panel on screen. Nobody else needs the mod, and the server does not need it either.

### ✨ Features

- **Works with both voice mods** — one jar hooks into Simple Voice Chat, Plasmo Voice, or both, whichever you have. Proximity chat and group calls are translated; music discs and other mods' audio are left alone.
- **Subtitles above heads** — a bubble over the speaker while they are on screen, scaled with distance; a panel at the bottom (or top) for players behind you, far away, or in a group call.
- **Live drafts** — with the local engine, the sentence appears while it is being spoken and settles into the final translation when the speaker pauses, usually under two seconds later.
- **Whisper on your own PC** — one click installs a private speech engine: large-v3-turbo on an NVIDIA graphics card, a smaller model on the processor. Nothing leaves your computer, there is no key and no bill. The mod starts and stops it by itself.
- **Or in the cloud** — Groq (very fast, has a free tier), OpenAI, or your own OpenAI-compatible server.
- **Three translators** — Google with no key at all, DeepL with a free key, or an AI model (OpenAI, Groq, OpenRouter, Ollama on your PC, or any compatible server) that keeps slang, tone and swearing and reads the previous lines for context. If the chosen one fails, Google fills in.
- **Knows Minecraft** — item, block, mob and biome names come from the game's own language files, so "follow me to the nether, I have ender pearls" becomes "следуй за мной в Незер, у меня есть эндер-жемчуг", not "в Пустоту, жемчуг Эндера".
- **Remembers who speaks what** — once a player has been heard in a language a few times, Whisper is told, which makes short phrases far more accurate. Players who switch language are followed.
- **Filters Whisper's inventions** — no "Thanks for watching" or "Продолжение следует..." over a player who only breathed.
- **Spoken translation in the speaker's voice** — optionally, every translation is also read aloud from the player who said it, in a copy of their own voice made from a few seconds of their speech, while their real voice gets quieter. Runs on your graphics card with Chatterbox, in 23 languages.
- **Speak other languages yourself** — turn on "Send my speech translated" and others hear what you said in English, German or any of 23 languages, in your voice, a few seconds after you finish. They need nothing installed.
- **Per-player switch, history, chat copy** — turn translation off for a friend who speaks your language, scroll back through everything said this session, copy it, or have translations written into the chat.
- **Part of the voice mod's menu** — a button next to Simple Voice Chat's own icons, and a Voice Translate section in Plasmo Voice's Addons tab with the main switches. The settings window itself is laid out like Plasmo Voice's settings and the voice changer studio.
- **English, Russian and Ukrainian interface**.

### 📦 Dependencies

- **Simple Voice Chat** or **Plasmo Voice** (or both) — at least one is needed, otherwise there is nothing to translate.
- **Fabric API** — Fabric builds only.
- Minecraft **26.1 – 26.3**. Builds for 1.21 are planned.

### 🎮 Getting started

1. Install the mod next to your voice mod. Open the settings with **J**, the speech bubble icon in Simple Voice Chat's menu, or the Addons tab in Plasmo Voice's settings.
2. On the **Speech** tab, press **Install the speech engine**. It downloads about 3 GB once (1.6 GB of it is the model), keeps going while you play, and says in chat when it is done. Everything goes into one folder, shared by all your Minecraft instances:
   - Windows: `%LOCALAPPDATA%\VoiceTranslateEngine`
   - Linux: `~/.local/share/voice-translate-engine`
   - macOS: `~/Library/Application Support/VoiceTranslateEngine`
3. That is it. Subtitles appear as people talk, in your game's language. The **General** tab changes the target language and shows how fast translation is going.

### 🔊 Spoken translation

The **Voice** tab has two switches that need a second, bigger engine. Press **Install the voice engine** there: about 6 GB once, into the same folder. It needs an NVIDIA graphics card with about 5 GB of free memory next to the speech engine (an 8 GB card runs both); on the processor it works but is far too slow to keep up.

- **Speak translations** reads other players' translations aloud from where they stand. With **In the speaker's voice** on, the first few seconds of a player's speech are used to copy their voice; until then, and with the switch off, a neutral voice speaks. **Original voice** sets how quiet their own voice becomes.
- **Send my speech translated** records your phrase while you talk and sends nothing; when you stop, it is recognised, translated into the language you picked and sent through the voice mod in your voice. On an RTX 4060 Ti a five-second phrase arrives about five to seven seconds after you stop. If anything fails, your original phrase is sent. A key to flip it is in the controls.

Voices are spoken in Arabic, Chinese, Danish, Dutch, English, Finnish, French, German, Greek, Hebrew, Hindi, Italian, Japanese, Korean, Malay, Norwegian, Polish, Portuguese, Russian, Spanish, Swahili, Swedish and Turkish. Other languages, Ukrainian among them, stay subtitles.

No graphics card, or no wish to download anything? Pick **Groq** on the Speech tab and paste a free key from [console.groq.com](https://console.groq.com). Groq's Whisper is as accurate as the local one and answers in a fraction of a second.

### ⚙️ Settings

| Tab | Setting | What it does |
|---|---|---|
| General | Translate into | Your game's language, or any of 40 others |
| General | Players speak | Detect per player, or fix one language for everyone |
| General | Subtitles for my language | Also show speech that needs no translation |
| Speech | Recognition | This computer, Groq, OpenAI, or your own server |
| Speech | Model / Runs on | Local model size and graphics card or processor |
| Speech | Pause / Sensitivity | How long a silence ends a sentence, and what counts as speech |
| Translation | Translator | Google, DeepL or an AI model, with keys and model names |
| Display | Above heads / Panel | Where subtitles go; the panel can show everyone, only players off screen, or nobody |
| Display | Show original, live drafts, copy to chat, text size, background, bubble distance | |
| Voice | Speak translations / In the speaker's voice | Read other players' translations aloud, in a copy of their voice |
| Voice | Original voice / Translation volume | How quiet the real voice gets, how loud the spoken translation is |
| Voice | Send my speech translated / Into | Others hear you in another language, in your voice |
| History | | Everything translated this session, newest first; copy or clear |
| Players | | Turn translation off for single players |

Keys, in Minecraft's controls under **Voice Translate**: open settings (**J**), turn translation on and off, open history, send my speech translated on and off (the last three unbound).

The settings live in `config/voice-translate.json`. API keys are stored there in plain text, like any other mod's settings; they are masked on screen and never written to the log.

### 🔒 Privacy

With the local engine, nothing about other players' voices leaves your computer. With Groq or OpenAI, their voices are sent to that service to be recognised, and the Speech tab says so. Recognised text is sent to the translator you chose. Spoken translation runs only on your computer. Voices are never recorded to disk.

### 🛠️ Building

```
build-all.bat
```

builds all six jars (26.1, 26.2, 26.3 × Fabric, NeoForge) into `dist/`. One target: `gradlew :26.3.x-fabric:buildAndCollect`. A dev client starts with Simple Voice Chat by default, or with Plasmo Voice: `gradlew :26.3.x-fabric:runClient -Pvoice=plasmo`. Java 25 is needed.

### 🔗 Links

- [GitHub](https://github.com/imsawiq/voice-translate)
- [Discord](https://discord.gg/UTxEy4PtSU)

# RU

**Voice Translate** — клиентский мод для **Fabric и NeoForge**, который переводит речь других игроков в **Simple Voice Chat** или **Plasmo Voice** в живые субтитры. Речь распознаётся Whisper, переводится на ваш язык и показывается над головой говорящего и на панели на экране. Остальным игрокам и серверу мод не нужен.

### ✨ Особенности

- **Работает с обоими голосовыми модами** — один jar подключается к Simple Voice Chat, Plasmo Voice или к обоим сразу. Переводится голос рядом и групповые звонки; музыкальные пластинки и звук других модов не трогаются.
- **Субтитры над головой** — облачко над говорящим, пока он на экране, с учётом расстояния; панель снизу (или сверху) для тех, кто за спиной, далеко или в группе.
- **Живые черновики** — с локальным движком фраза появляется, пока её произносят, и превращается в итоговый перевод, когда человек делает паузу, обычно меньше чем через две секунды.
- **Whisper на вашем ПК** — движок распознавания ставится одной кнопкой: large-v3-turbo на видеокарте NVIDIA, модель поменьше на процессоре. Ничего не уходит с компьютера, не нужен ключ и ничего не платится. Мод сам запускает и останавливает движок.
- **Или в облаке** — Groq (очень быстро, есть бесплатный тариф), OpenAI или свой сервер с API как у OpenAI.
- **Три переводчика** — Google без всякого ключа, DeepL с бесплатным ключом или ИИ-модель (OpenAI, Groq, OpenRouter, Ollama на вашем ПК или любой совместимый сервер), которая сохраняет сленг, тон и мат и учитывает предыдущие фразы. Если выбранный переводчик не ответил, переведёт Google.
- **Знает Minecraft** — названия предметов, блоков, мобов и биомов берутся из языковых файлов самой игры, поэтому «follow me to the nether, I have ender pearls» становится «следуй за мной в Незер, у меня есть эндер-жемчуг», а не «в Пустоту, жемчуг Эндера».
- **Помнит, кто на каком языке говорит** — после нескольких фраз Whisper получает подсказку о языке игрока, и короткие фразы распознаются заметно точнее. Если игрок перешёл на другой язык, мод это заметит.
- **Отсеивает выдумки Whisper** — никаких «Продолжение следует...» и «Субтитры сделал DimaTorzok» поверх игрока, который просто вздохнул.
- **Озвучка перевода голосом говорящего** — по желанию каждый перевод ещё и звучит от игрока, который его сказал, копией его собственного голоса по нескольким секундам речи, а настоящий голос становится тише. Работает на видеокарте через Chatterbox, на 23 языках.
- **Говорите на других языках сами** — включите «Отправлять мою речь переводом», и остальные услышат сказанное вами на английском, немецком или любом из 23 языков вашим голосом, через несколько секунд после того, как вы закончили. Им ничего ставить не нужно.
- **Выключение для отдельных игроков, история, копия в чат** — отключите перевод для друга, который говорит на вашем языке, пролистайте всё сказанное за сессию, скопируйте или дублируйте переводы в чат.
- **Встроен в меню голосового мода** — кнопка рядом с иконками Simple Voice Chat и раздел Voice Translate во вкладке «Аддоны» в настройках Plasmo Voice с главными переключателями. Само окно настроек устроено как настройки Plasmo Voice и студия voice changer'а.
- **Интерфейс на английском, русском и украинском**.

### 📦 Зависимости

- **Simple Voice Chat** или **Plasmo Voice** (или оба) — нужен хотя бы один, иначе переводить нечего.
- **Fabric API** — только для Fabric.
- Minecraft **26.1 – 26.3**. Сборки для 1.21 будут позже.

### 🎮 С чего начать

1. Поставьте мод рядом с голосовым модом. Настройки открываются клавишей **J**, иконкой с облачками в меню Simple Voice Chat или со вкладки «Аддоны» в настройках Plasmo Voice.
2. На вкладке **Речь** нажмите **Установить движок распознавания**. Один раз скачается около 3 ГБ (1,6 ГБ из них — модель), установка идёт, пока вы играете, и сообщит в чат, когда закончит. Всё лежит в одной папке, общей для всех сборок Minecraft:
   - Windows: `%LOCALAPPDATA%\VoiceTranslateEngine`
   - Linux: `~/.local/share/voice-translate-engine`
   - macOS: `~/Library/Application Support/VoiceTranslateEngine`
3. Готово. Субтитры появляются, пока люди говорят, на языке вашей игры. На вкладке **Основное** можно сменить язык перевода и посмотреть, насколько быстро всё работает.

### 🔊 Озвучка

На вкладке **Озвучка** два переключателя, которым нужен второй, более тяжёлый движок. Нажмите там **Установить голосовой движок**: один раз около 6 ГБ, в ту же папку. Нужна видеокарта NVIDIA, у которой рядом с движком распознавания свободно около 5 ГБ памяти (карта на 8 ГБ тянет оба); на процессоре работает, но слишком медленно, чтобы успевать.

- **Озвучивать перевод** читает переводы других игроков вслух с того места, где они стоят. С **Голосом говорящего** первые несколько секунд речи игрока идут на копию его голоса; до этого, и если переключатель выключен, говорит нейтральный голос. **Громкость оригинала** задаёт, насколько тише становится его собственный голос.
- **Отправлять мою речь переводом** записывает фразу, пока вы говорите, и ничего не отправляет; когда замолчите, она распознаётся, переводится на выбранный язык и уходит через голосовой мод вашим голосом. На RTX 4060 Ti пятисекундная фраза доходит примерно через пять–семь секунд после того, как вы замолчали. Если что-то не удалось, уйдёт исходная фраза. Клавиша для переключения есть в управлении.

Озвучиваются английский, арабский, греческий, датский, иврит, испанский, итальянский, китайский, корейский, малайский, немецкий, нидерландский, норвежский, польский, португальский, русский, суахили, турецкий, финский, французский, хинди, шведский и японский. Остальные языки, в том числе украинский, остаются субтитрами.

Нет видеокарты или не хочется ничего скачивать? Выберите **Groq** на вкладке «Речь» и вставьте бесплатный ключ с [console.groq.com](https://console.groq.com). Whisper у Groq такой же точный, как локальный, и отвечает за доли секунды.

### ⚙️ Настройки

| Вкладка | Настройка | Что делает |
|---|---|---|
| Основное | Переводить на | Язык игры или любой из 40 других |
| Основное | Игроки говорят на | Определять для каждого или задать один язык для всех |
| Основное | Субтитры на моём языке | Показывать и речь, которую переводить не нужно |
| Речь | Распознавание | Этот компьютер, Groq, OpenAI или свой сервер |
| Речь | Модель / Работает на | Размер локальной модели, видеокарта или процессор |
| Речь | Пауза / Чувствительность | Сколько тишины заканчивает фразу и что считать речью |
| Перевод | Переводчик | Google, DeepL или ИИ-модель, с ключами и названием модели |
| Отображение | Над головой / Панель | Где показывать субтитры; панель — для всех, только для тех, кого не видно, или скрыта |
| Отображение | Оригинал, живые черновики, дублировать в чат, размер текста, фон, расстояние для облачков | |
| Озвучка | Озвучивать перевод / Голосом говорящего | Читать переводы других игроков вслух копией их голоса |
| Озвучка | Громкость оригинала / Громкость перевода | Насколько тише настоящий голос и насколько громко звучит перевод |
| Озвучка | Отправлять мою речь переводом / На язык | Остальные слышат вас на другом языке вашим голосом |
| История | | Всё переведённое за сессию, новое сверху; копировать или очистить |
| Игроки | | Отключить перевод для отдельных игроков |

Клавиши в управлении Minecraft, раздел **Voice Translate**: открыть настройки (**J**), включить и выключить перевод, открыть историю, включить и выключить отправку моей речи переводом (последние три без назначения).

Настройки хранятся в `config/voice-translate.json`. Ключи API лежат там открытым текстом, как и настройки любого другого мода; на экране они скрыты и никогда не пишутся в лог.

### 🔒 Приватность

С локальным движком ничего о голосах других игроков не покидает ваш компьютер. С Groq или OpenAI их голоса отправляются в этот сервис для распознавания, и вкладка «Речь» об этом предупреждает. Распознанный текст отправляется выбранному переводчику. Озвучка работает только на вашем компьютере. Голоса никогда не записываются на диск.

### 🛠️ Сборка

```
build-all.bat
```

собирает все шесть jar (26.1, 26.2, 26.3 × Fabric, NeoForge) в `dist/`. Одна цель: `gradlew :26.3.x-fabric:buildAndCollect`. Клиент для разработки запускается с Simple Voice Chat, а с Plasmo Voice так: `gradlew :26.3.x-fabric:runClient -Pvoice=plasmo`. Нужна Java 25.

### 🔗 Ссылки

- [GitHub](https://github.com/imsawiq/voice-translate)
- [Discord](https://discord.gg/UTxEy4PtSU)
