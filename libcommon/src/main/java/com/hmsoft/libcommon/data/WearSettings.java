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

package com.hmsoft.libcommon.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.hmsoft.libcommon.R;

/***
 * Represents watch settings that are set on the phone.
 */
public class WearSettings {

    public static final int RAW_DATA_LEN = 2;

    public final byte[] rawData;

    public WearSettings() {
        rawData = new byte[RAW_DATA_LEN];
    }

    public WearSettings(byte[] rawData) {
        this.rawData = new byte[RAW_DATA_LEN];
        for(int c = 0; c < rawData.length && c < RAW_DATA_LEN; c++) {
            this.rawData[c] = rawData[c];
        }
    }

    public boolean getShakeEnabled() {
        return rawData[0] != 0;
    }

    public byte getShakeLevel() {
        return rawData[0];
    }

    public byte getShakeCameraMode() {
        return rawData[1];
    }

    public void loadFromPreferences(Context context, SharedPreferences prefs) {
        byte level = Byte.parseByte(prefs.getString(context.getString(R.string.preference_watch_shake_level_key), "3"));
        byte mode = Byte.parseByte(prefs.getString(context.getString(R.string.preference_watch_shake_shutter_mode_key), "1"));

        rawData[0] = level;
        rawData[1] = mode;
    }

    public void saveToPreferences(Context context, SharedPreferences prefs) {
        prefs.edit()
                .putString(context.getString(R.string.preference_watch_shake_level_key), rawData[0] + "")
                .putString(context.getString(R.string.preference_watch_shake_shutter_mode_key), rawData[1] + "")
                .apply();
    }
}
