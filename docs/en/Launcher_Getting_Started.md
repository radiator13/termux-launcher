# Getting Started

## Install & Setup

1. Download and install the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases).

2. Open the app normally at first to finish Termux's bootstrap process.

   - If you want to change the package manager to pacman, do it before setting Termux Launcher as the Home app so that you can still get into fail-safe mode. See [Switching package manager](https://wiki.termux.com/wiki/Switching_package_manager).
   - Switching to pacman has benefits, such as having all repositories preconfigured for you, including glibc, tur, etc.
   - Speed-wise, there is no meaningful difference between pacman and the default pkg/apt setup, provided you have chosen a single fastest mirror for the default package manager.

3. Open Android settings and select Termux Launcher as the default Home app. There is also a shortcut available in Termux Launcher:

   ```text
   Long press on Terminal & more -> Apps Bar -> Set as home launcher
   ```

4. That's it to get started. Everything works as it would in native Termux.

## Recommended Apps

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) is recommended for terminal and tmux-heavy use.
- [Shizuku](https://github.com/rikkaapps/shizuku) is optional. Install it only if you want the optional privileged features.

Use these matching companion forks if you install Termux add-ons:

- [Termux:API](https://github.com/PickleHik3/termux-api)
- [Termux:Styling](https://github.com/PickleHik3/termux-styling)

Using mismatched Termux add-ons can cause shared UID or signing problems.

## Notes on App Preferences

You can long press on the terminal and click **More** to access relevant preference pages. A few noteworthy ones are listed below.

### 1. Appearance

Control the opacity and blur of the terminal, dock, and Termux sessions menu.

- **Terminal Material colors toggle:** Turn this on if you want the Termux shell to inherit the Material colors from the system. It will apply the color scheme to the terminal background, text, cursor, and ANSI colors. Turning this on will create `material-colors.properties` and `material-colors.sh` inside the `~/.termux` directory, which you can source for your specific needs. More information is available at [Launcher Material Colors](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Launcher_Material_Colors.md).
- **Dock Blur:** Dock blur does not work if you are using a live wallpaper. It will be automatically disabled if a live wallpaper is detected.
- **Dock Size:** Controls the height of the app icons row.
- **Compact dock spacing:** Tightens the distance between various rows in the dock, including Extra Keys, the A-Z row, and app icons. It also uses a smaller page indicator. It is recommended to turn this on if you want two rows of Termux Extra Keys; otherwise, the terminal size becomes too small. You can find a few examples at [Termux Extra Keys](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Termux_Extrakeys.md).

### 2. Apps Bar

All launcher-specific settings are configured here.

- **Double tap alphabets row lockscreen:** You have two options: Shizuku, which provides the system screen-off animation, and Accessibility service, which is the typical method used by other launchers but may cause the screen to flicker once.
- **Search Strictness:** This is something inherited from TEL. I haven't seen a use for it yet.
- **Input Split Character:** The default is `%`. Typing the character specified here will trigger app search, and the results will be displayed on the Apps Bar. It is recommended to choose a seldom-used character.
- **Reset App Order:** By default, app icons are ranked based on the number of times each app has been launched. This was done to make it easier to launch apps with a swipe gesture from the A-Z row to the app icons. The most launched app icon will be directly above its respective alphabet, so you can swipe up to choose and launch it. This button resets the ranking.

## Additional Notes

This section is sort of guided setup for setting up tmux and btop (without root, using shizuku), you can read through and copy paste each snippet, or you can use the  optional installation script.

Prerequisites;
* turn on **Terminal Material colors** from Appearance settings first. 
* ensure rish is available in your $PATH.

Then run:

```sh
curl -fsSL "https://raw.githubusercontent.com/PickleHik3/termux-launcher/dev/docs/en/examples/setup-tmux-btop" -o ~/setup-tmux-btop
chmod 700 ~/setup-tmux-btop
~/setup-tmux-btop
```

### Terminal Multiplexer

[tmux](https://github.com/tmux/tmux/wiki) is recommended. You can read about my setup, which uses Material colors and the Shizuku backend for real system stats, at [tmux setup](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Launcher_Tmux_Status_Setup.md).

You can create tmux key bindings to launch Android apps using the example below. In this example, `Alt + w` opens WhatsApp:

```tmux
bind -n M-w run-shell 'tmux display-message "Opening WhatsApp"; launcherctl launch whatsapp >/dev/null 2>&1 || tmux display-message "Launch failed: WhatsApp"'
```

Although you could use [zellij](https://zellij.dev/) as well, tmux may be slightly better for battery life.

### btop with Real Android System Usage

This setup works without root, uses Shizuku/rish, and works in vanilla Termux.

You can pull actual system usage statistics using local ADB through [Shizuku](https://github.com/rikkaapps/shizuku) or its fork, [Shizuku](https://github.com/thedjchi/Shizuku).

Follow the [official guide](https://shizuku.rikka.app/guide/setup/) to set up Shizuku. After setup, open the Shizuku app and tap **Use Shizuku in terminal apps**. It will ask for storage permission and create two files:

- `rish`
- `rish_shizuku.dex`

Copy both files somewhere in Termux's `$PATH`, such as `../usr/bin/`, or create a `~/.local/bin` directory and add it to your shell's path.

Then, use your favorite text editor to open `rish`, scroll down to the bottom, and change the second-to-last line to:

```sh
RISH_APPLICATION_ID="com.termux"
```

Now run `rish` from the terminal. The Shizuku permission window should appear. After you grant permission, `rish` will drop you into your phone's ADB shell.

You can then pipe commands to this shell directly from Termux using `rish -c`. You can read more about this [here](https://github.com/RikkaApps/Shizuku-API/tree/master/rish).

If permissions are missing, you can run:

```sh
chmod +x "$(command -v rish)"
```

Once `rish` is available in your path, you can use my script, [setup-btop-rish](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/examples/setup-btop-rish), to configure it properly.

The script does the following:

1. Downloads the generic Linux binary for btop from its [releases page](https://github.com/aristocratos/btop/releases).
2. Copies it to `/data/local/tmp`, which is your phone's temporary directory, and creates two config files in the same directory.

   Although this has not happened to me, your device may clear the shared temp folder on reboot. If that happens, you will need to run the script again to restore the binary and configs to the appropriate locations.

3. Creates two wrappers inside Termux's `$PATH`, usually `../usr/bin/`:

   - `btop-shizuku`
   - `mini-btop-shizuku`

4. Disables network and storage information because Android's ADB limitations prevent access to those details.

Two variants are created:

- **btop-shizuku:** The full layout. You may need to zoom out the terminal to make it fit.
- **mini-btop-shizuku:** A smaller layout that is more suitable for smaller terminal windows.

You can use the command below to download and run the script:

```sh
curl -fsSL "https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/docs/en/examples/setup-btop-rish" -o ~/setup-btop-rish
chmod 700 ~/setup-btop-rish
~/setup-btop-rish
```
