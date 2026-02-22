package com.overboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Manages the overlay window that displays the keyboard UI.
 *
 * The keyboard is rendered in a TYPE_APPLICATION_OVERLAY window so it floats
 * over host apps without causing them to resize. FLAG_NOT_FOCUSABLE keeps
 * input focus on the host app's text field (preserving the InputConnection),
 * while still allowing touch events on the keyboard.
 */
public class OverlayManager
{
  private final Context _context;
  private final WindowManager _windowManager;
  private FrameLayout _overlayContainer;
  private boolean _isShowing = false;

  /** When collapse button is enabled, holds the horizontal layout. */
  private LinearLayout _contentLayout;
  /** Index of the keyboard view within _contentLayout. */
  private int _keyboardViewIndex;
  private CollapseListener _collapseListener;
  private DragListener _dragListener;
  private boolean _collapseEnabled;
  private Config.Handedness _handedness;
  /** Vertical offset from the bottom of the screen (negative = moved up). */
  private int _yOffset = 0;
  /** Whether FLAG_SECURE is set on the overlay (for password fields). */
  private boolean _secure = false;

  public interface CollapseListener
  {
    void onCollapseRequested();
  }

  public interface DragListener
  {
    void onDragStarted();
  }

  public OverlayManager(Context context)
  {
    _context = context;
    _windowManager =
      (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
  }

  public void setCollapseListener(CollapseListener listener)
  {
    _collapseListener = listener;
  }

  public void setDragListener(DragListener listener)
  {
    _dragListener = listener;
  }

  /** Show the given view in the overlay window. */
  public void show(View view, Config.Handedness handedness, boolean collapseEnabled)
  {
    _handedness = handedness;
    _collapseEnabled = collapseEnabled;

    if (!Settings.canDrawOverlays(_context))
      return;

    if (_isShowing)
    {
      if (_overlayContainer != null && _overlayContainer.getWindowToken() != null)
      {
        replaceView(view);
        return;
      }
      _overlayContainer = null;
      _isShowing = false;
    }

    _overlayContainer = new FrameLayout(_context);

    if (collapseEnabled)
    {
      _contentLayout = new LinearLayout(_context);
      _contentLayout.setOrientation(LinearLayout.HORIZONTAL);
      _contentLayout.setLayoutParams(new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT));

      View collapseButton = createCollapseButton();
      detachFromParent(view);
      LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
          0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
      view.setLayoutParams(viewParams);

      if (handedness == Config.Handedness.RIGHT)
      {
        _contentLayout.addView(view);
        _contentLayout.addView(collapseButton);
        _keyboardViewIndex = 0;
      }
      else
      {
        _contentLayout.addView(collapseButton);
        _contentLayout.addView(view);
        _keyboardViewIndex = 1;
      }

      _overlayContainer.addView(_contentLayout);
    }
    else
    {
      _contentLayout = null;
      detachFromParent(view);
      _overlayContainer.addView(view);
    }

    try
    {
      _windowManager.addView(_overlayContainer, createLayoutParams());
      _isShowing = true;
    }
    catch (Exception e)
    {
      Logs.exn("OverlayManager.show", e);
      _overlayContainer = null;
      _contentLayout = null;
      _isShowing = false;
    }
  }

  /** Replace the view inside the overlay without recreating the window. */
  public void replaceView(View view)
  {
    if (!_isShowing || _overlayContainer == null)
      return;
    if (_overlayContainer.getWindowToken() == null)
    {
      // Window was detached — reset state instead of modifying a dead container
      _overlayContainer = null;
      _contentLayout = null;
      _isShowing = false;
      return;
    }
    try
    {
      detachFromParent(view);
      if (_contentLayout != null)
      {
        _contentLayout.removeViewAt(_keyboardViewIndex);
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        view.setLayoutParams(viewParams);
        _contentLayout.addView(view, _keyboardViewIndex);
      }
      else
      {
        _overlayContainer.removeAllViews();
        _overlayContainer.addView(view);
      }
    }
    catch (Exception e)
    {
      Logs.exn("OverlayManager.replaceView", e);
    }
  }

  /** Hide and remove the overlay window. */
  public void hide()
  {
    if (!_isShowing || _overlayContainer == null)
      return;
    try
    {
      if (_contentLayout != null)
        _contentLayout.removeAllViews();
      _overlayContainer.removeAllViews();
      _windowManager.removeView(_overlayContainer);
    }
    catch (Exception e)
    {
      // View was not attached or window manager state is inconsistent
      Logs.exn("OverlayManager.hide", e);
    }
    _overlayContainer = null;
    _contentLayout = null;
    _isShowing = false;
  }

