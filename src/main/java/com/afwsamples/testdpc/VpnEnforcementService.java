package com.afwsamples.testdpc;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.afwsamples.testdpc.common.AppSecurity;
import java.util.Collections;

/**
 * Low-overhead Always-on VPN recovery. It listens for default-network changes instead of polling.
 * Reapplies the configured VPN only after a short debounce and only when the active network is
 * not a VPN. It never launches or force-stops the provider app.
 */
@TargetApi(Build.VERSION_CODES.N)
public final class VpnEnforcementService extends Service {
  private static final String TAG = "DpcVpnEnforcement";
  private static final String CHANNEL_ID = "vpn_enforcement";
  private static final int NOTIFICATION_ID = 7041;
  private static final long DEBOUNCE_MS = 6000L;
  private static final long REAPPLY_COOLDOWN_MS = 30000L;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private ConnectivityManager connectivity;
  private ConnectivityManager.NetworkCallback callback;
  private long lastReapplyAt;

  public static void enable(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
        || !AppSecurity.isVpnEnforcementEnabled(context)) return;
    Intent intent = new Intent(context, VpnEnforcementService.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }

  public static void disable(Context context) {
    context.stopService(new Intent(context, VpnEnforcementService.class));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    startForeground(NOTIFICATION_ID, buildNotification());
    connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivity != null) {
      callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onLost(Network network) {
          scheduleRecoveryCheck();
        }
        @Override public void onAvailable(Network network) {
          scheduleRecoveryCheck();
        }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
          if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) scheduleRecoveryCheck();
        }
      };
      try {
        connectivity.registerDefaultNetworkCallback(callback);
      } catch (RuntimeException e) {
        Log.e(TAG, "Could not register network callback", e);
      }
    }
    scheduleRecoveryCheck();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!AppSecurity.isVpnEnforcementEnabled(this)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    return START_STICKY;
  }

  private Notification buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      if (manager != null) {
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID, "Always-on VPN recovery", NotificationManager.IMPORTANCE_LOW));
      }
    }
    Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
    return builder.setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("Always-on VPN protection")
        .setContentText("Listening for VPN network changes; no continuous polling")
        .setOngoing(true)
        .build();
  }

  private void scheduleRecoveryCheck() {
    handler.removeCallbacks(recoveryCheck);
    handler.postDelayed(recoveryCheck, DEBOUNCE_MS);
  }

  private final Runnable recoveryCheck = () -> {
    if (!AppSecurity.isVpnEnforcementEnabled(this) || connectivity == null) return;
    Network active = connectivity.getActiveNetwork();
    NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return;

    long now = System.currentTimeMillis();
    if (now - lastReapplyAt < REAPPLY_COOLDOWN_MS) return;
    String remembered = AppSecurity.getEnforcedVpnPackage(this);
    if (remembered == null || remembered.trim().isEmpty()) return;

    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    ComponentName admin = new ComponentName(this, DeviceAdminReceiver.class);
    try {
      String currentlyConfigured = dpm.getAlwaysOnVpnPackage(admin);
      if (currentlyConfigured == null || !remembered.equals(currentlyConfigured)) {
        Log.w(TAG, "Always-on VPN selection changed; not overwriting a different selection.");
        return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        dpm.setAlwaysOnVpnPackage(admin, remembered, true, Collections.singleton("com.android.settings"));
      } else {
        dpm.setAlwaysOnVpnPackage(admin, remembered, true);
      }
      lastReapplyAt = now;
      Log.w(TAG, "Reapplied Always-on VPN policy after VPN network loss.");
    } catch (Exception e) {
      Log.e(TAG, "Failed to reapply Always-on VPN policy", e);
    }
  };

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (connectivity != null && callback != null) {
      try { connectivity.unregisterNetworkCallback(callback); }
      catch (RuntimeException ignored) {}
    }
    super.onDestroy();
  }

  @Override public IBinder onBind(Intent intent) { return null; }
}
