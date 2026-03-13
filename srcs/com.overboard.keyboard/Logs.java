package com.overboard.keyboard;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.LogPrinter;
import android.view.inputmethod.EditorInfo;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Logs
{
  static final String TAG = "com.overboard.keyboard";

  static LogPrinter _debug_logs = null;

  static final String LOG_FILE_NAME = "debug_log.txt";
  static final long MAX_LOG_SIZE = 2 * 1024 * 1024; // 2 MB
  static volatile boolean _file_logging_active = false;
  static HandlerThread _logThread;
  static Handler _logHandler;
  static PrintWriter _logWriter;
  static File _logFile;

  static final SimpleDateFormat _timestamp_fmt =
    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

  public static void set_debug_logs(boolean d)
  {
    _debug_logs = d ? new LogPrinter(Log.DEBUG, TAG) : null;
  }

  public static void start_file_logging(Context ctx)
  {
    if (_file_logging_active)
      return;
    try
    {
      _logFile = new File(ctx.getFilesDir(), LOG_FILE_NAME);
      // Truncate if over size limit
      if (_logFile.exists() && _logFile.length() > MAX_LOG_SIZE)
        _logFile.delete();
      _logThread = new HandlerThread("DebugLogWriter");
      _logThread.start();
      _logHandler = new Handler(_logThread.getLooper());
      _logWriter = new PrintWriter(new FileWriter(_logFile, true), false);
      _file_logging_active = true;
      // Write header with app version for correlating logs with releases
      String appVersion = "unknown";
      try
      {
        appVersion = ctx.getPackageManager()
            .getPackageInfo(ctx.getPackageName(), 0).versionName;
      }
      catch (Exception e) { /* ignore */ }
      write_to_file("=== Debug logging started ===");
      write_to_file("App: " + appVersion);
      write_to_file("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
      write_to_file("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to start file logging", e);
      _file_logging_active = false;
    }
  }

  public static void stop_file_logging()
  {
    if (!_file_logging_active)
      return;
    _file_logging_active = false;
    if (_logHandler != null)
    {
      _logHandler.post(() -> {
        try
        {
          if (_logWriter != null)
          {
            _logWriter.println(timestamp() + " === Debug logging stopped ===");
            _logWriter.flush();
            _logWriter.close();
          }
        }
        catch (Exception e) { /* ignore */ }
        _logWriter = null;
      });
    }
    if (_logThread != null)
    {
      _logThread.quitSafely();
      _logThread = null;
    }
    _logHandler = null;
  }

  public static boolean is_file_logging()
  {
    return _file_logging_active;
  }

  public static File get_log_file()
  {
    return _logFile;
  }

  static String timestamp()
  {
    return _timestamp_fmt.format(new Date());
  }

  static void write_to_file(final String line)
  {
    if (!_file_logging_active || _logHandler == null)
      return;
    final String stamped = timestamp() + " " + line;
    _logHandler.post(() -> {
      try
      {
        if (_logWriter != null)
        {
          _logWriter.println(stamped);
          _logWriter.flush();
        }
      }
      catch (Exception e) { /* ignore */ }
    });
  }

  /** Write directly to the log file on the calling thread.
      Used by the crash handler when the background thread may not run. */
  static void write_to_file_sync(String line)
  {
    if (_logWriter == null)
      return;
    try
    {
      _logWriter.println(timestamp() + " " + line);
      _logWriter.flush();
    }
    catch (Exception e) { /* ignore */ }
  }

  public static void install_crash_handler()
  {
    final Thread.UncaughtExceptionHandler defaultHandler =
      Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      write_to_file_sync("UNCAUGHT EXCEPTION in thread " + thread.getName());
      write_to_file_sync(Log.getStackTraceString(throwable));
      if (defaultHandler != null)
        defaultHandler.uncaughtException(thread, throwable);
    });
  }

  public static void debug_startup_input_view(EditorInfo info, Config conf)
  {
    if (_debug_logs == null && !_file_logging_active)
      return;
    if (_debug_logs != null)
    {
      info.dump(_debug_logs, "");
      if (info.extras != null)
        _debug_logs.println("extras: "+info.extras.toString());
    }
    if (_file_logging_active)
    {
      write_to_file("EditorInfo: inputType=" + info.inputType
          + " imeOptions=" + info.imeOptions
          + " packageName=" + info.packageName);
    }
  }

  public static void debug_config_migration(int from_version, int to_version)
  {
    debug("Migrating config version from " + from_version + " to " + to_version);
  }

  public static void debug(String s)
  {
    if (_debug_logs != null)
      _debug_logs.println(s);
    if (_file_logging_active)
      write_to_file(s);
  }

  public static void exn(String msg, Exception e)
  {
    Log.e(TAG, msg, e);
    if (_file_logging_active)
      write_to_file("EXCEPTION " + msg + "\n" + Log.getStackTraceString(e));
  }

  public static void trace()
  {
    String stackTrace = Log.getStackTraceString(new Exception());
    if (_debug_logs != null)
      _debug_logs.println(stackTrace);
    if (_file_logging_active)
      write_to_file("TRACE " + stackTrace);
  }
}
