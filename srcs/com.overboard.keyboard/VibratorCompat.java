package com.overboard.keyboard;

import android.content.Context;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class VibratorCompat
{
  public static void vibrate(View v, Config config)
  {
    if (config.vibrate_custom)
    {
      if (config.vibrate_duration > 0)
        vibrator_vibrate(v, config.vibrate_duration);
    }
    else
    {
      // performHapticFeedback may not work in overlay windows on some devices.
      // Fall back to the Vibrator service if it returns false.
      boolean ok = v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
          HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
      if (!ok)
        vibrator_vibrate(v, 20);
    }
  }

  /** Use the older [Vibrator] when the newer API is not available or the user
      wants more control. */
  static void vibrator_vibrate(View v, long duration)
  {
    try
    {
      get_vibrator(v).vibrate(duration);
    }
    catch (Exception e) {}
  }

  static Vibrator vibrator_service = null;

  static Vibrator get_vibrator(View v)
  {
    if (vibrator_service == null)
    {
      vibrator_service =
        (Vibrator)v.getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }
    return vibrator_service;
  }
}
