/*
 * Copyright (C) 2017 Vincent Breitmoser <look@my.amazin.horse>
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

package org.sufficientlysecure.keychain.ui;


import java.io.IOException;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewAnimator;

import nordpol.android.NfcGuideView;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.securitytoken.SecurityTokenInfo;
import org.sufficientlysecure.keychain.service.input.SecurityTokenChangePinParcel;
import org.sufficientlysecure.keychain.ui.base.BaseSecurityTokenActivity;
import org.sufficientlysecure.keychain.ui.util.ThemeChanger;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.OrientationUtils;
import org.sufficientlysecure.keychain.util.Passphrase;


/**
 * This class provides a communication interface to OpenPGP applications on ISO SmartCard compliant
 * NFC devices.
 * For the full specs, see http://g10code.com/docs/openpgp-card-2.0.pdf
 */
public class SecurityTokenChangePinOperationActivity extends BaseSecurityTokenActivity {
    public static final String EXTRA_CHANGE_PIN_PARCEL = "change_pin_parcel";

    public static final String RESULT_TOKEN_INFO = "token_info";

    public ViewAnimator vAnimator;
    public TextView vErrorText;
    private TextView vErrorTextPin;
    public Button vErrorTryAgainButton;
    public NfcGuideView nfcGuideView;

    private SecurityTokenChangePinParcel changePinInput;

    private SecurityTokenInfo resultTokenInfo;

    @Override
    protected void initTheme() {
        mThemeChanger = new ThemeChanger(this);
        mThemeChanger.setThemes(R.style.Theme_Keychain_Light_Dialog,
                R.style.Theme_Keychain_Dark_Dialog);
        mThemeChanger.changeTheme();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(Constants.TAG, "NfcOperationActivity.onCreate");

        nfcGuideView = (NfcGuideView) findViewById(R.id.nfc_guide_view);

        // prevent annoying orientation changes while fumbling with the device
        OrientationUtils.lockOrientation(this);
        // prevent close when touching outside of the dialog (happens easily when fumbling with the device)
        setFinishOnTouchOutside(false);
        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setTitle(R.string.security_token_nfc_text);

        vAnimator = (ViewAnimator) findViewById(R.id.view_animator);
        vAnimator.setDisplayedChild(0);

        nfcGuideView.setCurrentStatus(NfcGuideView.NfcGuideViewStatus.STARTING_POSITION);

        vErrorText = (TextView) findViewById(R.id.security_token_activity_3_error_text);
        vErrorTextPin = (TextView) findViewById(R.id.security_token_activity_4_error_text);
        vErrorTryAgainButton = (Button) findViewById(R.id.security_token_activity_3_error_try_again);
        vErrorTryAgainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeTagHandling();

                vAnimator.setDisplayedChild(0);

                nfcGuideView.setVisibility(View.VISIBLE);
                nfcGuideView.setCurrentStatus(NfcGuideView.NfcGuideViewStatus.STARTING_POSITION);
            }
        });
        Button vCancel = (Button) findViewById(R.id.security_token_activity_0_cancel);
        vCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        findViewById(R.id.security_token_activity_4_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent result = new Intent();
                result.putExtra(RESULT_TOKEN_INFO, resultTokenInfo);
                setResult(RESULT_CANCELED, result);
                finish();
            }
        });

        changePinInput = getIntent().getParcelableExtra(EXTRA_CHANGE_PIN_PARCEL);
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.security_token_operation_activity);
    }

    @Override
    public void onSecurityTokenPreExecute() {
        // start with indeterminate progress
        vAnimator.setDisplayedChild(1);
        nfcGuideView.setCurrentStatus(NfcGuideView.NfcGuideViewStatus.TRANSFERRING);
    }

    @Override
    protected void doSecurityTokenInBackground() throws IOException {
        mSecurityTokenConnection.setAdminPin(new Passphrase(changePinInput.getAdminPin()));
        mSecurityTokenConnection.resetPin(changePinInput.getNewPin());

        resultTokenInfo = mSecurityTokenConnection.getTokenInfo();
    }

    @Override
    protected final void onSecurityTokenPostExecute() {
        Intent result = new Intent();
        result.putExtra(RESULT_TOKEN_INFO, resultTokenInfo);
        setResult(RESULT_OK, result);

        // show finish
        vAnimator.setDisplayedChild(2);

        nfcGuideView.setCurrentStatus(NfcGuideView.NfcGuideViewStatus.DONE);

        if (mSecurityTokenConnection.isPersistentConnectionAllowed()) {
            // Just close
            finish();
        } else {
            mSecurityTokenConnection.clearSecureMessaging();
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    // check all 200ms if Security Token has been taken away
                    while (true) {
                        if (isSecurityTokenConnected()) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ignored) {
                            }
                        } else {
                            return null;
                        }
                    }
                }

                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    finish();
                }
            }.execute();
        }
    }

    @Override
    protected void onSecurityTokenError(String error) {
        pauseTagHandling();

        vErrorText.setText(error + "\n\n" + getString(R.string.security_token_nfc_try_again_text));
        vAnimator.setDisplayedChild(3);

        nfcGuideView.setVisibility(View.GONE);
    }

    @Override
    public void onSecurityTokenPinError(String error, SecurityTokenInfo tokeninfo) {
        resultTokenInfo = tokeninfo;

        pauseTagHandling();

        vErrorTextPin.setText(error);
        vAnimator.setDisplayedChild(4);

        nfcGuideView.setVisibility(View.GONE);
    }
}
