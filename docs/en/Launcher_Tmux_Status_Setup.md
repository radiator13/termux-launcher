# tmux Status Setup

This page shows the manual setup path for the example tmux status bar used with Termux Launcher. Nothing here is installed by the APK. Each command is optional and can be copied one step at a time.

If you use TPM, the easiest path is the separate [termux-launcher-tmux](https://github.com/PickleHik3/termux-launcher-tmux) plugin. It packages the Material tmux theme and keeps this page focused on the helper scripts and manual example files.

The example status bar includes:

- Material colors from the launcher wallpaper theme.
- CPU and RAM widgets from `launcherctl resources`, with a `rish` fallback for plain Termux setups.
- A free cached weather widget using automatic location lookup.
- Optional click-to-open `btop` through Shizuku `rish`.
- Optional second tmux status row for the `kew` now-playing ticker.
- Optional extra-keys button for `termux-reload-settings`.

## 1. Requirements

Install the basic Termux packages used by the examples:

```sh
pkg update
pkg install tmux curl jq
```

For icons, use a terminal font with Nerd Font symbols. Without it, the widgets still work but some icons may show as boxes.

Optional features need extra setup:

- Shizuku is needed for Shizuku-backed launcher features, CPU/RAM resource access, and the rish-backed `btop` helper.
- `kew` is only needed if you want the second-row music ticker.

## TPM Theme Plugin

Install the helper scripts in section 3 if you want CPU, RAM, weather, or `kew` widgets. Then add the theme plugin to `~/.tmux.conf`:

```tmux
set -g @plugin 'PickleHik3/termux-launcher-tmux'

run '~/.tmux/plugins/tpm/tpm'
```

Reload tmux and press `prefix + I` to install it. The plugin README includes a screenshot and the current layout details.

## 2. Check LauncherCtl

Open Termux Launcher once, then check that the shell bridge works:

```sh
launcherctl status
launcherctl apps
```

If `launcherctl` is missing, restart Termux Launcher and try again. The APK installs the command when the launcher session starts.

## 3. Download the Status Helpers

The helper scripts live in the wiki examples. They are downloaded into your Termux home directory, not bundled into the APK.

```sh
BASE='https://raw.githubusercontent.com/PickleHik3/termux-launcher/dev/docs/en/examples'
mkdir -p ~/.local/bin
curl -fsSL "$BASE/launcher-system-monitor" -o ~/.local/bin/launcher-system-monitor
curl -fsSL "$BASE/launcher-weather-widget" -o ~/.local/bin/launcher-weather-widget
curl -fsSL "$BASE/kew-tmux-status" -o ~/.local/bin/kew-tmux-status
chmod 700 ~/.local/bin/launcher-system-monitor
chmod 700 ~/.local/bin/launcher-weather-widget
chmod 700 ~/.local/bin/kew-tmux-status
```

Test them:

```sh
launcher-system-monitor cpu
launcher-system-monitor ram
launcher-weather-widget
```

If you already installed the helper scripts and only want to refresh them after an APK or docs update, run:

```sh
launcherctl update-scripts
```

This updates the repo-owned helper scripts and keeps your `~/.tmux.conf` unchanged.

The `launcher-system-monitor` helper prefers `launcherctl resources`. The `rish` fallback is kept for compatibility with plain Termux + Shizuku users, but it is less efficient because it has to start a Shizuku shell to sample system files.

## 4. Download the tmux Example

This replaces `~/.tmux.conf`, so make a backup first if you already have one:

```sh
BASE='https://raw.githubusercontent.com/PickleHik3/termux-launcher/dev/docs/en/examples'
[ -f ~/.tmux.conf ] && cp ~/.tmux.conf ~/.tmux.conf.backup
mkdir -p ~/.tmux
curl -fsSL "$BASE/tmux.conf" -o ~/.tmux.conf
curl -fsSL "$BASE/material-theme.tmux" -o ~/.tmux/material-theme.tmux
chmod 600 ~/.tmux.conf
chmod 700 ~/.tmux/material-theme.tmux
```

Start tmux:

```sh
tmux
```

If tmux is already running:

```sh
tmux source-file ~/.tmux.conf
```

## 5. Optional Shizuku btop Helper

Install and start Shizuku first, then make sure `rish` is available in your `$PATH`.

Download the helper:

```sh
BASE='https://raw.githubusercontent.com/PickleHik3/termux-launcher/dev/docs/en/examples'
curl -fsSL "$BASE/setup-btop-rish" -o ~/setup-btop-rish
chmod 700 ~/setup-btop-rish
```

Run it:

```sh
~/setup-btop-rish
```

After setup, tapping the CPU or RAM area in the example tmux status bar opens `mini-btop-shizuku` in a new tmux window on tmux 3.6 or newer.

The mini layout is the better default for Android: it keeps CPU and processes visible, uses a slower two-second refresh, and disables network, disk, and I/O panels because those counters are often unavailable or incomplete through Shizuku `rish`.

`setup-btop-rish` works in plain Termux as long as `rish` works. Termux Launcher users should still prefer `launcherctl resources` for tmux CPU/RAM widgets because it avoids repeated Shizuku shell startup.

## 6. Optional Extra-Keys Layouts

The example tmux config binds `F12` to:

```sh
termux-reload-settings
```

Binding `termux-reload-settings` through tmux gives you a quick shell recovery button after launcher updates, terminal theme changes, or stale UI state. The key runs inside tmux instead of typing the command into the active pane.

Use one of the examples below in `~/.termux/termux.properties`. Replace any existing `extra-keys = ...` entry with the block you choose, then run `termux-reload-settings`.

### One-Row Layout

This is the compact option. It keeps one row of tmux helpers above the soft keyboard:

- `♼` runs `termux-reload-settings` through tmux.
- `𝍣` splits the current tmux pane vertically, with horizontal split `𝍬` as the swipe-up popup.
- `⓵`, `⓶`, and `⓷` jump to tmux windows 1, 2, and 3.
- `✎` enters tmux copy mode.
- The keyboard key toggles the soft keyboard, with paste on swipe-up.
- `㋡` sends the tmux prefix.

```properties
extra-keys = [[ \
  {macro: "CTRL b F12", display: "♼"}, \
  {macro: "CTRL b h", display: "𝍣", popup: {macro: "CTRL b v", display: "𝍬"}}, \
  {macro: "CTRL b 1", display: "⓵"}, \
  {macro: "CTRL b 2", display: "⓶"}, \
  {macro: "CTRL b 3", display: "⓷"}, \
  {macro: "CTRL b [", display: "✎"}, \
  {key: KEYBOARD, popup: PASTE}, \
  {macro: "CTRL b", display: "㋡"} \
]]
```

### Two-Row Layout

This is the fuller dock-oriented option. It pairs better with the launcher apps bar and AZ row, especially when compact dock spacing is enabled:

- The first row is mostly tmux navigation and session controls.
- `𝍣` and `𝍬` split the current tmux pane vertically and horizontally.
- `⬸` and `⤑` move to the previous or next tmux window through `ALT LEFT` and `ALT RIGHT`.
- `+` creates a tmux window.
- `✏` enters tmux copy mode.
- `□` toggles pane zoom, `×` closes the current pane, and swipe-up `⊠` closes the current window.
- The second row keeps regular terminal keys: `Esc`, `Tab`, `Shift`, `Ctrl`, `Alt`, left/right arrows, keyboard toggle, and paste.
- Swipe up on `Esc` to run the tmux `F12` reload binding.

```properties
extra-keys = [[ \
  {macro: "CTRL b h", display: "𝍣"}, \
  {macro: "CTRL b v", display: "𝍬"}, \
  {macro: "ALT LEFT", display: "⬸"}, \
  {macro: "CTRL b c", display: "+"}, \
  {macro: "ALT RIGHT", display: "⤑"}, \
  {macro: "CTRL b [", display: "✏"}, \
  {macro: "CTRL b z", display: "□"}, \
  {macro: "CTRL b x", display: "×", popup: {macro: "CTRL b k", display: "⊠"}} \
], [ \
  {key: ESC, display: "Esc", popup: {macro: "CTRL b F12", display: "⟲"}}, \
  {key: TAB, display: "TAB"}, \
  {key: SHIFT, display: "SHFT"}, \
  {key: CTRL, display: "CTRL"}, \
  {key: ALT, display: "ALT"}, \
  {key: LEFT, popup: DOWN}, \
  {key: RIGHT, popup: UP}, \
  {key: KEYBOARD, popup: PASTE} \
]]
```

Reload Termux settings after changing the property:

```sh
termux-reload-settings
```

Both examples assume the tmux config from this guide is installed. They use `CTRL b` as the tmux prefix, and the example config keeps `CTRL b` available as a secondary prefix.

## 7. Optional kew Ticker

The full tmux example already references `kew-tmux-status`. If `kew-now-playing --peek` returns a track, tmux switches to two status rows and shows the ticker on the second row. When no music is playing, tmux returns to one status row.

Install `kew-now-playing` from this repo if you do not already have it:

```sh
mkdir -p ~/.local/bin
curl -fsSL 'https://raw.githubusercontent.com/PickleHik3/termux-launcher/dev/resources/scripts/statusbar/kew-now-playing' -o ~/.local/bin/kew-now-playing
chmod 700 ~/.local/bin/kew-now-playing
```

## 8. Troubleshooting

If the status bar shows empty widgets:

```sh
command -v launcherctl
command -v jq
launcherctl status
launcherctl resources
```

If weather is empty, wait a few seconds and run:

```sh
launcher-weather-widget
```

The weather helper uses a cache, so it avoids hitting the free service on every tmux refresh.
