package com.afwsamples.testdpc.policy;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.afwsamples.testdpc.DeviceAdminReceiver;
import com.afwsamples.testdpc.R;
import com.afwsamples.testdpc.common.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppSelectionActivity extends Activity {
  public static final String EXTRA_MODE = "mode";
  public static final String EXTRA_SELECTED_PACKAGES = "selected_packages";
  public static final int MODE_HIDE = 1;
  public static final int MODE_UNHIDE = 2;
  public static final int MODE_SUSPEND = 3;
  public static final int MODE_UNSUSPEND = 4;

  private final ArrayList<AppItem> allApps = new ArrayList<>();
  private final Set<String> selected = new HashSet<>();
  private PackageManager packageManager;
  private DevicePolicyManager devicePolicyManager;
  private ComponentName admin;
  private ListView listView;
  private EditText search;
  private TextView status;
  private TextView selectedText;
  private android.widget.Button actionButton;
  private int mode;

  private static final class AppItem {
    final String packageName;
    final String label;
    final Drawable icon;
    final boolean launcher;
    AppItem(String packageName, String label, Drawable icon, boolean launcher) {
      this.packageName = packageName;
      this.label = label;
      this.icon = icon;
      this.launcher = launcher;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    packageManager = getPackageManager();
    devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    admin = new ComponentName(this, DeviceAdminReceiver.class);
    mode = getIntent().getIntExtra(EXTRA_MODE, MODE_HIDE);
    buildLoadingUi();
    loadAppsAsync();
  }

  private void buildLoadingUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.WHITE);

    TextView title = new TextView(this);
    title.setText(getTitleText());
    title.setTextSize(22);
    title.setTextColor(Color.DKGRAY);
    title.setGravity(Gravity.CENTER_VERTICAL);
    title.setPadding(24, 12, 24, 8);
    root.addView(title, new LinearLayout.LayoutParams(-1, 60));

    search = new EditText(this);
    search.setSingleLine(true);
    search.setHint("Search apps or package names");
    search.setPadding(24, 0, 24, 0);
    root.addView(search, new LinearLayout.LayoutParams(-1, 58));

    status = new TextView(this);
    status.setText("Loading apps…");
    status.setGravity(Gravity.CENTER);
    status.setTextSize(15);
    root.addView(status, new LinearLayout.LayoutParams(-1, 44));

    ProgressBar progress = new ProgressBar(this);
    root.addView(progress, new LinearLayout.LayoutParams(-1, 4));

    listView = new ListView(this);
    listView.setDividerHeight(1);
    root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));

    LinearLayout bottom = new LinearLayout(this);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    bottom.setPadding(16, 8, 16, 8);
    selectedText = new TextView(this);
    selectedText.setText("0 selected");
    selectedText.setTextSize(15);
    bottom.addView(selectedText, new LinearLayout.LayoutParams(0, 56, 1f));

    actionButton = new android.widget.Button(this);
    actionButton.setText(getActionText());
    actionButton.setAllCaps(false);
    actionButton.setEnabled(false);
    bottom.addView(actionButton, new LinearLayout.LayoutParams(150, 56));
    root.addView(bottom, new LinearLayout.LayoutParams(-1, 72));

    search.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
      public void onTextChanged(CharSequence s, int st, int before, int count) { refreshList(selectedText); }
      public void afterTextChanged(android.text.Editable e) {}
    });
    actionButton.setOnClickListener(v -> {
      if (selected.isEmpty()) return;
      Intent result = new Intent();
      result.putExtra(EXTRA_MODE, mode);
      result.putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, new ArrayList<>(selected));
      setResult(RESULT_OK, result);
      finish();
    });

    setContentView(root);
  }

  private void loadAppsAsync() {
    AsyncTask.execute(() -> {
      final ArrayList<AppItem> loaded = new ArrayList<>();
      Set<String> launcherPackages = new HashSet<>();
      Intent launcherIntent = Util.getLauncherIntent(this);
      List<ResolveInfo> resolvers =
          packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
      for (ResolveInfo r : resolvers) {
        if (r.activityInfo != null) launcherPackages.add(r.activityInfo.packageName);
      }

      List<ApplicationInfo> installed =
          packageManager.getInstalledApplications(PackageManager.MATCH_ALL);
      for (ApplicationInfo info : installed) {
        boolean include;
        if (mode == MODE_HIDE) {
          include = !devicePolicyManager.isApplicationHidden(admin, info.packageName);
        } else if (mode == MODE_UNHIDE) {
          include = devicePolicyManager.isApplicationHidden(admin, info.packageName);
        } else if (mode == MODE_SUSPEND) {
          include = !devicePolicyManager.isPackageSuspended(info.packageName);
        } else {
          include = devicePolicyManager.isPackageSuspended(info.packageName);
        }
        if (!include) continue;
        CharSequence label = packageManager.getApplicationLabel(info);
        loaded.add(new AppItem(
            info.packageName,
            label == null ? info.packageName : label.toString(),
            packageManager.getApplicationIcon(info),
            launcherPackages.contains(info.packageName)));
      }
      Collections.sort(loaded, (a, b) -> {
        if (a.launcher != b.launcher) return a.launcher ? -1 : 1;
        int byLabel = a.label.compareToIgnoreCase(b.label);
        return byLabel != 0 ? byLabel : a.packageName.compareToIgnoreCase(b.packageName);
      });

      runOnUiThread(() -> {
        allApps.clear();
        allApps.addAll(loaded);
        status.setText(allApps.size() + " apps");
        refreshList(null);
      });
    });
  }

  private void refreshList(TextView selectedText) {
    if (listView == null) return;
    String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.getDefault());
    ArrayList<AppItem> filtered = new ArrayList<>();
    for (AppItem item : allApps) {
      if (query.isEmpty()
          || item.label.toLowerCase(Locale.getDefault()).contains(query)
          || item.packageName.toLowerCase(Locale.getDefault()).contains(query)) {
        filtered.add(item);
      }
    }
    listView.setAdapter(new AppAdapter(filtered));
    if (selectedText != null) selectedText.setText(selected.size() + " selected");
    actionButton.setEnabled(!selected.isEmpty());
  }

  private final class AppAdapter extends BaseAdapter {
    private final List<AppItem> items;
    AppAdapter(List<AppItem> items) { this.items = items; }
    public int getCount() { return items.size(); }
    public Object getItem(int position) { return items.get(position); }
    public long getItemId(int position) { return position; }

    public View getView(int position, View convertView, ViewGroup parent) {
      LinearLayout row = new LinearLayout(AppSelectionActivity.this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(18, 12, 12, 12);

      ImageView icon = new ImageView(AppSelectionActivity.this);
      icon.setImageDrawable(items.get(position).icon);
      row.addView(icon, new LinearLayout.LayoutParams(64, 64));

      LinearLayout textBox = new LinearLayout(AppSelectionActivity.this);
      textBox.setOrientation(LinearLayout.VERTICAL);
      textBox.setPadding(18, 0, 8, 0);
      row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));

      TextView name = new TextView(AppSelectionActivity.this);
      name.setText(items.get(position).label);
      name.setTextSize(18);
      name.setTextColor(Color.DKGRAY);
      textBox.addView(name);

      TextView pkg = new TextView(AppSelectionActivity.this);
      pkg.setText(items.get(position).packageName);
      pkg.setTextSize(13);
      pkg.setTextColor(Color.GRAY);
      textBox.addView(pkg);

      CheckBox box = new CheckBox(AppSelectionActivity.this);
      box.setChecked(selected.contains(items.get(position).packageName));
      row.addView(box, new LinearLayout.LayoutParams(-2, -2));

      View.OnClickListener toggle = v -> {
        String pkgName = items.get(position).packageName;
        if (selected.contains(pkgName)) selected.remove(pkgName);
        else selected.add(pkgName);
        box.setChecked(selected.contains(pkgName));
        if (actionButton != null) actionButton.setEnabled(!selected.isEmpty());
        refreshList(selectedText);
      };
      row.setOnClickListener(toggle);
      box.setOnClickListener(toggle);
      return row;
    }
  }

  private String getTitleText() {
    switch (mode) {
      case MODE_UNHIDE: return "Unhide Apps";
      case MODE_SUSPEND: return "Suspend Apps";
      case MODE_UNSUSPEND: return "Unsuspend Apps";
      default: return "Hide Apps";
    }
  }

  private String getActionText() {
    switch (mode) {
      case MODE_UNHIDE: return "Unhide selected";
      case MODE_SUSPEND: return "Suspend selected";
      case MODE_UNSUSPEND: return "Unsuspend selected";
      default: return "Hide selected";
    }
  }
}
