package com.afwsamples.testdpc.transferownership;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TransferSuccessActivity extends Activity {
  public static final String EXTRA_TARGET = "target";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    int pad = (int) (28 * getResources().getDisplayMetrics().density);
    root.setPadding(pad, pad, pad, pad);
    root.setBackgroundColor(Color.WHITE);

    TextView icon = new TextView(this);
    icon.setText("✓");
    icon.setTextSize(64);
    icon.setTextColor(Color.rgb(30, 120, 70));
    icon.setGravity(Gravity.CENTER);
    root.addView(icon, new LinearLayout.LayoutParams(-1, -2));

    TextView title = new TextView(this);
    title.setText("Ownership transferred");
    title.setTextSize(27);
    title.setTypeface(null, Typeface.BOLD);
    title.setTextColor(Color.DKGRAY);
    title.setGravity(Gravity.CENTER);
    root.addView(title, new LinearLayout.LayoutParams(-1, -2));

    TextView message = new TextView(this);
    String target = getIntent().getStringExtra(EXTRA_TARGET);
    message.setText("The device policy ownership transfer completed successfully."
        + (target == null ? "" : "\n\nNew owner: " + target)
        + "\n\nTest DPC is no longer the device owner/profile owner.");
    message.setTextSize(16);
    message.setTextColor(Color.DKGRAY);
    message.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
    mp.topMargin = (int) (18 * getResources().getDisplayMetrics().density);
    root.addView(message, mp);

    Button done = new Button(this);
    done.setText("Done");
    done.setAllCaps(false);
    done.setOnClickListener(v -> finishAndRemoveTask());
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 54);
    bp.topMargin = (int) (24 * getResources().getDisplayMetrics().density);
    root.addView(done, bp);

    setContentView(root);
  }

  @Override
  public void onBackPressed() {
    finishAndRemoveTask();
  }
}
