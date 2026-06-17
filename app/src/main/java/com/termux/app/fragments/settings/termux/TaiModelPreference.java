package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * Card-style row for a TAI model: name + status pill, role tag (summary),
 * monospace size/accelerator/memory meta line, and a download progress bar.
 */
public final class TaiModelPreference extends Preference {

    /** Backend tone controls pill color: LiteRT uses primaryContainer, MLC uses tertiaryContainer. */
    public enum BackendTone { LITERT, MLC, NEUTRAL }

    private boolean showProgress;
    private boolean indeterminate;
    private int progress;
    private CharSequence pillText = "";
    private boolean pillAccent;
    private BackendTone backendTone = BackendTone.NEUTRAL;
    private CharSequence metaLine = "";
    private CharSequence primaryActionText = "";
    private boolean primaryActionEnabled = true;
    private boolean primaryActionDestructive;
    private View.OnClickListener primaryActionClickListener;
    private CharSequence tuneActionText = "";
    private View.OnClickListener tuneActionClickListener;

    public TaiModelPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_tai_model);
        setIconSpaceReserved(false);
    }

    public void setDownloadProgress(boolean showProgress, boolean indeterminate, int progress) {
        this.showProgress = showProgress;
        this.indeterminate = indeterminate;
        this.progress = Math.max(0, Math.min(10000, progress));
        notifyChanged();
    }

    public void setPill(@Nullable CharSequence text, boolean accent) {
        this.pillText = text == null ? "" : text;
        this.pillAccent = accent;
        notifyChanged();
    }

    public void setBackendTone(@NonNull BackendTone tone) {
        this.backendTone = tone;
        notifyChanged();
    }

    public void setMetaLine(@Nullable CharSequence metaLine) {
        this.metaLine = metaLine == null ? "" : metaLine;
        notifyChanged();
    }

public void setPrimaryAction(@Nullable CharSequence text, boolean enabled,
                                  @Nullable View.OnClickListener listener) {
        this.primaryActionText = text == null ? "" : text;
        this.primaryActionEnabled = enabled;
        this.primaryActionDestructive = false;
        this.primaryActionClickListener = listener;
        notifyChanged();
    }

    public void setPrimaryAction(@Nullable CharSequence text, boolean enabled,
                                  boolean destructive, @Nullable View.OnClickListener listener) {
        this.primaryActionText = text == null ? "" : text;
        this.primaryActionEnabled = enabled;
        this.primaryActionDestructive = destructive;
        this.primaryActionClickListener = listener;
        notifyChanged();
    }

    public void setTuneAction(@Nullable CharSequence text, @Nullable View.OnClickListener listener) {
        this.tuneActionText = text == null ? "" : text;
        this.tuneActionClickListener = listener;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View view = holder.findViewById(R.id.tai_download_progress);
        if (view instanceof ProgressBar) {
            ProgressBar progressBar = (ProgressBar) view;
            progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            progressBar.setIndeterminate(indeterminate);
            progressBar.setProgress(progress);
        }

        TextView pill = (TextView) holder.findViewById(R.id.tai_model_pill);
        if (pill != null) {
            if (pillText.length() == 0) {
                pill.setVisibility(View.GONE);
            } else {
                pill.setVisibility(View.VISIBLE);
                pill.setText(pillText);
                int pillTextColor;
                int pillBgColor;
                if (pillAccent) {
                    int pillAttr = backendTone == BackendTone.MLC
                        ? com.termux.shared.R.attr.termuxColorTertiaryContainer
                        : com.termux.shared.R.attr.termuxColorPrimaryContainer;
                    int pillOnAttr = backendTone == BackendTone.MLC
                        ? com.termux.shared.R.attr.termuxColorOnTertiaryContainer
                        : com.termux.shared.R.attr.termuxColorOnPrimaryContainer;
                    pillTextColor = resolveAttrColor(pillOnAttr);
                    pillBgColor = resolveAttrColor(pillAttr);
                } else {
                    pillTextColor = resolveAttrColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
                    pillBgColor = resolveAttrColor(com.termux.shared.R.attr.termuxColorSurfacePanel);
                }
                pill.setTextColor(pillTextColor);
                pill.setBackgroundTintList(ColorStateList.valueOf(pillBgColor));
            }
        }

        TextView meta = (TextView) holder.findViewById(R.id.tai_model_meta);
        if (meta != null) {
            if (metaLine.length() == 0) {
                meta.setVisibility(View.GONE);
            } else {
                meta.setVisibility(View.VISIBLE);
                meta.setText(metaLine);
            }
        }

        Button tuneAction = (Button) holder.findViewById(R.id.tai_model_tune_action);
        boolean showTune = bindActionButton(tuneAction, tuneActionText, true, tuneActionClickListener);
        Button primaryAction = (Button) holder.findViewById(R.id.tai_model_primary_action);
        boolean showPrimary = bindActionButton(primaryAction, primaryActionText, primaryActionEnabled,
            primaryActionClickListener);
        if (primaryAction != null && primaryActionDestructive) {
            primaryAction.setTextColor(resolveAttrColor(com.termux.shared.R.attr.termuxColorError));
        }
        LinearLayout actions = (LinearLayout) holder.findViewById(R.id.tai_model_actions);
        if (actions != null) actions.setVisibility(showTune || showPrimary ? View.VISIBLE : View.GONE);
    }

    private boolean bindActionButton(@Nullable Button button, @NonNull CharSequence text,
                                     boolean enabled, @Nullable View.OnClickListener listener) {
        if (button == null) return false;
        if (text.length() == 0) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return false;
        }
        button.setVisibility(View.VISIBLE);
        button.setText(text);
        button.setEnabled(enabled);
        button.setOnClickListener(listener);
        return true;
    }

    private int resolveAttrColor(int attr) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant);
    }
}
