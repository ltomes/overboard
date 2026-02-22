package com.overboard.keyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Keyboard2View extends View
  implements View.OnTouchListener, Pointers.IPointerEventHandler
{
  private KeyboardData _keyboard;

  /** The key holding the shift key is used to set shift state from
      autocapitalisation. */
  private KeyboardData.Key _shift_key;

  /** Used to add fake pointers. */
  private KeyboardData.Key _compose_key;

  private Pointers _pointers;

  private Pointers.Modifiers _mods;

  private static int _currentWhat = 0;

  private Config _config;

  private float _keyWidth;
  private float _mainLabelSize;
  private float _subLabelSize;
  private float _marginRight;
  private float _marginLeft;
  private float _marginBottom;
  private int _insets_left = 0;
  private int _insets_right = 0;
  private int _insets_bottom = 0;

  private Theme _theme;
  private Theme.Computed _tc;

  private static RectF _tmpRect = new RectF();

  private Paint _topGradientPaint;
  private float _topGradientHeight;

  /** Opacity multiplier [0.0, 1.0] for idle fade / peek mode. */
  private float _fadeMultiplier = 1.0f;
  private boolean _peekMode = false;
  private boolean _selectionFadeActive = false;
  private ValueAnimator _fadeAnimator;
  private final Handler _fadeHandler = new Handler(Looper.getMainLooper());
  private final Runnable _fadeOutRunnable = () -> startFadeAnimation(0.1f, 800);

  enum Vertical
  {
    TOP,
    CENTER,
    BOTTOM
  }

  public Keyboard2View(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _config = Config.globalConfig();
    _pointers = new Pointers(this, _config);
    refresh_navigation_bar(context);
    setOnTouchListener(this);
    int layout_id = (attrs == null) ? 0 :
      attrs.getAttributeResourceValue(null, "layout", 0);
    if (layout_id == 0)
      reset();
    else
      setKeyboard(KeyboardData.load(getResources(), layout_id));
  }

  /** Re-apply a theme style. Used by snapshot tests. */
  public void applyThemeStyle(int defStyleRes)
  {
    _theme = new Theme(getContext(), null, defStyleRes);
    requestLayout();
    invalidate();
  }

  private Window getParentWindow(Context context)
  {
    if (context instanceof InputMethodService)
      return ((InputMethodService)context).getWindow().getWindow();
    if (context instanceof ContextWrapper)
      return getParentWindow(((ContextWrapper)context).getBaseContext());
    return null;
  }

  public void refresh_navigation_bar(Context context)
  {
    if (VERSION.SDK_INT < 21)
      return;
    // The intermediate Window is a [Dialog].
    // In overlay mode, the view is not attached to the IME window, so
    // getParentWindow may return a window we shouldn't modify. Guard against
    // null and catch exceptions.
    Window w = getParentWindow(context);
    if (w == null)
      return;
    try
    {
      w.setNavigationBarColor(_theme.colorNavBar);
    }
    catch (Exception e) { return; }
    if (VERSION.SDK_INT < 26)
      return;
    int uiFlags = getSystemUiVisibility();
    if (_theme.isLightNavBar)
      uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    else
      uiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    setSystemUiVisibility(uiFlags);
  }

  public void setKeyboard(KeyboardData kw)
  {
    _keyboard = kw;
    _shift_key = _keyboard.findKeyWithValue(KeyValue.SHIFT);
    _compose_key = _keyboard.findKeyWithValue(KeyValue.COMPOSE);
    KeyModifier.set_modmap(_keyboard.modmap);
    reset();
  }

  public void reset()
  {
    _mods = Pointers.Modifiers.EMPTY;
    _pointers.clear();
    requestLayout();
    invalidate();
  }

  void set_fake_ptr_latched(KeyboardData.Key key, KeyValue kv, boolean latched,
      boolean lock)
  {
    if (_keyboard == null || key == null)
      return;
    _pointers.set_fake_pointer_state(key, kv, latched, lock);
  }

  /** Called by auto-capitalisation. */
  public void set_shift_state(boolean latched, boolean lock)
  {
    set_fake_ptr_latched(_shift_key, KeyValue.SHIFT, latched, lock);
  }

  /** Called from [KeyEventHandler]. */
  public void set_compose_pending(boolean pending)
  {
    set_fake_ptr_latched(_compose_key, KeyValue.COMPOSE, pending, false);
  }

  /** Called from [Keybard2.onUpdateSelection].  */
  public void set_selection_state(boolean selection_state)
  {
    if (_config.editor_config.selection_mode_enabled)
      set_fake_ptr_latched(KeyboardData.Key.EMPTY,
          KeyValue.SELECTION_MODE, selection_state, true);
  }

  public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
  {
    return KeyModifier.modify(k, mods);
  }

  public void onPointerDown(KeyValue k, boolean isSwipe)
  {
    updateFlags();
    _config.handler.key_down(k, isSwipe);
    invalidate();
    vibrate();
  }

  public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
  {
    // [key_up] must be called before [updateFlags]. The latter might disable
    // flags.
    _config.handler.key_up(k, mods);
    updateFlags();
    invalidate();
  }

  public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
  {
    _config.handler.key_up(k, mods);
    updateFlags();
  }

  public void onPointerFlagsChanged(boolean shouldVibrate)
  {
    updateFlags();
    invalidate();
    if (shouldVibrate)
      vibrate();
  }

  private void updateFlags()
  {
    _mods = _pointers.getModifiers();
    _config.handler.mods_changed(_mods);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    resetIdleTimer();
    if (_fadeMultiplier < 1.0f && !_peekMode && !_selectionFadeActive)
      snapToOpaque();
    int p;
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        _pointers.onTouchUp(event.getPointerId(event.getActionIndex()));
        break;
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        p = event.getActionIndex();
        float tx = event.getX(p);
        float ty = event.getY(p);
        KeyboardData.Key key = getKeyAtPosition(tx, ty);
        if (key != null)
          _pointers.onTouchDown(tx, ty, event.getPointerId(p), key);
        break;
      case MotionEvent.ACTION_MOVE:
        for (p = 0; p < event.getPointerCount(); p++)
          _pointers.onTouchMove(event.getX(p), event.getY(p), event.getPointerId(p));
        break;
      case MotionEvent.ACTION_CANCEL:
        _pointers.onTouchCancel();
        break;
      default:
        return (false);
    }
    return (true);
  }

  private KeyboardData.Row getRowAtPosition(float ty)
  {
    if (_keyboard == null || _tc == null)
      return null;
    float y = _config.marginTop;
    if (ty < y)
      return null;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += (row.shift + row.height) * _tc.row_height;
      if (ty < y)
        return row;
    }
    return null;
  }

  private KeyboardData.Key getKeyAtPosition(float tx, float ty)
  {
    KeyboardData.Row row = getRowAtPosition(ty);
    float x = _marginLeft;
    if (row == null || tx < x)
      return null;
    for (KeyboardData.Key key : row.keys)
    {
      float xLeft = x + key.shift * _keyWidth;
      float xRight = xLeft + key.width * _keyWidth;
      if (tx < xLeft)
        return null;
      if (tx < xRight)
        return key;
      x = xRight;
    }
    return null;
  }

  private void vibrate()
  {
    VibratorCompat.vibrate(this, _config);
  }

  @Override
  public void onMeasure(int wSpec, int hSpec)
  {
    int width;
    DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
    int specMode = MeasureSpec.getMode(wSpec);
    if (specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST)
      width = MeasureSpec.getSize(wSpec);
    else
      width = dm.widthPixels;
    _marginLeft = Math.max(_config.horizontal_margin, _insets_left);
    _marginRight = Math.max(_config.horizontal_margin, _insets_right);
    _marginBottom = _config.margin_bottom + _insets_bottom;
    if (specMode == MeasureSpec.UNSPECIFIED)
      width += _insets_left + _insets_right;
    _keyWidth = (width - _marginLeft - _marginRight) / _keyboard.keysWidth;
    _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard);
    // Compute the size of labels based on the width or the height of keys. The
    // margin around keys is taken into account. Keys normal aspect ratio is
    // assumed to be 3/2 for a 10 columns layout. It's generally more, the
    // width computation is useful when the keyboard is unusually high.
    float labelBaseSize = Math.min(
        _tc.row_height - _tc.vertical_margin,
        (width / 10 - _tc.horizontal_margin) * 3/2
        ) * _config.characterSize;
    _mainLabelSize = labelBaseSize * _config.labelTextSize;
    _subLabelSize = labelBaseSize * _config.sublabelTextSize;
    int height =
      (int)(_tc.row_height * _keyboard.keysHeight
          + _config.marginTop + _marginBottom);
    setMeasuredDimension(width, height);
    // Top-edge gradient
    if (_config.topGradientEnabled)
    {
      float gradH = 30 * dm.density;
      _topGradientHeight = gradH;
      _topGradientPaint = new Paint();
      _topGradientPaint.setShader(new LinearGradient(
          0, 0, 0, gradH,
          0xFF000000, 0x00000000,
          Shader.TileMode.CLAMP));
      _topGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }
    else
    {
      _topGradientPaint = null;
      _topGradientHeight = 0;
    }
  }

  Rect _cached_exclusion_rect = new Rect();
  List<Rect> _cached_exclusion_rects = Arrays.asList(_cached_exclusion_rect);
  @Override
  public void onLayout(boolean changed, int left, int top, int right, int bottom)
  {
    if (!changed)
      return;
    if (VERSION.SDK_INT >= 29)
    {
      // Disable the back-gesture on the keyboard area
      _cached_exclusion_rect.set(
          left + (int)_marginLeft,
          top + (int)_config.marginTop,
          right - (int)_marginRight,
          bottom - (int)_marginBottom);
      setSystemGestureExclusionRects(_cached_exclusion_rects);
    }
  }

  @Override
  public WindowInsets onApplyWindowInsets(WindowInsets wi)
  {
    // LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS is set in [Keyboard2#updateSoftInputWindowLayoutParams] for SDK_INT >= 35.
    if (VERSION.SDK_INT < 35)
      return wi;
    int insets_types =
      WindowInsets.Type.systemBars()
      | WindowInsets.Type.displayCutout();
    Insets insets = wi.getInsets(insets_types);
    _insets_left = insets.left;
    _insets_right = insets.right;
    _insets_bottom = insets.bottom;
    return WindowInsets.CONSUMED;
  }

  /** Horizontal and vertical position of the 9 indexes. */
  static final Paint.Align[] LABEL_POSITION_H = new Paint.Align[]{
    Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.LEFT,
    Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.RIGHT,
    Paint.Align.CENTER, Paint.Align.CENTER
  };

  static final Vertical[] LABEL_POSITION_V = new Vertical[]{
    Vertical.CENTER, Vertical.TOP, Vertical.TOP, Vertical.BOTTOM,
    Vertical.BOTTOM, Vertical.CENTER, Vertical.CENTER, Vertical.TOP,
    Vertical.BOTTOM
  };

  @Override
  protected void onDraw(Canvas canvas)
  {
    // Guard: _tc is created in onMeasure(); in overlay mode the framework can
    // schedule onDraw before onMeasure has run.
    if (_tc == null || _keyboard == null)
      return;
    int layerAlpha = (int)(255 * _fadeMultiplier);
    boolean useFadeLayer = layerAlpha < 255;
    if (useFadeLayer)
      canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), layerAlpha);
    // DST_OUT compositing requires an offscreen layer so the gradient
    // erases content rather than the underlying window.
    boolean useTopFade = _topGradientPaint != null && _topGradientHeight > 0;
    if (useTopFade)
      canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
    float y = _tc.margin_top;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (KeyboardData.Key k : row.keys)
      {
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        boolean isKeyDown = _pointers.isKeyDown(k);
        boolean isAccent = isAccentKey(k);
        boolean isMod = !isAccent && isModifierStyleKey(k);
        Theme.Computed.Key tc_key;
        if (isAccent)
          tc_key = isKeyDown ? _tc.key_accent_activated : _tc.key_accent;
        else if (isMod)
          tc_key = isKeyDown ? _tc.key_modifier_activated : _tc.key_modifier;
        else
          tc_key = isKeyDown ? _tc.key_activated : _tc.key;
        drawKeyFrame(canvas, x, y, keyW, keyH, tc_key);
        if (_tc.dimple_paint != null)
          drawKeyDimples(canvas, x, y, keyW, keyH, tc_key);
        if (k.keys[0] != null)
          drawLabel(canvas, k.keys[0], keyW / 2f + x, y, keyH, isKeyDown, isMod, isAccent, tc_key);
        for (int i = 1; i < 9; i++)
        {
          if (k.keys[i] != null)
            drawSubLabel(canvas, k.keys[i], x, y, keyW, keyH, i, isKeyDown, isMod, isAccent, tc_key);
        }
        drawIndication(canvas, k, x, y, keyW, keyH, _tc);
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
    if (useTopFade)
    {
      canvas.translate(0, _tc.margin_top);
      canvas.drawRect(0, 0, getWidth(), _topGradientHeight, _topGradientPaint);
      canvas.restore();
    }
    if (useFadeLayer)
      canvas.restore();
  }

  /** Reset idle fade timer. Called on every touch. */
  private void resetIdleTimer()
  {
    _fadeHandler.removeCallbacks(_fadeOutRunnable);
    if (_config.idleFadeEnabled && !_peekMode && !_selectionFadeActive)
      _fadeHandler.postDelayed(_fadeOutRunnable, _config.idleFadeTimeout);
  }

  /** Immediately restore full opacity. */
  private void snapToOpaque()
  {
    if (_fadeAnimator != null && _fadeAnimator.isRunning())
      _fadeAnimator.cancel();
    _fadeMultiplier = 1.0f;
    applyContainerAlpha();
    invalidate();
  }

  private void startFadeAnimation(float target, long durationMs)
  {
    if (_fadeAnimator != null && _fadeAnimator.isRunning())
      _fadeAnimator.cancel();
    _fadeAnimator = ValueAnimator.ofFloat(_fadeMultiplier, target);
    _fadeAnimator.setDuration(durationMs);
    _fadeAnimator.addUpdateListener(animation -> {
      _fadeMultiplier = (float)animation.getAnimatedValue();
      applyContainerAlpha();
      invalidate();
    });
    _fadeAnimator.start();
  }

  /** Apply the fade multiplier to the keyboard background. */
  private void applyContainerAlpha()
  {
    if (_config == null) return;
    if (getBackground() != null)
      getBackground().setAlpha((int)(_config.keyboardOpacity * _fadeMultiplier));
  }

  /** Toggle peek mode (called from long-press spacebar event). */
  public void togglePeekMode()
  {
    _peekMode = !_peekMode;
    if (_peekMode)
      startFadeAnimation(0.1f, 300);
    else
      startFadeAnimation(1.0f, 150);
    resetIdleTimer();
  }

  /** Exit peek mode and any fade state, restoring full opacity. */
  public void exitFadeModes()
  {
    _peekMode = false;
    _selectionFadeActive = false;
    snapToOpaque();
    resetIdleTimer();
  }

  /** Fade when text is selected; restore when selection clears. */
  public void setSelectionFade(boolean active)
  {
    _selectionFadeActive = active;
    if (active)
      startFadeAnimation(0.15f, 200);
    else if (!_peekMode)
    {
      startFadeAnimation(1.0f, 100);
      resetIdleTimer();
    }
  }

  @Override
  public void onDetachedFromWindow()
  {
    super.onDetachedFromWindow();
    _fadeHandler.removeCallbacks(_fadeOutRunnable);
    if (_fadeAnimator != null)
      _fadeAnimator.cancel();
    _fadeMultiplier = 1.0f;
  }

  /** Draw borders and background of the key. */
  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    float w = tc.border_width;
    float padding = w / 2.f;
    _tmpRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);
    canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    if (w > 0.f)
    {
      float overlap = r - r * 0.85f + w; // sin(45°)
      drawBorder(canvas, x, y, x + overlap, y + keyH, tc.border_left_paint, tc);
      drawBorder(canvas, x + keyW - overlap, y, x + keyW, y + keyH, tc.border_right_paint, tc);
      drawBorder(canvas, x, y, x + keyW, y + overlap, tc.border_top_paint, tc);
      drawBorder(canvas, x, y + keyH - overlap, x + keyW, y + keyH, tc.border_bottom_paint, tc);
    }
  }

  /** Clip to draw a border at a time. This allows to call [drawRoundRect]
      several time with the same parameters but a different Paint. */
  void drawBorder(Canvas canvas, float clipl, float clipt, float clipr,
      float clipb, Paint paint, Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    canvas.save();
    canvas.clipRect(clipl, clipt, clipr, clipb);
    canvas.drawRoundRect(_tmpRect, r, r, paint);
    canvas.restore();
  }

  private int labelColor(KeyValue k, boolean isKeyDown, boolean sublabel,
      boolean isModifier, boolean isAccent)
  {
    if (isKeyDown)
    {
      int flags = _pointers.getKeyFlags(k);
      if (flags != -1)
      {
        if ((flags & Pointers.FLAG_P_LOCKED) != 0)
          return _theme.lockedColor;
      }
      return _theme.activatedColor;
    }
    if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY | KeyValue.FLAG_GREYED))
    {
      if (k.hasFlagsAny(KeyValue.FLAG_GREYED))
        return _theme.greyedLabelColor;
      return _theme.secondaryLabelColor;
    }
    if (isAccent)
    {
      if (sublabel && _theme.subLabelAccentColor != 0)
        return _theme.subLabelAccentColor;
      if (!sublabel && _theme.labelAccentColor != 0)
        return _theme.labelAccentColor;
    }
    if (isModifier)
    {
      if (sublabel && _theme.subLabelModifierColor != 0)
        return _theme.subLabelModifierColor;
      if (!sublabel && _theme.labelModifierColor != 0)
        return _theme.labelModifierColor;
    }
    return sublabel ? _theme.subLabelColor : _theme.labelColor;
  }

  private void drawLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyH, boolean isKeyDown, boolean isModifier, boolean isAccent,
      Theme.Computed.Key tc)
  {
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, true);
    boolean specialFont = kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT);
    int color = labelColor(kv, isKeyDown, false, isModifier, isAccent);
    Paint p = tc.label_paint(specialFont, color, textSize);
    float textY = (keyH - p.ascent() - p.descent()) / 2f + y;
    if (tc._outline_enabled)
    {
      Paint op = tc.label_outline_paint(specialFont, 0xCC000000, textSize);
      canvas.drawText(kv.getString(), x, textY, op);
    }
    canvas.drawText(kv.getString(), x, textY, p);
  }

  private void drawSubLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyW, float keyH, int sub_index, boolean isKeyDown,
      boolean isModifier, boolean isAccent, Theme.Computed.Key tc)
  {
    Paint.Align a = LABEL_POSITION_H[sub_index];
    Vertical v = LABEL_POSITION_V[sub_index];
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, false);
    boolean specialFont = kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT);
    int color = labelColor(kv, isKeyDown, true, isModifier, isAccent);
    Paint p = tc.sublabel_paint(specialFont, color, textSize, a);
    float subPadding = _config.keyPadding;
    float textY = y;
    if (v == Vertical.CENTER)
      textY += (keyH - p.ascent() - p.descent()) / 2f;
    else
      textY += (v == Vertical.TOP) ? subPadding - p.ascent() : keyH - subPadding - p.descent();
    float textX = x;
    if (a == Paint.Align.CENTER)
      textX += keyW / 2f;
    else
      textX += (a == Paint.Align.LEFT) ? subPadding : keyW - subPadding;
    String label = kv.getString();
    int label_len = label.length();
    if (label_len > 3 && kv.getKind() == KeyValue.Kind.String)
      label_len = 3;
    if (tc._outline_enabled)
    {
      Paint op = tc.sublabel_outline_paint(specialFont, 0xCC000000, textSize, a);
      canvas.drawText(label, 0, label_len, textX, textY, op);
    }
    canvas.drawText(label, 0, label_len, textX, textY, p);
  }

  private void drawIndication(Canvas canvas, KeyboardData.Key k, float x,
      float y, float keyW, float keyH, Theme.Computed tc)
  {
    if (k.indication == null || k.indication.equals(""))
      return;
    Paint p = tc.indication_paint;
    p.setTextSize(_subLabelSize);
    canvas.drawText(k.indication, 0, k.indication.length(),
        x + keyW / 2f, (keyH - p.ascent() - p.descent()) * 4/5 + y, p);
  }

  /** Returns true if this key should use the modifier background color.
      Alpha keys (a-z) use the normal background; everything else is modifier-style. */
  static boolean isModifierStyleKey(KeyboardData.Key k)
  {
    if (k.keys[0] == null)
      return true;
    KeyValue kv = k.keys[0];
    if (kv.getKind() == KeyValue.Kind.Char && Character.isLetter(kv.getChar()))
      return false;
    return true;
  }

  /** Accent keys get a distinct color (e.g. red Enter/Esc on /dev/tty). */
  static boolean isAccentKey(KeyboardData.Key k)
  {
    if (k.keys[0] == null)
      return false;
    KeyValue kv = k.keys[0];
    if (kv.getKind() == KeyValue.Kind.Keyevent)
    {
      int code = kv.getKeyevent();
      return code == android.view.KeyEvent.KEYCODE_ENTER
          || code == android.view.KeyEvent.KEYCODE_ESCAPE;
    }
    if (kv.getKind() == KeyValue.Kind.Event
        && kv.getEvent() == KeyValue.Event.ACTION)
      return true;
    return false;
  }

  /** Draw scattered dimple dots on a key face (for injection-molding themes). */
  void drawKeyDimples(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc)
  {
    float w = tc.border_width;
    float inset = w + 2f;
    float innerW = keyW - inset * 2;
    float innerH = keyH - inset * 2;
    if (innerW <= 0 || innerH <= 0)
      return;
    // Deterministic seed from position so pattern is stable across redraws
    long seed = Float.floatToIntBits(x) * 31L + Float.floatToIntBits(y);
    Random rng = new Random(seed);
    // Scale dot count by key area relative to a standard key
    float refArea = _keyWidth * _tc.row_height;
    int dotCount = Math.max(4, (int)(20 * (innerW * innerH) / refArea));
    float density = getResources().getDisplayMetrics().density;
    float dotRadius = 1.5f * density;
    for (int i = 0; i < dotCount; i++)
    {
      float dx = x + inset + rng.nextFloat() * innerW;
      float dy = y + inset + rng.nextFloat() * innerH;
      canvas.drawCircle(dx, dy, dotRadius, _tc.dimple_paint);
    }
  }

  private float scaleTextSize(KeyValue k, boolean main_label)
  {
    float smaller_font = k.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT) ? 0.75f : 1.f;
    float label_size = main_label ? _mainLabelSize : _subLabelSize;
    return label_size * smaller_font;
  }
}
