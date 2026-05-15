# Using the Launcher

## Main Surface

The terminal is the primary home screen. Launcher controls sit around the Termux session instead of replacing it.

## Pinned Apps

Pinned apps live in the apps row.

To add an app:

- Long-press an app icon and choose pin.
- Or long-press empty space in the apps row and choose from the installed app list.

To rearrange pinned apps:

- Long-press an app icon.
- Move it laterally within the row to pick it up.
- Drop it in empty space to move it.
- Drop it on another icon to create a folder.

If drag movement feels finicky, long-press empty space in the apps row and manage pinned apps or folders from the list flow.

## Alphabet Row

Swipe across the alphabet row to filter installed apps by letter.

From the alphabet row, swipe upward deliberately to choose an app. The selected app launches when you lift your finger.

Double-tapping the alphabet row can lock the phone when optional Shizuku integration is enabled.

## Terminal-Driven Search

The launcher can read a search query from terminal input. The default split character is `%`.

Example:

```text
%whatsapp
```

Change the split character from:

```text
Settings -> Apps Bar -> Input split character
```

## Appearance

Launcher appearance is configured from app settings. Current controls include wallpaper-aware Material colors, blur tuning, monochrome icons, and terminal/launcher surface styling.

Material shell colors are also written for shell integrations:

```sh
~/.termux/material-colors.sh
~/.termux/material-colors.properties
```

See [Terminal Material colors](Launcher_Material_Colors) for prompt and tmux examples.