  /** Update the overlay window layout params (e.g. after rotation). */
  public void updateLayout()
  {
    if (!_isShowing || _overlayContainer == null)
      return;
    if (_overlayContainer.getWindowToken() == null)
    {
      _overlayContainer = null;
      _contentLayout = null;
      _isShowing = false;
      return;
    }
    try
    {
      _windowManager.updateViewLayout(_overlayContainer, createLayoutParams());
    }
    catch (Exception e)
    {
      Logs.exn("OverlayManager.updateLayout", e);
      _overlayContainer = null;
      _contentLayout = null;
      _isShowing = false;
    }
  }

  public boolean isShowing()
  {
    return _isShowing;
  }

  /** Set FLAG_SECURE on the overlay window to prevent screenshots of
      password entry. Call before or after show(); updates live if showing. */
  public void setSecure(boolean secure)
  {
    if (_secure == secure)
      return;
    _secure = secure;
    if (_isShowing)
      updateLayout();
  }

  private View createCollapseButton()
  {
    float density = _context.getResources().getDisplayMetrics().density;
    int widthPx = (int)(28 * density);

    CollapseButtonView button = new CollapseButtonView(_context,
        _handedness == Config.Handedness.RIGHT);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        widthPx, ViewGroup.LayoutParams.MATCH_PARENT);
    button.setLayoutParams(params);

    int touchSlop = ViewConfiguration.get(_context).getScaledTouchSlop();
    button.setOnTouchListener(new View.OnTouchListener()
    {
      private float _startRawY;
      private int _startYOffset;
      private boolean _dragging;

      @Override
      public boolean onTouch(View v, MotionEvent event)
      {
        switch (event.getActionMasked())
        {
          case MotionEvent.ACTION_DOWN:
            _startRawY = event.getRawY();
            _startYOffset = _yOffset;
            _dragging = false;
            return true;

          case MotionEvent.ACTION_MOVE:
            float dy = event.getRawY() - _startRawY;
            if (!_dragging && Math.abs(dy) > touchSlop)
            {
              _dragging = true;
              if (_dragListener != null)
                _dragListener.onDragStarted();
            }
            if (_dragging)
            {
              // gravity=BOTTOM: negative y moves the window up
              _yOffset = _startYOffset - (int)dy;
              if (_yOffset < 0) _yOffset = 0;
              updateLayout();
            }
            return true;

          case MotionEvent.ACTION_UP:
            if (!_dragging)
            {
              if (_collapseListener != null)
                _collapseListener.onCollapseRequested();
            }
            return true;

          case MotionEvent.ACTION_CANCEL:
            return true;
        }
        return false;
      }
    });
    return button;
  }

  private WindowManager.LayoutParams createLayoutParams()
  {
    int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    if (_secure)
      flags |= WindowManager.LayoutParams.FLAG_SECURE;
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    );
    params.gravity = Gravity.BOTTOM;
    params.y = _yOffset;
    if (Build.VERSION.SDK_INT >= 28)
    {
      params.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
    return params;
  }

  private static void detachFromParent(View view)
  {
    if (view.getParent() instanceof ViewGroup)
      ((ViewGroup)view.getParent()).removeView(view);
  }

  /** Custom view for the collapse button — draws a small chevron indicator. */
  static class CollapseButtonView extends View
  {
    private final Paint _paint;
    private final boolean _pointsRight;

    CollapseButtonView(Context context, boolean pointsRight)
    {
      super(context);
      _pointsRight = pointsRight;
      _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      _paint.setColor(0x60FFFFFF);
      _paint.setStyle(Paint.Style.STROKE);
      _paint.setStrokeWidth(3f);
      _paint.setStrokeCap(Paint.Cap.ROUND);
      _paint.setStrokeJoin(Paint.Join.ROUND);
      setBackgroundColor(0x30808080);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
      super.onDraw(canvas);
      float w = getWidth();
      float h = getHeight();
      float cx = w / 2f;
      float cy = h / 2f;
      float chevronH = 12f;
      float chevronW = 6f;
      Path path = new Path();
      if (_pointsRight)
      {
        path.moveTo(cx - chevronW, cy - chevronH);
        path.lineTo(cx + chevronW, cy);
        path.lineTo(cx - chevronW, cy + chevronH);
      }
      else
      {
        path.moveTo(cx + chevronW, cy - chevronH);
        path.lineTo(cx - chevronW, cy);
        path.lineTo(cx + chevronW, cy + chevronH);
      }
      canvas.drawPath(path, _paint);
    }
  }
}
