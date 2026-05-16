#!/data/data/com.termux/files/usr/bin/sh

set -eu

colors_sh="${HOME}/.termux/material-colors.sh"
colors_properties="${HOME}/.termux/material-colors.properties"

if [ -r "$colors_sh" ]; then
	. "$colors_sh"
elif [ -r "$colors_properties" ]; then
	while IFS='=' read -r key value; do
		case "$key" in
			''|\#*) continue ;;
		esac
		name=$(printf '%s' "$key" | tr '[:lower:]' '[:upper:]' | tr '-' '_')
		eval "TERMUX_MATERIAL_${name}=\$value"
	done < "$colors_properties"
else
	exit 0
fi

surface=${TERMUX_MATERIAL_SURFACE:-#0F1512}
surface_container=${TERMUX_MATERIAL_SURFACE_CONTAINER:-$surface}
surface_container_high=${TERMUX_MATERIAL_SURFACE_CONTAINER_HIGH:-$surface_container}
surface_container_highest=${TERMUX_MATERIAL_SURFACE_CONTAINER_HIGHEST:-$surface_container_high}
surface_variant=${TERMUX_MATERIAL_SURFACE_VARIANT:-$surface_container_highest}
on_surface=${TERMUX_MATERIAL_ON_SURFACE:-#DEE4DE}
on_surface_variant=${TERMUX_MATERIAL_ON_SURFACE_VARIANT:-$on_surface}
outline_variant=${TERMUX_MATERIAL_OUTLINE_VARIANT:-$surface_variant}
primary=${TERMUX_MATERIAL_PRIMARY:-#8CD5B3}
on_primary=${TERMUX_MATERIAL_ON_PRIMARY:-#003826}
secondary=${TERMUX_MATERIAL_SECONDARY:-#B3CCBE}
tertiary=${TERMUX_MATERIAL_TERTIARY:-#A5CCDF}
error=${TERMUX_MATERIAL_ERROR:-#F2B8B5}
error_container=${TERMUX_MATERIAL_ERROR_CONTAINER:-#8C1D18}

tmux set-option -g status-style "bg=${surface},fg=${on_surface}"
tmux set-option -g status-left-length 42
tmux set-option -g status-right-length 88
tmux set-option -g window-status-separator ""

tmux set-option -g status-left "#[fg=${primary},bg=${surface_container_highest},bold] #S #[fg=${surface_container_highest},bg=${surface}]"
tmux set-option -g status on
tmux set-option -g status-right "#(kew-tmux-status)#[fg=${error},bg=${surface}]#{?pane_in_mode, COPY,}#[fg=${tertiary}]#{?client_prefix, PREFIX,}#[fg=${secondary}]#{?window_zoomed_flag, ZOOM,}#[range=user|btop,fg=${primary}] #(launcher-system-monitor cpu)#[range=none] #[fg=${outline_variant}]│ #[range=user|btop,fg=${secondary}]#(launcher-system-monitor ram)#[range=none] #[fg=${outline_variant}]│ #[fg=${tertiary}]#(launcher-weather-widget) "
tmux set-option -g status-format[1] "#[align=centre,fg=${on_surface_variant},bg=${surface}]#(kew-now-playing)"
tmux bind-key -n MouseDown1Status run-shell 'case "#{mouse_status_range}" in btop) command -v mini-btop-shizuku >/dev/null 2>&1 && tmux new-window -n btop "mini-btop-shizuku" || tmux display-message "Run ~/setup-btop-rish first" ;; esac'

tmux set-window-option -g window-status-format "#[fg=${on_surface_variant},bg=${surface}] #I:#W "
tmux set-window-option -g window-status-current-format "#[fg=${on_primary},bg=${primary},bold] #I:#W #[fg=${primary},bg=${surface}]"
tmux set-window-option -g window-status-activity-style "fg=${tertiary},bg=${surface},bold"
tmux set-window-option -g window-status-bell-style "fg=${error},bg=${error_container},bold"

tmux set-option -g pane-border-style "fg=${outline_variant}"
tmux set-option -g pane-active-border-style "fg=${primary}"
tmux set-option -g display-panes-colour "$secondary"
tmux set-option -g display-panes-active-colour "$primary"

tmux set-option -g message-style "bg=${surface_container_highest},fg=${on_surface}"
tmux set-option -g message-command-style "bg=${surface_container_high},fg=${on_surface}"
tmux set-option -g mode-style "bg=${primary},fg=${on_primary},bold"
tmux set-window-option -g clock-mode-colour "$primary"

tmux set-option -g copy-mode-match-style "bg=${surface_variant},fg=${on_surface}"
tmux set-option -g copy-mode-current-match-style "bg=${tertiary},fg=${surface}"
