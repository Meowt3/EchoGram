# EchoGram

**Нативный Android Telegram-клиент, созданный специально для прослушивания аудиокниг.**  
*A native Android Telegram client built specifically for audiobooks.*

[![Build EchoGram APK](https://github.com/Meowt3/EchoGram/actions/workflows/build.yml/badge.svg)](https://github.com/Meowt3/EchoGram/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Возможности (RU)

- **Вход без боли**: встроенные ключи Telegram API в релизных сборках — больше не нужно каждый раз вводить `api_id` и `api_hash`. Поддержка входа по **номеру телефона**, **QR-коду** (в том числе в один тап через установленный Telegram), **2FA-паролю** и **прокси (MTProto / SOCKS5 / HTTP)** прямо на экране входа.
- **Полноценный клиент Telegram**: список всех чатов и архива с папками (`Все`, `Аудиокниги`, `Каналы`, `Группы`, `Боты`, `Архив`), чтение и отправка сообщений (удобно для ботов поиска книг), глобальный поиск публичных каналов (`@username`).
- **Режим аудиотеки в любом чате**: мгновенное переключение между лентой чата и каталогом аудиокниг внутри канала; добавление каналов в «Источники аудиокниг» одной звёздочкой.
- **Прогрессивный стриминг и офлайн**: воспроизведение начинается сразу без ожидания полной загрузки файла, с авто-восстановлением при обрывах и скачиванием книг в офлайн.
- **Удобства для аудиокниг**: сохранение точной позиции по каждой книге, умная перемотка назад после паузы, скорость `0.75x–2.5x`, пропуск пауз (*Skip Silence*), таймер сна, закладки с заметками, главы ID3, группировка серий.
- **Звук и USB ЦАП**: встроенные DSP-пресеты (*Чёткий голос*, *Ночной*, *Бас*), усиление громкости и поддержка USB DAC / Bit-Perfect вывода на Android 14+.
- **Оформление Material 3**: Светлая, Тёмная, AMOLED и системная (Material You) темы.

## Features (EN)

- **Frictionless Telegram Sign-In**: API credentials are baked into release builds via CI secrets (no manual `api_id` / `api_hash` prompts). Supports **Phone + Code + 2FA**, **QR Code / One-Tap Telegram login**, and **MTProto / SOCKS5 / HTTP proxy** setup directly from the login screen.
- **Full Telegram Fork Workflow**: Paginated main & archive chats, folder filters, in-chat messaging & bot commands, global public channel search, and one-tap switching between Chat Feed and Audiobook Catalog.
- **Progressive TDLib Streaming & Offline Cache**: Instant chunked playback via a custom Media3 `BaseDataSource` with stale `file_reference` recovery and offline downloads.
- **Audiobook-First Player**: Per-track resume positions, smart rewind after pause, `0.75x–2.5x` speed, Skip Silence, Sleep Timer, timestamped bookmarks, ID3 chapters, DSP presets, and Android 14+ Bit-Perfect USB DAC routing.

---

## Скачать APK / Download

Готовые установочные файлы (`Release` и `Debug`) доступны во вкладке **[Releases](https://github.com/Meowt3/EchoGram/releases)** и в артефактах **[GitHub Actions](https://github.com/Meowt3/EchoGram/actions)**.

## Сборка из исходников / Build from Source

1. Установите JDK 17 и Android SDK (API 35).
2. Создайте `local.properties` в корне проекта и укажите ваши ключи с [my.telegram.org](https://my.telegram.org):
   ```properties
   tg.api.id=YOUR_API_ID
   tg.api.hash=YOUR_API_HASH
   ```
3. Соберите APK:
   ```bash
   gradle assembleRelease
   ```

## Технологии и благодарности / Credits

- [TDLib](https://github.com/tdlib/td) & [tdlibx/td](https://github.com/tdlibx/td) — MTProto client library
- [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) — Audio playback & MediaSessionService
- [Jetpack Compose & Material 3](https://developer.android.com/compose) — Modern declarative UI
- [ZXing](https://github.com/zxing/zxing) — QR code generation
