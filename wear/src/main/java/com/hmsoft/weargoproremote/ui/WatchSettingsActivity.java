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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WearableListView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.libcommon.gopro.GoProStatus;
import com.hmsoft.weargoproremote.R;
import com.hmsoft.weargoproremote.WearApplication;

public class WatchSettingsActivity extends Activity
        implements WearableListView.ClickListener {

    private static final String TAG = "WatchSettingsActivity";

    GoProStatus mLastStatus;
    int mCurrentSettingIndex;
    WearableListView mListView;
    RelativeLayout mLoadingPanel;

    static final int SETTING_SPOT_METER = 0;
    static final int SETTING_BEEP_VOLUME = 1;
    static final int SETTING_UPSIDE_DOWN = 2;
    static final int SETTING_LEDS = 3;
    static final int SETTING_LOCATE = 4;
    //static final int SETTING_OSD = 4;
    static final int SETTING_TURN_OFF = 5;

    private IntentFilter mIntentFilter = new IntentFilter(WearApplication.ACTION_STATUS_UPDATED);
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WearApplication.ACTION_STATUS_UPDATED.equals(action)) {
                mLastStatus = new GoProStatus(GoProStatus.LastCameraStatus);
                if(mLastStatus.CameraMode == GoProStatus.CAMERA_MODE_OFF_WIFION) {
                    finish();
                } else {
                    mListView.setAdapter(null);
                    mListView.setAdapter(new SettingsAdapter(WatchSettingsActivity.this));
                    mListView.scrollToPosition(mCurrentSettingIndex);
                    mLoadingPanel.setVisibility(View.GONE);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_settings);
        mListView = (WearableListView) findViewById(R.id.list);
        mLoadingPanel = (RelativeLayout)findViewById(R.id.loadingPanel);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        mListView.setAdapter(new SettingsAdapter(this));
        mListView.setClickListener(this);
     }

    @Override
    protected void onDestroy() {
        mListView.setAdapter(null);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mLastStatus = new GoProStatus(GoProStatus.LastCameraStatus);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        super.onResume();
    }

    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {

        mCurrentSettingIndex = (Integer)viewHolder.itemView.getTag();
        byte[] data = new byte[1];
        String message = "";

        switch (mCurrentSettingIndex) {
            case SETTING_SPOT_METER:
                data[0] = (byte)(mLastStatus.SpotMeter ? 0 : 1);
                message = WearMessages.MESSAGE_SET_SPOTMETTER;
                break;
            case SETTING_BEEP_VOLUME:
                data[0] = -1;
                switch (mLastStatus.CurrentBeepVolume) {
                    case GoProStatus.VOLUME_100:
                        data[0] = GoProStatus.VOLUME_70;
                        break;
                    case GoProStatus.VOLUME_70:
                        data[0] = GoProStatus.VOLUME_OFF;
                        break;
                    case GoProStatus.VOLUME_OFF:
                        data[0] = GoProStatus.VOLUME_100;
                        break;
                }
                if(data[0] > -1) {
                    message = WearMessages.MESSAGE_SET_BEEP_VOLUME;
                }
                break;
            case SETTING_UPSIDE_DOWN:
                data[0] = (byte)(mLastStatus.IsUpsideDown ? 0 : 1);
                message = WearMessages.MESSAGE_SET_UPSIDEDOWN;
                break;
            case SETTING_LEDS:
                data[0] = -1;
                switch (mLastStatus.Leds) {
                    case GoProStatus.LEDS_4:
                        data[0] = GoProStatus.LEDS_2;
                        break;
                    case GoProStatus.LEDS_2:
                        data[0] = GoProStatus.LEDS_OFF;
                        break;
                    case GoProStatus.LEDS_OFF:
                        data[0] = GoProStatus.LEDS_4;
                        break;
                }
                if(data[0] > -1) {
                    message = WearMessages.MESSAGE_SET_LEDS;
                }
                break;
            /*case SETTING_OSD:
                data[0] = (byte)(mLastStatus.IsOsdOn ? 0 : 1);
                message = WearMessages.MESSAGE_SET_OSD;
                break;*/
            case SETTING_LOCATE:
                data[0] = (byte)(mLastStatus.IsBeeping ? 0 : 1);
                message = WearMessages.MESSAGE_SET_BEEPING;
                break;
            case SETTING_TURN_OFF:
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(which == DialogInterface.BUTTON_POSITIVE) {
                            sendWearMessage(WearMessages.MESSAGE_POWEROFF, null);
                        }
                    }
                };

                Drawable icon = getResources()
                        .getDrawable(R.drawable.icon_power).mutate();

                icon.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY );

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder
                        .setTitle(R.string.setting_poweroff)
                        .setMessage(R.string.prompt_are_you_sure)
                        .setIcon(icon)
                        .setInverseBackgroundForced(true)
                        .setPositiveButton(R.string.label_yes, dialogClickListener)
                        .setNegativeButton(R.string.label_no, dialogClickListener).show();
                break;
        }

        if(!TextUtils.isEmpty(message)) {
            sendWearMessage(message, data);
        }
    }

    @Override
    public void onTopEmptyRegionClick() {

    }


    String getSettingValue(int setting) {
        switch (setting) {
            case SETTING_SPOT_METER:
                return mLastStatus.SpotMeter ? getString(R.string.label_yes) : getString(R.string.label_no);
            case SETTING_BEEP_VOLUME:
                switch (mLastStatus.CurrentBeepVolume) {
                    case GoProStatus.VOLUME_100:
                        return "100%";
                    case GoProStatus.VOLUME_70:
                        return "70%";
                    case GoProStatus.VOLUME_OFF:
                        return getString(R.string.label_off);
                }
            case SETTING_UPSIDE_DOWN:
                return mLastStatus.IsUpsideDown ? getString(R.string.label_yes) : getString(R.string.label_no);
            case SETTING_LOCATE:
                return  mLastStatus.IsBeeping ? getString(R.string.label_yes) : getString(R.string.label_no);
            case SETTING_LEDS:
                switch (mLastStatus.Leds) {
                    case GoProStatus.LEDS_2:
                        return "2";
                    case GoProStatus.LEDS_4:
                        return "4";
                    case GoProStatus.LEDS_OFF:
                        return  getString(R.string.label_off);
                }
            /*case SETTING_OSD:
                return mLastStatus.IsOsdOn ? "Yes" : "No";*/

        }
        return "";
    }

    void sendWearMessage(String path, byte[] data) {
        mLoadingPanel.setVisibility(View.VISIBLE);
        WearApplication app = (WearApplication)getApplication();
        if(app != null) {
            if(app.MessageSender != null) {
                app.MessageSender.sendWearMessage(path, data, new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if(!sendMessageResult.getStatus().isSuccess()) {
                            mLoadingPanel.setVisibility(View.GONE);
                        }
                    }
                });
                return;
            } else {
                Logger.error(TAG, "WearApplication.MessageSender is null.");
            }
        } else {
            Logger.error(TAG, "Application is not WearApplication");
        }
        mLoadingPanel.setVisibility(View.GONE);
    }

    private static final class SettingsAdapter extends WearableListView.Adapter {

        private WatchSettingsActivity mActivity;
        private final LayoutInflater mInflater;

        private SettingsAdapter(WatchSettingsActivity activity) {
            mActivity = activity;
            mInflater = LayoutInflater.from(activity);
        }

        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new WearableListView.ViewHolder(
                    mInflater.inflate(R.layout.selection_list_item, null));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int i) {
            /*
            Spot Meter -> on/off
            Beep volume -> 100/70/off
            Camera Upside Down - yes/no
            Leds -> 4/2/off
            OSD -> on/off
            Turn off -> Confirmation -> finish activity
            */

            TextView view = (TextView) holder.itemView.findViewById(R.id.name);
            ImageView imageView = (ImageView) holder.itemView.findViewById(R.id.circle);
            imageView.setImageResource(R.drawable.icon_setting_up);
            holder.itemView.setTag(i);
            String value = mActivity.getSettingValue(i);
            switch (i) {
                case SETTING_SPOT_METER:
                    view.setText(mActivity.getString(R.string.setting_spot_metter, value));
                    break;
                case SETTING_BEEP_VOLUME:
                    view.setText(mActivity.getString(R.string.setting_beep, value));
                    break;
                case SETTING_UPSIDE_DOWN:
                    view.setText(mActivity.getString(R.string.setting_upside, value));
                    break;
                case SETTING_LEDS:
                    view.setText(mActivity.getString(R.string.setting_leds, value));
                    break;
                /*case SETTING_OSD:
                    view.setText(mActivity.getString(R.string.setting_osd, value));
                    break;*/
                case SETTING_LOCATE:
                    view.setText(mActivity.getString(R.string.setting_locate, value));
                    break;
                case SETTING_TURN_OFF:
                    view.setText(R.string.setting_poweroff);
                    imageView.setImageResource(R.drawable.icon_power);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return 6;
        }
    }
}
