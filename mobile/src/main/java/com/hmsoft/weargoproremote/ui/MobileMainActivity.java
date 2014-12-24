/*
 * Copyright (C) 2014 Mauricio Rodriguez (ranametal@users.sf.net)
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

package com.hmsoft.weargoproremote.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.data.WearSettings;
import com.hmsoft.libcommon.general.Utils;
import com.hmsoft.libcommon.gopro.GoProController;
import com.hmsoft.libcommon.gopro.GoProStatus;
import com.hmsoft.libcommon.sensors.ShakeDetectEventListener;
import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.R;
import com.hmsoft.weargoproremote.helpers.WifiHelper;
import com.hmsoft.weargoproremote.services.WearMessageHandlerService;

public class MobileMainActivity extends PreferenceActivity implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
        ShakeDetectEventListener.ShakeDetectActivityListener,
        GoogleApiClient.ConnectionCallbacks {

    //GoProController mGoProController;
    ShakeDetectEventListener mShakeDetectActivity;
    private GoogleApiClient mGoogleApiClient;
    private Node mWearNode = null;
    private PreferenceCategory mPrefCategoryWatch;
    private boolean mWatchSettingsChanged;
    private WearSettings fCurrentWearSettings;
    EditTextPreference mWifiNamePref;
    EditTextPreference mWifiPassPref;
    GoProController mGoProController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        bindPreferencesSummaryToValue(getPreferenceScreen());
        mPrefCategoryWatch = (PreferenceCategory)findPreference(getString(R.string.preference_category_watch_key));
        fCurrentWearSettings = new WearSettings();
        fCurrentWearSettings.loadFromPreferences(this, PreferenceManager.getDefaultSharedPreferences(this));

        mWifiNamePref = ((EditTextPreference)findPreference(getString(R.string.preference_wifi_name_key)));
        mWifiPassPref = ((EditTextPreference)findPreference(getString(R.string.preference_wifi_password_key)));

        Preference version = findPreference("preference_version_number");
        version.setTitle(BuildConfig.VERSION_NAME);
        version.setSummary(BuildConfig.FLAVOR + " " + BuildConfig.BUILD_TYPE);

        if(TextUtils.isEmpty(mWifiNamePref.getText()) || TextUtils.isEmpty(mWifiPassPref.getText())) {
            TestConnectionTask.run(this, findPreference(getString(R.string.preference_wifi_test_key)));
        }
    }

    @Override
    protected void onDestroy() {
        if(mShakeDetectActivity != null) mShakeDetectActivity.clear();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mShakeDetectActivity != null) {
            mShakeDetectActivity.stopDetecting();
        }
        if(mWatchSettingsChanged) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (mGoogleApiClient.isConnected() && mWearNode != null) {
                WearSettings wearSettings = new WearSettings();
                wearSettings.loadFromPreferences(this, prefs);
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mWearNode.getId(),
                        WearMessages.MESSAGE_SET_WEAR_SETTINGS, wearSettings.rawData).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            fCurrentWearSettings.saveToPreferences(MobileMainActivity.this, prefs);
                        }
                    }
                });
            } else {
                fCurrentWearSettings.saveToPreferences(MobileMainActivity.this, prefs);
            }
        }
        disconnectGoogleApiClient();
    }

    @Override
    protected void onResume() {
        mWatchSettingsChanged = false;
        super.onResume();
        if(mShakeDetectActivity != null) {
            mShakeDetectActivity.startDetecting();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        if(BuildConfig.DEBUG) {
            getMenuInflater().inflate(R.menu.menu_settings, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        if(BuildConfig.DEBUG) {

            int id = item.getItemId();

        //noinspection SimplifiableIfStatement
            if (id == R.id.action_settings) {
                /*Intent i = new Intent(this, TestActivity.class);
                startActivity(i);*/
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        if(getString(R.string.preference_wifi_test_key).equals(preference.getKey())) {
           TestConnectionTask.run(this, preference);
           return true;
        }
        return false;
    }

    private void bindPreferencesSummaryToValue(PreferenceGroup group) {
        int prefCount = group.getPreferenceCount();
        for(int c = 0; c < prefCount; c++) {
            Preference preference = group.getPreference(c);
            if(preference instanceof PreferenceGroup) {
                bindPreferencesSummaryToValue((PreferenceGroup)preference);
            } else {
                bindPreferenceSummaryToValue(preference);
            }
        }
    }

    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(this);
        preference.setOnPreferenceClickListener(this);
        Object value = null;
        if(preference instanceof CheckBoxPreference) {
            value = ((CheckBoxPreference)preference).isChecked();
        } else if(preference instanceof ListPreference) {
            value = ((ListPreference)preference).getValue();
        } else if(preference instanceof EditTextPreference) {
            value = ((EditTextPreference)preference).getText();
        }
        // Trigger the listener immediately with the preference's
        // current value.
        if(value != null) {
            this.onPreferenceChange(preference,	value);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            stringValue = (index >= 0 ? listPreference.getEntries()[index] : "").toString();
        } else if(preference instanceof CheckBoxPreference) {
            boolean checked = Boolean.parseBoolean(stringValue);
            stringValue = (checked ? getString(R.string.value_enabled) : getString(R.string.value_disabled));
        }

        preference.setSummary(stringValue);

        if(mPrefCategoryWatch != null && mPrefCategoryWatch.findPreference(preference.getKey()) != null) {
            mWatchSettingsChanged = true;

            connectGoogleApiClient();

            if(mShakeDetectActivity != null && preference.getKey().equals(getString(com.hmsoft.libcommon.R.string.preference_watch_shake_level_key))) {
                mShakeDetectActivity.setMinimumEachDirection(Integer.parseInt(value.toString()));
            }
        }

        return true;
    }

    void setWiFiConfig(String ssid, String pass) {
        mWifiNamePref.setText(ssid);
        onPreferenceChange(mWifiNamePref, ssid);

        mWifiPassPref.setText(pass);
        onPreferenceChange(mWifiPassPref, pass);
    }

    private void connectGoogleApiClient() {
        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addApi(Wearable.API)
                    .build();
        }

        if(!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
    }

    private void disconnectGoogleApiClient() {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    @Override
    public void shakeDetected() {
        Utils.playTone(this);
    }

    @Override
    public void onConnected(Bundle bundle) {
        PendingResult<NodeApi.GetConnectedNodesResult> pending = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        pending.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if(result.getNodes().size()>0) {
                    mWearNode = result.getNodes().get(0);
                } else {
                    mWearNode = null;
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private static class TestConnectionTask extends AsyncTask<Object, Void, Boolean> {

        private Preference preference;
        private MobileMainActivity mActivity;
        private static TestConnectionTask instance;
        private String mWifiSSID;
        private String mWifiPass;
        private boolean mNeedUpdateWifiConfig;

        private TestConnectionTask() {}

        public static void run(MobileMainActivity activity, Preference pref) {
            if(instance == null) {
                instance = new TestConnectionTask();

                String pass = activity.mWifiPassPref.getText();
                String ssid = activity.mWifiNamePref.getText();

                pref.setSummary(activity.getString(R.string.status_connection));

                activity.mGoProController = GoProController.getDefaultInstance(pass);

                instance.mActivity = activity;
                instance.execute(pref, ssid, pass);
            }
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            preference = (Preference)params[0];

            mWifiSSID = params[1].toString();
            mWifiPass = params[2].toString();

            if(TextUtils.isEmpty(mWifiSSID)) {
                mWifiSSID = WifiHelper.getCurrentWifiName(mActivity);
            }

            if(TextUtils.isEmpty(mWifiPass)) {
                mWifiPass = mActivity.mGoProController.getPassword();
                mNeedUpdateWifiConfig = !TextUtils.isEmpty(mWifiPass);
                mActivity.mGoProController = GoProController.getDefaultInstance(mWifiPass);
            }

            boolean success = WifiHelper.connectToWifi(mActivity, mWifiSSID, mWifiPass);

            if(!success) {
                return false;
            }

            GoProStatus status;
            int c = 0;
            while((status = mActivity.mGoProController.getStatus() ).CameraMode == GoProStatus.UNKNOW) {
                Utils.sleep(1000);
                if(++c > 3) {
                    break;
                }
            }

            if(status.CameraMode == GoProStatus.CAMERA_MODE_OFF_WIFION) {
                if(mActivity.mGoProController.turnOn()) {
                    Utils.sleep(5000);
                    status = mActivity.mGoProController.getStatus();
                }
            }

            success = status.CameraMode > GoProStatus.UNKNOW;            
            return success;
        }

        @Override
        protected  void onCancelled() {
            super.onCancelled();
            instance = null;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            instance = null;
            if(success) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
                preference.setSummary(mActivity.getString(R.string.status_connected));
                if(mActivity.mShakeDetectActivity == null) {
                    mActivity.mShakeDetectActivity = new ShakeDetectEventListener(mActivity);
                    mActivity.mShakeDetectActivity.addListener(mActivity);
                }
                int level = Integer.parseInt(prefs.getString(mActivity.getString(com.hmsoft.libcommon.R.string.preference_watch_shake_level_key), "3"));
                mActivity.mShakeDetectActivity.setMinimumEachDirection(level);
            } else {
                preference.setSummary(mActivity.getString(R.string.status_not_connected));
                //mActivity.mGoProController = null;
            }
            if(mNeedUpdateWifiConfig) {
                mActivity.setWiFiConfig(mWifiSSID, mWifiPass);
                Intent i = new Intent(mActivity, WearMessageHandlerService.class);
                i.setAction(WearMessageHandlerService.ACTION_SEND_MESSAGE_TO_WEAR);
                i.putExtra(WearMessageHandlerService.EXTRA_MESSAGE, WearMessages.MESSAGE_LAUNCH_ACTIVITY);
                mActivity.startService(i);
            }
        }
    }
}
