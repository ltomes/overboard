package com.overboard.keyboard.prefs;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.overboard.keyboard.Config;
import com.overboard.keyboard.R;

/**
 * Theme picker preference that shows color swatches for each theme.
 * Replaces the standard ListPreference with a visual preview.
 */
public class ThemePreference extends DialogPreference
{
  private String _value;
  private String _pendingValue;
  private String[] _entryValues;
  private String[] _entryNames;
  private RadioButton[] _radioButtons;

  public ThemePreference(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _entryValues = context.getResources().getStringArray(R.array.pref_theme_values);
    _entryNames = context.getResources().getStringArray(R.array.pref_theme_entries);
    setDialogTitle(getTitle());
    setPositiveButtonText(android.R.string.ok);
    setNegativeButtonText(android.R.string.cancel);
  }

  @Override
  protected View onCreateDialogView()
  {
    Context ctx = getContext();
    float density = ctx.getResources().getDisplayMetrics().density;
    int pad = (int)(12 * density);

    ScrollView scroll = new ScrollView(ctx);
    LinearLayout list = new LinearLayout(ctx);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(pad, pad, pad, pad);

    _radioButtons = new RadioButton[_entryValues.length];

    for (int i = 0; i < _entryValues.length; i++)
    {
      final String value = _entryValues[i];
      final int index = i;

      LinearLayout row = new LinearLayout(ctx);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setPadding(pad / 2, (int)(8 * density), pad / 2, (int)(8 * density));
      row.setGravity(android.view.Gravity.CENTER_VERTICAL);

      int styleRes = resolveThemeStyle(ctx, value);
      ThemeSwatchView swatch = new ThemeSwatchView(ctx, styleRes);
      int swatchW = (int)(100 * density);
      int swatchH = (int)(36 * density);
      LinearLayout.LayoutParams swatchParams =
          new LinearLayout.LayoutParams(swatchW, swatchH);
      swatch.setLayoutParams(swatchParams);

      TextView label = new TextView(ctx);
      label.setText(_entryNames[i]);
      label.setTextSize(15);
      LinearLayout.LayoutParams labelParams =
          new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      labelParams.setMarginStart((int)(12 * density));
      label.setLayoutParams(labelParams);

      RadioButton radio = new RadioButton(ctx);
      radio.setChecked(value.equals(_value));
      radio.setClickable(false);
      _radioButtons[i] = radio;

      row.addView(swatch);
      row.addView(label);
      row.addView(radio);

      row.setClickable(true);
      row.setOnClickListener(v -> {
        _pendingValue = value;
        for (int j = 0; j < _radioButtons.length; j++)
          _radioButtons[j].setChecked(j == index);
      });

      list.addView(row);
    }

    scroll.addView(list);
    return scroll;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult)
  {
    if (positiveResult && _pendingValue != null)
    {
      if (callChangeListener(_pendingValue))
      {
        _value = _pendingValue;
        persistString(_value);
        updateSummary();
      }
    }
    _pendingValue = null;
  }

  @Override
  protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue)
  {
    if (restorePersistedValue)
      _value = getPersistedString("system");
    else
    {
      _value = (String)defaultValue;
      persistString(_value);
    }
    updateSummary();
  }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index)
  {
    return a.getString(index);
  }

  private void updateSummary()
  {
    if (_value == null) return;
    for (int i = 0; i < _entryValues.length; i++)
    {
      if (_entryValues[i].equals(_value))
      {
        setSummary(_entryNames[i]);
        return;
      }
    }
    setSummary(_value);
  }

  /** Resolve a theme value string to a style resource ID, handling
      night-mode-dependent themes like "system" and "monet". */
  private static int resolveThemeStyle(Context ctx, String value)
  {
    return Config.getThemeId(ctx.getResources(), value);
  }

  /** Draws 4 colored rounded rectangles representing a theme's key colors. */
  static class ThemeSwatchView extends View
  {
    private final int _colorKeyboard;
    private final int _colorKey;
    private final int _colorModifier;
    private final int _colorAccent;
    private final int _colorLabel;
    private final Paint _paint;
    private final Paint _textPaint;
    private final RectF _rect;

    ThemeSwatchView(Context ctx, int styleRes)
    {
      super(ctx);
      int colorKeyboard = 0xFF333333;
      int colorKey = 0xFF555555;
      int colorModifier = 0;
      int colorAccent = 0;
      int colorLabel = 0xFFFFFFFF;

      try
      {
        TypedArray s = ctx.getTheme().obtainStyledAttributes(
            null, R.styleable.keyboard, 0, styleRes);
        colorKeyboard = s.getColor(R.styleable.keyboard_colorKeyboard, 0xFF333333);
        colorKey = s.getColor(R.styleable.keyboard_colorKey, 0xFF555555);
        colorModifier = s.getColor(R.styleable.keyboard_colorKeyModifier, 0);
        colorAccent = s.getColor(R.styleable.keyboard_colorKeyAccent, 0);
        colorLabel = s.getColor(R.styleable.keyboard_colorLabel, 0xFFFFFFFF);
        s.recycle();
      }
      catch (Exception e) { /* use fallback colors */ }

      if (colorModifier == 0) colorModifier = colorKey;
      if (colorAccent == 0) colorAccent = colorKey;

      _colorKeyboard = colorKeyboard;
      _colorKey = colorKey;
      _colorModifier = colorModifier;
      _colorAccent = colorAccent;
      _colorLabel = colorLabel;

      _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      _textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      _textPaint.setColor(_colorLabel);
      _textPaint.setTextAlign(Paint.Align.CENTER);
      _rect = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
      float w = getWidth();
      float h = getHeight();
      float gap = 2f * getResources().getDisplayMetrics().density;
      float radius = 3f * getResources().getDisplayMetrics().density;

      // Background
      _paint.setColor(_colorKeyboard);
      canvas.drawRoundRect(0, 0, w, h, radius, radius, _paint);

      // 4 key swatches
      int[] colors = { _colorKey, _colorKey, _colorModifier, _colorAccent };
      float keyW = (w - gap * 5) / 4;
      float keyH = h - gap * 2;
      float y = gap;

      _textPaint.setTextSize(keyH * 0.5f);

      for (int i = 0; i < 4; i++)
      {
        float x = gap + i * (keyW + gap);
        _rect.set(x, y, x + keyW, y + keyH);
        _paint.setColor(colors[i]);
        canvas.drawRoundRect(_rect, radius, radius, _paint);

        // Draw "Abc" on the first two key swatches (normal keys)
        if (i == 0)
        {
          float textY = _rect.centerY() - (_textPaint.descent() + _textPaint.ascent()) / 2;
          canvas.drawText("Ab", _rect.centerX(), textY, _textPaint);
        }
      }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
      float density = getResources().getDisplayMetrics().density;
      int w = (int)(100 * density);
      int h = (int)(36 * density);
      setMeasuredDimension(
          resolveSize(w, widthMeasureSpec),
          resolveSize(h, heightMeasureSpec));
    }
  }
}
