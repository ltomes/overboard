package com.overboard.keyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class SettingsActivity extends PreferenceActivity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    AppThemeHelper.applyToActivity(this);
    super.onCreate(savedInstanceState);
    // The preferences can't be read when in direct-boot mode. Avoid crashing
    // and don't allow changing the settings.
    // Run the config migration on this prefs as it might be different from the
    // one used by the keyboard, which have been migrated.
    try
    {
      Config.migrate(getPreferenceManager().getSharedPreferences());
    }
    catch (Exception _e) { fallbackEncrypted(); return; }
    addPreferencesFromResource(R.xml.settings);

    boolean foldableDevice = FoldStateTracker.isFoldableDevice(this);
    findPreference("margin_bottom_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("margin_bottom_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_portrait_unfolded").setEnabled(foldableDevice);
    findPreference("horizontal_margin_landscape_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_unfolded").setEnabled(foldableDevice);
    findPreference("keyboard_height_landscape_unfolded").setEnabled(foldableDevice);

    // Debug settings are only available in debug builds (R.bool.debug_logs
    // is set to true by build.gradle.kts for the debug build type only).
    if (getResources().getBoolean(R.bool.debug_logs))
    {
      findPreference("share_debug_log").setOnPreferenceClickListener(pref -> {
        share_debug_log();
        return true;
      });
    }
    else
    {
      Preference debugCategory = findPreference("category_debug");
      if (debugCategory != null)
        getPreferenceScreen().removePreference(debugCategory);
    }
  }

  private void share_debug_log()
  {
    File logFile = Logs.get_log_file();
    if (logFile == null || !logFile.exists() || logFile.length() == 0)
    {
      Toast.makeText(this, "No debug log file found. Enable debug logging first.",
          Toast.LENGTH_SHORT).show();
      return;
    }
    Uri uri = FileProvider.getUriForFile(this,
        getPackageName() + ".fileprovider", logFile);
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_STREAM, uri);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivity(Intent.createChooser(intent, "Share debug log"));
  }

  void fallbackEncrypted()
  {
    // Can't communicate with the user here.
    finish();
  }

  protected void onStop()
  {
    DirectBootAwarePreferences
      .copy_preferences_to_protected_storage(this,
          getPreferenceManager().getSharedPreferences());
    super.onStop();
  }
}
