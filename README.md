# EchoGram

**Стриминговый Material 3 клиент Telegram для аудиокниг на Android.**  
*A Material 3 streaming Telegram client for audiobooks on Android.*

[![Build EchoGram APK](https://github.com/Meowt3/EchoGram/actions/workflows/build.yml/badge.svg)](https://github.com/Meowt3/EchoGram/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Возможности (RU)

- **Стриминговый интерфейс Material 3**: динамическая цветовая палитра под обложку текущей книги, **Главная** с персональными миксами, автоплейлистами и рекомендациями, **Категории** (автогруппировка по хештегам, темам и длительности), **Поиск** и **Библиотека** (избранное, плейлисты, скачанное, серии книг, статистика чтения, источники).
- **Быстрый вход в Telegram без `api_id` и `api_hash`**: ключи Telegram API вшиваются при сборке из GitHub Actions Secrets — при запуске открывается полноценный экран входа по **номеру телефона**, **QR-коду** (или в один тап через установленный Telegram), с поддержкой **2FA**, **прокси (MTProto / SOCKS5 / HTTP)** прямо на экране входа и офлайн-режима.
- **Встроенный клиент Telegram (TDLib 1.8.56)**: полная пагинация основных и архивных чатов/каналов/ботов, гибридная загрузка новых аудиокниг (`GetChatHistory` + `SearchChatMessages`), встроенный экран чата Telegram с отправкой сообщений, синхронизация прогресса через «Избранное» Telegram.
- **Плеер для аудиокниг (Media3 / ExoPlayer)**: 7 стилей анимации полоски прогресса, прогрессивный стриминг с автовосстановлением при обрывах, офлайн-загрузка, сохранение позиции, умная перемотка после паузы, скорость, пропуск пауз, таймер сна, закладки, главы ID3, виджет рабочего стола и 5 вариантов иконки приложения.
- **Звук и USB ЦАП**: DSP-пресеты (*Чёткая речь*, *Ночной*, *Глубокий бас*), усиление громкости и вывод на USB DAC / Bit-Perfect на Android 14+.

## Features (EN)

- **Material 3 Streaming UI**: Dynamic cover-art palette, Home screen with mixes & auto-playlists, auto-discovered Categories, Search, Library (favorites, playlists, offline, series, reading stats, sources), 7 progress bar styles, home-screen widget, and 5 app icon presets.
- **Frictionless Telegram Sign-In**: API keys injected via CI secrets (no manual `api_id`/`api_hash` prompts). Full-screen login with **Phone + Code + 2FA**, **QR Code / One-Tap Telegram login**, and pre-login **MTProto / SOCKS5 / HTTP proxy** support.
- **Native TDLib + Media3 Engine**: Paginated Main & Archive chat loading, hybrid recent + search audiobook discovery, in-app Telegram chat view, Telegram Saved Messages cloud sync, progressive chunked streaming, offline cache, ID3 chapters, DSP presets, and Android 14+ Bit-Perfect USB DAC routing.

---

## Скачать APK / Download

Готовые APK (`Release` и `Debug`) доступны во вкладке **[Releases](https://github.com/Meowt3/EchoGram/releases)**.
