# Getting Started

## Install & Setup

1. Download and install the latest APK from [Releases](https://github.com/PickleHik3/termux-launcher/releases).
2. It is suggested you open the app normally at the beginning to finish termux's bootstrap process.
    - If you want to change pacakge manager to pacman you must do it without setting termux-launcher as home app (to be able to get into fail-safe mode), see [Switching package manager](https://wiki.termux.com/wiki/Switching_package_manager).
    - Switching to pacman has its benefits such as all the repo's comes preconfigured for you (glibc, tur etc.).
    - Speed wise, there is no meaningful difference between pacman and pkg/apt default (provided you have chosen a single fastest mirror for the default pkg manager).
3. Open Android settings and select Termux Launcher as the default Home app. There is a shortcut avaialable for you in termux launcher;
    ```text
    Long Press on Terminal & more -> Apps Bar -> Set as home launcher
4. Thats it to get you started, everything works as it would in native termux.

## Recommended Apps

- [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) is recommended for terminal and tmux-heavy use.
- [Shizuku](https://github.com/rikkaapps/shizuku) is optional. Install it only if you want the optional privileged features.

Use these matching companion forks if you install Termux add-ons:

- [Termux:API](https://github.com/PickleHik3/termux-api)
- [Termux:Styling](https://github.com/PickleHik3/termux-styling)

Using mismatched Termux add-ons can cause shared UID or signing problems.

## Notes on App Preferences

You can long press on the terminal & click 'more' to access relevent preferences pages. I am just mentioning a few noteworthy ones below;

### 1. **Appearance**

Control the opacity & blur of the terminal, dock, termux sessions menu. 
   - **Terminal Material colors toggle:** turn this on if you want the termux shell to inherit the material colors from system, it will apply color scheme for the terminal background, text, cursor and ANSI colors. turning this on will create `material-colors.properties` and `material-colors.sh` inside the ~/.termux directory which you can source for your specific needs. More information is available at [Launcher Material colors](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Launcher_Material_Colors.md).
   - **Dock Blur:** Dock blur does not work if you have a live wallpaper is being used, it will be autodisabled if you are using one.
   - **Dock Size:** Controls the height of the app icons row
   - **Compact dock spacing:** tightens the distance between various rows in the dock (extrakeys, AZ row & App icons) & uses a smaller pages indicator. Recommended to turn it on if you want 2 rows of termux extrakeys, otherwise the terminal size becomes too small. You can find a few of my examples at: [Termux ExtraKeys](https://github.com/PickleHik3/termux-launcher/blob/main/docs/en/Termux_Extrakeys.md).

### 2. **Apps Bar**

All the launcher specific settings are configured here. 
    - **Double tap alphabets row lockscreen:**: You have 2 options, shizuku (get system screen off animation) and Accessibility service (typical method used by other launchers, screen may flicker once)
    - **Search Strictness:** something that i inherited from TEL, haven't seen a use for it yet. 
    - **Input Split Character:** default is `%`, typing the character specified here will trigger app search and the results will be displayed on the apps row. Recommended to choose an seldom used character. 
    - **Reset App Order:** by default, the app icons are ranked based on number of times it has been launched, this was done to make it easier to launch apps with a swipe gesture from the AZ row to the app icons, the most launched app icon will be directly above their respective alphabet, so you can just swipe up to choose and launch it. This button resets the ranking. 
