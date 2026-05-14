# Optional Shizuku Integration

Shizuku is not required for normal launcher usage.

When enabled and granted, Shizuku supports optional privileged launcher features:

- Double-tap the alphabet row to lock the phone.
- System status helpers for tmux/status bar integrations.
- Controlled privileged command execution through LauncherCtl policy, when explicitly enabled.

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

## LauncherCtl Exec Policy

Privileged command execution is disabled by default. If you enable it, the policy is stored at:

```sh
~/.launcherctl/config.json
```

Allowed commands must match configured prefixes. See [LauncherCtl API](LauncherCtl_API) for the policy format and security model.

Keep this disabled unless you have a specific command you want to expose. The launcher does not need it for normal app launching.

## TTY Commands

Use `tty-exec` for interactive tools that need a real terminal:

```sh
launcherctl tty-doctor
launcherctl tty-exec "id"
```

`tty-doctor` checks the local `rish` files used for Shizuku-backed terminal commands.

## Privacy Notes

Notification and media helpers require Android notification listener access. Without that permission, the launcher still works normally, but `launcherctl media`, `launcherctl art`, and `launcherctl notifications` return limited or empty data.
