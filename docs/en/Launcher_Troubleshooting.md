# Troubleshooting

## Terminal Slows Down or Stutters

If terminal input, redraws, or screen updates become slow after an app update, launcher restart, or exiting the last terminal session, run:

```sh
termux-reload-settings
```

This reloads the Termux settings and styling layer around the active launcher session. It usually clears stale terminal UI state without needing to reinstall or reset the app.

## Restart the Launcher

If the launcher itself needs a full restart:

```sh
launcherctl restart
```

## Last Terminal Session Was Closed

If the shell exits while the launcher is active as the system home app, the launcher process can be left in a degraded state.

First try:

```sh
termux-reload-settings
```

If the launcher itself still feels broken, restart it:


```sh
launcherctl restart
```

If the bridge is unavailable, restart the app from Android.

## LauncherCtl Is Not Responding

Check:

```sh
launcherctl status
```

If the command is missing or the endpoint cannot be reached, restart the launcher. The app installs the CLI script when `TermuxActivity` starts.

## Shizuku Features Do Not Work

Normal launcher usage does not require Shizuku. For Shizuku-only features:

- Confirm Shizuku is running.
- Grant permission to Termux Launcher.
- Run `launcherctl status` and check backend state.

## Media or Notifications Are Empty

`launcherctl media`, `launcherctl art`, and `launcherctl notifications` require Android notification listener access. Grant that permission in Android settings, then check again.
