package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
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

    public void setOnFilterSelectedListener(@Nullable OnFilterSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        bindPill((TextView) holder.findViewById(R.id.tai_catalog_filter_all), "all");
        bindPill((TextView) holder.findViewById(R.id.tai_catalog_filter_litert), TaiModelSpec.BACKEND_LITERT_LM);
        bindPill((TextView) holder.findViewById(R.id.tai_catalog_filter_mnn), TaiModelSpec.BACKEND_MNN_LLM);
    }

    private void bindPill(@Nullable TextView pill, @NonNull String value) {
        if (pill == null) return;
        boolean selected = value.equals(selectedValue);
        pill.setSelected(selected);
        int bgAttr = selected
            ? com.termux.shared.R.attr.termuxColorPrimaryContainer
            : com.termux.shared.R.attr.termuxColorSurfacePanel;
        int textAttr = selected
            ? com.termux.shared.R.attr.termuxColorOnPrimaryContainer
            : com.termux.shared.R.attr.termuxColorOnSurfaceVariant;
        pill.setBackgroundTintList(ColorStateList.valueOf(resolveColor(bgAttr)));
        pill.setTextColor(resolveColor(textAttr));
        pill.setOnClickListener(view -> {
            if (!value.equals(selectedValue)) {
                selectedValue = value;
                notifyChanged();
                if (listener != null) listener.onFilterSelected(value);
            }
        });
    }

    private int resolveColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant);
    }
}
