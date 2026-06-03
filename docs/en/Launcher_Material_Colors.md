# Terminal Material Colors

Termux Launcher can export wallpaper-derived Material colors into files under `~/.termux`. Shell tools can source or read those files to match the launcher theme.

## Generated Files

The launcher writes:

```sh
~/.termux/material-colors.sh
~/.termux/material-colors.properties
```

Use the shell file for `sh`, `bash`, `zsh`, and prompt scripts. Use the properties file for tools that prefer key/value config.

The shell file exports names like:

```sh
TERMUX_MATERIAL_PRIMARY
TERMUX_MATERIAL_ON_SURFACE
TERMUX_MATERIAL_SURFACE
TERMUX_MATERIAL_SURFACE_CONTAINER
TERMUX_MATERIAL_TERMINAL_BACKGROUND
TERMUX_MATERIAL_TERMINAL_FOREGROUND
TERMUX_MATERIAL_TERMINAL_COLOR4
```

## Shell Example

Source the generated colors if the file exists:

```sh
if [ -r "$HOME/.termux/material-colors.sh" ]; then
    . "$HOME/.termux/material-colors.sh"
fi
```

Inspect the generated file to see every available variable:

```sh
sed -n '1,80p' "$HOME/.termux/material-colors.sh"
```

Simple prompt pattern using 24-bit terminal color:

```sh
if [ -r "$HOME/.termux/material-colors.sh" ]; then
    . "$HOME/.termux/material-colors.sh"
fi

hex_to_rgb() {
    color="${1#\#}"
    red="${color%????}"
    green="${color#??}"
    green="${green%??}"
    blue="${color#????}"
    printf '%d;%d;%d' "0x$red" "0x$green" "0x$blue"
}

prompt_color="$(hex_to_rgb "${TERMUX_MATERIAL_PRIMARY:-#40C4FF}")"
reset='\[\033[0m\]'
PS1="\[\033[38;2;${prompt_color}m\]\u@\h${reset}:\w \$ "
```

## tmux Example

Source the generated shell file before starting tmux, then pass values into tmux options from your shell startup files.

Example `~/.bashrc`:

```sh
if [ -r "$HOME/.termux/material-colors.sh" ]; then
    . "$HOME/.termux/material-colors.sh"
fi
```

Example `~/.tmux.conf` pattern using exported environment variables:

```tmux
set -g status on
set -g status-interval 5
set -g status-style "fg=#{E:TERMUX_MATERIAL_ON_SURFACE},bg=#{E:TERMUX_MATERIAL_SURFACE_CONTAINER}"
set -g window-status-current-style "fg=#{E:TERMUX_MATERIAL_SURFACE},bg=#{E:TERMUX_MATERIAL_PRIMARY}"
set -g status-left '#S '
set -g status-right '#(launcherctl status >/dev/null 2>&1 && date "+%H:%M")'
```

For a full theme, inspect `~/.termux/material-colors.sh`, choose the exported variables you want, and map them into tmux `status-style`, `window-status-style`, `window-status-current-style`, and `message-style`.

## Full tmux Example

For the full beginner-friendly setup order, including prerequisites and optional Shizuku pieces, follow [tmux status setup](Launcher_Tmux_Status_Setup). If you use TPM, install the packaged [termux-launcher-tmux](https://github.com/PickleHik3/termux-launcher-tmux) theme plugin instead of copying the raw theme file.

The wiki also includes the raw example files:

- [tmux.conf example](examples/tmux.conf)
- [material-theme.tmux example](examples/material-theme.tmux)
- [kew-tmux-status example](examples/kew-tmux-status)
- [Shizuku helper examples](Launcher_Shizuku_Examples)

Install the tmux example with `curl`:

```sh
BASE='https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/docs/en/examples'
mkdir -p ~/.tmux ~/.local/bin
curl -fsSL "$BASE/tmux.conf" -o ~/.tmux.conf
curl -fsSL "$BASE/material-theme.tmux" -o ~/.tmux/material-theme.tmux
curl -fsSL "$BASE/kew-tmux-status" -o ~/.local/bin/kew-tmux-status
chmod 600 ~/.tmux.conf
chmod 700 ~/.tmux/material-theme.tmux
chmod 700 ~/.local/bin/kew-tmux-status
```

If tmux is already running:

```sh
tmux source-file ~/.tmux.conf
```

The full [tmux status setup](Launcher_Tmux_Status_Setup) page includes an example `extra-keys` layout with a `termux-reload-settings` recovery button bound through tmux.

## Refreshing Colors

If terminal colors or launcher styling feel stale after an update or restart:

```sh
termux-reload-settings
```

This reloads the Termux settings layer around the active launcher session.
