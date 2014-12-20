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

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat.Builder;
import android.text.TextUtils;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.libcommon.general.Timer;
import com.hmsoft.libcommon.general.Utils;
import com.hmsoft.libcommon.gopro.GoProController;
import com.hmsoft.libcommon.gopro.GoProStatus;
import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.R;
import com.hmsoft.weargoproremote.helpers.WifiHelper;
import com.hmsoft.weargoproremote.ui.MobileMainActivity;

import java.io.File;

public class WearMessageHandlerService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener, Handler.Callback {

    private static final String TAG = "WearMessageHandlerService";
    private static final int MESSAGE_INTENT = 1;

    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".ACTION_STOP";
    public static final String ACTION_HANDLE_MESSAGE_FROM_WEAR = BuildConfig.APPLICATION_ID + ".HANDLE_MESSAGE_FROM_WEAR";
    public static final String ACTION_SEND_MESSAGE_TO_WEAR = BuildConfig.APPLICATION_ID + ".SEND_MESSAGE_TO_WEAR";
    //public static final String ACTION_LONG_TASK = BuildConfig.APPLICATION_ID + ".LONG_TASK";
    //public static final String EXTRA_TASK = BuildConfig.APPLICATION_ID + ".EXTRA_TASK";
    public static final String EXTRA_MESSAGE = BuildConfig.APPLICATION_ID + ".EXTRA_MESSAGE";
    public static final String EXTRA_DATA = BuildConfig.APPLICATION_ID + ".EXTRA_DATA";
    public static final String EXTRA_DONT_SEND_STOP_TO_WEAR = BuildConfig.APPLICATION_ID + ".DONT_SEND_STOP_TO_WEAR";

    private static volatile Integer sCurrentWiFiNetworkId = null;

    private Builder mNotificationBuilder;
    private volatile boolean mPreviewEnabled;
    private volatile GoProController mGoProController;
    private volatile GoogleApiClient mGoogleApiClient;
    private volatile Node mWearableNode = null;
    private volatile String mPass;
    private volatile SharedPreferences mPrefs;

    private volatile Looper mExecutorLooper;
    private volatile Handler mWorkHandler;
    //private volatile Looper mLongTaskLooper;
    //private volatile Handler mLongTaskHandler;

    private Timer mSendStatusTimer;
    private String mCameraName;
    volatile boolean mStopped;
    private File mThumbsCacheDirectoryFile;

