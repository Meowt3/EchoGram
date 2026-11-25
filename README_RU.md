<div align="center">

<img src="assets/icon.svg" alt="EchoGram" width="96" />

# EchoGram

**Открытый нативный Android-плеер аудиокниг и стриминговый клиент Telegram в стиле Material 3 на базе TDLib и Media3.**

[![English](https://img.shields.io/badge/Language-English-1f6feb?style=for-the-badge)](README.md) [![Русский](https://img.shields.io/badge/Язык-Русский-2ea44f?style=for-the-badge)](README_RU.md)

[![Последний релиз](https://img.shields.io/github/v/release/Meowt3/EchoGram?style=flat-square&logo=github&color=2ea44f)](https://github.com/Meowt3/EchoGram/releases/latest)
[![Скачивания](https://img.shields.io/github/downloads/Meowt3/EchoGram/total?style=flat-square&logo=github)](https://github.com/Meowt3/EchoGram/releases)
[![Сборка APK](https://img.shields.io/github/actions/workflow/status/Meowt3/EchoGram/build.yml?style=flat-square&label=build)](https://github.com/Meowt3/EchoGram/actions/workflows/build.yml)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20%7C%20API%2035-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Meowt3/EchoGram/releases/latest)
[![TDLib](https://img.shields.io/badge/TDLib-1.8.56-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://github.com/tdlib/td)
[![Media3](https://img.shields.io/badge/Media3-1.4.1-blue?style=flat-square)](https://github.com/androidx/media)
[![Лицензия](https://img.shields.io/github/license/Meowt3/EchoGram?style=flat-square)](LICENSE)

[Скачать](#скачать) · [Возможности](#возможности) · [Звук и USB ЦАП](#звук-и-usb-цап) · [Архитектура](#архитектура) · [Сборка из исходников](#сборка-из-исходников)

</div>

> **English:** Open-source native Android audiobook player & Material 3 streaming Telegram client built on **TDLib** and **Media3 (ExoPlayer)**. Progressive MTProto streaming, offline downloads, cover-art dynamic themes, DSP presets, **Bit-Perfect USB DAC**, QR / phone login, and Telegram Saved Messages cloud sync. See [README.md](README.md) for the English documentation.

---

## О приложении

**EchoGram** превращает ваши каналы, группы, боты и «Избранное» в Telegram в полноценный стриминговый сервис для аудиокниг на Android без рекламы и подписок. Приложение написано нативно на **Kotlin** и **Jetpack Compose** в дизайне **Material 3**, использует официальную библиотеку **TDLib (MTProto)** и собственный потоковый аудиодвижок на **AndroidX Media3 (ExoPlayer)**.

## Скачать

Скачайте последний подписанный APK в разделе [GitHub Releases](https://github.com/Meowt3/EchoGram/releases/latest):

| Архитектура | Файл | Для каких устройств |
| --- | --- | --- |
| **`arm64-v8a`** *(Рекомендуется)* | [`EchoGram-v2.1.4-arm64-v8a-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-arm64-v8a-release.apk) | Современные 64-битные смартфоны (минимальный размер) |
| **`armeabi-v7a`** | [`EchoGram-v2.1.4-armeabi-v7a-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-armeabi-v7a-release.apk) | Старые 32-битные смартфоны и Android TV приставки |
| **`x86_64`** | [`EchoGram-v2.1.4-x86_64-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-x86_64-release.apk) | Эмуляторы, Chromebook, WSA и планшеты на x86_64 |
| **Universal** | [`EchoGram-v2.1.4-universal-release.apk`](https://github.com/Meowt3/EchoGram/releases/latest/download/EchoGram-v2.1.4-universal-release.apk) | Любой Android 8.0+ (если не уверены в архитектуре) |

### Быстрый старт

1. Скачайте и установите последний APK из [Releases](https://github.com/Meowt3/EchoGram/releases/latest).
2. Войдите в Telegram по **номеру телефона** или **QR-коду** (поддерживается вход в один тап через установленный на устройстве Telegram). При необходимости настройте **MTProto / SOCKS5 / HTTP прокси** прямо на экране входа.
3. Отметьте нужные каналы, боты и чаты в **Настройки → Источники** — главная лента, миксы, категории, серии и поиск сформируются автоматически.

## Возможности

- **Стриминговый дизайн Material 3**: динамическая цветовая палитра под обложку текущей книги (`Palette`), Светлая / Тёмная / Системная темы, 7 живых стилей полоски прогресса (*Волна, Свечение, Импульсы, Эквалайзер, Комета, Пунктир, Классика*), 5 сменных иконок приложения и виджет рабочего стола.
- **Умная аудиотека и рекомендации**: персональные миксы и автоплейлисты на Главной, автогруппировка категорий (по хештегам, темам, описанию и длительности), объединение многосерийных книг в серии, избранное, пользовательские плейлисты, статистика прослушивания и быстрый поиск.
- **Нативный клиент Telegram (TDLib)**: поддержка основных и архивных чатов, встроенный просмотр чатов Telegram с отправкой сообщений и команд ботам, настройка прокси по ссылкам (`tg://proxy`, `t.me/proxy`, `tg://socks`), а также облачная синхронизация прогресса, закладок и плейлистов через чат **«Избранное»** (плюс локальный экспорт/импорт JSON).
- **Прогрессивный стриминг и офлайн**: собственный `TdlibAudioDataSource` начинает воспроизведение мгновенно без ожидания полной загрузки файла, поддерживает быструю перемотку и сохранение любых аудиокниг в офлайн.
- **Функции для аудиокниг**: точное запоминание позиции по каждой книге, умная перемотка назад после паузы, регулировка скорости, **пропуск пауз (Skip Silence)**, настраиваемый шаг перемотки (`10с / 15с / 30с`), таймер сна (по минутам или до конца трека), закладки с заметками и навигация по встроенным главам ID3.

## Звук и USB ЦАП

EchoGram создан для комфортного звучания речи и работы с внешними Hi-Res ЦАП:

| Параметр | Подробности |
| --- | --- |
| **Форматы аудио** | `MP3`, `M4A`, `M4B`, `AAC`, `OGG`, `OPUS`, `FLAC`, `WAV`, `AMR`, `MKA`, `WEBM` |
| **DSP и разборчивость речи** | Пресеты эквалайзера (*Стандарт, Чёткая речь, Ночной режим, Глубокий бас*), усиление громкости (`+0…+12 дБ`) и пропуск пауз |
| **USB ЦАП и Bit-Perfect** | Автоопределение USB-C ЦАП (`AudioDeviceCallback`), выбор устройства вывода и режим **Bit-Perfect** (обход системного микшера Android 14+ с подбором частоты integer PCM под поток) |
| **Фоновое воспроизведение** | Media3 `MediaSessionService`, шторка с кнопками (`Пред / -15с / Пауза / +15с / След`), управление с гарнитуры и сетевой WakeLock |

## Архитектура

| Слой | Технология |
| --- | --- |
| **Язык и UI** | Kotlin `2.0.21`, Jetpack Compose, Material 3, AndroidX Palette, Coil |
| **Ядро Telegram** | [TDLib](https://github.com/tdlib/td) `1.8.56` через [tdlibx/td](https://github.com/tdlibx/td) (JNI MTProto) |
| **Аудиодвижок** | [AndroidX Media3](https://github.com/androidx/media) `1.4.1` (`ExoPlayer`, `MediaSessionService`, `TdlibAudioDataSource`) |
| **QR-авторизация** | [ZXing Core](https://github.com/zxing/zxing) `3.5.3` (`tg://login`) |
| **Платформа** | Android 8.0–15+ (`minSdk 26`, `targetSdk 35`) |

## Сборка из исходников

1. Установите **JDK 17** и **Android SDK (API 35)**.
2. Получите `api_id` и `api_hash` на [my.telegram.org](https://my.telegram.org).
3. Создайте `local.properties` в корне проекта (или задайте переменные окружения `TG_API_ID` / `TG_API_HASH`):
   ```properties
   tg.api.id=ВАШ_API_ID
   tg.api.hash=ВАШ_API_HASH
   ```
4. Запустите сборку:
   ```bash
   gradle assembleRelease
   ```
   Готовый файл появится в `app/build/outputs/apk/release/app-release.apk`.

## Благодарности и лицензия

- [TDLib](https://github.com/tdlib/td) и [tdlibx/td](https://github.com/tdlibx/td) — официальная библиотека клиента Telegram
- [AndroidX Media3](https://github.com/androidx/media) — медиадвижок и фоновые сессии
- [ZXing](https://github.com/zxing/zxing) — генерация QR-кодов

Проект распространяется под лицензией **GNU General Public License v3.0** — подробности в файле [LICENSE](LICENSE).
