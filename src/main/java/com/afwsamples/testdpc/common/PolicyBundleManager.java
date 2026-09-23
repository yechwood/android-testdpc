package com.afwsamples.testdpc.common;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

  public static void importInto(Context context, InputStream in) throws Exception {
    byte[] data = readAll(in);
    JSONObject root = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
    if (!"TestDPC Policy Profile".equals(root.optString("format"))) throw new IllegalArgumentException("Not a TestDPC policy profile");
    if (root.optInt("version", 0) != 1) throw new IllegalArgumentException("Unsupported policy profile version");

    SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = prefs.edit().clear();
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
    editor.apply();

    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    ComponentName admin = new ComponentName(context, com.afwsamples.testdpc.DeviceAdminReceiver.class);
    applyPackageList(context, dpm, admin, root.optJSONArray("hiddenPackages"), 1);
    applyPackageList(context, dpm, admin, root.optJSONArray("suspendedPackages"), 2);
    applyPackageList(context, dpm, admin, root.optJSONArray("blockedUninstallPackages"), 3);
  }

  private static void applyPackageList(Context context, DevicePolicyManager dpm, ComponentName admin, JSONArray desired, int type) {
    java.util.HashSet<String> set = new java.util.HashSet<>();
    if (desired != null) for (int i = 0; i < desired.length(); i++) set.add(desired.optString(i));
    for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(
        android.content.pm.PackageManager.MATCH_ALL)) {
      String pkg = app.packageName;
      boolean want = set.contains(pkg);
      try {
        if (type == 1) dpm.setApplicationHidden(admin, pkg, want);
        else if (type == 2) dpm.setPackagesSuspended(new String[]{pkg}, want);
        else dpm.setUninstallBlocked(admin, pkg, want);
      } catch (Exception ignored) {}
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[8192]; int n;
    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    return out.toByteArray();
  }
}
