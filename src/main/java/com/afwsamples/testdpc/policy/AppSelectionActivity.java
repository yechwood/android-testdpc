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
  public static final String EXTRA_QUICK_ACCESS_RETURN = "quick_access_return";
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
  private android.widget.Button selectAllButton;
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
  public void onBackPressed() {
    if (getIntent().getBooleanExtra(EXTRA_QUICK_ACCESS_RETURN, false)) {
      Intent result = new Intent();
      result.putExtra(EXTRA_QUICK_ACCESS_RETURN, true);
      setResult(RESULT_CANCELED, result);
    }
    super.onBackPressed();
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

    selectAllButton = new android.widget.Button(this);
    selectAllButton.setText("Select all");
    selectAllButton.setAllCaps(false);
    selectAllButton.setTextSize(14);
    selectAllButton.setMinWidth(0);
    selectAllButton.setPadding(2, 0, 2, 0);
    LinearLayout.LayoutParams selectLp = new LinearLayout.LayoutParams(0, 56, 1.15f);
    selectLp.leftMargin = 4;
    selectLp.rightMargin = 4;
    bottom.addView(selectAllButton, selectLp);

    actionButton = new android.widget.Button(this);
    actionButton.setText(getActionText());
    actionButton.setAllCaps(false);
    actionButton.setTextSize(14);
    actionButton.setMinWidth(0);
    actionButton.setPadding(2, 0, 2, 0);
    actionButton.setEnabled(false);
    LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, 56, 1f);
    bottom.addView(actionButton, actionLp);
    root.addView(bottom, new LinearLayout.LayoutParams(-1, 72));

    search.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
      public void onTextChanged(CharSequence s, int st, int before, int count) { refreshList(selectedText); }
      public void afterTextChanged(android.text.Editable e) {}
    });
    selectAllButton.setOnClickListener(v -> toggleSelectAll());

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
          include = !isPackageSuspended(info.packageName);
        } else {
          include = isPackageSuspended(info.packageName);
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
    int firstVisible = listView.getFirstVisiblePosition();
    View firstChild = listView.getChildAt(0);
    int firstTop = firstChild == null ? 0 : firstChild.getTop();
    listView.setAdapter(new AppAdapter(filtered));
    if (firstVisible > 0 || firstTop != 0) listView.setSelectionFromTop(firstVisible, firstTop);
    if (selectedText != null) selectedText.setText(selected.size() + " selected");
    actionButton.setEnabled(!selected.isEmpty());
    if (selectAllButton != null) {
      boolean all = !filtered.isEmpty() && selected.containsAll(packageNames(filtered));
      selectAllButton.setText(all ? "Unselect all" : "Select all");
    }
  }

  private final class AppAdapter extends BaseAdapter {
    private final List<AppItem> items;
    private final boolean hasLauncher;
    private final boolean hasOther;
    AppAdapter(List<AppItem> items) {
      this.items = items;
      hasLauncher = hasType(true);
      hasOther = hasType(false);
    }
    private boolean hasType(boolean launcher) {
      for (AppItem item : items) if (item.launcher == launcher) return true;
      return false;
    }
    public int getCount() {
      return items.size() + (hasLauncher ? 1 : 0) + (hasOther ? 1 : 0);
    }
    public Object getItem(int position) { return null; }
    public long getItemId(int position) { return position; }
    public int getViewTypeCount() { return 3; }
    public int getItemViewType(int position) {
      if (hasLauncher && position == 0) return 1;
      int offset = hasLauncher ? 1 : 0;
      int launcherCount = hasLauncher ? countType(true) : 0;
      if (hasOther && position == offset + launcherCount) return 2;
      return 0;
    }
    private int countType(boolean launcher) {
      int n = 0; for (AppItem item : items) if (item.launcher == launcher) n++; return n;
    }
    private AppItem appAt(int position) {
      int index = position - (hasLauncher ? 1 : 0);
      if (hasOther && position > (hasLauncher ? countType(true) : -1)) index--;
      return items.get(index);
    }
    public View getView(int position, View convertView, ViewGroup parent) {
      int type = getItemViewType(position);
      if (type != 0) {
        TextView header = new TextView(AppSelectionActivity.this);
        header.setText(type == 1 ? "APPS WITH LAUNCHERS" : "SYSTEM / NON-LAUNCHER APPS");
        header.setTextSize(14);
        header.setTextColor(Color.DKGRAY);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(20, 8, 20, 8);
        header.setBackgroundColor(0xffeeeeee);
        return header;
      }
      AppItem item = appAt(position);
      LinearLayout row = new LinearLayout(AppSelectionActivity.this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(18, 12, 12, 12);
      ImageView icon = new ImageView(AppSelectionActivity.this);
      icon.setImageDrawable(item.icon);
      row.addView(icon, new LinearLayout.LayoutParams(64, 64));
      LinearLayout textBox = new LinearLayout(AppSelectionActivity.this);
      textBox.setOrientation(LinearLayout.VERTICAL);
      textBox.setPadding(18, 0, 8, 0);
      row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));
      TextView name = new TextView(AppSelectionActivity.this);
      name.setText(item.label); name.setTextSize(18); name.setTextColor(Color.DKGRAY);
      textBox.addView(name);
      TextView pkg = new TextView(AppSelectionActivity.this);
      pkg.setText(item.packageName); pkg.setTextSize(13); pkg.setTextColor(Color.GRAY);
      textBox.addView(pkg);
      CheckBox box = new CheckBox(AppSelectionActivity.this);
      box.setChecked(selected.contains(item.packageName));
      row.addView(box, new LinearLayout.LayoutParams(-2, -2));
      View.OnClickListener toggle = v -> {
        if (selected.contains(item.packageName)) selected.remove(item.packageName);
        else selected.add(item.packageName);
        box.setChecked(selected.contains(item.packageName));
        if (actionButton != null) actionButton.setEnabled(!selected.isEmpty());
        if (selectedText != null) selectedText.setText(selected.size() + " selected");
        if (selectAllButton != null) {
          String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.getDefault());
          int visible = 0, visibleSelected = 0;
          for (AppItem a : allApps) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.getDefault()).contains(q)
                || a.packageName.toLowerCase(Locale.getDefault()).contains(q)) {
              visible++;
              if (selected.contains(a.packageName)) visibleSelected++;
            }
          }
          selectAllButton.setText(visible > 0 && visible == visibleSelected ? "Unselect all" : "Select all");
        }
      };
      row.setOnClickListener(toggle);
      box.setOnClickListener(toggle);
      return row;
    }
  }

  private Set<String> packageNames(List<AppItem> items) {
    Set<String> names = new HashSet<>();
    for (AppItem item : items) names.add(item.packageName);
    return names;
  }

  private void toggleSelectAll() {
    String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.getDefault());
    ArrayList<AppItem> filtered = new ArrayList<>();
    for (AppItem item : allApps) {
      if (query.isEmpty() || item.label.toLowerCase(Locale.getDefault()).contains(query)
          || item.packageName.toLowerCase(Locale.getDefault()).contains(query)) filtered.add(item);
    }
    Set<String> visible = packageNames(filtered);
    if (!visible.isEmpty() && selected.containsAll(visible)) selected.removeAll(visible);
    else selected.addAll(visible);
    if (selectedText != null) selectedText.setText(selected.size() + " selected");
    if (actionButton != null) actionButton.setEnabled(!selected.isEmpty());
    if (selectAllButton != null) selectAllButton.setText(selected.containsAll(visible) ? "Unselect all" : "Select all");
    if (listView != null && listView.getAdapter() != null) ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
  }

  private boolean isPackageSuspended(String packageName) {
    try {
      return devicePolicyManager.isPackageSuspended(admin, packageName);
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    } catch (RuntimeException e) {
      return false;
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
