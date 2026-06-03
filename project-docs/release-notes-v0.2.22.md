# v0.2.22

## What changed

- Added `launcherctl update-scripts` so existing users can refresh optional tmux/status helper scripts without repeating Getting Started.
- Updated the tmux CPU/RAM helper to prefer efficient `launcherctl resources` data, with a bounded cached `rish` fallback for plain Termux + Shizuku setups.
- Improved optional Shizuku btop setup so explicit `RISH_BIN=/path/to/rish` is preserved in generated wrappers.
- Updated tmux/Shizuku docs and helper download URLs for release use.

## Quick user help

- After installing the APK, open Termux Launcher once so `launcherctl` is installed.
- If you already use the optional tmux helpers, run:

```sh
launcherctl update-scripts
```

- If tmux is already running, reload it after updating helpers:

```sh
tmux source-file ~/.tmux.conf
```

- `launcher-system-monitor cpu` and `launcher-system-monitor ram` should now show real values through `launcherctl resources`.
