package com.overboard.keyboard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;

/**
 * Applies the user's keyboard theme colors to app activities (launcher,
 * settings) so the app looks cohesive with the chosen keyboard theme.
 *
 * Call {@link #applyToActivity(Activity)} before setContentView() or
 * addPreferencesFromResource().
 */
public final class AppThemeHelper
{
  /** Apply the keyboard theme colors to the given activity. Falls back to
      the default Material Light theme if anything goes wrong. */
  public static void applyToActivity(Activity activity)
  {
    try
    {
      SharedPreferences prefs =
          PreferenceManager.getDefaultSharedPreferences(activity);
      String themeName = prefs.getString("theme", "system");
      int styleRes = Config.getThemeId(activity.getResources(), themeName);

      // Read isLightTheme and key colors from the keyboard theme
      TypedArray s = activity.getTheme().obtainStyledAttributes(
          null, R.styleable.keyboard, 0, styleRes);
      int colorKeyboard = s.getColor(R.styleable.keyboard_colorKeyboard, 0xFF1b1b1b);
      int colorLabel = s.getColor(R.styleable.keyboard_colorLabel, 0xFFFFFFFF);
      s.recycle();

      // Determine light/dark from the keyboard theme
      TypedArray ta = activity.obtainStyledAttributes(styleRes,
          new int[]{ android.R.attr.isLightTheme });
      boolean isLight = ta.getBoolean(0, false);
      ta.recycle();

      // Apply the appropriate base theme
      activity.setTheme(isLight ? R.style.appThemeLight : R.style.appThemeDark);

      // Override window colors
      Window window = activity.getWindow();
      window.setStatusBarColor(colorKeyboard);
      window.setNavigationBarColor(colorKeyboard);
      if (Build.VERSION.SDK_INT >= 23)
      {
        int flags = window.getDecorView().getSystemUiVisibility();
        if (isLight)
          flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else
          flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
      }
      if (Build.VERSION.SDK_INT >= 26)
      {
        int flags = window.getDecorView().getSystemUiVisibility();
        if (isLight)
          flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else
          flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
      }

      // Set the window background to the keyboard background color
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.getDecorView().setBackgroundColor(colorKeyboard);
    }
    catch (Exception e)
    {
      // Fallback: use default app theme, no crash
      activity.setTheme(R.style.appTheme);
    }
  }
}
