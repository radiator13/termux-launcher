# Termux Launcher

⚠️🤖 this project is entirely vibe coded, it is in very usable state, a couple of passes for security concerns has been done with GPT 5.5 default, does not seem to affect my battery life (nothing phone 2) 🤖⚠️

❗ this app cannot be installed alongside your existing termux app ❗
❗ if terminal slows down, run termux-reload-settings ❗

Termux Launcher is a terminal-first Android home launcher inspired by [TEL](https://github.com/t-e-l/tel), built on [termux-app](https://github.com/termux/termux-app) and [termux-monet](https://github.com/Termux-Monet/termux-monet). 

[Download builds](https://github.com/PickleHik3/termux-launcher/releases) | [Documentation](docs/en/index.md) | [LauncherCtl API](docs/en/LauncherCtl_API.md) | [Changelog](CHANGELOG.md)

<img src="screenshots/demo.gif" alt="Launcher demo" width="360">

## About

Designed to be a Terminal/ TUI android home launcher. 
What started out as me just wanting sixel image drawing in [TEL](https://github.com/t-e-l/tel) spiralled out of scope to what this project is today. 
All credits go to the amazing developers and contributors of Termux, TEL & Termux:Monet.

## Features

- Termux as the actual Android home launcher
- Sixel image drawing in terminal (don't attempt gif's)
- App Dock with terminal app search
- Integration with Android system material themes for both Termux-launcher surfaces as well as exposed inside termux shell so you can use it for theming your shell/prompt or tmux.
- `launcherctl` shell bridge for launching apps and reading launcher/system data, read more at [LauncherCtl API](docs/en/LauncherCtl_API.md)
- On device LLM backends using Google's LiteRT and Alibaba's MNN, read more at [Termux AI](https://github.com/PickleHik3/termux-launcher/blob/experimental/docs/en/Termux_AI.md) 
- Optional Shizuku integration for screen lock and privileged status helpers, Rear more at [Optional Shizuku integration](docs/en/Launcher_Optional_Shizuku.md)

## Installation

Download the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases) and matching companion forks when using Termux add-ons:
  - [Termux:API](https://github.com/PickleHik3/termux-api)
  - [Termux:Styling](https://github.com/PickleHik3/termux-styling)

See [Getting Started](docs/en/Launcher_Getting_Started.md) for the setup flow.

Recommended Apps:

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) 
- [Shizuku](https://github.com/rikkaapps/shizuku) only if you want optional privileged features
- Optional [termux-launcher-tmux](https://github.com/PickleHik3/termux-launcher-tmux) theme plugin, or the manual [tmux status setup](docs/en/Launcher_Tmux_Status_Setup.md), for Material colors, CPU/RAM/weather widgets, extra keys, `kew`, and rish-backed `btop`

## Documentation

- [Getting started](docs/en/Launcher_Getting_Started.md)
- [Using the launcher](docs/en/Launcher_Usage.md)
- [Shell integration](docs/en/Launcher_Shell_Integration.md)
- [Terminal Material colors](docs/en/Launcher_Material_Colors.md)
- [Termux extra keys](docs/en/Termux_Extrakeys.md)
- [tmux status setup](docs/en/Launcher_Tmux_Status_Setup.md) and [theme plugin](https://github.com/PickleHik3/termux-launcher-tmux)
- [Optional Shizuku integration](docs/en/Launcher_Optional_Shizuku.md)
- [Shizuku helper examples](docs/en/Launcher_Shizuku_Examples.md)
- [LauncherCtl API](docs/en/LauncherCtl_API.md)
- [tui apps/toys](docs/tui.txt)
- [Troubleshooting](docs/en/Launcher_Troubleshooting.md)

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

- When Termux is set as the home launcher and the last terminal shell exits, Android may recreate the activity before Termux can exit its process cleanly, can be fixed by running 'termux-reload-settings'.

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