    @Override
    public void onCreate() {
        super.onCreate();
        if(Logger.DEBUG) Logger.debug(TAG, "Service created");

        HandlerThread executorThread = new HandlerThread("WearMessageHandlerService");
        executorThread.start();
        mExecutorLooper = executorThread.getLooper();
        mWorkHandler = new Handler(mExecutorLooper, this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPass = mPrefs.getString(getString(R.string.preference_wifi_password_key), "");
        mPreviewEnabled = mPrefs.getBoolean(getString(R.string.preference_watch_preview_enabled_key), true);
        mGoProController = GoProController.getDefaultInstance(mPass);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mSendStatusTimer = new Timer(60000, mWorkHandler, new Timer.TimerTask() {
            @Override
            public void onTick(int ticks) {
                sendCameraStatus();
                if(BuildConfig.DEBUG) Logger.debug(TAG, "Status sent...");
            }
        });
        mSendStatusTimer.start();

        updateNotification(getString(R.string.status_connection_starting));
    }

    @Override
    public void onDestroy() {
        mSendStatusTimer.stop();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mExecutorLooper.quit();
        disconnectGoogleApiClient();
        stopForeground(true);
        GoProController.clear();
        if(Logger.DEBUG) Logger.debug(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind (Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null) {
            final String action = intent.getAction();

            if(ACTION_STOP.equals(action)) {
                mStopped = true;
                mWorkHandler.removeCallbacksAndMessages(null);
                mSendStatusTimer.stop();
                updateNotification(getString(R.string.status_connection_disconnecting));
            }

            Message msg = mWorkHandler.obtainMessage(MESSAGE_INTENT, intent);
            mWorkHandler.sendMessage(msg);
        }
        return START_STICKY;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {        
        if(getString(R.string.preference_wifi_password_key).equals(key)) {
            mPass = sharedPreferences.getString(key, "");
            mGoProController = GoProController.getDefaultInstance(mPass);
        } else {
            mPreviewEnabled = mPrefs.getBoolean(getString(R.string.preference_watch_preview_enabled_key), true);
        }
    }

    /*private void longTaskMessage(int task) {
        if(mLongTaskLooper == null) {
            HandlerThread longTaskThread = new HandlerThread("longTaskThread");
            longTaskThread.start();
            mLongTaskLooper = longTaskThread.getLooper();
            mLongTaskHandler = new Handler(mLongTaskLooper, this);
        }
        Intent i = new Intent(ACTION_LONG_TASK);
        i.putExtra(EXTRA_TASK, task);
        
        Message msg = mLongTaskHandler.obtainMessage(MESSAGE_INTENT, i);
        mLongTaskHandler.sendMessage(msg);
    }*/

    private File getThumbsCacheDirectoryFile() {
        if(mThumbsCacheDirectoryFile == null) {
            mThumbsCacheDirectoryFile = new File(getExternalCacheDir(), "thumbs");
        }
        return mThumbsCacheDirectoryFile;
    }

    void updateNotification(String contentText) {
        if(mNotificationBuilder == null) {
            Context context = getApplicationContext();
            Intent activityIntent = new Intent(context, MobileMainActivity.class);
            activityIntent.setAction(Intent.ACTION_MAIN);
            activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent stopIntent = new Intent(context, WearMessageHandlerService.class);
            stopIntent.setAction(ACTION_STOP);

            mNotificationBuilder = (new Builder(this))
                    .setSmallIcon(R.drawable.icon_notification)
                    .setContentTitle(getString(R.string.notification_tittle))
                    .setContentIntent(PendingIntent.getActivity(context, 0, activityIntent, 0))
                    .setLocalOnly(true)
                    .addAction(R.drawable.icon_power, getString(R.string.action_stop),
                            PendingIntent.getService(context, 0, stopIntent, 0));
        }

        mNotificationBuilder.setContentText(contentText);
        startForeground(1, mNotificationBuilder.build());
    }

    private boolean connectGoogleApiClient() {
        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();
        }

        if(!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.blockingConnect();
        }

        return mGoogleApiClient.isConnected();
    }

    private void disconnectGoogleApiClient() {
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
            if(Logger.DEBUG) Logger.debug(TAG, "GAPI client disconnected.");
        }
    }

    private boolean findWearableNode() {
        if(mGoogleApiClient != null && mWearableNode == null) {
            NodeApi.GetConnectedNodesResult result = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
            if (result.getNodes().size() > 0) {
                mWearableNode = result.getNodes().get(0);
                if (BuildConfig.DEBUG) {
                    Logger.debug(TAG, "Found wearable: name=" + mWearableNode.getDisplayName() +
                            ", id=" + mWearableNode.getId());
                }
            } else {
                mWearableNode = null;
            }
        }
        return mWearableNode != null;
    }

    private void sendToWearable(String path, String param, byte[] data) {
        sendToWearable(WearMessages.addParamToMessage(path, param), data);
    }

    private void sendToWearable(String path, byte[] data) {

        if(!connectGoogleApiClient()) {
            Logger.error(TAG, "Failed to connect GoogleApiClient");
            return;
        }

        if(!findWearableNode()) {
            Logger.error(TAG, "Failed to find wearable node");
            return;
        }

        if(BuildConfig.DEBUG) Logger.debug(TAG, "Send to wearable:" + path);

        Wearable.MessageApi.sendMessage(mGoogleApiClient, mWearableNode.getId(),
                    path, data).await();
    }

    void sendCameraStatus() {
        sendCameraStatus(0);
    }

    private void sendCameraStatus(long time2Wait) {
        if(time2Wait > 0) Utils.sleep(time2Wait);

        byte[] status = mGoProController.getRawStatus();        
        sendToWearable(WearMessages.MESSAGE_CAMERA_STATUS, status);
        byte mode = status != null && status.length >  GoProStatus.Fields.CAMERA_MODE_FIELD ?
                status[GoProStatus.Fields.CAMERA_MODE_FIELD] : GoProStatus.UNKNOW;

        String text;
        if(mode == GoProStatus.UNKNOW) {
            text = getString(R.string.status_connection_failed);
        } else if(mode == GoProStatus.CAMERA_MODE_OFF_WIFION) {
            text = getString(R.string.status_connection_camera_off);
        } else {            
            if(TextUtils.isEmpty(mCameraName)) {
                text = getString(R.string.status_connection_connected1);
            } else {
                text = getString(R.string.status_connection_connected, mCameraName);
            }            
        }
        updateNotification(text);
    }

    private byte setCameraMode(byte newMode) {
        byte cameraMode = GoProStatus.UNKNOW;
        byte[] status = mGoProController.getLastStatus();
        if (status != null && status.length > GoProStatus.Fields.CAMERA_MODE_FIELD) {
            cameraMode = status[GoProStatus.Fields.CAMERA_MODE_FIELD];
        }
        if (cameraMode <= GoProStatus.UNKNOW) {
            return GoProStatus.UNKNOW;
        }
        if (newMode < 0 || cameraMode == newMode) {
            return cameraMode;
        }
        if (mGoProController.setCameraMode(newMode)) {
            Utils.sleep(750);
            return newMode;
        }
        return GoProStatus.UNKNOW;
    }

    private boolean sendThumbsToWatch(int start, int end, boolean forceSync) {
        if(forceSync) {
            mGoProController.refreshMediaCachedList();
        }

        File cacheDir = getThumbsCacheDirectoryFile();
        mGoProController.setThumbnailCacheDirectory(cacheDir);
        boolean send = false;
        for(int c = start; c <= end; c++) {
            String fileName = mGoProController.getMediaFileNameAtReverseIndex(c);
            if(!TextUtils.isEmpty(fileName) && (forceSync || !(new File(cacheDir, fileName)).exists())) {
                byte[] thumb =  mGoProController.getImgThumbnail(fileName);
                if(thumb != null) {
                    String name = GoProController.Thumbnail.getWearName(fileName);
                    sendToWearable(WearMessages.MESSAGE_SYNC_THUMB, name, thumb);
                    send = true;
                    if (Logger.DEBUG) Logger.debug(TAG, "Synced thumb: %s", fileName);
                }
            }
        }
        return send;
    }

    @Override
    public boolean handleMessage(Message msg) {
        Intent intent = (Intent)msg.obj;

        final String action = intent.getAction();
        final String message = intent.getStringExtra(EXTRA_MESSAGE);
        final byte[] data = intent.getByteArrayExtra(EXTRA_DATA);

        long startTime = System.currentTimeMillis();

        switch (action) {
            case ACTION_HANDLE_MESSAGE_FROM_WEAR:
                if (!mStopped) handleMessage(message, data);
                break;
            case ACTION_SEND_MESSAGE_TO_WEAR:
                sendToWearable(message, data);
                break;
            case ACTION_STOP:
                boolean sendStopMsg = !intent.getBooleanExtra(EXTRA_DONT_SEND_STOP_TO_WEAR, false);
                if (sendStopMsg) sendToWearable(WearMessages.MESSAGE_STOP, null);
                handleMessage(WearMessages.MESSAGE_DISCONNECT, null);
                break;
        }

        if(BuildConfig.DEBUG) {
            long time = System.currentTimeMillis() - startTime;
            Logger.debug(TAG, "Message %s handled in %ds (%d)", message, time / 1000, time);
        }

        return true;
    }

    /*private void handleLongTask(int task) {
    }*/

    private void handleMessage(String message, byte[] data) {

        long waitTime = 0;
        byte param = WearMessages.PARAM_UNKNOW;
        if(data != null && data.length > 0) {
            param = data[0];
        }

        switch (message) {
            case WearMessages.MESSAGE_CONNECT:
                updateNotification(getString(R.string.status_connection_connecting));
                String ssid = mPrefs.getString(getString(R.string.preference_wifi_name_key), "");
                if(sCurrentWiFiNetworkId == null) {
                    sCurrentWiFiNetworkId = WifiHelper.getCurrentWiFiNetworkId(getApplicationContext());
                }
                WifiHelper.connectToWifi(getApplicationContext(), ssid, mPass);

                if(GoProStatus.LastCameraStatus != null &&
                        GoProStatus.LastCameraStatus.length == GoProStatus.RAW_STATUS_LEN &&
                        GoProStatus.LastCameraStatus[GoProStatus.Fields.CAMERA_MODE_FIELD] == GoProStatus.CAMERA_MODE_OFF_WIFION) {
                    if(mGoProController.turnOn()) {
                        Utils.sleep(5000);
                    }
                }
                byte[] name = mGoProController.getRawCameraName();
                if(name != null) {
                    mCameraName = GoProStatus.getCameraName(new String(name));
                    sendToWearable(WearMessages.MESSAGE_CAMERA_NAME, name);
                }

                sendCameraStatus();

                if(name != null) {
                    sendThumbsToWatch(1, 50, false);
                }
                break;

            case WearMessages.MESSAGE_DISCONNECT:
                updateNotification(getString(R.string.status_connection_disconnecting));
                if(sCurrentWiFiNetworkId != null) {
                    boolean restoreNet =  mPrefs.getBoolean(getString(R.string.preference_wifi_restore_network_key), true);
                    if(restoreNet) {
                        WifiHelper.connectToWifi(getApplicationContext(), sCurrentWiFiNetworkId);
                    }
                    sCurrentWiFiNetworkId = null;
                }
                stopForeground(true);
                stopSelf();
                break;

            case WearMessages.MESSAGE_SHUTTER:
                byte mode = setCameraMode(param);
                if (mode != GoProStatus.UNKNOW) {
                    boolean isRecording = false;
                    byte[] status = mGoProController.getLastStatus();
                    if (status != null && status.length > GoProStatus.Fields.IS_RECORDING_FIELD) {
                        isRecording = status[GoProStatus.Fields.IS_RECORDING_FIELD] != 0;
                    }
                    if (mGoProController.shutter(!isRecording)) {
                        waitTime = isRecording ? 1550 : 1250;
                        if (mPreviewEnabled && (mode == GoProStatus.CAMERA_MODE_PHOTO || mode == GoProStatus.CAMERA_MODE_BURST ||
                                isRecording)) {
                            waitTime = 900;
                            if (mode == GoProStatus.CAMERA_MODE_BURST) {
                                waitTime = 1350;
                            }
                            Utils.sleep(waitTime);
                            mGoProController.setThumbnailCacheDirectory(getThumbsCacheDirectoryFile());
                            GoProController.Thumbnail thumb = mGoProController.getLastImgThumbnail();
                            if (thumb != null) {
                                String thumbName = thumb.getWearName();
                                sendToWearable(WearMessages.MESSAGE_LAST_IMG_THUMB, thumbName,
                                        thumb.BitmapData);
                            }
                            waitTime = 350;
                            sendThumbsToWatch(1, 10, false);
                        }
                    }
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_CAMERA_MODE:
                if (setCameraMode(param) != GoProStatus.UNKNOW) {
                    waitTime = 200;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_VIDEO_MODE:
                if (mGoProController.setVideoMode(param)) {
                    waitTime = 1100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_TIMELAPSE_INTERVAL:
                if (mGoProController.setTimelapseInterval(param)) {
                    waitTime = 1100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_CAMERA_STATUS:
                sendCameraStatus();
                break;

            case WearMessages.MESSAGE_SET_SPOTMETTER:
                if (mGoProController.setSpotMeter(param == 1)) {
                    waitTime = 100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_BEEP_VOLUME:
                if (mGoProController.setVolume(param)) {
                    waitTime = 100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_UPSIDEDOWN:
                if (mGoProController.setOrientationUpDown(param == 1)) {
                    waitTime = 100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_LEDS:
                if (mGoProController.setLeds(param)) {
                    waitTime = 100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_OSD:
                if (mGoProController.setOnScreenDisplay(param == 1)) {
                    waitTime = 100;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SET_BEEPING:
                if (mGoProController.locate(param == 1)) {
                    waitTime = 500;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_POWEROFF:
                if (mGoProController.turnOff()) {
                    waitTime = 500;
                }
                sendCameraStatus(waitTime);
                break;

            case WearMessages.MESSAGE_SYNC_THUMB:
                int start = 0;
                int end = 0;
                boolean forceSync = false;

                if(data != null) {
                    start = data[0];
                    end = data[1];
                    forceSync = data[2] == 1;
                }

                boolean send = sendThumbsToWatch(start, end, forceSync);
                if (send) {
                    sendToWearable(WearMessages.MESSAGE_SYNC_THUMB_FINISHED, null);
                }
                break;
        }
    }
}