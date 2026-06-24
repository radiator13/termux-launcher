package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
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
        CharSequence[] styledEntries = styleEntries(context, entries);
        new MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle(preference))
            .setSingleChoiceItems(styledEntries, checked, (dialog, which) -> {
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

    /**
     * Multi-line ListPreference entries embed "Title\nDescription" in one string. The default
     * single-choice row renders the whole thing in one uniform style, so the title and its hint
     * read as one undifferentiated blob. Restyle each multi-line entry so the title line stays
     * full-weight at normal size and the description sits smaller and dimmer beneath it.
     */
    private static CharSequence[] styleEntries(@NonNull Context context, @NonNull CharSequence[] entries) {
        int descriptionColor = MaterialColors.getColor(
            context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFF888888);
        CharSequence[] styled = new CharSequence[entries.length];
        for (int i = 0; i < entries.length; i++) {
            CharSequence entry = entries[i];
            if (entry == null) {
                styled[i] = null;
                continue;
            }
            String text = entry.toString();
            int newline = text.indexOf('\n');
            if (newline < 0) {
                styled[i] = entry;
                continue;
            }
            String titleLine = text.substring(0, newline).trim();
            String description = text.substring(newline + 1).trim();
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(titleLine);
            builder.setSpan(new StyleSpan(Typeface.BOLD), 0, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!description.isEmpty()) {
                builder.append("\n\n");
                int descStart = builder.length();
                builder.append(description);
                builder.setSpan(new RelativeSizeSpan(0.82f), descStart, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(descriptionColor), descStart, builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            styled[i] = builder;
        }
        return styled;
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
