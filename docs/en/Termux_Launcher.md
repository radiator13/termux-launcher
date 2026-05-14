# Termux Launcher

Termux Launcher is a terminal-first Android launcher. It keeps a full Termux session as the home screen and adds launcher controls around it: pinned apps, folders, alphabet filtering, terminal-driven search, and shell automation through `launcherctl`.

## Project Shape

The launcher is heavily inspired by [TEL](https://github.com/t-e-l/tel). It started from [termux-monet](https://github.com/Termux-Monet/termux-monet) for sixel-capable terminal rendering, wallpaper support, and Material theming, then moved onto the upstream [termux-app](https://github.com/termux/termux-app) base.

The goal is not to replace Termux with a separate launcher shell. The Termux session remains the main surface, and the launcher features are built around that workflow.

## What It Does

- Acts as the Android home app.
- Runs the normal Termux terminal session on the home screen.
- Shows pinned apps and folders above the extra keys area.
- Provides an alphabet row for quick filtering and gesture launch.
- Supports terminal-driven app search.
- Exposes a local shell bridge named `launcherctl`.
- Uses wallpaper-aware Material styling and configurable blur.
- Supports optional Shizuku-backed integrations.

## Optional Integrations

Normal launcher use does not require Shizuku, root, Termux:API, or Termux:Styling.

Those integrations are useful for specific workflows:

- Shizuku: lock-screen gesture and privileged status helpers.
- Termux:API: Android-facing Termux helper commands.
- Termux:Styling: matching styling support from the companion fork.
- `tooie`: tmux/status bar integrations built around launcher data.

## Next Pages

- [Getting started](Launcher_Getting_Started)
- [Using the launcher](Launcher_Usage)
- [Shell integration](Launcher_Shell_Integration)
- [Terminal Material colors](Launcher_Material_Colors)
- [Troubleshooting](Launcher_Troubleshooting)
