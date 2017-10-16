/*
 * Copyright (C) 2015 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2015 Vincent Breitmoser <v.breitmoser@mugenguild.com>
 * Copyright (C) 2013-2014 Signe Rüsch
 * Copyright (C) 2013-2014 Philipp Jakubeit
 * Copyright (C) 2016 Nikita Mikhailov <nikita.s.mikhailov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.base;


import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.os.AsyncTask;
import android.os.Bundle;

import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;
import nordpol.android.TagDispatcherBuilder;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.securitytoken.CardException;
import org.sufficientlysecure.keychain.securitytoken.NfcTransport;
import org.sufficientlysecure.keychain.securitytoken.SecurityTokenConnection;
import org.sufficientlysecure.keychain.securitytoken.SecurityTokenInfo;
import org.sufficientlysecure.keychain.securitytoken.Transport;
import org.sufficientlysecure.keychain.securitytoken.UsbConnectionDispatcher;
import org.sufficientlysecure.keychain.securitytoken.usb.UsbTransport;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.service.input.RequiredInputParcel;
import org.sufficientlysecure.keychain.ui.CreateKeyActivity;
import org.sufficientlysecure.keychain.ui.PassphraseDialogActivity;
import org.sufficientlysecure.keychain.ui.dialog.FidesmoInstallDialog;
import org.sufficientlysecure.keychain.ui.dialog.FidesmoPgpInstallDialog;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.util.Notify.Style;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.Passphrase;

public abstract class BaseSecurityTokenActivity extends BaseActivity
        implements OnDiscoveredTagListener, UsbConnectionDispatcher.OnDiscoveredUsbDeviceListener {
    public static final int REQUEST_CODE_PIN = 1;

    public static final String EXTRA_TAG_HANDLING_ENABLED = "tag_handling_enabled";

    private static final String FIDESMO_APP_PACKAGE = "com.fidesmo.sec.android";

    protected TagDispatcher mNfcTagDispatcher;
    protected UsbConnectionDispatcher mUsbDispatcher;
    private boolean mTagHandlingEnabled;

    private SecurityTokenInfo tokenInfo;
    private Passphrase mCachedPin;

    /**
     * Override to change UI before SecurityToken handling (UI thread)
     */
    protected void onSecurityTokenPreExecute() {
    }

    /**
     * Override to implement SecurityToken operations (background thread)
     */
    protected void doSecurityTokenInBackground(SecurityTokenConnection stConnection) throws IOException {
        tokenInfo = stConnection.getTokenInfo();
        Log.d(Constants.TAG, "Security Token: " + tokenInfo);
    }

    /**
     * Override to handle result of SecurityToken operations (UI thread)
     */
    protected void onSecurityTokenPostExecute(SecurityTokenConnection stConnection) {
        Intent intent = new Intent(this, CreateKeyActivity.class);
        intent.putExtra(CreateKeyActivity.EXTRA_SECURITY_TOKEN_INFO, tokenInfo);
        startActivity(intent);
    }

    /**
     * Override to use something different than Notify (UI thread)
     */
    protected void onSecurityTokenError(String error) {
        Notify.create(this, error, Style.WARN).show();
    }

    /**
     * Override to do something when PIN is wrong, e.g., clear passphrases (UI thread)
     */
    protected void onSecurityTokenPinError(String error, SecurityTokenInfo tokeninfo) {
        onSecurityTokenError(error);
    }

    public void tagDiscovered(Tag tag) {
        // Actual NFC operations are executed in doInBackground to not block the UI thread
        if (!mTagHandlingEnabled) {
            return;
        }

        NfcTransport nfcTransport = new NfcTransport(tag);
        securityTokenDiscovered(nfcTransport);
    }

    public void usbDeviceDiscovered(UsbDevice usbDevice) {
        // Actual USB operations are executed in doInBackground to not block the UI thread
        if (!mTagHandlingEnabled) {
            return;
        }

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        UsbTransport usbTransport = new UsbTransport(usbDevice, usbManager);
        securityTokenDiscovered(usbTransport);
    }

    public void securityTokenDiscovered(final Transport transport) {
        // Actual Security Token operations are executed in doInBackground to not block the UI thread
        if (!mTagHandlingEnabled)
            return;

        final SecurityTokenConnection stConnection =
                SecurityTokenConnection.getInstanceForTransport(transport, mCachedPin);

        new AsyncTask<Void, Void, IOException>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                onSecurityTokenPreExecute();
            }

            @Override
            protected IOException doInBackground(Void... params) {
                try {
                    stConnection.connectIfNecessary(getBaseContext());

                    handleSecurityToken(stConnection);
                } catch (IOException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(IOException exception) {
                super.onPostExecute(exception);

                if (exception != null) {
                    handleSecurityTokenError(stConnection, exception);
                    return;
                }

                onSecurityTokenPostExecute(stConnection);
            }
        }.execute();
    }

    protected void pauseTagHandling() {
        mTagHandlingEnabled = false;
    }

    protected void resumeTagHandling() {
        mTagHandlingEnabled = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNfcTagDispatcher = new TagDispatcherBuilder(this, this)
                .enableUnavailableNfcUserPrompt(false)
                .enableSounds(true)
                .enableDispatchingOnUiThread(true)
                .enableBroadcomWorkaround(false)
                .build();

        mUsbDispatcher = new UsbConnectionDispatcher(this, this);

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            mTagHandlingEnabled = savedInstanceState.getBoolean(EXTRA_TAG_HANDLING_ENABLED);
        } else {
            mTagHandlingEnabled = true;
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            throw new AssertionError("should not happen: NfcOperationActivity.onCreate is called instead of onNewIntent!");
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(EXTRA_TAG_HANDLING_ENABLED, mTagHandlingEnabled);
    }

    /**
     * This activity is started as a singleTop activity.
     * All new NFC Intents which are delivered to this activity are handled here
     */
    @Override
    public void onNewIntent(final Intent intent) {
        mNfcTagDispatcher.interceptIntent(intent);
    }

    private void handleSecurityTokenError(SecurityTokenConnection stConnection, IOException e) {

        if (e instanceof TagLostException) {
            onSecurityTokenError(getString(R.string.security_token_error_tag_lost));
            return;
        }

        if (e instanceof IsoDepNotSupportedException) {
            onSecurityTokenError(getString(R.string.security_token_error_iso_dep_not_supported));
            return;
        }

        short status;
        if (e instanceof CardException) {
            status = ((CardException) e).getResponseCode();
        } else {
            status = -1;
        }

        // Wrong PIN, a status of 63CX indicates X attempts remaining.
        // NOTE: Used in ykneo-openpgp version < 1.0.10, changed to 0x6982 in 1.0.11
        // https://github.com/Yubico/ykneo-openpgp/commit/90c2b91e86fb0e43ee234dd258834e75e3416410
        if ((status & (short) 0xFFF0) == 0x63C0) {
            int tries = status & 0x000F;

            SecurityTokenInfo tokeninfo = null;
            try {
                tokeninfo = stConnection.getTokenInfo();
            } catch (IOException e2) {
                // don't care
            }
            // hook to do something different when PIN is wrong
            onSecurityTokenPinError(
                    getResources().getQuantityString(R.plurals.security_token_error_pin, tries, tries), tokeninfo);
            return;
        }

        // Otherwise, all status codes are fixed values.
        switch (status) {

            // These error conditions are likely to be experienced by an end user.

            /* OpenPGP Card Spec: Security status not satisfied, PW wrong,
            PW not checked (command not allowed), Secure messaging incorrect (checksum and/or cryptogram) */
            // NOTE: Used in ykneo-openpgp >= 1.0.11 for wrong PIN
            case 0x6982: {
                SecurityTokenInfo tokeninfo = null;
                try {
                    tokeninfo = stConnection.getTokenInfo();
                } catch (IOException e2) {
                    // don't care
                }

                // hook to do something different when PIN is wrong
                onSecurityTokenPinError(getString(R.string.security_token_error_security_not_satisfied), tokeninfo);
                break;
            }
            /* OpenPGP Card Spec: Selected file in termination state */
            case 0x6285: {
                onSecurityTokenError(getString(R.string.security_token_error_terminated));
                break;
            }
            /* OpenPGP Card Spec: Wrong length (Lc and/or Le) */
            // NOTE: Used in ykneo-openpgp < 1.0.10 for too short PIN, changed in 1.0.11 to 0x6A80 for too short PIN
            // https://github.com/Yubico/ykneo-openpgp/commit/b49ce8241917e7c087a4dab7b2c755420ff4500f
            case 0x6700: {
                // hook to do something different when PIN is wrong
                onSecurityTokenPinError(getString(R.string.security_token_error_wrong_length), null);
                break;
            }
            /* OpenPGP Card Spec: Incorrect parameters in the data field */
            // NOTE: Used in ykneo-openpgp >= 1.0.11 for too short PIN
            case 0x6A80: {
                // hook to do something different when PIN is wrong
                onSecurityTokenPinError(getString(R.string.security_token_error_bad_data), null);
                break;
            }
            /* OpenPGP Card Spec: Authentication method blocked, PW blocked (error counter zero) */
            case 0x6983: {
                onSecurityTokenError(getString(R.string.security_token_error_authentication_blocked));
                break;
            }
            /* OpenPGP Card Spec: Condition of use not satisfied */
            case 0x6985: {
                onSecurityTokenError(getString(R.string.security_token_error_conditions_not_satisfied));
                break;
            }
            /* OpenPGP Card Spec: SM data objects incorrect (e.g. wrong TLV-structure in command data) */
            // NOTE: 6A88 is "Not Found" in the spec, but ykneo-openpgp also returns 6A83 for this in some cases.
            case 0x6A88:
            case 0x6A83: {
                onSecurityTokenError(getString(R.string.security_token_error_data_not_found));
                break;
            }
            // 6F00 is a JavaCard proprietary status code, SW_UNKNOWN, and usually represents an
            // unhandled exception on the security token.
            case 0x6F00: {
                onSecurityTokenError(getString(R.string.security_token_error_unknown));
                break;
            }
            // 6A82 app not installed on security token!
            case 0x6A82: {
                if (stConnection.isFidesmoToken()) {
                    // Check if the Fidesmo app is installed
                    if (isAndroidAppInstalled(FIDESMO_APP_PACKAGE)) {
                        promptFidesmoPgpInstall();
                    } else {
                        promptFidesmoAppInstall();
                    }
                } else { // Other (possibly) compatible hardware
                    onSecurityTokenError(getString(R.string.security_token_error_pgp_app_not_installed));
                }
                break;
            }

            // These errors should not occur in everyday use; if they are returned, it means we
            // made a mistake sending data to the token, or the token is misbehaving.

            /* OpenPGP Card Spec: Last command of the chain expected */
            case 0x6883: {
                onSecurityTokenError(getString(R.string.security_token_error_chaining_error));
                break;
            }
            /* OpenPGP Card Spec: Wrong parameters P1-P2 */
            case 0x6B00: {
                onSecurityTokenError(getString(R.string.security_token_error_header, "P1/P2"));
                break;
            }
            /* OpenPGP Card Spec: Instruction (INS) not supported */
            case 0x6D00: {
                onSecurityTokenError(getString(R.string.security_token_error_header, "INS"));
                break;
            }
            /* OpenPGP Card Spec: Class (CLA) not supported */
            case 0x6E00: {
                onSecurityTokenError(getString(R.string.security_token_error_header, "CLA"));
                break;
            }
            default: {
                onSecurityTokenError(getString(R.string.security_token_error, e.getMessage()));
                break;
            }
        }

    }

    /**
     * Called when the system is about to start resuming a previous activity,
     * disables NFC Foreground Dispatch
     */
    public void onPause() {
        super.onPause();
        Log.d(Constants.TAG, "BaseNfcActivity.onPause");

        mNfcTagDispatcher.disableExclusiveNfc();
    }

    /**
     * Called when the activity will start interacting with the user,
     * enables NFC Foreground Dispatch
     */
    public void onResume() {
        super.onResume();
        Log.d(Constants.TAG, "BaseNfcActivity.onResume");
        mNfcTagDispatcher.enableExclusiveNfc();
    }

    protected void obtainSecurityTokenPin(RequiredInputParcel requiredInput) {
        try {
            Passphrase passphrase = PassphraseCacheService.getCachedPassphrase(this,
                    requiredInput.getMasterKeyId(), requiredInput.getSubKeyId());
            if (passphrase != null) {
                mCachedPin = passphrase;
                return;
            }

            Intent intent = new Intent(this, PassphraseDialogActivity.class);
            intent.putExtra(PassphraseDialogActivity.EXTRA_REQUIRED_INPUT,
                    RequiredInputParcel.createRequiredPassphrase(requiredInput));
            startActivityForResult(intent, REQUEST_CODE_PIN);
        } catch (PassphraseCacheService.KeyNotFoundException e) {
            throw new AssertionError(
                    "tried to find passphrase for non-existing key. this is a programming error!");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_PIN: {
                if (resultCode != Activity.RESULT_OK) {
                    setResult(resultCode);
                    finish();
                    return;
                }
                CryptoInputParcel input = data.getParcelableExtra(PassphraseDialogActivity.RESULT_CRYPTO_INPUT);
                mCachedPin = input.getPassphrase();
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected void handleSecurityToken(SecurityTokenConnection stConnection) throws IOException {
        doSecurityTokenInBackground(stConnection);
    }

    public static class IsoDepNotSupportedException extends IOException {

        public IsoDepNotSupportedException(String detailMessage) {
            super(detailMessage);
        }

    }

    /**
     * Ask user if she wants to install PGP onto her Fidesmo token
     */
    private void promptFidesmoPgpInstall() {
        FidesmoPgpInstallDialog fidesmoPgpInstallDialog = new FidesmoPgpInstallDialog();
        fidesmoPgpInstallDialog.show(getSupportFragmentManager(), "fidesmoPgpInstallDialog");
    }

    /**
     * Show a Dialog to the user informing that Fidesmo App must be installed and with option
     * to launch the Google Play store.
     */
    private void promptFidesmoAppInstall() {
        FidesmoInstallDialog fidesmoInstallDialog = new FidesmoInstallDialog();
        fidesmoInstallDialog.show(getSupportFragmentManager(), "fidesmoInstallDialog");
    }

    /**
     * Use the package manager to detect if an application is installed on the phone
     *
     * @param uri an URI identifying the application's package
     * @return 'true' if the app is installed
     */
    private boolean isAndroidAppInstalled(String uri) {
        PackageManager mPackageManager = getPackageManager();
        boolean mAppInstalled;
        try {
            mPackageManager.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            mAppInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Constants.TAG, "App not installed on Android device");
            mAppInstalled = false;
        }
        return mAppInstalled;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mUsbDispatcher.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUsbDispatcher.onStart();
    }

    /**
     * Run Security Token routines if last used token is connected and supports
     * persistent connections
     */
    public void checkDeviceConnection() {
        mUsbDispatcher.rescanDevices();
    }
}
