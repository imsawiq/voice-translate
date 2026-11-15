# Changelog

## Unreleased

### EN

- Other players' translations can be read aloud from where they stand, with their real voice turned down. Piper voices in 37 languages, matched to each speaker as male or female, take a fraction of a second on any processor and about 60 MB per language. Players' own voices can be copied with OpenVoice, an optional extra on the new Voice tab that also runs on the processor, about a second per sentence, and marks every copy with the WavMark watermark.
- Your own speech can be sent translated: the phrase is recorded while you talk and sent in the chosen language about two seconds after you stop. Works on Simple Voice Chat and Plasmo Voice, and other players need nothing.
- The speech engine is now light by default: Parakeet recognises 25 European languages on any processor in well under a second, and the whole install is about 1 GB instead of 3. Whisper stays available, and NVIDIA's libraries for it are an optional download.
- Switching the speech model no longer loses a downloaded model or starts a second download.

### RU

- Переводы других игроков можно слушать голосом с того места, где они стоят, а их настоящий голос становится тише. Голоса Piper на 37 языках, мужской или женский под говорящего, работают за доли секунды на любом процессоре и весят около 60 МБ на язык. Собственные голоса игроков можно копировать через OpenVoice: ставится отдельно на новой вкладке «Озвучка», тоже работает на процессоре, около секунды на фразу, и помечает каждую копию водяным знаком WavMark.
- Свою речь можно отправлять переводом: фраза записывается, пока вы говорите, и уходит на выбранном языке примерно через две секунды после того, как замолчите. Работает в Simple Voice Chat и Plasmo Voice, другим игрокам ничего не нужно.
- Движок распознавания теперь лёгкий по умолчанию: Parakeet распознаёт 25 европейских языков на любом процессоре быстрее секунды, а вся установка весит около 1 ГБ вместо 3. Whisper остался, библиотеки NVIDIA для него скачиваются по желанию.
- Смена модели распознавания больше не теряет скачанную модель и не запускает вторую загрузку.

## 1.0.0

### EN

First release, for Minecraft 26.1, 26.2 and 26.3 on Fabric and NeoForge.

- Translates other players' speech from Simple Voice Chat and Plasmo Voice into subtitles above their heads and in an on-screen panel.
- Recognition with Whisper on your own PC (one-click install, CUDA or processor), Groq, OpenAI, or your own server.
- Translation with Google (no key), DeepL, or an AI model through any OpenAI-compatible API, with Google as the fallback.
- Minecraft's own names for items, blocks, mobs and biomes are used in translations from English.
- Per-player language memory, a filter for Whisper's invented phrases, live drafts, history, per-player switches and an optional copy to chat.

### RU

Первый выпуск, для Minecraft 26.1, 26.2 и 26.3 на Fabric и NeoForge.

- Переводит речь других игроков из Simple Voice Chat и Plasmo Voice в субтитры над головой и на панели на экране.
- Распознавание через Whisper на вашем ПК (установка одной кнопкой, CUDA или процессор), Groq, OpenAI или свой сервер.
- Перевод через Google (без ключа), DeepL или ИИ-модель по любому API, совместимому с OpenAI; если что-то не ответило, переводит Google.
- В переводах с английского используются названия предметов, блоков, мобов и биомов из самой игры.
- Память языка каждого игрока, фильтр выдуманных Whisper фраз, живые черновики, история, отключение для отдельных игроков и дублирование в чат.
