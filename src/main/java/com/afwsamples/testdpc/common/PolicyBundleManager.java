package com.afwsamples.testdpc.common;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PolicyBundleManager {
  private PolicyBundleManager() {}

  public static void write(Context context, OutputStream out) throws Exception {
    SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
    JSONObject root = new JSONObject();
    root.put("format", "TestDPC Policy Profile");
    root.put("version", 1);
    root.put("createdAt", System.currentTimeMillis());
    JSONObject values = new JSONObject();
    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
      Object v = e.getValue();
      if (v instanceof String || v instanceof Boolean || v instanceof Integer
          || v instanceof Long || v instanceof Float) {
        values.put(e.getKey(), v);
      } else if (v instanceof java.util.Set) {
        JSONArray a = new JSONArray();
        for (Object item : (java.util.Set<?>) v) a.put(String.valueOf(item));
        values.put(e.getKey(), a);
      }
    }
    root.put("preferences", values);

    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    ComponentName admin = new ComponentName(context, com.afwsamples.testdpc.DeviceAdminReceiver.class);
    JSONArray hidden = new JSONArray();
    JSONArray suspended = new JSONArray();
    JSONArray blockedUninstall = new JSONArray();
    for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(
        android.content.pm.PackageManager.MATCH_ALL)) {
      String pkg = app.packageName;
      if (dpm.isApplicationHidden(admin, pkg)) hidden.put(pkg);
      try {
        if (dpm.isPackageSuspended(admin, pkg)) suspended.put(pkg);
      } catch (Exception ignored) {}
      try {
        if (dpm.isUninstallBlocked(admin, pkg)) blockedUninstall.put(pkg);
      } catch (Exception ignored) {}
    }
    root.put("hiddenPackages", hidden);
    root.put("suspendedPackages", suspended);
    root.put("blockedUninstallPackages", blockedUninstall);
    root.put("portableNotice",
        "Policy profiles transfer configurable policy values and app policy states. Device-bound credentials, certificates, accounts and hardware-specific policies are not exported.");
    out.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public static ImportResult importInto(Context context, InputStream in) throws Exception {
    byte[] data = readAll(in);
    JSONObject root = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
    if (!"TestDPC Policy Profile".equals(root.optString("format"))) {
      throw new IllegalArgumentException("Not a TestDPC policy profile");
    }
    if (root.optInt("version", 0) != 1) {
      throw new IllegalArgumentException("Unsupported policy profile version");
    }

    SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = prefs.edit();
    JSONObject values = root.optJSONObject("preferences");
    if (values != null) {
      JSONArray names = values.names();
      if (names != null) for (int i = 0; i < names.length(); i++) {
        String key = names.getString(i);
        Object v = values.get(key);
        if (v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
        else if (v instanceof Integer) editor.putInt(key, (Integer) v);
        else if (v instanceof Long) editor.putLong(key, (Long) v);
        else if (v instanceof Double) editor.putFloat(key, ((Double) v).floatValue());
        else if (v instanceof String) editor.putString(key, (String) v);
        else if (v instanceof JSONArray) {
          java.util.Set<String> set = new java.util.HashSet<>();
          JSONArray a = (JSONArray) v;
          for (int j = 0; j < a.length(); j++) set.add(a.getString(j));
          editor.putStringSet(key, set);
        }
      }
    }
    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    ComponentName admin = new ComponentName(context, com.afwsamples.testdpc.DeviceAdminReceiver.class);
    ImportResult result = new ImportResult();
    result.add(applyPackageList(context, dpm, admin, root.optJSONArray("hiddenPackages"), 1));
    result.add(applyPackageList(context, dpm, admin, root.optJSONArray("suspendedPackages"), 2));
    result.add(applyPackageList(context, dpm, admin, root.optJSONArray("blockedUninstallPackages"), 3));
    if (!result.isSuccessful()) {
      throw new IllegalStateException(result.toMessage());
    }
    editor.apply();
    return result;
  }

  public static final class ImportResult {
    private int successCount;
    private int failureCount;
    private final java.util.ArrayList<String> failures = new java.util.ArrayList<>();

    private void add(ImportResult other) {
      successCount += other.successCount;
      failureCount += other.failureCount;
      failures.addAll(other.failures);
    }

    private boolean isSuccessful() { return failureCount == 0; }

    public String toMessage() {
      StringBuilder message = new StringBuilder("The profile could not be applied completely. ");
      message.append(successCount).append(" package operations succeeded and ")
          .append(failureCount).append(" failed.");
      int limit = Math.min(5, failures.size());
      if (limit > 0) {
        message.append("\n\nFailed packages:");
        for (int i = 0; i < limit; i++) message.append("\n• ").append(failures.get(i));
        if (failures.size() > limit) message.append("\n…and ").append(failures.size() - limit).append(" more.");
      }
      return message.toString();
    }

    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
  }

  private static ImportResult applyPackageList(Context context, DevicePolicyManager dpm,
      ComponentName admin, JSONArray desired, int type) {
    ImportResult result = new ImportResult();
    java.util.HashSet<String> set = new java.util.HashSet<>();
    if (desired != null) {
      for (int i = 0; i < desired.length(); i++) set.add(desired.optString(i));
    }
    for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(
        android.content.pm.PackageManager.MATCH_ALL)) {
      String pkg = app.packageName;
      boolean want = set.contains(pkg);
      try {
        boolean current;
        if (type == 1) {
          current = dpm.isApplicationHidden(admin, pkg);
          if (current != want && !dpm.setApplicationHidden(admin, pkg, want)) {
            throw new IllegalStateException("DPC rejected hidden-state change");
          }
        } else if (type == 2) {
          current = dpm.isPackageSuspended(admin, pkg);
          if (current != want) {
            String[] failed = dpm.setPackagesSuspended(admin, new String[]{pkg}, want);
            if (failed != null && failed.length > 0) {
              throw new IllegalStateException("DPC rejected suspension change");
            }
          }
        } else {
          current = dpm.isUninstallBlocked(admin, pkg);
          if (current != want) dpm.setUninstallBlocked(admin, pkg, want);
        }
        if (current != want) result.successCount++;
      } catch (Exception e) {
        result.failureCount++;
        if (result.failures.size() < 50) result.failures.add(pkg + ": " + e.getClass().getSimpleName());
      }
    }
    return result;
  }

  private static byte[] readAll(InputStream in) throws Exception {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    return out.toByteArray();
  }
}
