# Termux Launcher

Termux Launcher is a terminal-first Android launcher built on top of [termux-app](https://github.com/termux/termux-app) and [termux-monet](https://github.com/Termux-Monet/termux-monet). It keeps the full Termux session front and center, adds a fast app bar for touch launch and alphabet filtering, and stays usable as a daily launcher instead of a one-off shell skin.

[Download builds from Releases](https://github.com/PickleHik3/termux-launcher/releases)

## Highlights

- Termux as the actual home screen, not a widget inside another launcher
- Pinned app row plus alphabet scrub filtering for the full installed app list
- Live install and uninstall refresh in the app bar without restarting the launcher
- Keyboard-first search flow with configurable split character handling
- Wallpaper-aware theming, blur controls, monochrome icons, and launcher visual tuning
- Optional Shizuku hooks for screen locking and privileged status integrations

## Companion Apps

Use the matching forks below to avoid shared UID or signing mismatches:

- [Termux:API](https://github.com/PickleHik3/termux-api)
- [Termux:Styling](https://github.com/PickleHik3/termux-styling)

## Optional Shizuku Integration

Shizuku is not required for normal launcher usage.

If enabled, the current privileged integrations are limited to:

- double-tap A-Z row to lock the screen
- system stats support for tmux status bar integrations

If you use the tmux status helpers, see [tooie](https://github.com/PickleHik3/tooie).

## Setup Notes

- [Shizuku](https://github.com/rikkaapps/shizuku) is optional.
- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) is strongly recommended for tmux-heavy use.
- By default, typing `/` in the terminal starts app search in the launcher bar. This can be changed in `Settings -> Apps Bar -> Input split character`.

## Shell Launching

You can launch apps directly from the shell with `launcherctl launch`:

```sh
launcherctl launch whatsapp
```

Example tmux binding:

```tmux
bind -n M-w run-shell 'tmux display-message "Opening WhatsApp"\; launcherctl launch whatsapp >/dev/null 2>&1 || tmux display-message "Launch failed: WhatsApp"'
```

## Demo

![Launcher demo](screenshots/launcher-demo.gif)

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/01-home.png" alt="Screenshot 1" width="320"></td>
    <td><img src="screenshots/02-terminal-status.png" alt="Screenshot 2" width="320"></td>
  </tr>
  <tr>
    <td><img src="screenshots/03-apps-bar.png" alt="Screenshot 3" width="320"></td>
    <td><img src="screenshots/04-settings-home.png" alt="Screenshot 4" width="320"></td>
  </tr>
  <tr>
    <td><img src="screenshots/05-look-and-feel.png" alt="Screenshot 5" width="320"></td>
    <td><img src="screenshots/06-apps-bar-settings.png" alt="Screenshot 6" width="320"></td>
  </tr>
  <tr>
    <td><img src="screenshots/07-terminal-overlay.png" alt="Screenshot 7" width="320"></td>
    <td><img src="screenshots/08-light-theme.png" alt="Screenshot 8" width="320"></td>
  </tr>
</table>

## Known Limitations

- Android 12+ phantom process restrictions can still affect long-running Termux workloads under heavy background pressure. See [termux-app issue #2366](https://github.com/termux/termux-app/issues/2366).
- If the shell exits while the launcher is active as the system home app, Android can leave the process in a degraded state until it is restarted.

## Upstream Base

- [termux-app](https://github.com/termux/termux-app)
- [termux-monet](https://github.com/Termux-Monet/termux-monet)
- [TEL](https://github.com/t-e-l/tel)
