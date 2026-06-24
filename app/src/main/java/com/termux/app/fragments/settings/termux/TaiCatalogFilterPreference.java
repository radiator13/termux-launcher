package com.termux.app.fragments.settings.termux;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;
import com.termux.ai.TaiModelSpec;

public final class TaiCatalogFilterPreference extends Preference {

    public interface OnFilterSelectedListener {
        void onFilterSelected(@NonNull String value);
    }

    private String selectedValue = "all";
    private boolean includeAllOption = true;
    @Nullable private OnFilterSelectedListener listener;

    public TaiCatalogFilterPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_tai_catalog_filters);
        setIconSpaceReserved(false);
        setSelectable(false);
    }

    public TaiCatalogFilterPreference(@NonNull Context context) {
        this(context, null);
    }

    public void setSelectedValue(@NonNull String value) {
        selectedValue = value;
        notifyChanged();
    }

    public void setIncludeAllOption(boolean includeAllOption) {
        this.includeAllOption = includeAllOption;
        if (!includeAllOption && ("all".equals(selectedValue) || "installed".equals(selectedValue) || "usable".equals(selectedValue))) {
            selectedValue = TaiModelSpec.BACKEND_LITERT_LM;
        }
        notifyChanged();
    }

    public void setOnFilterSelectedListener(@Nullable OnFilterSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView all = (TextView) holder.findViewById(R.id.tai_catalog_filter_all);
        TextView liteRt = (TextView) holder.findViewById(R.id.tai_catalog_filter_litert);
        TextView mnn = (TextView) holder.findViewById(R.id.tai_catalog_filter_mnn);
        TextView installed = (TextView) holder.findViewById(R.id.tai_catalog_filter_installed);
        TextView usable = (TextView) holder.findViewById(R.id.tai_catalog_filter_usable);
        View indicator = holder.findViewById(R.id.tai_backend_segment_indicator);
        LinearLayout labels = (LinearLayout) holder.findViewById(R.id.tai_backend_segment_labels);
        if (all != null) all.setVisibility(includeAllOption ? View.VISIBLE : View.GONE);
        if (installed != null) installed.setVisibility(includeAllOption ? View.VISIBLE : View.GONE);
        if (usable != null) usable.setVisibility(includeAllOption ? View.VISIBLE : View.GONE);
        bindPill(all, "all");
        bindPill(liteRt, TaiModelSpec.BACKEND_LITERT_LM);
        bindPill(mnn, TaiModelSpec.BACKEND_MNN_LLM);
        bindPill(installed, "installed");
        bindPill(usable, "usable");
        positionIndicator(labels, indicator, selectedPill(all, liteRt, mnn, installed, usable), false);
    }

    private void bindPill(@Nullable TextView pill, @NonNull String value) {
        if (pill == null) return;
        boolean selected = value.equals(selectedValue);
        pill.setSelected(selected);
        int textAttr = selected
            ? com.termux.shared.R.attr.termuxColorOnPrimaryContainer
            : com.termux.shared.R.attr.termuxColorOnSurfaceVariant;
        pill.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        pill.setTextColor(resolveColor(textAttr));
        pill.setOnClickListener(view -> {
            if (!value.equals(selectedValue)) {
                selectedValue = value;
                LinearLayout labels = (LinearLayout) pill.getParent();
                refreshPillColors(labels);
                animateIndicator(labels);
                if (listener != null) pill.postDelayed(() -> listener.onFilterSelected(value), 180L);
            }
        });
    }

    private void animateIndicator(@Nullable View parent) {
        if (!(parent instanceof LinearLayout)) return;
        LinearLayout labels = (LinearLayout) parent;
        View indicator = ((View) labels.getParent()).findViewById(R.id.tai_backend_segment_indicator);
        TextView selected = selectedPill(
            labels.findViewById(R.id.tai_catalog_filter_all),
            labels.findViewById(R.id.tai_catalog_filter_litert),
            labels.findViewById(R.id.tai_catalog_filter_mnn),
            labels.findViewById(R.id.tai_catalog_filter_installed),
            labels.findViewById(R.id.tai_catalog_filter_usable));
        positionIndicator(labels, indicator, selected, true);
    }

    private void refreshPillColors(@NonNull LinearLayout labels) {
        TextView all = labels.findViewById(R.id.tai_catalog_filter_all);
        TextView liteRt = labels.findViewById(R.id.tai_catalog_filter_litert);
        TextView mnn = labels.findViewById(R.id.tai_catalog_filter_mnn);
        TextView installed = labels.findViewById(R.id.tai_catalog_filter_installed);
        TextView usable = labels.findViewById(R.id.tai_catalog_filter_usable);
        refreshPillColor(all, "all");
        refreshPillColor(liteRt, TaiModelSpec.BACKEND_LITERT_LM);
        refreshPillColor(mnn, TaiModelSpec.BACKEND_MNN_LLM);
        refreshPillColor(installed, "installed");
        refreshPillColor(usable, "usable");
    }

    private void refreshPillColor(@Nullable TextView pill, @NonNull String value) {
        if (pill == null) return;
        boolean selected = value.equals(selectedValue);
        pill.setSelected(selected);
        int textAttr = selected
            ? com.termux.shared.R.attr.termuxColorOnPrimaryContainer
            : com.termux.shared.R.attr.termuxColorOnSurfaceVariant;
        pill.setTextColor(resolveColor(textAttr));
    }

    @Nullable
    private TextView selectedPill(@Nullable TextView all, @Nullable TextView liteRt, @Nullable TextView mnn,
                                  @Nullable TextView installed, @Nullable TextView usable) {
        if ("all".equals(selectedValue) && includeAllOption) return all;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(selectedValue)) return mnn;
        if ("installed".equals(selectedValue) && includeAllOption) return installed;
        if ("usable".equals(selectedValue) && includeAllOption) return usable;
        return liteRt;
    }

    private void positionIndicator(@Nullable LinearLayout labels, @Nullable View indicator,
                                   @Nullable TextView selected, boolean animate) {
        if (labels == null || indicator == null || selected == null) return;
        labels.post(() -> {
            if (selected.getVisibility() != View.VISIBLE || selected.getWidth() <= 0) return;
            ViewGroup.LayoutParams params = indicator.getLayoutParams();
            if (params.width != selected.getWidth()) {
                params.width = selected.getWidth();
                indicator.setLayoutParams(params);
            }
            float target = selected.getLeft();
            if (animate) {
                ValueAnimator animator = ValueAnimator.ofFloat(indicator.getTranslationX(), target);
                animator.setDuration(180L);
                animator.addUpdateListener(animation ->
                    indicator.setTranslationX((Float) animation.getAnimatedValue()));
                animator.start();
            } else {
                indicator.setTranslationX(target);
            }
        });
    }

    private int resolveColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant);
    }
}
