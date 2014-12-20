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

package com.hmsoft.weargoproremote.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;

import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.R;

public class WifiIntentReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiIntentReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if(BuildConfig.DEBUG) Logger.debug(TAG, "onReceive:" + action);

        if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION .equals(action)) {
            SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
            if(state == SupplicantState.COMPLETED) {
                final WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
                if(wifiManager != null) {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    if (info != null) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        String ssid = prefs.getString(context.getString(R.string.preference_wifi_name_key), "");
                        if (("\"" + ssid  + "\"").equals(info.getSSID())) {
                            Intent i = new Intent(context, WearMessageHandlerService.class);
                            i.setAction(WearMessageHandlerService.ACTION_SEND_MESSAGE_TO_WEAR);
                            i.putExtra(WearMessageHandlerService.EXTRA_MESSAGE,
                                    WearMessages.MESSAGE_LAUNCH_ACTIVITY);
                            context.startService(i);
                        }
                    }
                }
            }
        }
    }
}