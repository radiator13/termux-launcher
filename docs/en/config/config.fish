set -g fish_greeting ""

# Comment this line, or set it to 0, to disable tmux autostart.
set -g fish_auto_tmux 1

set -gx TMPDIR "$HOME/.tmp"

if not test -d "$TMPDIR"
    mkdir -p "$TMPDIR"
end

if not set -q COLORTERM
    set -gx COLORTERM truecolor
end

fish_add_path "$HOME/.local/bin" "$HOME/.termux/bin"

# Keep the prompt near the bottom after clearing the screen.
function __move_cursor_to_bottom
    if type -q tput
        set -l lines (tput lines 2>/dev/null)

        if string match -rq '^[0-9]+$' -- "$lines"; and test "$lines" -gt 1
            command tput cup (math "$lines - 2") 0 2>/dev/null
        end
    end
end

function clear
    command clear
    __move_cursor_to_bottom
end

# yazi helper: exit yazi into the directory it was viewing.
function y
    set -l tmp (mktemp -t "yazi-cwd.XXXXXX")

    command yazi $argv --cwd-file="$tmp"

    if read -l cwd <"$tmp"; and test "$cwd" != "$PWD"; and test -d "$cwd"
        builtin cd -- "$cwd"
    end

    rm -f -- "$tmp"
end

abbr -a cc clear
abbr -a ee exit
abbr -a ii 'pacman -S --needed --noconfirm'
abbr -a ss 'pacman -Ss'
abbr -a rr termux-reload-settings
abbr -a uu 'pacman -Syu --needed --noconfirm'
abbr -a cdd 'cd ..'
abbr -a nn nvim
abbr -a fishy 'nvim ~/.config/fish/config.fish'
abbr -a tmuxy 'nvim ~/.tmux.conf'
abbr -a termuxy 'nvim ~/.termux/termux.properties'
abbr -a w which
abbr -a ccfg 'cd ~/.config/'
abbr -a rfish 'exec fish'
abbr -a rtermux termux-reload-settings
abbr -a rtmux 'tmux source-file ~/.tmux.conf'
abbr -a ktmux 'tmux kill-server'
abbr -a mm mkdir
abbr -a py python
abbr -a gitc 'git clone'
abbr -a pd proot-distro
abbr -a cx 'codex --sandbox danger-full-access'
abbr -a cxr 'codex --sandbox danger-full-access resume'
abbr -a t tmux

if status is-interactive
    # Start or attach to tmux automatically when available.
    if test "$fish_auto_tmux" = 1; and type -q tmux; and not set -q TMUX
        set -l tmux_flag "$TMPDIR/.tmux-first-shell-done"
        set -l tmux_session main

        if test -e "$tmux_flag"
            if tmux has-session -t "$tmux_session" 2>/dev/null
                exec tmux attach -t "$tmux_session"
            else
                exec tmux new-session -A -s "$tmux_session"
            end
        else
            touch "$tmux_flag"
            tmux new-session -d -s "$tmux_session" 2>/dev/null
        end
    end

    # Clear once on startup, then place the first prompt near the bottom.
    function fish_greeting
        command clear
        __move_cursor_to_bottom
    end

    # Starship prompt, with transient prompt enabled when supported.
    if type -q starship
        starship init fish | source
        enable_transience
    end

    # eza replaces ls-style commands when installed.
    if type -q eza
        function ls
            command eza --group-directories-first --icons=auto $argv
        end

        function l
            command eza --group-directories-first --icons=auto $argv
        end

        function la
            command eza --all --group-directories-first --icons=auto $argv
        end

        function ll
            command eza --long --all --header --git --group-directories-first --icons=auto $argv
        end

        function lt
            command eza --tree --level=2 --group-directories-first --icons=auto $argv
        end
    end

    # zoxide powers cd; the wrapper also lists the target directory.
    if type -q zoxide
        zoxide init --cmd cd fish | source

        functions --erase cd
        function cd --wraps=__zoxide_z
            __zoxide_z $argv
            and ls
        end
    else
        function cd --wraps=cd
            builtin cd $argv
            and ls
        end
    end
end
