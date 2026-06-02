# Shell Integration

`launcherctl` is the local shell bridge installed by the launcher. It talks to a localhost API running inside the app process and uses the same app catalog as the on-screen launcher.

## Common Commands

Check whether the bridge is running:

```sh
launcherctl status
```

List launchable apps:

```sh
launcherctl apps
```

Launch an app by label or package-style query:

```sh
launcherctl launch whatsapp
```

Restart the launcher:

```sh
launcherctl restart
```

Rotate the LauncherCtl token:

```sh
launcherctl token rotate
```

## App Launch Examples

Launch WhatsApp:

```sh
launcherctl launch whatsapp
```

Launch Termux:API if installed:

```sh
launcherctl launch "termux api"
```

Search the exact catalog used by the launcher UI:

```sh
launcherctl apps | less
```

## tmux Binding Example

```tmux
bind -n M-w run-shell 'tmux display-message "Opening WhatsApp"; launcherctl launch whatsapp >/dev/null 2>&1 || tmux display-message "Launch failed: WhatsApp"'
```

## System and Media Data

LauncherCtl can expose system resources, notification state, media metadata, and album art when the required Android permissions are granted.

Common commands:

```sh
launcherctl resources
launcherctl media
launcherctl art
launcherctl notifications
```

Refresh optional shell/tmux helper scripts after an APK update:

```sh
launcherctl update-scripts
```

This command updates repo-owned helper scripts and leaves `~/.tmux.conf` alone.

Media and notification commands require Android notification listener access.

## Material Colors

Termux Launcher can export wallpaper-derived Material colors for shell integrations such as prompts, status bars, and tmux. See [Terminal Material colors](Launcher_Material_Colors).

For endpoint details and security behavior, see [LauncherCtl API](LauncherCtl_API).
