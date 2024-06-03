package com.jahirtrap.vosk;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            EditTextPreference lineCommandPreference = findPreference("line_command_preference");
            if (lineCommandPreference != null) {
                updateLineCommandSummary(lineCommandPreference);

                lineCommandPreference.setOnPreferenceChangeListener((preference, value) -> {
                    String cleanedValue = ((String) value).trim();
                    if (cleanedValue.isEmpty()) {
                        cleanedValue = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("line_command_preference", "línea");
                    }
                    preference.setSummary(getString(R.string.line_command_summary, cleanedValue));
                    ((EditTextPreference) preference).setText(cleanedValue);
                    return false;
                });
            }

            SwitchPreferenceCompat visualizerSwitch = findPreference("visualizer_switch");
            if (visualizerSwitch != null) {
                updateVisualizerSummary(visualizerSwitch);

                visualizerSwitch.setOnPreferenceChangeListener((preference, value) -> {
                    boolean isChecked = (Boolean) value;
                    updateVisualizerSummary((SwitchPreferenceCompat) preference, isChecked);
                    return true;
                });
            }

            Preference templatesPreference = findPreference("templates");
            if (templatesPreference != null) {
                templatesPreference.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(getContext(), TemplatesActivity.class));
                    return true;
                });
            }

            Preference resetPreference = findPreference("reset_preferences");
            if (resetPreference != null) {
                resetPreference.setOnPreferenceClickListener(preference -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.reset_preferences_alert_title)
                            .setMessage(R.string.reset_preferences_alert_message)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().clear().apply();
                                PreferenceManager.setDefaultValues(requireContext(), R.xml.root_preferences, true);

                                getParentFragmentManager()
                                        .beginTransaction()
                                        .replace(R.id.settings_container, new SettingsFragment())
                                        .commit();
                            })
                            .setNegativeButton(R.string.no, null)
                            .show();
                    return true;
                });
            }
        }

        private void updateLineCommandSummary(EditTextPreference preference) {
            updateLineCommandSummary(preference, preference.getText());
        }

        private void updateLineCommandSummary(EditTextPreference preference, String value) {
            if (value == null || value.trim().isEmpty()) {
                value = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("line_command_preference", "línea");
            } else {
                value = value.trim();
            }
            preference.setSummary(getString(R.string.line_command_summary, value));
        }

        private void updateVisualizerSummary(SwitchPreferenceCompat preference) {
            updateVisualizerSummary(preference, preference.isChecked());
        }

        private void updateVisualizerSummary(SwitchPreferenceCompat preference, boolean isEnabled) {
            String status = isEnabled ? getString(R.string.on) : getString(R.string.off);
            preference.setSummary(getString(R.string.visualizer_switch_summary, status));
        }
    }
}
