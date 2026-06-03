# Optional Shizuku Integration

Shizuku is not required for normal launcher usage.

When enabled and granted, Shizuku supports optional privileged launcher features:

- Double-tap the alphabet row to lock the phone.
- System status helpers for tmux/status bar integrations.

## Setup

1. Install and start [Shizuku](https://github.com/rikkaapps/shizuku).
2. Open Termux Launcher.
3. Grant the launcher Shizuku permission when prompted.
4. Check status:

```sh
launcherctl status
```

The launcher should still work if Shizuku is stopped, missing, or denied. In that state, only Shizuku-backed features are unavailable.

## Lock Screen Gesture

When Shizuku is available, double-tap the alphabet row to lock the phone. This uses the Shizuku backend instead of an Android accessibility service.

If it does not work:

```sh
launcherctl status
```

Confirm that Shizuku is running and that Termux Launcher has permission.

## TTY Commands

Use `tty-exec` for interactive tools that need a real terminal:

```sh
launcherctl tty-doctor
launcherctl tty-exec "id"
```

`tty-doctor` checks the local `rish` files used for Shizuku-backed terminal commands.

## Optional Helper Examples

For the full manual setup order, including tmux config, widgets, extra keys, and optional `btop`, follow [tmux status setup](Launcher_Tmux_Status_Setup).

The wiki also includes optional downloadable examples for tmux CPU/RAM widgets, a free cached weather widget, and a `btop` helper that runs through Shizuku `rish`:

- [Shizuku helper examples](Launcher_Shizuku_Examples)
- [launcher-system-monitor](examples/launcher-system-monitor)
- [launcher-weather-widget](examples/launcher-weather-widget)
- [setup-btop-rish](examples/setup-btop-rish)

Quick install:

```sh
BASE='https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/docs/en/examples'
mkdir -p ~/.local/bin
curl -fsSL "$BASE/launcher-system-monitor" -o ~/.local/bin/launcher-system-monitor
curl -fsSL "$BASE/launcher-weather-widget" -o ~/.local/bin/launcher-weather-widget
curl -fsSL "$BASE/setup-btop-rish" -o ~/setup-btop-rish
chmod 700 ~/.local/bin/launcher-system-monitor ~/.local/bin/launcher-weather-widget ~/setup-btop-rish
```

After updating the APK, refresh already-installed helper scripts without repeating the full setup:

```sh
launcherctl update-scripts
```

The CPU/RAM helper uses `launcherctl resources` first. Its `rish` fallback is useful for plain Termux + Shizuku setups, but it is less efficient because it has to start a Shizuku shell for system sampling.

## Privacy Notes

Notification and media helpers require Android notification listener access. Without that permission, the launcher still works normally, but `launcherctl media`, `launcherctl art`, and `launcherctl notifications` return limited or empty data.
