# Getting Started

## Install

1. Download the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases).
2. Install the APK on your Android device.
3. Open Android settings and select Termux Launcher as the default Home app.
4. Start the launcher and let the Termux session initialize.

## Recommended Apps

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) is recommended for terminal and tmux-heavy use.
- [Shizuku](https://github.com/rikkaapps/shizuku) is optional. Install it only if you want the optional privileged features.

Use these matching companion forks if you install Termux add-ons:

- [Termux:API](https://github.com/PickleHik3/termux-api)
- [Termux:Styling](https://github.com/PickleHik3/termux-styling)

Using mismatched Termux add-ons can cause shared UID or signing problems.

## First Checks

After installation:

```sh
launcherctl status
launcherctl apps
```

`launcherctl status` shows whether the local launcher bridge is running. `launcherctl apps` prints the launchable Android app catalog used by the launcher UI.

## Optional tmux Status Setup

The APK does not install personal tmux config, shell helpers, or status widgets. To recreate the example setup manually, follow [tmux status setup](Launcher_Tmux_Status_Setup).

That guide covers the recommended order:

1. Install the small Termux packages used by the examples.
2. Check that `launcherctl` works.
3. Download the CPU/RAM, weather, and optional `kew` helpers.
4. Download the example tmux config and Material theme.
5. Optionally add Shizuku `btop` and the extra-keys reload button.

## Search Split Character

By default, typing `%` in the terminal starts app search in the launcher bar. Change this from:

```text
Settings -> Apps Bar -> Input split character
```

## Home Launcher Mode

The app is intended to be used as the system home launcher. It can also appear in recent apps like normal Termux, but that mode is less tested and may expose terminal lifecycle oddities.
