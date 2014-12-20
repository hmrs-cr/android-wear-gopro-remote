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

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.weargoproremote.BuildConfig;

public class MobileDataLayerListenerService extends WearableListenerService  {

    private static final String TAG = "MobileDataLayerListenerService";
	
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(BuildConfig.DEBUG) Logger.debug(TAG, "Message received: %s", messageEvent.getPath());

        String path = messageEvent.getPath();
        Context context = getApplicationContext();
        Intent i = new Intent(context, WearMessageHandlerService.class);

        if(WearMessages.MESSAGE_DISCONNECT.equals(path)) {
            i.setAction(WearMessageHandlerService.ACTION_STOP);
            i.putExtra(WearMessageHandlerService.EXTRA_DONT_SEND_STOP_TO_WEAR, true);
        } else {
            i.setAction(WearMessageHandlerService.ACTION_HANDLE_MESSAGE_FROM_WEAR);
            i.putExtra(WearMessageHandlerService.EXTRA_MESSAGE, path);
            i.putExtra(WearMessageHandlerService.EXTRA_DATA, messageEvent.getData());
        }

        context.startService(i);
    }  	
}