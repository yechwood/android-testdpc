package com.afwsamples.testdpc.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AppSecurity {
  private static final String PREFS = "dpc_app_security";
  private static final String KEY_SALT = "salt";
  private static final String KEY_HASH = "hash";
  private static final String KEY_PASSWORD_SET = "password_set";
  private static final String KEY_POLICY_EDITED = "policy_edited";
  private static final int ITERATIONS = 120000;
  private static final int KEY_LENGTH = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  private AppSecurity() {}

  public static boolean hasPassword(Context context) {
    return !TextUtils.isEmpty(prefs(context).getString(KEY_HASH, null));
  }

  public static boolean setPassword(Context context, String password) {
    if (TextUtils.isEmpty(password)) return false;
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    byte[] hash = derive(password.toCharArray(), salt);
    if (hash == null) return false;
    prefs(context).edit()
        .putString(KEY_SALT, hex(salt))
        .putString(KEY_HASH, hex(hash))
        .putLong(KEY_PASSWORD_SET, System.currentTimeMillis())
        .apply();
    return true;
  }

  public static boolean verify(Context context, String password) {
    String saltHex = prefs(context).getString(KEY_SALT, null);
    String expectedHex = prefs(context).getString(KEY_HASH, null);
    if (saltHex == null || expectedHex == null) return false;
    byte[] actual = derive(password.toCharArray(), fromHex(saltHex));
    return actual != null && MessageDigest.isEqual(actual, fromHex(expectedHex));
  }

  public static long getPasswordSetTime(Context context) {
    return prefs(context).getLong(KEY_PASSWORD_SET, 0L);
  }

  public static long getPolicyEditedTime(Context context) {
    return prefs(context).getLong(KEY_POLICY_EDITED, 0L);
  }

  public static void markPolicyEdited(Context context) {
    prefs(context).edit().putLong(KEY_POLICY_EDITED, System.currentTimeMillis()).apply();
  }

  public static String formatTime(long time) {
    if (time == 0L) return "Not set";
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(new Date(time));
  }

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static byte[] derive(char[] password, byte[] salt) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception e) {
      try {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private static String hex(byte[] data) {
    StringBuilder out = new StringBuilder(data.length * 2);
    for (byte b : data) out.append(String.format(Locale.US, "%02x", b & 0xff));
    return out.toString();
  }

  private static byte[] fromHex(String value) {
    byte[] out = new byte[value.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
