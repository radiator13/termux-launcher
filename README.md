# Termux Launcher

⚠️🤖 This project is mostly vibe-coded and experimental, but it is in a very usable state. A couple of GPT-5.5 security review passes have been done, and it does not seem to affect my Nothing Phone 2 battery life. It started because I liked TEL so much. 🤖⚠️

❗ this app cannot be installed alongside your existing termux app ❗
❗ if terminal slows down, run termux-reload-settings ❗

Termux Launcher is a terminal-first Android home launcher inspired by [TEL](https://github.com/t-e-l/tel), built on [termux-app](https://github.com/termux/termux-app) and [termux-monet](https://github.com/Termux-Monet/termux-monet).

[Download builds](https://github.com/PickleHik3/termux-launcher/releases) | [Getting Started](docs/en/Launcher_Getting_Started.md) | [LauncherCtl](docs/en/LauncherCtl_API.md) | [Termux AI](docs/en/Termux_AI.md) | [Changelog](CHANGELOG.md)

<img src="screenshots/demo.gif" alt="Launcher demo" width="360">

## About

Designed to be a Terminal/TUI Android home launcher.
What started out as me just wanting sixel image drawing in [TEL](https://github.com/t-e-l/tel) spiralled out of scope to what this project is today.
All credits go to the amazing developers and contributors of Termux, TEL, and Termux:Monet.

## Features

- Termux as the actual Android home launcher
- Sixel image drawing in terminal
- App dock with terminal app search
- Android Material theme integration for launcher surfaces and Termux shell theming
- `launcherctl` shell bridge for launching apps and reading launcher/system data
- On-device LLM backends using Google's LiteRT and Alibaba's MNN
- Optional Shizuku integration for screen lock and privileged status helpers

## Installation

Download the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases), install it, then select Termux Launcher as your Android home app.

Recommended setup:

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) for terminal and tmux-heavy use
- [Shizuku](https://github.com/rikkaapps/shizuku) only if you want optional privileged features
- Optional [termux-launcher-tmux](https://github.com/PickleHik3/termux-launcher-tmux) theme plugin, installed through the [Getting Started](docs/en/Launcher_Getting_Started.md) flow, for Material colors, CPU/RAM/weather widgets, extra keys, `kew`, and rish-backed `btop`
- Matching companion forks when using Termux add-ons:
  - [Termux:API](https://github.com/PickleHik3/termux-api/releases)
  - [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases)

See [Getting Started](docs/en/Launcher_Getting_Started.md) for the setup flow.

## Documentation

- [Getting Started](docs/en/Launcher_Getting_Started.md): install, launcher basics, tmux setup, rish setup, Extra Keys, and troubleshooting.
- [LauncherCtl](docs/en/LauncherCtl_API.md): shell commands, endpoint files, API basics, and permissions.
- [Termux AI](docs/en/Termux_AI.md): local model setup, `tai`, OpenAI-compatible clients, and troubleshooting.
- [Developer Docs](docs/en/Developer_Docs.md): advanced API routes, runtime notes, helper scripts, and security details.

## Quick Shell Example

Launch an Android app from the terminal:

```sh
launcherctl launch whatsapp
```

Example tmux binding:

```tmux
bind -n M-w run-shell 'tmux display-message "Opening WhatsApp"; launcherctl launch whatsapp >/dev/null 2>&1 || tmux display-message "Launch failed: WhatsApp"'
```

## Known Limitations

- When Termux is set as the home launcher and the last terminal shell exits, Android may recreate the activity before Termux can exit cleanly. Run `termux-reload-settings` if the terminal slows down or feels stale.

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/1.png" alt="Home screen" width="320"></td>
    <td><img src="screenshots/2.png" alt="Apps bar" width="320"></td>
  </tr>
  <tr>
    <td><img src="screenshots/3.png" alt="Settings" width="320"></td>
    <td><img src="screenshots/4.png" alt="Light theme" width="320"></td>
  </tr>
</table>

## Upstream Base

- [termux-app](https://github.com/termux/termux-app)
- [termux-monet](https://github.com/Termux-Monet/termux-monet)
- [TEL](https://github.com/t-e-l/tel)
