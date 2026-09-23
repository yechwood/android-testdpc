package com.afwsamples.testdpc.policy;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import rikka.shizuku.Shizuku;

/** Shizuku/Sui integration and permission status for elevated package operations. */
public class ShizukuActivity extends Activity {
  private static final int REQUEST_CODE = 4207;
  private TextView status;

  private final Shizuku.OnRequestPermissionResultListener permissionListener =
      (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE) updateStatus();
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Shizuku.addRequestPermissionResultListener(permissionListener);
    buildUi();
    updateStatus();
  }

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.WHITE);

    TextView title = new TextView(this);
    title.setText("Shizuku support");
    title.setTextSize(26);
    title.setTextColor(Color.DKGRAY);
    root.addView(title, new LinearLayout.LayoutParams(-1, 72));

    status = new TextView(this);
    status.setTextSize(16);
    status.setTextColor(Color.DKGRAY);
    root.addView(status, new LinearLayout.LayoutParams(-1, -2));

    TextView info = new TextView(this);
    info.setText("Shizuku/Sui can provide shell or root-level access to compatible operations. "
        + "It does not replace Test DPC device-owner/profile-owner privileges, so DPC-only APIs "
        + "still require the appropriate Android ownership state.");
    info.setTextSize(15);
    info.setTextColor(Color.GRAY);
    info.setPadding(0, 18, 0, 18);
    root.addView(info, new LinearLayout.LayoutParams(-1, -2));

    Button request = new Button(this);
    request.setText("Grant Shizuku permission");
    request.setAllCaps(false);
    request.setOnClickListener(v -> {
      try {
        if (!Shizuku.pingBinder()) {
          updateStatus();
          return;
        }
        Shizuku.requestPermission(REQUEST_CODE);
      } catch (RuntimeException e) {
        status.setText("Shizuku error: " + (e.getMessage() == null ? "permission request failed" : e.getMessage()));
      }
    });
    root.addView(request, new LinearLayout.LayoutParams(-1, 58));

    Button refresh = new Button(this);
    refresh.setText("Refresh status");
    refresh.setAllCaps(false);
    refresh.setOnClickListener(v -> updateStatus());
    LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(-1, 58);
    refreshLp.topMargin = 8;
    root.addView(refresh, refreshLp);

    Button download = new Button(this);
    download.setText("Get Shizuku");
    download.setAllCaps(false);
    download.setOnClickListener(v -> {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("https://shizuku.rikka.app/download/")));
      } catch (RuntimeException ignored) {}
    });
    LinearLayout.LayoutParams downloadLp = new LinearLayout.LayoutParams(-1, 58);
    downloadLp.topMargin = 8;
    root.addView(download, downloadLp);

    ScrollView scroll = new ScrollView(this);
    scroll.addView(root);
    setContentView(scroll);
  }

  private void updateStatus() {
    boolean running;
    try {
      running = Shizuku.pingBinder();
    } catch (RuntimeException e) {
      running = false;
    }
    if (!running) {
      status.setText("Shizuku is not running or is not installed.\n\n"
          + "Install/start Shizuku or Sui, then return here and refresh.");
      return;
    }
    try {
      boolean granted = Shizuku.isPreV11()
          || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
      status.setText(granted
          ? "Shizuku is running and permission is granted."
          : "Shizuku is running, but permission has not been granted to Test DPC.");
    } catch (RuntimeException e) {
      status.setText("Shizuku is running, but its permission state could not be read.");
    }
  }

  @Override
  protected void onDestroy() {
    Shizuku.removeRequestPermissionResultListener(permissionListener);
    super.onDestroy();
  }
}
