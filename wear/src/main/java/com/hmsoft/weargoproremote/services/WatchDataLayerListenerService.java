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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.data.WearSettings;
import com.hmsoft.libcommon.general.Utils;
import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.ui.WatchMainActivity;

public class WatchDataLayerListenerService extends WearableListenerService  {

    private static final String TAG = "WearCameraListenerService";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(BuildConfig.DEBUG) Logger.debug(TAG, "onMessageReceived: " + messageEvent.getPath());
        handleMessage(messageEvent.getPath(), messageEvent.getData());
    }

    private void handleMessage(String message, byte[] data) {

        switch (message) {
            case WearMessages.MESSAGE_SET_WEAR_SETTINGS:
                new WearSettings(data).saveToPreferences(this, PreferenceManager.getDefaultSharedPreferences(this));
                break;

            case WearMessages.MESSAGE_LAUNCH_ACTIVITY:
                Intent startIntent = new Intent(this, WatchMainActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startIntent);
                break;
        }
    }

    public static void disable(Context context) {
        Utils.disableComponent(context, WatchDataLayerListenerService.class);
    }

    public static void enable(Context context) {
        Utils.enableComponent(context, WatchDataLayerListenerService.class);
    }
}