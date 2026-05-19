# Termux Extrakeys Reference

In addition to original [wiki](https://wiki.termux.com/wiki/Touch_Keyboard) termux-launcher includes additional **PASTE** key thanks to [termux-monet](https://github.com/Termux-Monet/termux-monet).

extrakeys row can display characters supported by android (not nerd fonts), i often use [Glyphy](https://glyphy.io/) to find suitable characters. 

Below are some examples for extrakeys, just copy and paste the entire block to your ~/.termux/termux.properties & run `termux-reload-settings` to apply.

### One-Row Layout

This is the compact option. It keeps one row of tmux helpers above the soft keyboard:

- `♼` runs `termux-reload-settings` through tmux.
- `𝍣` splits the current tmux pane vertically, with horizontal split `𝍬` as the swipe-up popup.
- `⓵`, `⓶`, and `⓷` jump to tmux windows 1, 2, and 3.
- `✎` enters tmux copy mode.
- The keyboard key toggles the soft keyboard, with paste on swipe-up.
- `㋡` sends the tmux prefix.

```properties
extra-keys = [[ \
  {macro: "CTRL b F12", display: "♼"}, \
  {macro: "CTRL b h", display: "𝍣", popup: {macro: "CTRL b v", display: "𝍬"}}, \
  {macro: "CTRL b 1", display: "⓵"}, \
  {macro: "CTRL b 2", display: "⓶"}, \
  {macro: "CTRL b 3", display: "⓷"}, \
  {macro: "CTRL b [", display: "✎"}, \
  {key: KEYBOARD, popup: PASTE}, \
  {macro: "CTRL b", display: "㋡"} \
]]
```

### Two-Row Layout

This is the fuller dock-oriented option, Recommended to turn on the compact dock from `Appearance` settings page:

- The first row is mostly tmux navigation and session controls.
- `𝍣` and `𝍬` split the current tmux pane vertically and horizontally.
- `✏` enters tmux copy mode.
- `⬸` and `⤑` move to the previous or next tmux window through `ALT LEFT` and `ALT RIGHT`.
- `+` creates a tmux window.
- `□` toggles pane zoom, `×` closes the current pane, and swipe-up `⊠` closes the current window.
- The second row keeps regular terminal keys: `Esc`, `Tab`, `Shift`, `Ctrl`, `Alt`, left/right arrows, keyboard toggle, and paste.
- Swipe up on `Esc` to run the tmux `F12` reload binding.

```properties
extra-keys = [[ \
  {macro: "CTRL b h", display: "𝍣"}, \
  {macro: "CTRL b v", display: "𝍬"}, \
  {macro: "ALT LEFT", display: "⬸"}, \
  {macro: "CTRL b c", display: "+"}, \
  {macro: "ALT RIGHT", display: "⤑"}, \
  {macro: "CTRL b [", display: "✏"}, \
  {macro: "CTRL b z", display: "□"}, \
  {macro: "CTRL b x", display: "×", popup: {macro: "CTRL b k", display: "⊠"}} \
], [ \
  {key: ESC, display: "Esc", popup: {macro: "CTRL b F12", display: "⟲"}}, \
  {key: TAB, display: "TAB"}, \
  {key: SHIFT, display: "SHFT"}, \
  {key: CTRL, display: "CTRL"}, \
  {key: ALT, display: "ALT"}, \
  {key: LEFT, popup: DOWN}, \
  {key: RIGHT, popup: UP}, \
  {key: KEYBOARD, popup: PASTE} \
]]
```

Reload Termux settings after changing the property:

```sh
termux-reload-settings
```

Both examples assume the tmux config from this guide is installed. They use `CTRL b` as the tmux prefix, and the example config keeps `CTRL b` available as a secondary prefix.
