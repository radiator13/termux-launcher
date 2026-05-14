# Changelog

## 0.2.14

### Changed
- Improved dock blur implementation and wallpaper sampling so the phone is not a hand warmer anymore.
- Improved dock motion, IME restore, and return-home animation.
- Improved Material theming across terminal surfaces, dock surfaces, extra keys, and app UI surfaces.
- Added an Appearance toggle to apply Material colors to the Termux shell.
- Exposed Material colors in `~/.termux/material-colors.sh` and `~/.termux/material-colors.properties` for shell integrations such as tmux status bars.

### Fixed
- Fixed the text input field in the extra keys bar/dock so Android keyboard text input can target the field correctly.
- Fixed dock blur flashes and blur pauses during IME transitions.
- Fixed managed/system wallpaper blur alignment and fallback handling.

## 0.2.13

### Fixed
- Fixed Shizuku reconnect after launcher restarts.
- Fixed dock blur state with live wallpapers.
- Improved terminal exit/relaunch behavior.
- Performance refinements and cleanup.

## 0.2.10

### Changed
- Improved launcher search, duplicate app labeling, folder popup sizing, and `launcherctl` status/notification metadata.

### Fixed
- Fixed `launcherctl /v1/apps` to match the launcher’s real app catalog.
- Fixed pinned-page resets during reorder and folder creation.
- Fixed stale pinned and folder app references.
- Fixed folder editor search and package-only folder refs.
- Fixed immediate folder updates for `Move to dock` and `Delete`.
- Fixed collapsed folder previews not refreshing after folder changes.
- Fixed extra right-side padding in the folder popup.
- Removed the pinned-row bloom overlay while keeping page indicators.
