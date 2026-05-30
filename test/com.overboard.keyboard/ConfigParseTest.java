package com.overboard.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression test for the preference-parse fallbacks in {@link Config}.
 *
 * A corrupted, empty, or non-numeric string preference (e.g. from a restored
 * backup or a migration) used to throw NumberFormatException out of
 * Config.refresh(). Because refresh() runs on the onStartInputView path, that
 * uncaught exception crashed the IME and locked up text input device-wide.
 * These tests pin the fallback-to-default behavior so it can't regress.
 */
public class ConfigParseTest
{
  public ConfigParseTest() {}

  @Test
  public void parse_int_valid()
  {
    assertEquals(7, Config.parse_int("7", 2));
    assertEquals(-3, Config.parse_int("-3", 2));
    assertEquals(0, Config.parse_int("0", 2));
  }

  @Test
  public void parse_int_falls_back_on_bad_input()
  {
    assertEquals(2, Config.parse_int(null, 2));
    assertEquals(2, Config.parse_int("", 2));
    assertEquals(2, Config.parse_int("   ", 2));
    assertEquals(2, Config.parse_int("abc", 2));
    assertEquals(2, Config.parse_int("3.5", 2)); // valid float, not an int
    assertEquals(5, Config.parse_int("0x10", 5)); // not a decimal int literal
  }

  @Test
  public void parse_float_valid()
  {
    assertEquals(15f, Config.parse_float("15", 30f), 0f);
    assertEquals(2.5f, Config.parse_float("2.5", 30f), 0f);
    assertEquals(-0.5f, Config.parse_float("-0.5", 30f), 0f);
  }

  @Test
  public void parse_float_falls_back_on_bad_input()
  {
    assertEquals(30f, Config.parse_float(null, 30f), 0f);
    assertEquals(30f, Config.parse_float("", 30f), 0f);
    assertEquals(30f, Config.parse_float("abc", 30f), 0f);
    assertEquals(30f, Config.parse_float("1,5", 30f), 0f); // comma decimal
  }
}
