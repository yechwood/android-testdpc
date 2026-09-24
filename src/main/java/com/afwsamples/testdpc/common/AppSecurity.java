package com.afwsamples.testdpc.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

public final class AppSecurity {
  private static final String PREFS = "dpc_app_security";
  private static final String KEY_SALT = "salt";
  private static final String KEY_HASH = "hash";
  private static final String KEY_PASSWORD_SET = "password_set";
  private static final String KEY_POLICY_EDITED = "policy_edited";
  private static final String KEY_TOTP_SECRET = "totp_secret";
  private static final String KEY_TOTP_IV = "totp_iv";
  private static final String KEYSTORE_ALIAS = "TestDpcTotpKey";
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
    if (password == null) return false;
    String saltHex = prefs(context).getString(KEY_SALT, null);
    String expectedHex = prefs(context).getString(KEY_HASH, null);
    if (saltHex == null || expectedHex == null) return false;
    try {
      byte[] actual = derive(password.toCharArray(), fromHex(saltHex));
      return actual != null && MessageDigest.isEqual(actual, fromHex(expectedHex));
    } catch (RuntimeException e) {
      return false;
    }
  }

  public static boolean hasTotp(Context context) {
    return !TextUtils.isEmpty(getTotpSecret(context));
  }

  public static String enableTotp(Context context) {
    byte[] secret = new byte[20];
    RANDOM.nextBytes(secret);
    String encoded = base32Encode(secret);
    if (!storeEncryptedTotpSecret(context, encoded)) {
      return null;
    }
    return encoded;
  }

  public static void disableTotp(Context context) {
    prefs(context).edit().remove(KEY_TOTP_SECRET).remove(KEY_TOTP_IV).apply();
  }

  public static String getTotpSecret(Context context) {
    SharedPreferences p = prefs(context);
    String encrypted = p.getString(KEY_TOTP_SECRET, null);
    if (TextUtils.isEmpty(encrypted)) return null;

    String ivEncoded = p.getString(KEY_TOTP_IV, null);
    if (!TextUtils.isEmpty(ivEncoded)) {
      try {
        return decryptTotp(encrypted, ivEncoded);
      } catch (Exception e) {
        return null;
      }
    }

    // Migrate the legacy plaintext secret exactly once.
    if (isLikelyBase32(encrypted)) {
      if (storeEncryptedTotpSecret(context, encrypted)) {
        return encrypted;
      }
    }
    return null;
  }

  public static boolean verifyTotp(Context context, String code) {
    String secret = getTotpSecret(context);
    if (TextUtils.isEmpty(secret) || code == null) return false;
    String normalized = code.replaceAll("\\s+", "");
    if (!normalized.matches("\\d{6}")) return false;
    long step = System.currentTimeMillis() / 1000L / 30L;
    for (long offset = -1; offset <= 1; offset++) {
      if (generateTotpCode(secret, step + offset).equals(normalized)) return true;
    }
    return false;
  }

  public static String generateTotpCode(String secret, long counter) {
    try {
      byte[] key = base32Decode(secret);
      byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(data);
      int offset = hash[hash.length - 1] & 0x0f;
      int binary = ((hash[offset] & 0x7f) << 24)
          | ((hash[offset + 1] & 0xff) << 16)
          | ((hash[offset + 2] & 0xff) << 8)
          | (hash[offset + 3] & 0xff);
      return String.format(Locale.US, "%06d", binary % 1000000);
    } catch (Exception e) {
      return "";
    }
  }

  private static boolean storeEncryptedTotpSecret(Context context, String secret) {
    try {
      SecretKey key = getOrCreateTotpKey();
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] ciphertext = cipher.doFinal(secret.getBytes("UTF-8"));
      prefs(context).edit()
          .putString(KEY_TOTP_SECRET, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
          .putString(KEY_TOTP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
          .apply();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static String decryptTotp(String ciphertext, String ivEncoded) throws Exception {
    byte[] iv = Base64.decode(ivEncoded, Base64.NO_WRAP);
    byte[] data = Base64.decode(ciphertext, Base64.NO_WRAP);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateTotpKey(), new GCMParameterSpec(128, iv));
    return new String(cipher.doFinal(data), "UTF-8");
  }

  private static SecretKey getOrCreateTotpKey() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
    keyStore.load(null);
    if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
      return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
    }
    KeyGenerator generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(
        KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build());
    return generator.generateKey();
  }

  private static boolean isLikelyBase32(String value) {
    return value.length() >= 16 && value.matches("[A-Za-z2-7=\\s-]+");
  }

  private static String base32Encode(byte[] data) {
    final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0, bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        out.append(alphabet[(buffer >> (bits - 5)) & 31]);
        bits -= 5;
      }
    }
    if (bits > 0) out.append(alphabet[(buffer << (5 - bits)) & 31]);
    return out.toString();
  }

  private static byte[] base32Decode(String value) {
    String s = value.replaceAll("[=\\s-]", "").toUpperCase(Locale.US);
    byte[] out = new byte[s.length() * 5 / 8];
    int buffer = 0, bits = 0, index = 0;
    for (int i = 0; i < s.length(); i++) {
      int v = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(s.charAt(i));
      if (v < 0) throw new IllegalArgumentException("Invalid Base32 secret");
      buffer = (buffer << 5) | v;
      bits += 5;
      if (bits >= 8) {
        out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return out;
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
    if ((value.length() & 1) != 0) throw new IllegalArgumentException("Invalid hex");
    byte[] out = new byte[value.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
