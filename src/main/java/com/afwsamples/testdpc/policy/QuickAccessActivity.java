package com.afwsamples.testdpc.policy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.afwsamples.testdpc.PolicyManagementActivity;

public class QuickAccessActivity extends Activity {
  private static final String KEY_HIDE = "hide_apps";
  private static final String KEY_UNHIDE = "unhide_apps";
  private static final String KEY_SUSPEND = "suspend_apps";
  private static final String KEY_UNSUSPEND = "unsuspend_apps";
  private static final String KEY_VPN = "set_always_on_vpn";
  private static final String KEY_RESTRICTIONS = "set_user_restrictions";
  private static final String KEY_FRP = "set_factory_reset_protection_policy";
  private static final String KEY_UNINSTALL = "block_uninstallation_by_pkg";

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    PolicyManagementActivity.markAuthenticatedSession();
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.WHITE);
    root.setPadding(24, 24, 24, 24);
    TextView title = new TextView(this);
    title.setText("Quick access"); title.setTextSize(28);
    title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.DKGRAY);
    root.addView(title, new LinearLayout.LayoutParams(-1, 72));
    TextView sub = new TextView(this);
    sub.setText("Your most-used device policies"); sub.setTextSize(15); sub.setTextColor(Color.GRAY);
    root.addView(sub, new LinearLayout.LayoutParams(-1, 48));
    addButton(root, "Hide apps", KEY_HIDE);
    addButton(root, "Unhide apps", KEY_UNHIDE);
    addButton(root, "Suspend apps", KEY_SUSPEND);
    addButton(root, "Unsuspend apps", KEY_UNSUSPEND);
    addButton(root, "Set on-device VPN", KEY_VPN);
    addButton(root, "User restrictions", KEY_RESTRICTIONS);
    addButton(root, "Factory reset protection policy", KEY_FRP);
    addButton(root, "Restrict uninstalling an app", KEY_UNINSTALL);
    TextView note = new TextView(this);
    note.setText("These open the full policy editor so the change can be reviewed normally.");
    note.setTextSize(13); note.setTextColor(Color.GRAY); note.setPadding(4, 20, 4, 4);
    root.addView(note, new LinearLayout.LayoutParams(-1, -2));
    setContentView(root);
  }

  private void addButton(LinearLayout root, String label, String key) {
    Button b = new Button(this);
    b.setText(label); b.setAllCaps(false); b.setTextSize(16);
    b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    b.setOnClickListener(v -> {
      Intent i = new Intent(this, PolicyManagementActivity.class);
      i.putExtra(PolicyManagementActivity.EXTRA_QUICK_ACTION, key);
      i.putExtra(PolicyManagementActivity.EXTRA_RETURN_TO_QUICK_ACCESS, true);
      i.putExtra(PolicyManagementActivity.EXTRA_SKIP_PASSWORD, true);
      startActivity(i);
    });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 58);
    lp.bottomMargin = 8; root.addView(b, lp);
  }
}
