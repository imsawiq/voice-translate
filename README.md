[![GitHub](https://cdn.modrinth.com/data/cached_images/53263a70bb7a7689208e40d465e4377be013ccca.png)](https://github.com/imsawiq)
[![Discord](https://cdn.modrinth.com/data/cached_images/be7e1b5fe7e280c38c5b29dd81c453ddfdad25c2.png)](https://discord.gg/UTxEy4PtSU)

# EN

**Fabric and NeoForge** client mod for **Simple Voice Chat** and **Plasmo Voice**. It translates what other players say into live subtitles in your language, above their heads and in a panel on screen, and can read the translations aloud or send your own speech in another language. Nobody else needs the mod, and the server does not need it either.

> Beta: things work, but expect rough edges. Bug reports in Discord help a lot.

### ✨ Features

- **Works with both voice mods** — one jar hooks into Simple Voice Chat, Plasmo Voice, or both. Proximity chat and group calls are translated; music discs and other mods' audio are left alone.
- **Subtitles above heads** — a bubble over the speaker while they are on screen, scaled with distance; a panel for players behind you, far away, or in a group call.
- **Live drafts** — the sentence appears while it is being spoken and settles into the final translation when the speaker pauses.
- **Recognition on your PC** — one click installs a speech engine, about 1 GB once. Parakeet understands 25 European languages on any processor, no graphics card needed; Whisper covers 99 languages and is fastest on an NVIDIA card. The mod starts and stops it by itself.
- **Or in the cloud** — Groq (free tier), OpenAI, or your own OpenAI-compatible server, with your own key.
- **Three translators** — Google with no key, DeepL with a free key, or an AI model (OpenAI, Groq, OpenRouter, Ollama, any compatible server) that keeps slang and tone. If the chosen one fails, Google fills in.
- **Knows Minecraft** — item, block, mob and biome names come from the game's own language files, so "ender pearls" becomes "эндер-жемчуг", not "жемчуг Эндера".
- **Hear translations instead of reading** — other players' translations are spoken from where they stand while their real voice gets quieter. Piper voices in 37 languages, male or female to match the speaker, quick on any processor.
- **Speak other languages yourself** — your phrase is translated and sent through the voice mod about two seconds after you pause. Other players need nothing installed.
- **Voice copying** (optional) — translations in the speaker's own voice, copied from a few seconds of their speech by OpenVoice on your processor. Every copied voice carries WavMark, an inaudible watermark that marks it as generated.
- **Step-by-step guide** — opens the first time you open the settings and walks through setup in plain words.
- **History, per-player switch, chat copy** — scroll back through everything said this session, turn translation off for single players, or have translations written into chat.
- **Part of the voice mod's menu** — a button next to Simple Voice Chat's icons and a section in Plasmo Voice's Addons tab.
- **English, Russian and Ukrainian interface**.

### 📦 Dependencies

- **Simple Voice Chat** or **Plasmo Voice** (or both) — at least one.
- **Fabric API** — Fabric builds only.
- Minecraft **26.1 – 26.3**.

### 🎮 Getting started

1. Install the mod next to your voice mod and open the settings with **J**, the speech bubble icon in Simple Voice Chat's menu, or the Addons tab in Plasmo Voice's settings.
2. The guide opens and walks you through it. The main step is **Install the speech engine**: about 1 GB once, it keeps going while you play and says in chat when it is done.
3. Join a server with voice chat. When someone talks, the translation appears above their head a second later, in your game's language.
4. To hear translations or send your speech translated, open the **Voice** tab. Piper voices download by themselves, about 60 MB per language. **Copy voices** needs one more install there, about 450 MB.

Everything goes into one folder, shared by all your Minecraft instances: `%LOCALAPPDATA%\VoiceTranslateEngine` on Windows, `~/.local/share/voice-translate-engine` on Linux, `~/Library/Application Support/VoiceTranslateEngine` on macOS. Nothing is installed system-wide. No graphics card or no wish to download? Pick **Groq** on the Speech tab with a free key from [console.groq.com](https://console.groq.com).

### 🔒 Where your voice goes

- **Speech engine on your PC** (default): other players' voices and your own are recognised on your computer and never leave it.
- **Groq, OpenAI or your own server** (if you pick one): voices are sent to that service to be recognised, with your key. The Speech tab says so.
- **Translation**: only the recognised text is sent, to Google, DeepL, or the AI model you chose.
- **Spoken translations and voice copying** run on your computer only. Your translated speech goes to other players through the voice mod, like your normal voice.
- **Downloads** happen only when you press install: the installer uv from GitHub (checked against a hash in the mod), Python packages from PyPI and PyTorch's index, models and voices from Hugging Face.
- Voices are never recorded to disk. API keys stay in `config/voice-translate.json`, masked on screen and never written to the log.

### 🙏 Credits

Speech recognition: [Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) by NVIDIA (CC-BY-4.0) through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [Whisper](https://github.com/openai/whisper) through [faster-whisper](https://github.com/SYSTRAN/faster-whisper). Voices: [Piper](https://github.com/rhasspy/piper), each voice under the licence on its model card. Voice copying: [OpenVoice V2](https://github.com/myshell-ai/OpenVoice) by MyShell (MIT) and [WavMark](https://github.com/wavmark/wavmark).

### 🔗 Links

- **GitHub:** [imsawiq/voice-translate](https://github.com/imsawiq/voice-translate)
- **Discord:** [imsawiq's server](https://discord.gg/UTxEy4PtSU)

# RU

Клиентский мод для **Fabric и NeoForge** под **Simple Voice Chat** и **Plasmo Voice**. Переводит речь других игроков в живые субтитры на вашем языке, над головой и на панели на экране, а ещё может зачитывать переводы вслух и отправлять вашу речь на другом языке. Остальным игрокам и серверу мод не нужен.

> Бета: всё работает, но шероховатости будут. Сообщения об ошибках в Discord очень помогают.

### ✨ Особенности

- **Работает с обоими голосовыми модами** — один jar подключается к Simple Voice Chat, Plasmo Voice или к обоим. Переводится голос рядом и групповые звонки; пластинки и звук других модов не трогаются.
- **Субтитры над головой** — облачко над говорящим, пока он на экране, с учётом расстояния; панель для тех, кто за спиной, далеко или в группе.
- **Живые черновики** — фраза появляется, пока её говорят, и становится итоговым переводом, когда человек делает паузу.
- **Распознавание на вашем ПК** — движок ставится одной кнопкой, один раз около 1 ГБ. Parakeet понимает 25 европейских языков на любом процессоре, видеокарта не нужна; Whisper знает 99 языков и быстрее всего на видеокарте NVIDIA. Мод сам запускает и останавливает движок.
- **Или в облаке** — Groq (есть бесплатный тариф), OpenAI или свой сервер с API как у OpenAI, по вашему ключу.
- **Три переводчика** — Google без ключа, DeepL с бесплатным ключом или ИИ-модель (OpenAI, Groq, OpenRouter, Ollama, любой совместимый сервер), которая сохраняет сленг и тон. Если выбранный не ответил, переведёт Google.
- **Знает Minecraft** — названия предметов, блоков, мобов и биомов берутся из языковых файлов игры, поэтому «ender pearls» становится «эндер-жемчуг», а не «жемчуг Эндера».
- **Слушать перевод, а не читать** — переводы других игроков звучат с того места, где они стоят, а их настоящий голос становится тише. Голоса Piper на 37 языках, мужской или женский под говорящего, быстро на любом процессоре.
- **Говорите на других языках сами** — ваша фраза переводится и уходит через голосовой мод примерно через две секунды после паузы. Другим игрокам ничего ставить не нужно.
- **Копирование голоса** (по желанию) — перевод звучит голосом самого говорящего, скопированным OpenVoice по нескольким секундам его речи, на вашем процессоре. Каждый скопированный голос несёт WavMark, неслышимый водяной знак, по которому видно, что голос сгенерирован.
- **Пошаговое объяснение** — открывается при первом входе в настройки и простыми словами проводит через настройку.
- **История, отключение для игроков, копия в чат** — всё сказанное за сессию, отключение перевода для отдельных игроков, дублирование в чат.
- **Встроен в меню голосового мода** — кнопка рядом с иконками Simple Voice Chat и раздел во вкладке «Аддоны» Plasmo Voice.
- **Интерфейс на английском, русском и украинском**.

### 📦 Зависимости

- **Simple Voice Chat** или **Plasmo Voice** (или оба) — хотя бы один.
- **Fabric API** — только для Fabric.
- Minecraft **26.1 – 26.3**.

### 🎮 С чего начать

1. Поставьте мод рядом с голосовым модом и откройте настройки клавишей **J**, иконкой с облачками в меню Simple Voice Chat или во вкладке «Аддоны» в настройках Plasmo Voice.
2. Откроется пошаговое объяснение. Главный шаг — **Установить движок распознавания**: один раз около 1 ГБ, ставится, пока вы играете, и сообщает в чат, когда готово.
3. Зайдите на сервер с голосовым чатом. Когда кто-то заговорит, через секунду над его головой появится перевод на языке вашей игры.
4. Чтобы слушать переводы или отправлять свою речь переводом, откройте вкладку **Озвучка**. Голоса Piper скачиваются сами, около 60 МБ на язык. Для **Копировать голоса** там нужна ещё одна установка, около 450 МБ.

Всё лежит в одной папке, общей для всех сборок Minecraft: `%LOCALAPPDATA%\VoiceTranslateEngine` на Windows, `~/.local/share/voice-translate-engine` на Linux, `~/Library/Application Support/VoiceTranslateEngine` на macOS. В систему ничего не ставится. Нет видеокарты или не хочется качать? Выберите **Groq** на вкладке «Речь» и вставьте бесплатный ключ с [console.groq.com](https://console.groq.com).

### 🔒 Куда уходит голос

- **Движок на вашем ПК** (по умолчанию): голоса других игроков и ваш распознаются на вашем компьютере и никуда с него не уходят.
- **Groq, OpenAI или свой сервер** (если выбрали): голоса отправляются в этот сервис на распознавание, по вашему ключу. Вкладка «Речь» об этом предупреждает.
- **Перевод**: отправляется только распознанный текст, в Google, DeepL или выбранную ИИ-модель.
- **Озвучка и копирование голоса** работают только на вашем компьютере. Ваша речь переводом уходит другим игрокам через голосовой мод, как обычный голос.
- **Загрузки** происходят только по кнопке установки: установщик uv с GitHub (сверяется с хешем внутри мода), пакеты Python с PyPI и сайта PyTorch, модели и голоса с Hugging Face.
- Голоса никогда не записываются на диск. Ключи API лежат в `config/voice-translate.json`, на экране скрыты и не пишутся в лог.

### 🙏 Благодарности

Распознавание: [Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) от NVIDIA (CC-BY-4.0) через [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [Whisper](https://github.com/openai/whisper) через [faster-whisper](https://github.com/SYSTRAN/faster-whisper). Голоса: [Piper](https://github.com/rhasspy/piper), у каждого голоса своя лицензия в его описании. Копирование голоса: [OpenVoice V2](https://github.com/myshell-ai/OpenVoice) от MyShell (MIT) и [WavMark](https://github.com/wavmark/wavmark).

### 🔗 Ссылки

- **GitHub:** [imsawiq/voice-translate](https://github.com/imsawiq/voice-translate)
- **Discord:** [сервер imsawiq](https://discord.gg/UTxEy4PtSU)
