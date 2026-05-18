# Changelog

## 0.2.17

### Added
- Added notification dots.
- Added a compact dock toggle for users who need two rows of extra keys, available in Settings > Appearance.

### Changed
- Reworked the apps bar page indicator.
- Removed some items for better security.
- Refined the UI.

## 0.2.16

### Added
- Added global icon pack support for the apps bar and pinned dock.
- Added per-pinned app icon overrides, including apps inside folders.
- Added visual icon selection from installed icon packs.

### Changed
- Simplified launcher icon preferences and moved icon pack settings into Apps Bar.
- Updated icon picker, icon pack picker, wallpaper picker, and launcher popup surfaces to better match the app Material color theme.
- Improved dock background color when transparency or wallpaper is disabled.

### Fixed
- Fixed icon changes requiring a swipe before refreshing.
- Fixed custom icons being lost when apps move into or out of folders.
- Fixed folder previews and folder popup icons using stale system icons.
- Fixed themed icon controls that did not affect launcher icons.
- Fixed app launch reliability for default launch activities.

## 0.2.15

### Changed
- Refreshed launcher documentation and README links around getting started, usage, Material colors, shell integration, tmux setup, and optional Shizuku helpers.
- Restored the GitHub nightly debug build workflow for hosted APK validation.
- Removed stale bundled status helper scripts now covered by documented examples.

### Fixed
- Fixed intermittent first-attempt app launches by preferring normal launcher intents before falling back to `LauncherApps.startMainActivity()`.
- Improved Material color refresh behavior for terminal and shell integrations.

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
