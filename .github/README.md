![banner](banner-dark.png#gh-dark-mode-only)
![banner](banner-light.png#gh-light-mode-only)

# Sectile
[![License](https://img.shields.io/badge/License-Apache_2.0-F3CB09?style=for-the-badge)](LICENSE)
![Status](https://img.shields.io/badge/status-minimal_playable-2ea44f?style=for-the-badge)
![JDK](https://img.shields.io/badge/JDK-25-1C89CC?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge)
---
**Minimal and modular Hytale server implementation.**
---

The idea isn't to rewrite the whole server, but to give you the tools so you can do it your way and with your own rules.
Even people who don't know how to program can add plugins to extend it.

## Features
- **Fast Startup:** Boots in less than 500ms, making it ideal for ephemeral instances, lobbies, and minigame networks.
- **Joinable World:** Handles the full connection lifecycle (`HANDSHAKE` → `AUTHENTICATION` / `INSECURE_OPTIONS` → `PRE_LOGIN` → `POST_LOGIN`), spawning players into an empty void world with chunk streaming, position updates, and keep-alive ping/tick loops.
- **Isolated Microkernel:** Kernel details are hidden behind clean public APIs. Plugins run with strict classloader isolation so they can't break each other or the engine.
- **Developer experience first:** Designed to avoid rewriting the same files and to make things easier for the developer. No need to break anything internal (like NMS)
- **Clean Apache 2.0 License:** Pure clean-room protocol implementation focused on interoperability. No corporate NDAs, no competition bans, no closed gardens.

> [!WARNING]
> Not an Official Product: this is an independent, community-driven project and is not affiliated with, endorsed by, or produced by Hypixel > Studios Canada Inc.
> This is a minimal server engine and protocol implementation. It does not include vanilla gameplay features (survival mechanics, mob AI, world > generation, or crafting recipes) by default.
