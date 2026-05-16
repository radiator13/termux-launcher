# Shizuku Helper Examples

These examples are optional shell helpers for users who want tmux status widgets or a Shizuku-backed `btop` workflow. They are not installed by the APK and are not required for normal launcher use.

For a single ordered walkthrough, start with [tmux status setup](Launcher_Tmux_Status_Setup). This page is the lower-level reference for each downloadable helper.

They assume:

- Termux Launcher is installed and `launcherctl` works.
- `jq` is installed for JSON parsing.
- A Nerd Font is configured if you want the icons to render correctly.
- Shizuku/rish is configured for the `btop` helper.

Raw script base URL:

```sh
BASE='https://raw.githubusercontent.com/PickleHik3/termux-launcher/dev/docs/en/examples'
```

## System Stats Widget

Downloads a cached CPU/RAM formatter for tmux status bars. It uses `launcherctl resources` and caches results to avoid high-frequency API calls.

Install:

```sh
mkdir -p ~/.local/bin
curl -fsSL "$BASE/launcher-system-monitor" -o ~/.local/bin/launcher-system-monitor
chmod 700 ~/.local/bin/launcher-system-monitor
```

Usage:

```sh
launcher-system-monitor cpu
launcher-system-monitor ram
launcher-system-monitor compact
```

## Weather Widget

Downloads a cached weather formatter using free wttr.in automatic location detection. It uses day/night icon variants based on the device local time.

Install:

```sh
mkdir -p ~/.local/bin
curl -fsSL "$BASE/launcher-weather-widget" -o ~/.local/bin/launcher-weather-widget
chmod 700 ~/.local/bin/launcher-weather-widget
```

Usage:

```sh
launcher-weather-widget
```

## btop Through rish

Downloads a helper that installs a Linux `btop` binary into `/data/local/tmp` through Shizuku `rish`, then creates wrapper commands:

- `btop-shizuku`
- `mini-btop-shizuku`

The helper resolves `rish` from `$PATH` by default. Set `RISH_BIN=/path/to/rish` only if you need an explicit path.

Install:

```sh
curl -fsSL "$BASE/setup-btop-rish" -o ~/setup-btop-rish
chmod 700 ~/setup-btop-rish
```

Run setup:

```sh
~/setup-btop-rish
```

If you already have a compatible Linux `btop` binary:

```sh
~/setup-btop-rish --local /path/to/btop
```

## tmux Example

Example right-side status order: CPU, RAM, weather.

```tmux
set -g status-right '#(launcher-system-monitor cpu) │ #(launcher-system-monitor ram) │ #(launcher-weather-widget) '
```

Clickable CPU/RAM status ranges can open `mini-btop-shizuku` in a new tmux window on tmux 3.6 or newer:

```tmux
set -g status-right '#[range=user|btop]#(launcher-system-monitor cpu)#[range=none] │ #[range=user|btop]#(launcher-system-monitor ram)#[range=none] │ #(launcher-weather-widget) '
bind-key -n MouseDown1Status run-shell 'case "#{mouse_status_range}" in btop) command -v mini-btop-shizuku >/dev/null 2>&1 && tmux new-window -n btop "mini-btop-shizuku" || tmux display-message "Run ~/setup-btop-rish first" ;; esac'
```

Keep status polling modest:

```tmux
set -g status-interval 5
```

## Optional kew Ticker Row

If you use `kew`, the repo includes a `kew-tmux-status` helper that can toggle tmux between one status row and two status rows. The second row appears only when `kew-now-playing --peek` returns a track.

Install:

```sh
mkdir -p ~/.local/bin
curl -fsSL "$BASE/kew-tmux-status" -o ~/.local/bin/kew-tmux-status
chmod 700 ~/.local/bin/kew-tmux-status
```

Example theme lines:

```tmux
set -g status-right '#(kew-tmux-status)#(launcher-system-monitor cpu) │ #(launcher-system-monitor ram) │ #(launcher-weather-widget) '
set -g status-format[1] '#[align=centre]#(kew-now-playing)'
```
