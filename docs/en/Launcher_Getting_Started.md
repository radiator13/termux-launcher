# Getting Started

## Install & Setup

1. Download and install the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases).
    Optional companion apps:
        - [Termux:API](https://github.com/PickleHik3/termux-api/releases)
        - [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases)

2. Open the app normally at first to finish Termux's bootstrap process.

> [!NOTE]
> If you want switching package managers (aka to pacman) do it before step 3 (setting as your Home app), so you can still access fail-safe mode as required. See [Switching package manager](https://wiki.termux.com/wiki/Switching_package_manager).
> - Pacman simplifies repository setup by preconfiguring repositories such as glibc, TUR, and others.
> - In terms of performance, pacman offers no meaningful speed advantage over the default pkg/apt setup, as long as the latter is configured to use a single fast mirror.

3. Long press terminal area -> select more -> Settings -> Apps & Access -> Set as home launcher -> Choose Termux Launcher.

4. That's it to get started. Everything works as it would in native Termux.

## Shell customization

To recreate my shell configs which includes;

- Fish shell
- oh-my-posh prompt
- tmux
- eza
- zoxide
- btop (without root, uses local adb bridge `rish` provided by shizuku, more information [here](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Getting_Started.md#btop-with-real-android-system-usage)

### One shot yolo

> [!NOTE]
> install packages:
> ```sh
> pkg i -y tmux curl jq git fish oh-my-posh termux-api
> ```
> - Turn on **Material colors** from Settings -> Appearance settings.
> - Ensure rish is available in your $PATH. learn more about setting up shizuku & rish
>     1. [shizuku](https://shizuku.rikka.app/guide/setup/)
>     2. [rish](https://github.com/RikkaApps/Shizuku-API/tree/master/rish?night=1).

Run:

```sh
curl -fsSL "https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/docs/en/examples/setup-tmux-btop" -o ~/setup-tmux-btop
chmod 700 ~/setup-tmux-btop
~/setup-tmux-btop
```

> [!NOTE]
> If you have already completed this flow and later update the APK, refresh the shell helper scripts with:
>
> ```sh
> launcherctl update-scripts
> ```
> This keeps your tmux config intact and updates only the repo-owned helper scripts.

### Material colors in terminal

**Material colors** toggle in Settings -> Appearance Menu

Turn this on if you want the Termux shell to inherit the Material colors from the system. It will apply the color scheme to the terminal background, text, cursor, and ANSI colors. Turning this on will create `material-colors.properties` and `material-colors.sh` inside the `~/.termux` directory, which you can source for your specific needs.

Read More: [Launcher Material Colors](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Launcher-Material-Colors.md).

### Terminal Multiplexer - [tmux](https://github.com/tmux/tmux/wiki)

i've packaged my tmux theme as a TPM package, follow the steps at [termux-launcher-tmux](https://github.com/PickleHik3/termux-launcher-tmux)  which uses Material colors and the Shizuku backend for real system stats, at [tmux setup](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Launcher_Tmux_Status_Setup.md).

> [!NOTE]
> You can create tmux key bindings to launch Android apps using the example below. In this example, `Alt + w` opens WhatsApp:
>
> ```tmux
> bind -n M-w run-shell 'tmux display-message "Opening WhatsApp"; launcherctl launch whatsapp >/dev/null 2>&1 || tmux display-message "Launch failed: WhatsApp"'
> ```
>
> `Alt + e` opens a tmux keybinds quick reference. 


## Recommended Apps

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) is highly recommended for terminal and tmux-heavy use. Download it from [Play Store](https://play.google.com/store/apps/details?id=juloo.keyboard2)
- [Shizuku](https://github.com/rikkaapps/shizuku) is optional. Install it only if you want the optional privileged features.

Use these matching companion forks if you install Termux add-ons:

- [Termux:API](https://github.com/PickleHik3/termux-api)
- [Termux:Styling](https://github.com/PickleHik3/termux-styling)

Using mismatched Termux add-ons can cause shared UID or signing problems.

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

For tmux status widgets, Termux Launcher users should use the `launcherctl resources` path. The helper keeps a `rish` fallback for vanilla Termux, but that fallback is less efficient because it starts a Shizuku shell to sample system files.

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
