package com.overboard.keyboard.dict;

import android.app.Activity;
import android.os.Bundle;
import com.overboard.keyboard.AppThemeHelper;
import com.overboard.keyboard.R;

public class DictionariesActivity extends Activity
{
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    AppThemeHelper.applyToActivity(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.dictionaries_activity);
  }
}
