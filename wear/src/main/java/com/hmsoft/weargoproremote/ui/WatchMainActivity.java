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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.data.WearSettings;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.libcommon.general.Timer;
import com.hmsoft.libcommon.gopro.GoProController;
import com.hmsoft.libcommon.gopro.GoProStatus;
import com.hmsoft.libcommon.sensors.ShakeDetectEventListener;
import com.hmsoft.weargoproremote.R;
import com.hmsoft.weargoproremote.WearApplication;
import com.hmsoft.weargoproremote.cache.ThumbCache;
import com.hmsoft.weargoproremote.services.WatchDataLayerListenerService;

import java.io.File;

public class WatchMainActivity extends Activity  implements GoogleApiClient.ConnectionCallbacks,
        MessageApi.MessageListener,
        ShakeDetectEventListener.ShakeDetectActivityListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        WearApplication.WearMessageSender
{
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_CAMERA_MODE = 1;
    private static final int REQUEST_CODE_VIDEO_MODE = 2;
    private static final int REQUEST_CODE_TIMELAPSE_INTERVAL = 3;

    private boolean mDontSendDisconnect = false;
    RelativeLayout mLayoutControls;
    FrameLayout mLayoutStatus;
    WatchViewStub mWatchViewStub;
    ImageButton mBtnCameraMode;
    ImageButton mBtnReconnect;
    Button mBtnVideoMode;
    TextView mTxtBatteryLevel;
    ImageView mImgBatteryLevel;
    Button mBtnShutter;
    TextView mTxtStatus;
    ShakeDetectEventListener mShakeDetectActivity;
    RelativeLayout mLoadingPanel;
    ProgressBar mProgressBarConnecting;

    boolean mRefreshUi;
    boolean mActivityActive;
    boolean mMustKillProcOnDestroy;
    String mCameraName;
    GoogleApiClient mGoogleApiClient;
    Node mPhoneNode = null;
    short mRecordingMinutes;
    byte mRecordingSeconds;
    boolean mNameShowed = false;
    WearSettings mSettings;
    boolean mRunningPreview;

    Timer mUpdateRecordingTimeTimer;
    Timer mReconnectTimer;
    volatile long mLastMessageToPhone;
    private volatile File mThumbsCacheDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();

        mGoogleApiClient.connect();
        setContentView(R.layout.activity_main);
        mWatchViewStub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        mWatchViewStub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mBtnCameraMode = (ImageButton)findViewById(R.id.btnCameraMode);
                mBtnReconnect = (ImageButton)findViewById(R.id.btnReconnect);
                mBtnVideoMode = (Button)findViewById(R.id.btnVideoMode);
                mTxtBatteryLevel = (TextView)findViewById(R.id.txtBatteryLevel);
                mImgBatteryLevel = (ImageView)findViewById(R.id.imgBatteryLevel);
                mBtnShutter = (Button)findViewById(R.id.btnShutter);
                mTxtStatus = (TextView)findViewById(R.id.txtStatus);
                mLayoutControls = (RelativeLayout)findViewById(R.id.layoutControls);
                mLayoutStatus = (FrameLayout)findViewById(R.id.layoutStatus);
                mLoadingPanel = (RelativeLayout)findViewById(R.id.loadingPanel);
                mProgressBarConnecting = (ProgressBar)findViewById(R.id.progressBarConnecting);
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSettings = new WearSettings();
        mSettings.loadFromPreferences(this, PreferenceManager.getDefaultSharedPreferences(this));

        mThumbsCacheDirectory = ThumbCache.getThumbCacheFolderFile(this);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        WatchDataLayerListenerService.disable(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityActive = true;
        mRunningPreview = false;
        if(mShakeDetectActivity != null) mShakeDetectActivity.startDetecting();

        if(mRefreshUi) {
            updateStatusUi(new GoProStatus(GoProStatus.LastCameraStatus));
        }

        WearApplication app = (WearApplication)getApplication();
        if(app != null) {
            app.MessageSender = this;
        }
    }

    @Override
    protected void onPause() {
        // Allow shake shutter on preview activity
        if(!mRunningPreview) {
            if (mShakeDetectActivity != null) mShakeDetectActivity.stopDetecting();
        }
        mActivityActive = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Context context = getApplicationContext();
        WatchDataLayerListenerService.enable(context);
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
        if(!mDontSendDisconnect) sendToPhone(WearMessages.MESSAGE_DISCONNECT, null, null, false);
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
        if(mUpdateRecordingTimeTimer != null) mUpdateRecordingTimeTimer.stop();
        if(mReconnectTimer != null) mReconnectTimer.stop();
        if(mShakeDetectActivity != null) mShakeDetectActivity.clear();
        GoProStatus.LastCameraStatus = null;
        ThumbCache.clear();
        WearApplication app = (WearApplication)getApplication();
        if(app != null) {
            app.MessageSender = null;
        }
        super.onDestroy();
        if(mMustKillProcOnDestroy) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    void showSpinner() {
        mLoadingPanel.setVisibility(View.VISIBLE);
        if (mShakeDetectActivity != null) mShakeDetectActivity.stopDetecting();
    }

    void hideSpinner() {
        mLoadingPanel.setVisibility(View.GONE);
        if(mShakeDetectActivity != null) mShakeDetectActivity.startDetecting();
    }

    private void initShakeDetector(GoProStatus status) {
        boolean shakeDetectorAllowed = status != null && status.CameraMode > GoProStatus.UNKNOW &&
                (!status.IsRecording || mSettings.getShakeCameraMode() == GoProStatus.CAMERA_MODE_VIDEO);

        if(shakeDetectorAllowed && mSettings.getShakeEnabled()) {
            if(mShakeDetectActivity == null) {
                mShakeDetectActivity = new ShakeDetectEventListener(this);
                mShakeDetectActivity.addListener(this);
                if(Logger.DEBUG) Logger.debug(TAG, "Shake detector created");
            }
            mShakeDetectActivity.setMinimumEachDirection(mSettings.getShakeLevel());
        } else {
            if(mShakeDetectActivity != null) {
                mShakeDetectActivity.stopDetecting();
                mShakeDetectActivity = null;
                if(Logger.DEBUG) Logger.debug(TAG, "Shake detector destroyed");
            }
        }
    }

    void findPhoneNode() {
        PendingResult<NodeApi.GetConnectedNodesResult> pending = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        pending.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if(result.getNodes().size()>0) {
                    mPhoneNode = result.getNodes().get(0);
                    if(Logger.DEBUG) Logger.debug(TAG, "Found wearable: name=" + mPhoneNode.getDisplayName() + ", id=" + mPhoneNode.getId());
                    sendConnectMessage();
                } else {
                    mPhoneNode = null;
                    updateStatusUi(null);
                }
            }
        });
    }

    private boolean sendConnectMessage() {
        return sendToPhone(WearMessages.MESSAGE_CONNECT, null, new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                if (!sendMessageResult.getStatus().isSuccess()) {
                    updateStatusUi(null);
                }
            }
        }, false);
    }

    private boolean sendToPhone(String path, byte[] data,
                             final ResultCallback<MessageApi.SendMessageResult> callback,
                             boolean showSpinner) {

        if (mPhoneNode != null) {
            long diff = System.currentTimeMillis() - mLastMessageToPhone;
            if(diff <= 1000) {
                if(Logger.DEBUG) Logger.debug(TAG, "Last message to phone was %d millis ago", diff);
                return false;
            }
            if(showSpinner) showSpinner();
            if(Logger.DEBUG) Logger.debug(TAG, "Message sent %s", path);
            if (mShakeDetectActivity != null) mShakeDetectActivity.stopDetecting();
            PendingResult<MessageApi.SendMessageResult> pending = Wearable.MessageApi.sendMessage(mGoogleApiClient, mPhoneNode.getId(), path, data);
            pending.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult result) {
                    if (callback != null) {
                        callback.onResult(result);
                    }
                    if (result.getStatus().isSuccess()) {
                        mLastMessageToPhone = System.currentTimeMillis();
                    } else {
                        setFailStatusMessage(R.string.label_watch_disconnected, R.drawable.ic_retry);
                        Logger.warning(TAG, "Failed to send Message: " + result.getStatus());
                    }
                }
            });

            return true;
        } else {
            Logger.error(TAG, String.format("Tried to send message (%s) before device was found.", path));
            return false;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        findPhoneNode();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(Logger.DEBUG) Logger.debug(TAG, "onMessageReceived: " + messageEvent.getPath());
        handleMessage(messageEvent.getPath(), messageEvent.getData());
    }   

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        String selected = data.getStringExtra(SelectionListActivity.EXTRA_SELECTED_ITEM);
        String message = null;
        if (requestCode == REQUEST_CODE_CAMERA_MODE) {
            if(String.valueOf(GoProStatus.CAMERA_MODE_SETTINGS).equals(selected)) {
                startActivity(new Intent(this, WatchSettingsActivity.class));
                return;
            }
            message = WearMessages.MESSAGE_SET_CAMERA_MODE;
        }  else  if(requestCode == REQUEST_CODE_VIDEO_MODE) {
            message = WearMessages.MESSAGE_SET_VIDEO_MODE;
        } else if(requestCode == REQUEST_CODE_TIMELAPSE_INTERVAL) {
            message = WearMessages.MESSAGE_SET_TIMELAPSE_INTERVAL;
        }

        if(message != null) {
            sendToPhone(message, new byte[]{Byte.parseByte(selected)}, null, true);
        }
    }

    @Override
    public void shakeDetected() {
        if(getCurrentStatusValue(GoProStatus.Fields.IS_RECORDING_FIELD) == 1 &&
                mSettings.getShakeCameraMode() != GoProStatus.CAMERA_MODE_VIDEO) {
            Logger.warning(TAG, "Shake detector disabled while recording but shake detected event triggered.");
        } else {
            sendToPhone(WearMessages.MESSAGE_SHUTTER, new byte[]{mSettings.getShakeCameraMode()},
                    null, true);
        }
    }

    private void handleMessage(String path, final byte[] data) {

        String message = WearMessages.getMessage(path);
        switch (message) {
            case WearMessages.MESSAGE_CAMERA_STATUS:
                GoProStatus.LastCameraStatus = data;
                WearApplication.broadCastAction(this, WearApplication.ACTION_STATUS_UPDATED);
                mRefreshUi = !mActivityActive;
                if (mActivityActive) {
                    final GoProStatus status = new GoProStatus(data);
                    mWatchViewStub.post(new Runnable() {
                        @Override
                        public void run() {
                            updateStatusUi(status);
                        }
                    });
                }
                break;

            case WearMessages.MESSAGE_LAST_IMG_THUMB:
                if (data != null && data.length > 0) {
                    final String thumbFileName = WearMessages.getMessageParam(path);
                    mWatchViewStub.post(new Runnable() {
                        @Override
                        public void run() {
                            mRunningPreview = true;
                            ImageViewerActivity.showImage(WatchMainActivity.this, thumbFileName, data);
                            hideSpinner();
                        }
                    });
                    GoProController.saveThumbnailToCache(mThumbsCacheDirectory,
                            thumbFileName, data);
                }
                break;

            case WearMessages.MESSAGE_CAMERA_NAME:
                if (data != null && data.length > 0) {
                    mCameraName = GoProStatus.getCameraName(new String(data));
                }
                break;

            case WearMessages.MESSAGE_SET_WEAR_SETTINGS:
                mSettings = new WearSettings(data);
                mSettings.saveToPreferences(this, PreferenceManager.getDefaultSharedPreferences(this));
                break;

            case WearMessages.MESSAGE_LAUNCH_ACTIVITY:
                Intent startIntent = new Intent(this, WatchMainActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                startActivity(startIntent);
                sendToPhone(WearMessages.MESSAGE_CAMERA_STATUS, null, null, false);
                break;

            case WearMessages.MESSAGE_STOP:
                mDontSendDisconnect = true;
                finish();
                if (!mActivityActive) mMustKillProcOnDestroy = true;
                break;

            case WearMessages.MESSAGE_SYNC_THUMB_FINISHED:
                if (mRunningPreview) {
                    ImageViewerActivity.refreshImageList(this);
                }
                break;

            case WearMessages.MESSAGE_SYNC_THUMB:
                final String thumbFileName = WearMessages.getMessageParam(path);
                GoProController.saveThumbnailToCache(mThumbsCacheDirectory, thumbFileName, data);
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSettings.loadFromPreferences(this, sharedPreferences);
        if(getString(com.hmsoft.libcommon.R.string.preference_watch_shake_level_key).equals(key)) {
            initShakeDetector(new GoProStatus(GoProStatus.LastCameraStatus));
        }
    }

    @Override
    public void sendWearMessage(String path, byte[] data, ResultCallback<MessageApi.SendMessageResult> callback) {
        sendToPhone(path, data, callback, false);
    }

    private byte getCurrentStatusValue(int field) {
        if(GoProStatus.LastCameraStatus != null && GoProStatus.LastCameraStatus.length > field) {
            return GoProStatus.LastCameraStatus[field];
        }
        return GoProStatus.UNKNOW;
    }

    void incRecordingTime() {
        byte mode = getCurrentStatusValue(GoProStatus.Fields.CAMERA_MODE_FIELD);
        if(mode == GoProStatus.CAMERA_MODE_VIDEO) {
            if (getCurrentStatusValue(GoProStatus.Fields.IS_RECORDING_FIELD) == 1) {
                if (++mRecordingSeconds == 60) {
                    mRecordingSeconds = 0;
                    mRecordingMinutes++;
                }
                mBtnShutter.setText(String.format("%02d:%02d", mRecordingMinutes, mRecordingSeconds));
            }
        } else if(mode == GoProStatus.CAMERA_MODE_TIMELAPSE) {
            sendToPhone(WearMessages.MESSAGE_CAMERA_STATUS, null, null, false);
        }
    }

    private String minutesToString(int minutes) {
        int hours = minutes / 60;
        int mins = minutes - hours * 60;
        return String.format("%02dH:%02d", hours, mins);
    }

    private void setFailStatusMessage(int messageId, int iconReloadBtnId) {
        mTxtStatus.setText(messageId);
        mLayoutStatus.setVisibility(View.VISIBLE);
        mBtnReconnect.setVisibility(View.VISIBLE);
        mBtnReconnect.setImageResource(iconReloadBtnId);
        mLayoutControls.setVisibility(View.GONE);
        mProgressBarConnecting.setVisibility(View.GONE);
        hideSpinner();

    }

    private void updateStatusUi(final GoProStatus status) {

        initShakeDetector(status);

        if(mPhoneNode == null) {
            if(Logger.DEBUG) Logger.debug(TAG, "Watch Offline");
            setFailStatusMessage(R.string.label_watch_disconnected, R.drawable.ic_retry);
            return;
        }

        if(status == null || status.CameraMode == GoProStatus.UNKNOW) {
            if(Logger.DEBUG) Logger.debug(TAG, "Camera Offline");
            setFailStatusMessage(R.string.label_camera_offline, R.drawable.ic_retry);
            reconnect();
            return;
        }

        if(status.CameraMode == GoProStatus.CAMERA_MODE_OFF_WIFION) {
            if(Logger.DEBUG) Logger.debug(TAG, "Camera off");
            setFailStatusMessage(R.string.label_camera_off, R.drawable.icon_power);
            return;
        }

        if(status.CameraMode == GoProStatus.CAMERA_MODE_NO_CONFIG) {
            if(Logger.DEBUG) Logger.debug(TAG, "No WiFi config");
            setFailStatusMessage(R.string.label_wifi_noconfig, R.drawable.ic_retry);
            hideSpinner();
            return;
        }

        if(!mNameShowed) {
            if(!TextUtils.isEmpty(mCameraName)) {
                Toast.makeText(this, mCameraName, Toast.LENGTH_LONG).show();
                mNameShowed = true;
            }
        }

        mLayoutStatus.setVisibility(View.GONE);
        mLayoutControls.setVisibility(View.VISIBLE);
        //mTxtStatus.setText(mCameraName);

        mBtnShutter.setText(String.format("%d\n\n[ %d ]", status.PhotoCount, status.PhotosAvailable));
        mTxtBatteryLevel.setText(status.BatteryLevel + "%");
        if(status.BatteryLevel <= 100 && status.BatteryLevel >= 75) {
            mImgBatteryLevel.setImageResource(R.drawable.icon_batt_03);
        } else if(status.BatteryLevel < 75 && status.BatteryLevel >= 40) {
            mImgBatteryLevel.setImageResource(R.drawable.icon_batt_02);
        } else if(status.BatteryLevel < 40 && status.BatteryLevel >= 10) {
            mImgBatteryLevel.setImageResource(R.drawable.icon_batt_01);
        } else {
            mImgBatteryLevel.setImageResource(R.drawable.icon_batt_00);
        }

        if(status.CameraMode == GoProStatus.CAMERA_MODE_VIDEO) {
            mBtnCameraMode.setImageResource(R.drawable.detail_mode_icon_video);

            String viewAngle = "";
            switch (status.ViewAngle) {
                case GoProStatus.FOV_WIDE:
                    viewAngle = getString(R.string.view_angle_wide);
                    break;
                case GoProStatus.FOV_MEDIUM:
                    viewAngle = getString(R.string.view_angle_medium);
                    break;
                case GoProStatus.FOV_NARROW:
                    viewAngle = getString(R.string.view_angle_narrow);
                    break;
            }

            String videoMode = "";
            switch (status.VideoMode) {
                case GoProStatus.VIDEO_MODE_1080_30:
                    videoMode = getString(R.string.video_mode_108030);
                    break;
                case GoProStatus.VIDEO_MODE_720_30:
                    videoMode = getString(R.string.video_mode_72030);
                    break;
                case GoProStatus.VIDEO_MODE_720_60:
                    videoMode = getString(R.string.video_72060);
                    break;
                case GoProStatus.VIDEO_MODE_960_30:
                    videoMode = getString(R.string.video_mode_96030);
                    break;
                case GoProStatus.VIDEO_MODE_VGA_60:
                    videoMode = getString(R.string.video_mode_vga60);
                    break;
            }

            mBtnVideoMode.setText(videoMode + "\n" + viewAngle);

            if(status.IsRecording) {
                mRecordingMinutes = status.RecordingMinutes;
                mRecordingSeconds = status.RecordingSeconds;
                mBtnShutter.setText(String.format("%02d:%02d",  mRecordingMinutes, mRecordingSeconds));
                createUpdateRecordingTimeTimerIfNeeded();
                mUpdateRecordingTimeTimer.setInterval(1000);
                mUpdateRecordingTimeTimer.start();
                Drawable icon = getResources().getDrawable(R.drawable.detail_mode_icon_video).mutate();
                icon.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY );
                mBtnCameraMode.setImageDrawable(icon);
            }  else {
                mBtnShutter.setText(String.format("%d\n\n[ %s ]", status.VideoCount,
                        minutesToString(status.MinutesVideoRemaining)));

                if(mUpdateRecordingTimeTimer != null) mUpdateRecordingTimeTimer.stop();
            }
        } else if(status.CameraMode == GoProStatus.CAMERA_MODE_PHOTO) {
            mBtnCameraMode.setImageResource(R.drawable.detail_mode_icon_photo);
            mBtnVideoMode.setText(getPhotoModeLabel(status.PhotoMode));
        } else if(status.CameraMode == GoProStatus.CAMERA_MODE_BURST) {
            mBtnCameraMode.setImageResource(R.drawable.detail_mode_icon_burst);
            mBtnVideoMode.setText(getPhotoModeLabel(status.PhotoMode));
        } else if(status.CameraMode == GoProStatus.CAMERA_MODE_TIMELAPSE) {
            mBtnCameraMode.setImageResource(R.drawable.detail_mode_icon_timelapse);

            long timerInterval = 1000;
            String interval;
            if(status.TimelapseInterval == 0) {
                interval = "0.5s";
            } else {
                timerInterval = status.TimelapseInterval * 1000 + 50;
                interval = status.TimelapseInterval + "s";
            }

            if(status.IsRecording) {
                createUpdateRecordingTimeTimerIfNeeded();
                mUpdateRecordingTimeTimer.setInterval(timerInterval);
                mUpdateRecordingTimeTimer.start();
                Drawable icon = getResources().getDrawable(R.drawable.detail_mode_icon_timelapse).mutate();
                icon.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY );
                mBtnCameraMode.setImageDrawable(icon);
            } else {
                if(mUpdateRecordingTimeTimer != null) mUpdateRecordingTimeTimer.stop();
            }


            mBtnVideoMode.setText(getPhotoModeLabel(status.PhotoMode) + "\n" + interval);
        }

        hideSpinner();
    }

    private void createUpdateRecordingTimeTimerIfNeeded() {
        if(mUpdateRecordingTimeTimer == null) {
            mUpdateRecordingTimeTimer = new Timer(1000, mLayoutControls.getHandler(),
                    new Timer.TimerTask() {
                        @Override
                        public void onTick(int ticks) {
                            incRecordingTime();
                        }
                    });
        }
    }

    private void reconnect() {
        if(mReconnectTimer == null) {
            mReconnectTimer = new Timer(15000, new Timer.TimerTask() {
                @Override
                public void onTick(int ticks) {
                    if(mActivityActive) {
                        mReconnectTimer.stop();
                        if (mLayoutStatus.getVisibility() != View.GONE) {
                            tryReconnect(mBtnReconnect);
                        }
                    }
                }
            });
        }
        mReconnectTimer.start();
    }

    private String getPhotoModeLabel(byte photoMode) {
        switch (photoMode) {
            case GoProStatus.PHOTO_MODE_11MPW:
                return getString(R.string.photo_mode_11mpwide);
            case GoProStatus.PHOTO_MODE_5MPM:
                return getString(R.string.photo_mode_5mpmedium);
            case GoProStatus.PHOTO_MODE_5MPW:
                return getString(R.string.photo_mode_5mpwide);
            case GoProStatus.PHOTO_MODE_8MPM:
                return getString(R.string.photo_mode_8mpmedium);
        }
        return "";
    }

    public void changeCameraMode(View view) {
        if(getCurrentStatusValue(GoProStatus.Fields.IS_RECORDING_FIELD) == 1) {
            return;
        }

        String[] items = new String[] {
                getString(R.string.action_mode_video) + "|" + GoProStatus.CAMERA_MODE_VIDEO + "|" + R.drawable.detail_mode_icon_video,
                getString(R.string.action_mode_photo) + "|" + GoProStatus.CAMERA_MODE_PHOTO + "|" + R.drawable.detail_mode_icon_photo,
                getString(R.string.action_mode_burst) + "|" + GoProStatus.CAMERA_MODE_BURST + "|" + R.drawable.detail_mode_icon_burst,
                getString(R.string.action_mode_timelapse) + "|" + GoProStatus.CAMERA_MODE_TIMELAPSE + "|"  + R.drawable.detail_mode_icon_timelapse,
                getString(R.string.action_mode_settings) + "|" + GoProStatus.CAMERA_MODE_SETTINGS + "|" + R.drawable.icon_setting_up
        };
        Intent i = new Intent(this, SelectionListActivity.class);
        i.putExtra(SelectionListActivity.EXTRA_ITEMS, items);
        i.putExtra(SelectionListActivity.EXTRA_SELECTED_ITEM, getCurrentStatusValue(GoProStatus.Fields.CAMERA_MODE_FIELD) + "");
        startActivityForResult(i, REQUEST_CODE_CAMERA_MODE);
    }

    public void changeVideoMode(View view) {
        if(getCurrentStatusValue(GoProStatus.Fields.IS_RECORDING_FIELD) == 1) {
            return;
        }

        byte cameraMode = getCurrentStatusValue(GoProStatus.Fields.CAMERA_MODE_FIELD);
        if(cameraMode == GoProStatus.CAMERA_MODE_VIDEO) {
            String[] items = new String[] {
                    getString(R.string.action_video_vga60) + "|" + GoProStatus.VIDEO_MODE_VGA_60 + "|" + R.drawable.detail_mode_icon_video,
                    getString(R.string.action_video_72030) + "|" + GoProStatus.VIDEO_MODE_720_30 + "|" + R.drawable.detail_mode_icon_video,
                    getString(R.string.action_video_72060) + "|" + GoProStatus.VIDEO_MODE_720_60 + "|" + R.drawable.detail_mode_icon_video,
                    getString(R.string.action_video_96030) + "|" + GoProStatus.VIDEO_MODE_960_30 + "|" + R.drawable.detail_mode_icon_video,
                    getString(R.string.action_video_108030) + "|" + GoProStatus.VIDEO_MODE_1080_30 + "|" + R.drawable.detail_mode_icon_video,
            };
            Intent i = new Intent(this, SelectionListActivity.class);
            i.putExtra(SelectionListActivity.EXTRA_ITEMS, items);
            i.putExtra(SelectionListActivity.EXTRA_SELECTED_ITEM, getCurrentStatusValue(GoProStatus.Fields.VIDEO_MODE_FIELD) + "");
            startActivityForResult(i, REQUEST_CODE_VIDEO_MODE);
        } else if(cameraMode == GoProStatus.CAMERA_MODE_TIMELAPSE) {
            String[] items = new String[] {
                    getString(R.string.action_interval_halfsecond) + "|" + GoProStatus.TIME_LAPSE_HALFSECOND + "|"  + R.drawable.detail_mode_icon_timelapse,
                    getString(R.string.action_interval_1second) + "|" + GoProStatus.TIME_LAPSE_1SECOND + "|" + R.drawable.detail_mode_icon_timelapse,
                    getString(R.string.action_interval_2second) + "|" + GoProStatus.TIME_LAPSE_2SECOND + "|" + R.drawable.detail_mode_icon_timelapse,
                    getString(R.string.action_interval_5second) + "|" + GoProStatus.TIME_LAPSE_5SECOND + "|" + R.drawable.detail_mode_icon_timelapse,
            };
            Intent i = new Intent(this, SelectionListActivity.class);
            i.putExtra(SelectionListActivity.EXTRA_ITEMS, items);
            i.putExtra(SelectionListActivity.EXTRA_SELECTED_ITEM, getCurrentStatusValue(GoProStatus.Fields.TIMELAPSE_INTERVAL_FIELD) + "");
            startActivityForResult(i, REQUEST_CODE_TIMELAPSE_INTERVAL);
        }
    }

    public void fireShutter(View view) {
        if(mUpdateRecordingTimeTimer != null) mUpdateRecordingTimeTimer.stop();
        sendToPhone(WearMessages.MESSAGE_SHUTTER, null, null, true);
    }

    public void tryReconnect(View view) {

        boolean displayProgress = true;

        if(mPhoneNode == null) {
            findPhoneNode();
        } else {
            displayProgress = sendConnectMessage();
        }

        if(displayProgress) {
            mTxtStatus.setText(getString(R.string.label_connecting));
            mProgressBarConnecting.setVisibility(View.VISIBLE);
            mBtnReconnect.setVisibility(View.GONE);
        }
    }

    public void showImageViewer(View view) {
        mRunningPreview = true;
        ImageViewerActivity.showImage(this, null, null);
    }
}