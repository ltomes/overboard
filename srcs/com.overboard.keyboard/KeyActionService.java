package com.overboard.keyboard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Maps physical hardware-key presses to actions, globally (not only while the
 * keyboard is showing). An InputMethodService only receives key events while
 * its input view is visible, so global button mapping is implemented here as
 * an AccessibilityService that filters key events.
 *
 * The trigger key and the per-gesture actions are configured in Overboard's
 * settings ("Physical keys" category):
 *   - while the keyboard is showing -> voice typing (if enabled)
 *   - single-tap / double-tap / long-press -> configurable action
 * Defaults target the iKKO MindOne programmable key (KEYCODE_F1):
 * double-tap -> HA dashboard, long-press -> HA Assist.
 */
public class KeyActionService extends AccessibilityService
{
  /** Default trigger key — the MindOne programmable key reports F1. */
  static final int DEFAULT_KEYCODE = KeyEvent.KEYCODE_F1;
  static final long LONG_PRESS_MS = 500;
  static final long DOUBLE_TAP_MS = 280;

  static final String HA_PKG = "io.homeassistant.companion.android";
  static final String HA_DASHBOARD = "io.homeassistant.companion.android.launch.LaunchActivity";
  static final String HA_ASSIST = "io.homeassistant.companion.android.assist.AssistActivity";

  private final Handler _handler = new Handler(Looper.getMainLooper());
  private boolean _down = false;
  private boolean _longFired = false;
  private int _tapCount = 0;
  private Runnable _longPress;
  private Runnable _singleTap;

  private SharedPreferences prefs()
  {
    return DirectBootAwarePreferences.get_shared_preferences(this);
  }

  @Override
  protected boolean onKeyEvent(KeyEvent event)
  {
    final SharedPreferences p = prefs();
    if (!p.getBoolean("physical_keys_enabled", true))
      return false; // feature off — leave the key untouched
    int targetKey = parse_keycode(p.getString("physical_key_keycode", null), DEFAULT_KEYCODE);
    if (event.getKeyCode() != targetKey)
      return false; // not our key

    // While the Overboard keyboard is showing, the key acts as the
    // voice-typing key (fired on release, no gesture detection). The global
    // gesture actions apply only when the keyboard is hidden.
    if (Keyboard2.isKeyboardVisible() && p.getBoolean("physical_key_voice_when_open", true))
    {
      cancel_pending();
      if (event.getAction() == KeyEvent.ACTION_UP)
        Keyboard2.triggerVoiceTyping();
      return true;
    }

    switch (event.getAction())
    {
      case KeyEvent.ACTION_DOWN:
        if (event.getRepeatCount() == 0)
        {
          _down = true;
          _longFired = false;
          _longPress = new Runnable() { public void run() {
            if (_down)
            {
              _longFired = true;
              perform_action(prefs().getString("physical_key_long_press", "ha_assist"));
            }
          }};
          _handler.postDelayed(_longPress, LONG_PRESS_MS);
        }
        return true; // consume

      case KeyEvent.ACTION_UP:
        _down = false;
        if (_longPress != null)
          _handler.removeCallbacks(_longPress);
        if (_longFired)
        {
          _longFired = false;
          return true; // long-press already handled
        }
        // Tap: detect single vs double.
        _tapCount++;
        if (_tapCount == 1)
        {
          _singleTap = new Runnable() { public void run() {
            _tapCount = 0;
            perform_action(prefs().getString("physical_key_single_tap", "none"));
          }};
          _handler.postDelayed(_singleTap, DOUBLE_TAP_MS);
        }
        else
        {
          if (_singleTap != null)
            _handler.removeCallbacks(_singleTap);
          _tapCount = 0;
          perform_action(prefs().getString("physical_key_double_tap", "ha_dashboard"));
        }
        return true;
    }
    return true;
  }

  /** Cancel any in-progress gesture detection (e.g. when the keyboard appears
      mid-gesture and the key switches roles). */
  private void cancel_pending()
  {
    _down = false;
    _longFired = false;
    _tapCount = 0;
    if (_longPress != null)
      _handler.removeCallbacks(_longPress);
    if (_singleTap != null)
      _handler.removeCallbacks(_singleTap);
  }

  private void perform_action(String action)
  {
    if (action == null)
      return;
    switch (action)
    {
      case "voice":
        Keyboard2.triggerVoiceTyping();
        break;
      case "ha_dashboard":
        Logs.debug("KeyActionService: action -> HA dashboard");
        launch_activity(HA_PKG, HA_DASHBOARD);
        break;
      case "ha_assist":
        Logs.debug("KeyActionService: action -> HA Assist");
        launch_activity(HA_PKG, HA_ASSIST);
        break;
      case "none":
      default:
        break;
    }
  }

  static int parse_keycode(String s, int def)
  {
    if (s == null)
      return def;
    try { return Integer.parseInt(s.trim()); }
    catch (Exception e) { return def; }
  }

  private void launch_activity(String pkg, String cls)
  {
    try
    {
      Intent i = new Intent();
      i.setClassName(pkg, cls);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    }
    catch (Exception e)
    {
      Logs.exn("KeyActionService.launch_activity " + cls, e);
    }
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void onInterrupt() {}
}
