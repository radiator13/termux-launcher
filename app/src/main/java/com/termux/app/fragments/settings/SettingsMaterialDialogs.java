package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Renders the common androidx preference dialogs (ListPreference, EditTextPreference)
 * as rounded Material dialogs so every settings screen matches the redesign instead of
 * falling back to the platform AppCompat alert dialog.
 */
public final class SettingsMaterialDialogs {

    private SettingsMaterialDialogs() {}

    /** Returns true if a Material dialog was shown for this preference. */
    public static boolean show(@NonNull Context context, @NonNull Preference preference) {
        if (preference instanceof ListPreference) {
            return showList(context, (ListPreference) preference);
        }
        if (preference instanceof EditTextPreference) {
            return showEditText(context, (EditTextPreference) preference);
        }
        return false;
    }

    private static boolean showList(@NonNull Context context, @NonNull ListPreference preference) {
        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        if (entries == null || values == null) return false;
        int checked = preference.findIndexOfValue(preference.getValue());

        // Entries that embed "Title\nDescription" can't render correctly in a single CheckedTextView
        // (the radio centres over the whole block). Use a custom radio list that aligns each radio
        // with its bold title and puts the dim description beneath it.
        for (CharSequence entry : entries) {
            if (entry != null && entry.toString().indexOf('\n') >= 0) {
                return showRichList(context, preference, entries, values, checked);
            }
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle(preference))
            .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                if (which < 0 || which >= values.length) return;
                String value = values[which].toString();
                dialog.dismiss();
                if (preference.callChangeListener(value)) {
                    preference.setValue(value);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
        return true;
    }

    private static boolean showRichList(@NonNull Context context, @NonNull ListPreference preference,
                                        @NonNull CharSequence[] entries, @NonNull CharSequence[] values, int checked) {
        float d = context.getResources().getDisplayMetrics().density;
        int titleColor = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface, 0xFFECEFF4);
        int descColor = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFF9AA3B2);
        int accent = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0xFF8AB4F8);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(8 * d);
        container.setPadding(padH, Math.round(4 * d), padH, 0);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle(preference))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);

        for (int i = 0; i < entries.length; i++) {
            final int idx = i;
            String text = entries[i] == null ? "" : entries[i].toString();
            int nl = text.indexOf('\n');
            String title = nl < 0 ? text : text.substring(0, nl).trim();
            String desc = nl < 0 ? "" : text.substring(nl + 1).trim();

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            if (ripple.resourceId != 0) row.setBackgroundResource(ripple.resourceId);
            int rowPadV = Math.round(12 * d);
            row.setPadding(Math.round(8 * d), rowPadV, Math.round(8 * d), rowPadV);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            RadioButton radio = new RadioButton(context);
            radio.setClickable(false);
            radio.setFocusable(false);
            radio.setChecked(idx == checked);
            radio.setButtonTintList(ColorStateList.valueOf(accent));
            header.addView(radio);

            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextColor(titleColor);
            titleView.setTextSize(16f);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.setMarginStart(Math.round(8 * d));
            header.addView(titleView, titleParams);

            row.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (!desc.isEmpty()) {
                TextView descView = new TextView(context);
                descView.setText(desc);
                descView.setTextColor(descColor);
                descView.setTextSize(13f);
                descView.setLineSpacing(Math.round(2 * d), 1f);
                LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                descParams.setMarginStart(Math.round(48 * d)); // indent under the title (past the radio)
                descParams.topMargin = Math.round(2 * d);
                row.addView(descView, descParams);
            }

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (idx < values.length) {
                    String value = values[idx].toString();
                    if (preference.callChangeListener(value)) {
                        preference.setValue(value);
                    }
                }
            });
            container.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        dialog.show();
        return true;
    }

    private static boolean showEditText(@NonNull Context context, @NonNull EditTextPreference preference) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        String current = preference.getText();
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle(preference))
            .setView(wrap(context, input))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String value = input.getText().toString();
                if (preference.callChangeListener(value)) {
                    preference.setText(value);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
        return true;
    }

    private static CharSequence dialogTitle(@NonNull Preference preference) {
        if (preference instanceof androidx.preference.DialogPreference) {
            CharSequence dialogTitle = ((androidx.preference.DialogPreference) preference).getDialogTitle();
            if (dialogTitle != null) return dialogTitle;
        }
        return preference.getTitle();
    }

    /** Wraps a dialog input view with the standard Material dialog content padding. */
    public static LinearLayout wrap(@NonNull Context context, @NonNull View input) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        layout.addView(input);
        return layout;
    }
}
