package com.germainz.crappalinks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class Preferences extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null)
            getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();

    }

    public static class PrefsFragment extends PreferenceFragment {
        @SuppressWarnings("deprecation")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.prefs);

            Preference prefShowAppIcon = findPreference("pref_show_app_icon");
            prefShowAppIcon.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int state = (Boolean) newValue ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    final ComponentName alias = new ComponentName(getActivity(),
                            "com.germainz.crappalinks.Preferences-Alias");
                    PackageManager packageManager = getActivity().getPackageManager();
                    packageManager.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
                    return true;
                }
            });

            CheckBoxPreference prefUseLongUrl = (CheckBoxPreference) findPreference("pref_use_long_url");
            findPreference("pref_resolve_all").setEnabled(!prefUseLongUrl.isChecked());
            prefUseLongUrl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    findPreference("pref_resolve_all").setEnabled(!(Boolean) newValue);
                    return true;
                }
            });
        }
    }
}