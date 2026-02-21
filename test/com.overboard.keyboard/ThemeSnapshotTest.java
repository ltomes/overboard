package com.overboard.keyboard;

import android.content.SharedPreferences;

import app.cash.paparazzi.DeviceConfig;
import app.cash.paparazzi.Paparazzi;
import com.android.ide.common.rendering.api.SessionParams;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ThemeSnapshotTest
{
  @Rule
  public Paparazzi paparazzi = new Paparazzi();

  @Before
  public void setup()
  {
    // Use Pixel 5 for high-res 1080px-wide screenshots, SHRINK to crop to view height
    paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5, null,
        SessionParams.RenderingMode.SHRINK);
    // Initialize Config with stub prefs so the keyboard can render
    StubPrefs prefs = new StubPrefs();
    Config.initGlobalConfig(prefs, paparazzi.getResources(), false, null);
  }

  @Test
  public void pbtfansXRay()
  {
    snapshotTheme(R.style.PBTfansXRay, "PBTfans X-Ray");
  }

  @Test
  public void mitoPulse()
  {
    snapshotTheme(R.style.MitoPulse, "Mito Pulse");
  }

  @Test
  public void mitoTTY()
  {
    snapshotTheme(R.style.MitoTTY, "Mito TTY");
  }

  @Test
  public void mitoMT3()
  {
    snapshotTheme(R.style.MitoMT3, "Mito MT3");
  }

  @Test
  public void taiHaoMiami()
  {
    snapshotTheme(R.style.TaiHaoMiami, "Tai-Hao Miami");
  }

  @Test
  public void dark()
  {
    snapshotTheme(R.style.Dark, "Dark");
  }

  @Test
  public void light()
  {
    snapshotTheme(R.style.Light, "Light");
  }

  private void snapshotTheme(int styleRes, String name)
  {
    Keyboard2View view = new Keyboard2View(paparazzi.getContext(), null);
    view.applyThemeStyle(styleRes);
    KeyboardData kbd = KeyboardData.load(paparazzi.getResources(),
        R.xml.latn_qwerty_us);
    // Apply layout modifiers to add bottom row (Ctrl, Fn, space, arrows)
    kbd = LayoutModifier.modify_layout(kbd);
    view.setKeyboard(kbd);
    paparazzi.snapshot(view, name);
  }

  /** Minimal SharedPreferences that returns defaults for all getters. */
  static class StubPrefs implements SharedPreferences
  {
    public String getString(String key, String def) { return def; }
    public int getInt(String key, int def) { return def; }
    public long getLong(String key, long def) { return def; }
    public float getFloat(String key, float def) { return def; }
    public boolean getBoolean(String key, boolean def) { return def; }
    public boolean contains(String key) { return false; }
    public Map<String, ?> getAll() { return new HashMap<>(); }
    public Set<String> getStringSet(String key, Set<String> def) { return def; }
    public Editor edit() { return new StubEditor(); }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

    static class StubEditor implements Editor
    {
      public Editor putString(String k, String v) { return this; }
      public Editor putStringSet(String k, Set<String> v) { return this; }
      public Editor putInt(String k, int v) { return this; }
      public Editor putLong(String k, long v) { return this; }
      public Editor putFloat(String k, float v) { return this; }
      public Editor putBoolean(String k, boolean v) { return this; }
      public Editor remove(String k) { return this; }
      public Editor clear() { return this; }
      public boolean commit() { return true; }
      public void apply() {}
    }
  }
}
