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

package com.hmsoft.weargoproremote;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.hmsoft.libcommon.general.BaseApp;


public class WearApplication extends BaseApp {

    public static final String ACTION_STATUS_UPDATED = BuildConfig.APPLICATION_ID + ".action.STATUS_UPDATED";

    public WearMessageSender MessageSender;

    public interface WearMessageSender {
        public void sendWearMessage(String path, byte[] data,
                                final ResultCallback<MessageApi.SendMessageResult> callback);
    }

    public static void broadCastAction(Context context, String action) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(action));
    }
}
