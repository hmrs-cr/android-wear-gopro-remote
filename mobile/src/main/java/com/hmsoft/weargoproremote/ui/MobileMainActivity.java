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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
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
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.data.WearSettings;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.libcommon.general.Utils;
import com.hmsoft.libcommon.gopro.GoProController;
import com.hmsoft.libcommon.gopro.GoProStatus;
import com.hmsoft.libcommon.sensors.ShakeDetectEventListener;
import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.R;
import com.hmsoft.weargoproremote.helpers.WifiHelper;
import com.hmsoft.weargoproremote.services.WearMessageHandlerService;
import com.hmsoft.weargoproremote.services.WifiIntentReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class MobileMainActivity extends PreferenceActivity implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
        ShakeDetectEventListener.ShakeDetectActivityListener,
        GoogleApiClient.ConnectionCallbacks {

    static final String TAG = "MobileMainActivity";
    
    //GoProController mGoProController;
    ShakeDetectEventListener mShakeDetectActivity;
    private GoogleApiClient mGoogleApiClient;
    private Node mWearNode = null;
    private PreferenceCategory mPrefCategoryWatch;
    private boolean mWatchSettingsChanged;
    private WearSettings fCurrentWearSettings;
    AlertDialog mSetupDialog;
    BroadcastReceiver mWifiIntentReceiver;
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
        showSetupDialogIfNeeded();
        if(mShakeDetectActivity != null) {
            mShakeDetectActivity.startDetecting();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_send_logs) {
            String currentWifi = WifiHelper.getCurrentWifiName(this);
            if(currentWifi != null && currentWifi.equals(mWifiNamePref.getText())) {
                new SendLogsTask(this).execute(mWifiPassPref.getText());
                return true;
            } else {
                Toast.makeText(this, R.string.label_connect_to_gp_wifi, Toast.LENGTH_LONG).show();
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
        String stringValue = value != null ? value.toString() : "";
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            stringValue = (index >= 0 ? listPreference.getEntries()[index] : "").toString();
        } else if(preference instanceof CheckBoxPreference) {
            boolean checked = Boolean.parseBoolean(stringValue);
            
            if(getString(R.string.preference_wifi_autostart_key).equals(preference.getKey())) {
                if(checked) {
                    WifiIntentReceiver.enable(this);
                } else {
                    WifiIntentReceiver.disable(this);
                }
            }
            
            stringValue = (checked ? getString(R.string.value_enabled) : getString(R.string.value_disabled));
        }

        preference.setSummary(stringValue);

        if(mPrefCategoryWatch != null && mPrefCategoryWatch.findPreference(preference.getKey()) != null) {
            mWatchSettingsChanged = true;

            connectGoogleApiClient();

            if(mShakeDetectActivity != null && preference.getKey().equals(getString(com.hmsoft.libcommon.R.string.preference_watch_shake_level_key))) {
                mShakeDetectActivity.setMinimumEachDirection(Integer.parseInt(value != null ? value.toString() : "0"));
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
    
    void showSetupDialogIfNeeded() {
        if(mSetupDialog == null && 
                (TextUtils.isEmpty(mWifiNamePref.getText()) || 
                        TextUtils.isEmpty(mWifiPassPref.getText()))) {

            // 1. Instantiate an AlertDialog.Builder with its constructor
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // 2. Chain together various setter methods to set the dialog characteristics
            builder
                    .setMessage(getString(R.string.setup_dialog_message))
                    .setTitle(R.string.setup_dialog_title)
                    .setCancelable(false)
                    .setNegativeButton(R.string.setup_dialog_manual_config_button, null)
                    .setPositiveButton(R.string.setup_dialog_setup_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            unregisterReceiver(mWifiIntentReceiver);
                            mWifiIntentReceiver = null;
                            TestConnectionTask.run(MobileMainActivity.this,
                                    findPreference(getString(R.string.preference_wifi_test_key)));
                        }
                    })
                    .setNeutralButton(R.string.setup_dialog_open_wifi_settings_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            mSetupDialog = null;
                        }
                    });

           
            // 3. Get the AlertDialog from create()
            mSetupDialog = builder.show();
            
            if(mWifiIntentReceiver == null) {
                mWifiIntentReceiver = new WifiIntentReceiver(){
                    @Override
                    protected void onWiFiConnected(String ssid) {
                        if(ssid != null && ssid.toLowerCase().contains("gopro")) {
                            unregisterReceiver(mWifiIntentReceiver);
                            mWifiIntentReceiver = null;
                            TestConnectionTask.run(MobileMainActivity.this,
                                    findPreference(getString(R.string.preference_wifi_test_key)));
                            if(mSetupDialog != null) mSetupDialog.dismiss();
                        }
                    }
                };
                registerReceiver(mWifiIntentReceiver, 
                        new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            }
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

    private static class SendLogsTask extends AsyncTask<String, Void, String> {

        private Context mContext;

        public SendLogsTask(Context context) {
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {
            GoProController controller = GoProController.getDefaultInstance(params[0]);
            GoProController.CameraInfo cameraInfo = controller.getCameraInfo();

            StringBuilder sb = new StringBuilder();
            sb.append("Camera Info:\n");
            if(cameraInfo != null) cameraInfo.toString(sb);
            else sb.append("Failed to get camera info.\n");

            // Log the status data of the camera.
            controller.logCommandAndResponse = true;
            controller.getRawStatus();
            controller.logCommandAndResponse = false;

            sb.append("\nLog begins -----------------------------------------------------\n");
            File logsFolder = Logger.getLogFolder();
            File[] logFiles = logsFolder.listFiles();

            if(logFiles != null && logFiles.length > 0) {
                Arrays.sort(logFiles, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                    }
                });
                try {
                    BufferedReader br = new BufferedReader(new FileReader(logFiles[0]));
                    String line;
                    while((line = br.readLine()) != null) {
                        sb.append(line);sb.append(System.lineSeparator());
                    }
                } catch (IOException e) {
                    sb.append("Failed to read last log: ");sb.append(e);sb.append(System.lineSeparator());
                }
            } else {
                sb.append("No logs found.\n");
            }
            sb.append("\nLog ends -----------------------------------------------------\n");

            return sb.toString();
        }

        @Override
        protected void onPostExecute(String message) {
            Intent email = new Intent(Intent.ACTION_SEND);
            email.putExtra(Intent.EXTRA_EMAIL, new String[]{ mContext.getString(R.string.email_support) });
            email.putExtra(Intent.EXTRA_SUBJECT, "Wear GoPro Remote Logs");
            email.putExtra(Intent.EXTRA_TEXT, message);
            email.setType("message/rfc822");
            mContext.startActivity(Intent.createChooser(email, "Choose an Email client :"));
        }
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

                if(pass == null) pass = "";
                if(ssid == null) ssid = "";

                pref.setSummary(activity.getString(R.string.status_connection));

                activity.mGoProController = GoProController.getDefaultInstance(pass);

                instance.mActivity = activity;
                activity.mWifiNamePref.setEnabled(false);
                activity.mWifiPassPref.setEnabled(false);
                instance.execute(pref, ssid, pass);
            }
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                preference = (Preference) params[0];

                mWifiSSID = params[1].toString();
                mWifiPass = params[2].toString();

                WifiHelper.turnWifiOn(mActivity, 1000);
                
                if (TextUtils.isEmpty(mWifiSSID)) {
                    mWifiSSID = WifiHelper.getCurrentWifiName(mActivity);
                    mNeedUpdateWifiConfig = true;
                }

                if (TextUtils.isEmpty(mWifiPass)) {
                    mWifiPass = mActivity.mGoProController.getPassword();
                    if(!TextUtils.isEmpty(mWifiPass)) {
                        mWifiSSID = WifiHelper.getCurrentWifiName(mActivity);
                    }
                    mNeedUpdateWifiConfig = mNeedUpdateWifiConfig || !TextUtils.isEmpty(mWifiPass);
                    mActivity.mGoProController = GoProController.getDefaultInstance(mWifiPass);
                }

                boolean success = WifiHelper.connectToWifi(mActivity, mWifiSSID, mWifiPass);

                if (!success) {
                    return false;
                }

                mActivity.mGoProController.logCommandAndResponse = true;
                GoProStatus status;
                int c = 0;
                while ((status = mActivity.mGoProController.getStatus()).CameraMode == GoProStatus.UNKNOW) {
                    Utils.sleep(1000);
                    if (++c > 3) {
                        break;
                    }
                }

                if (status.CameraMode == GoProStatus.CAMERA_MODE_OFF_WIFION) {
                    if (mActivity.mGoProController.turnOn()) {
                        Utils.sleep(5000);
                        status = mActivity.mGoProController.getStatus();
                    }
                }

                success = status.CameraMode > GoProStatus.UNKNOW;
                return success;
            } catch (Exception e) {
                Logger.error(TAG, "Test Connection failed", e);
                return false;
            }
        }

        @Override
        protected  void onCancelled() {
            super.onCancelled();
            instance = null;
            mActivity.mWifiNamePref.setEnabled(true);
            mActivity.mWifiPassPref.setEnabled(true);
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
                mActivity.showSetupDialogIfNeeded();
                preference.setSummary(mActivity.getString(R.string.status_not_connected));
                //mActivity.mGoProController = null;
            }

            if(mNeedUpdateWifiConfig) {
                mActivity.setWiFiConfig(mWifiSSID, mWifiPass);
                
                if(PreferenceManager
                        .getDefaultSharedPreferences(mActivity)
                        .getBoolean(mActivity.getString(R.string.preference_wifi_autostart_key), false))
                {
                    Intent i = new Intent(mActivity, WearMessageHandlerService.class);
                    i.setAction(WearMessageHandlerService.ACTION_SEND_MESSAGE_TO_WEAR);
                    i.putExtra(WearMessageHandlerService.EXTRA_MESSAGE, WearMessages.MESSAGE_LAUNCH_ACTIVITY);
                    mActivity.startService(i);
                }
                
                if(success) {
                    Toast.makeText(mActivity, R.string.toast_wifi_configured, Toast.LENGTH_LONG).show();
                }
            }

            mActivity.mGoProController.logCommandAndResponse = false;
            mActivity.mWifiNamePref.setEnabled(true);
            mActivity.mWifiPassPref.setEnabled(true);
        }
    }
}
