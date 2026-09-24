/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afwsamples.testdpc;

import android.Manifest;
import android.R.id;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.afwsamples.testdpc.common.DumpableActivity;
import com.afwsamples.testdpc.common.AppSecurity;
import com.afwsamples.testdpc.common.PolicyBundleManager;
import com.afwsamples.testdpc.common.OnBackPressedHandler;
import com.afwsamples.testdpc.policy.PolicyManagementFragment;
import com.afwsamples.testdpc.search.PolicySearchFragment;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * An entry activity that shows a profile setup fragment if the app is not a profile or device
 * owner. Otherwise, a policy management fragment is shown.
 */
public class PolicyManagementActivity extends DumpableActivity
    implements FragmentManager.OnBackStackChangedListener {

  private static final String TAG = PolicyManagementActivity.class.getSimpleName();

  private static final String CMD_LOCK_TASK_MODE = "lock-task-mode";
  private static final String LOCK_MODE_ACTION_START = "start";
  private static final String LOCK_MODE_ACTION_STATUS = "status";
  private static final String LOCK_MODE_ACTION_STOP = "stop";
  public static final String EXTRA_QUICK_ACTION = "quick_action";
  /** Legacy extra retained for compatibility; it is deliberately ignored for authentication. */
  @Deprecated public static final String EXTRA_SKIP_PASSWORD = "skip_password";
  public static final String EXTRA_RETURN_TO_QUICK_ACCESS = "return_to_quick_access";
  private static final String EXTRA_AUTH_SESSION_TOKEN = "auth_session_token";
  private static final Set<String> AUTHORIZED_TOKENS =
      Collections.synchronizedSet(new HashSet<String>());
  private static boolean sAuthenticatedSession;

  /** Authorizes one specific in-app Quick Access transition. */
  public static void authorizeQuickAccessIntent(Intent intent) {
    String token = UUID.randomUUID().toString();
    AUTHORIZED_TOKENS.add(token);
    intent.putExtra(EXTRA_AUTH_SESSION_TOKEN, token);
  }

  private static boolean consumeAuthorizedToken(Intent intent) {
    String token = intent.getStringExtra(EXTRA_AUTH_SESSION_TOKEN);
    if (TextUtils.isEmpty(token)) return false;
    intent.removeExtra(EXTRA_AUTH_SESSION_TOKEN);
    return AUTHORIZED_TOKENS.remove(token);
  }
  private static final int POLICY_EXPORT_REQUEST = 9901;
  private static final int POLICY_IMPORT_REQUEST = 9902;

  private boolean mLockTaskMode;
  private boolean mUnlocked;
  private boolean mLeavingWithPrompt;
  private boolean mReturnToQuickAccess;
  private int mTapCount;
  private long mLastTapTime;
  private final java.util.ArrayList<String> mSessionChanges = new java.util.ArrayList<>();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getFragmentManager().addOnBackStackChangedListener(this);
    boolean skipPassword = consumeAuthorizedToken(getIntent());
    mReturnToQuickAccess = getIntent().getBooleanExtra(EXTRA_RETURN_TO_QUICK_ACCESS, false);
    if (hasAnyLoginMethod() && !skipPassword) {
      showProtectionScreen();
    } else {
      mUnlocked = true;
      sAuthenticatedSession = true;
      startMainContent();
    }
  }

  private void startMainContent() {
    if (isFinishing()) return;
    if (getActionBar() != null) getActionBar().show();
    setContentView(R.layout.activity_main);
    final String quickAction = getIntent().getStringExtra(EXTRA_QUICK_ACTION);
    getIntent().removeExtra(EXTRA_QUICK_ACTION);
    if (getFragmentManager().findFragmentByTag(PolicyManagementFragment.FRAGMENT_TAG) == null) {
      getFragmentManager()
          .beginTransaction()
          .replace(
              R.id.container, new PolicyManagementFragment(), PolicyManagementFragment.FRAGMENT_TAG)
          .commit();
    }
    mSessionChanges.clear();
    if (quickAction != null) new android.os.Handler().postDelayed(() -> {
      Fragment f = getFragmentManager().findFragmentByTag(PolicyManagementFragment.FRAGMENT_TAG);
      if (f instanceof PolicyManagementFragment) ((PolicyManagementFragment) f).openQuickAction(quickAction);
    }, 300);
  }

  private void showProtectionScreen() {
    mUnlocked = false;
    if (getActionBar() != null) getActionBar().hide();
    mTapCount = 0;
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    int pad = (int) (32 * getResources().getDisplayMetrics().density);
    root.setPadding(pad, pad, pad, pad);
    root.setBackgroundColor(Color.rgb(12, 18, 28));

    TextView icon = new TextView(this);
    icon.setText("◈");
    icon.setTextSize(56);
    icon.setTextColor(Color.WHITE);
    icon.setGravity(Gravity.CENTER);
    root.addView(icon, new LinearLayout.LayoutParams(-1, -2));

    TextView title = new TextView(this);
    title.setText("This app is protecting your device");
    title.setTextSize(26);
    title.setTextColor(Color.WHITE);
    title.setGravity(Gravity.CENTER);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
    titleParams.topMargin = (int) (18 * getResources().getDisplayMetrics().density);
    root.addView(title, titleParams);

    TextView subtitle = new TextView(this);
    subtitle.setText("Test DPC is protected.");
    subtitle.setTextSize(15);
    subtitle.setTextColor(Color.LTGRAY);
    subtitle.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
    subParams.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
    root.addView(subtitle, subParams);

    View touch = root;
    touch.setOnClickListener(v -> {
      long now = System.currentTimeMillis();
      if (now - mLastTapTime > 2000) mTapCount = 0;
      mLastTapTime = now;
      mTapCount++;
      if (mTapCount >= 7) {
        showModernPasswordPage();
      }
    });
    setContentView(root);
  }

  private final java.util.concurrent.ExecutorService mPasswordExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

  private boolean hasAnyLoginMethod() { return AppSecurity.hasPassword(this) || AppSecurity.hasTotp(this); }

  private void showModernPasswordPage() {
    mUnlocked = false;
    if (getActionBar() != null) getActionBar().hide();
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    int pad = (int) (28 * getResources().getDisplayMetrics().density);
    root.setPadding(pad, pad, pad, pad);
    root.setBackgroundColor(Color.rgb(12, 18, 28));

    TextView title = new TextView(this);
    title.setText("Unlock Test DPC");
    title.setTextSize(30);
    title.setTextColor(Color.WHITE);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    title.setGravity(Gravity.CENTER);
    root.addView(title, new LinearLayout.LayoutParams(-1, -2));

    TextView message = new TextView(this);
    message.setText(AppSecurity.hasPassword(this) && AppSecurity.hasTotp(this) ? "Enter your password or current 6-digit one-time code." : (AppSecurity.hasTotp(this) ? "Enter your current 6-digit one-time code." : "Enter your password to access device policies."));
    message.setTextSize(16);
    message.setTextColor(Color.LTGRAY);
    message.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
    mp.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
    root.addView(message, mp);

    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    input.setSingleLine(true);
    input.setHint("Enter password");
    input.setTextColor(Color.WHITE);
    input.setHintTextColor(Color.GRAY);
    input.setTextSize(17);
    input.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    input.setPadding(24, 0, 24, 0);
    GradientDrawable fieldBg = new GradientDrawable();
    fieldBg.setColor(Color.rgb(30, 39, 52));
    fieldBg.setCornerRadius(28);
    input.setBackground(fieldBg);
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, (int) (58 * getResources().getDisplayMetrics().density));
    ip.topMargin = (int) (28 * getResources().getDisplayMetrics().density);
    root.addView(input, ip);

    Button unlock = new Button(this);
    unlock.setText("Unlock");
    unlock.setTextSize(16);
    unlock.setAllCaps(false);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, (int) (54 * getResources().getDisplayMetrics().density));
    bp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
    root.addView(unlock, bp);

    unlock.setOnClickListener(v -> {
      final String password = input.getText().toString();
      unlock.setEnabled(false);
      unlock.setText("Checking…");
      mPasswordExecutor.execute(() -> {
        final boolean valid = (password.matches("\\d{6}") && AppSecurity.verifyTotp(this, password)) || AppSecurity.verify(this, password);
        runOnUiThread(() -> {
          if (isFinishing()) return;
          unlock.setEnabled(true);
          unlock.setText("Unlock");
          if (valid) {
            mUnlocked = true;
            sAuthenticatedSession = true;
            startMainContent();
          } else {
            input.setError("Incorrect password");
            input.selectAll();
          }
        });
      });
    });
    setContentView(root);
    input.requestFocus();
  }

  public void recordPolicyChange(String description) {
    if (mUnlocked && description != null && !description.trim().isEmpty()) {
      mSessionChanges.add(description);
      AppSecurity.markPolicyEdited(this);
    }
  }

  public void finishForQuickAccessReturn() {
    sAuthenticatedSession = true;
    mSessionChanges.clear();
    mLeavingWithPrompt = false;
    finish();
  }

  private boolean confirmLeaving() {
    if (!mUnlocked || mSessionChanges.isEmpty() || mLeavingWithPrompt) return false;
    mLeavingWithPrompt = true;
    StringBuilder message = new StringBuilder("The following changes were made during this session:\n\n");
    for (String change : mSessionChanges) {
      message.append("• ").append(change).append("\n");
    }
    message.append("\nSome changes may make the device less restricted. Review them before leaving.");
    new AlertDialog.Builder(this)
        .setTitle("Review policy changes")
        .setMessage(message.toString())
        .setNegativeButton("Stay", (d, w) -> mLeavingWithPrompt = false)
        .setPositiveButton("Leave", (d, w) -> {
          mSessionChanges.clear();
          mLeavingWithPrompt = false;
          sAuthenticatedSession = false;
          finish();
        })
        .setOnCancelListener(d -> mLeavingWithPrompt = false)
        .show();
    return true;
  }

  private void showPasswordSettings() {
    if (AppSecurity.hasPassword(this)) {
      final EditText current = new EditText(this);
      current.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      current.setHint("Current password");
      new AlertDialog.Builder(this)
          .setTitle("Change DPC password")
          .setMessage("Password set: " + AppSecurity.formatTime(AppSecurity.getPasswordSetTime(this))
              + "\nPolicy last edited: " + AppSecurity.formatTime(AppSecurity.getPolicyEditedTime(this)))
          .setView(current)
          .setPositiveButton("Continue", (d, w) -> {
            if (AppSecurity.verify(this, current.getText().toString())) {
              showSetPasswordDialog();
            } else {
              new AlertDialog.Builder(this).setMessage("Incorrect password.").setPositiveButton("OK", null).show();
            }
          })
          .setNeutralButton("One-time code", (d, w) -> showTotpSettings())
          .setNegativeButton("Cancel", null)
          .show();
    } else {
      new AlertDialog.Builder(this)
          .setTitle("App security")
          .setMessage("Password: not set\nOne-time code: " + (AppSecurity.hasTotp(this) ? "enabled" : "not set"))
          .setPositiveButton("Set password", (d, w) -> showSetPasswordDialog())
          .setNeutralButton("One-time code", (d, w) -> showTotpSettings())
          .setNegativeButton("Close", null)
          .show();
    }
  }

  private void showTotpSettings() {
    if (!AppSecurity.hasTotp(this)) {
      new AlertDialog.Builder(this)
          .setTitle("One-time code")
          .setMessage("Use an authenticator app as an additional way to unlock Test DPC. Opening this screen will not enable it until you choose Enable.")
          .setPositiveButton("Enable", (d, w) -> {
            String secret = AppSecurity.enableTotp(this);
            if (TextUtils.isEmpty(secret)) {
              new AlertDialog.Builder(this)
                  .setMessage("Could not create a secure authenticator secret on this device.")
                  .setPositiveButton("OK", null)
                  .show();
            } else {
              showTotpSecret(secret);
            }
          })
          .setNegativeButton("Cancel", null)
          .show();
      return;
    }
    String secret = AppSecurity.getTotpSecret(this);
    if (TextUtils.isEmpty(secret)) {
      AppSecurity.disableTotp(this);
      showTotpSettings();
      return;
    }
    showTotpSecret(secret);
  }

  private void copyTotpSecret(String secret) {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText("Authenticator secret", secret));
      Toast.makeText(this, "Secret copied to clipboard", Toast.LENGTH_SHORT).show();
    }
  }

  private void showTotpSecret(String secret) {
    final int dp = (int) getResources().getDisplayMetrics().density;
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(24 * dp, 20 * dp, 24 * dp, 8 * dp);

    TextView intro = new TextView(this);
    intro.setText("Scan the QR code or copy the secret into your authenticator app. Codes refresh every 30 seconds.");
    intro.setTextSize(15);
    intro.setTextColor(Color.DKGRAY);
    card.addView(intro, new LinearLayout.LayoutParams(-1, -2));

    TextView secretView = new TextView(this);
    secretView.setText(secret);
    secretView.setTextSize(19);
    secretView.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
    secretView.setTextColor(Color.rgb(25, 35, 50));
    secretView.setGravity(Gravity.CENTER);
    secretView.setPadding(16 * dp, 16 * dp, 16 * dp, 16 * dp);
    GradientDrawable secretBg = new GradientDrawable();
    secretBg.setColor(Color.rgb(238, 242, 247));
    secretBg.setCornerRadius(18 * dp);
    secretView.setBackground(secretBg);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
    sp.topMargin = 18 * dp;
    card.addView(secretView, sp);

    Button copy = new Button(this);
    copy.setText("Copy secret");
    copy.setAllCaps(false);
    copy.setOnClickListener(v -> copyTotpSecret(secret));
    card.addView(copy, new LinearLayout.LayoutParams(-1, 52 * dp));

    Button qr = new Button(this);
    qr.setText("Show QR code");
    qr.setAllCaps(false);
    qr.setOnClickListener(v -> showTotpQr(secret));
    card.addView(qr, new LinearLayout.LayoutParams(-1, 52 * dp));

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle("Authenticator setup")
        .setView(card)
        .setPositiveButton("Done", null)
        .setNeutralButton("Regenerate", null)
        .setNegativeButton("Disable", null)
        .create();
    dialog.setOnShowListener(d -> {
      Button regenerate = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
      regenerate.setOnClickListener(v -> new AlertDialog.Builder(this)
          .setTitle("Regenerate authenticator secret?")
          .setMessage("The current secret will stop working immediately. You will need to scan or copy the new secret.")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Regenerate", (confirm, which) -> {
            String next = AppSecurity.enableTotp(this);
            if (TextUtils.isEmpty(next)) {
              new AlertDialog.Builder(this)
                  .setMessage("Could not create a new secure authenticator secret.")
                  .setPositiveButton("OK", null)
                  .show();
            } else {
              dialog.dismiss();
              showTotpSecret(next);
            }
          })
          .show());
      Button disable = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
      disable.setOnClickListener(v -> new AlertDialog.Builder(this)
          .setTitle("Disable one-time code?")
          .setMessage("Authenticator codes will no longer be accepted for unlocking Test DPC.")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Disable", (confirm, which) -> {
            AppSecurity.disableTotp(this);
            dialog.dismiss();
          })
          .show());
    });
    dialog.show();
  }

  private void showTotpQr(String secret) {
    final int dp = (int) getResources().getDisplayMetrics().density;
    String otpUri = "otpauth://totp/TestDPC?secret=" + secret + "&issuer=Test%20DPC";
    try {
      int available = Math.max(160 * dp, getResources().getDisplayMetrics().widthPixels - 72 * dp);
      int size = Math.min(280 * dp, available);
      BitMatrix matrix = new QRCodeWriter().encode(otpUri, BarcodeFormat.QR_CODE, size, size);
      int[] pixels = new int[size * size];
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
      }
      android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
      bitmap.setPixels(pixels, 0, size, 0, 0, size, size);

      LinearLayout layout = new LinearLayout(this);
      layout.setOrientation(LinearLayout.VERTICAL);
      layout.setGravity(Gravity.CENTER_HORIZONTAL);
      layout.setPadding(24 * dp, 16 * dp, 24 * dp, 8 * dp);
      ImageView image = new ImageView(this);
      image.setImageBitmap(bitmap);
      image.setPadding(8 * dp, 8 * dp, 8 * dp, 8 * dp);
      layout.addView(image, new LinearLayout.LayoutParams(size + 16 * dp, size + 16 * dp));

      TextView label = new TextView(this);
      label.setText("Test DPC • one-time code");
      label.setTextSize(14);
      label.setTextColor(Color.GRAY);
      label.setGravity(Gravity.CENTER);
      layout.addView(label, new LinearLayout.LayoutParams(-1, 42 * dp));

      new AlertDialog.Builder(this)
          .setTitle("Scan QR code")
          .setView(layout)
          .setPositiveButton("Copy secret", (d, w) -> copyTotpSecret(secret))
          .setNegativeButton("Done", null)
          .show();
    } catch (WriterException e) {
      new AlertDialog.Builder(this)
          .setTitle("QR code")
          .setMessage("Could not create the QR code. You can copy the secret instead.")
          .setPositiveButton("Copy secret", (d, w) -> copyTotpSecret(secret))
          .setNegativeButton("Close", null)
          .show();
    }
  }

  private void showSetPasswordDialog() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (24 * getResources().getDisplayMetrics().density);
    layout.setPadding(pad, 0, pad, 0);
    EditText password = new EditText(this);
    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    password.setHint("New password");
    EditText confirm = new EditText(this);
    confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    confirm.setHint("Confirm password");
    layout.addView(password);
    layout.addView(confirm);
    new AlertDialog.Builder(this)
        .setTitle(AppSecurity.hasPassword(this) ? "Set new DPC password" : "Set DPC password")
        .setMessage("Password set: " + AppSecurity.formatTime(AppSecurity.getPasswordSetTime(this))
            + "\nPolicy last edited: " + AppSecurity.formatTime(AppSecurity.getPolicyEditedTime(this)))
        .setView(layout)
        .setPositiveButton("Save", (d, w) -> {
          String p = password.getText().toString();
          if (p.length() < 6 || !p.equals(confirm.getText().toString())) {
            new AlertDialog.Builder(this)
                .setMessage("Use at least 6 characters and make both entries match.")
                .setPositiveButton("OK", null).show();
            return;
          }
          if (AppSecurity.setPassword(this, p)) {
            new AlertDialog.Builder(this)
                .setMessage("Password saved.\nPassword set: " + AppSecurity.formatTime(AppSecurity.getPasswordSetTime(this))
                    + "\nPolicy last edited: " + AppSecurity.formatTime(AppSecurity.getPolicyEditedTime(this)))
                .setPositiveButton("OK", null).show();
          }
        })
        .setNegativeButton("Cancel", null)
        .show();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.policy_management_menu, menu);
    return true;
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.action_show_search) {
      getFragmentManager()
          .beginTransaction()
          .replace(R.id.container, PolicySearchFragment.newInstance())
          .addToBackStack("search")
          .commit();
    } else if (itemId == R.id.action_save_policy) {
      Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
      i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "TestDPC-policy.json");
      startActivityForResult(i, POLICY_EXPORT_REQUEST); return true;
    } else if (itemId == R.id.action_load_policy) {
      Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/json");
      i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, POLICY_IMPORT_REQUEST); return true;
    } else if (itemId == R.id.action_transfer_ownership) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getFragmentManager().beginTransaction()
            .replace(R.id.container, new com.afwsamples.testdpc.transferownership.PickTransferComponentFragment())
            .addToBackStack("transfer_ownership").commit();
      } else {
        new AlertDialog.Builder(this).setMessage("Ownership transfer requires Android 9 (API 28) or later.").setPositiveButton("OK", null).show();
      }
      return true;
    } else if (itemId == R.id.action_quick_access) {
      startActivity(new android.content.Intent(this, com.afwsamples.testdpc.policy.QuickAccessActivity.class));
      return true;
    } else if (itemId == R.id.action_app_security) {
      showPasswordSettings();
      return true;
    } else if (itemId == id.home) {
      getFragmentManager().popBackStack();
    }
    return false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
    try {
      if (requestCode == POLICY_EXPORT_REQUEST) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          PolicyBundleManager.write(this, out);
        }
        recordPolicyChange("Saved portable policy profile");
        new AlertDialog.Builder(this).setTitle("Policy saved")
            .setMessage("The policy profile was saved. You can move this file to another device and use Load policy.")
            .setPositiveButton("OK", null).show();
      } else if (requestCode == POLICY_IMPORT_REQUEST) {
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
          PolicyBundleManager.importInto(this, in);
        }
        recordPolicyChange("Loaded portable policy profile");
        recreate();
      }
    } catch (Exception e) {
      Log.e(TAG, "Policy profile operation failed", e);
      new AlertDialog.Builder(this).setTitle("Policy profile error")
          .setMessage(e.getMessage() == null ? "Could not process the policy profile." : e.getMessage())
          .setPositiveButton("OK", null).show();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!mUnlocked && hasAnyLoginMethod()) {
      showProtectionScreen();
    }

    String lockModeCommand = getIntent().getStringExtra(CMD_LOCK_TASK_MODE);
    if (lockModeCommand != null) {
      setLockTaskMode(lockModeCommand);
    }

    askNotificationPermission();
  }

  @Override
  public void onBackStackChanged() {
    // Show the up button in actionbar if back stack has any entry.
    getActionBar().setDisplayHomeAsUpEnabled(getFragmentManager().getBackStackEntryCount() > 0);
  }

  @Override
  public void onBackPressed() {
    if (confirmLeaving()) return;
    if (mReturnToQuickAccess) {
      sAuthenticatedSession = true;
      finish();
      return;
    }
    Fragment currFragment = getFragmentManager().findFragmentById(R.id.container);
    boolean onBackPressHandled = false;
    if (currFragment != null && currFragment instanceof OnBackPressedHandler) {
      onBackPressHandled = ((OnBackPressedHandler) currFragment).onBackPressed();
    }
    if (!onBackPressHandled) {
      super.onBackPressed();
    }
  }

  @Override
  public void onDestroy() {
    mPasswordExecutor.shutdownNow();
    super.onDestroy();
    getFragmentManager().removeOnBackStackChangedListener(this);
  }

  @Override
  public void dump(String prefix, FileDescriptor fd, PrintWriter pw, String[] args) {
    if (args != null && args.length > 0 && args[0].equals(CMD_LOCK_TASK_MODE)) {
      String action = args.length == 1 ? LOCK_MODE_ACTION_STATUS : args[1];
      switch (action) {
        case LOCK_MODE_ACTION_START:
          pw.println("Starting lock-task mode");
          startLockTaskMode();
          break;
        case LOCK_MODE_ACTION_STOP:
          pw.println("Stopping lock-task mode");
          stopLockTaskMode();
          break;
        case LOCK_MODE_ACTION_STATUS:
          dumpLockModeStatus(pw);
          break;
        default:
          pw.printf("Invalid lock-task mode action: %s\n", action);
      }
      return;
    }
    pw.print(prefix);
    dumpLockModeStatus(pw);

    super.dump(prefix, fd, pw, args);
  }

  private void startLockTaskMode() {
    if (mLockTaskMode) Log.w(TAG, "startLockTaskMode(): mLockTaskMode already true");
    mLockTaskMode = true;

    Log.i(TAG, "startLockTaskMode(): calling Activity.startLockTask()");
    startLockTask();
  }

  private void stopLockTaskMode() {
    if (!mLockTaskMode) Log.w(TAG, "startLockTaskMode(): mLockTaskMode already false");
    mLockTaskMode = false;

    Log.i(TAG, "stopLockTaskMode(): calling Activity.stopLockTask()");
    stopLockTask();
  }

  private void dumpLockModeStatus(PrintWriter pw) {
    pw.printf("lock-task mode: %b\n", mLockTaskMode);
  }

  private void setLockTaskMode(String action) {
    switch (action) {
      case LOCK_MODE_ACTION_START:
        startLockTaskMode();
        break;
      case LOCK_MODE_ACTION_STOP:
        stopLockTaskMode();
        break;
      case LOCK_MODE_ACTION_STATUS:
        Log.d(TAG, "lock-task mode status: " + mLockTaskMode);
        break;
      default:
        Log.e(TAG, "invalid lock-task action: " + action);
    }
  }

  private void askNotificationPermission() {
    // This is only necessary for API level >= 33 (TIRAMISU)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          == PackageManager.PERMISSION_GRANTED) {
        Log.d(TAG, "Notification permission granted");
      } else {
        Log.e(TAG, "Notification permission missing");
        // Directly ask for the permission
        ActivityCompat.requestPermissions(
            this, new String[] {Manifest.permission.POST_NOTIFICATIONS}, 101);
      }
    }
  }
}
