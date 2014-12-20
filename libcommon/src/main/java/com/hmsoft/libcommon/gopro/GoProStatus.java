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

package com.hmsoft.libcommon.gopro;

public class GoProStatus {

    public static final byte UNKNOW = -1;
    public static final byte OK = 1;

    public static final int RAW_STATUS_LEN = 31;

    public static final byte CAMERA_MODE_OFF_WIFION = -2;
    public static final byte CAMERA_MODE_VIDEO = 0;
    public static final byte CAMERA_MODE_PHOTO = 1;
    public static final byte CAMERA_MODE_BURST = 2;
    public static final byte CAMERA_MODE_TIMELAPSE = 3;
    public static final byte CAMERA_MODE_TIMER = 4;
    public static final byte CAMERA_MODE_HDMIPLAY = 5;
    public static final byte CAMERA_MODE_SETTINGS = 100;

    public static final byte STARTUP_MODE_VIDEO = 0;
    public static final byte STARTUP_MODE_PHOTO = 1;
    public static final byte STARTUP_MODE_BURST = 2;
    public static final byte STARTUP_MODE_TIMELAPSE = 3;

    public static final byte AUTOMATIC_POWER_OFF_NEVER = 0;
    public static final byte AUTOMATIC_POWER_OFF_60SEC = 1;
    public static final byte AUTOMATIC_POWER_OFF_120SEC = 2;
    public static final byte AUTOMATIC_POWER_OFF_300SEC = 3;

    public static final byte VOLUME_OFF = 0;
    public static final byte VOLUME_70 = 1;
    public static final byte VOLUME_100 = 2;

    public static final byte FOV_WIDE = 0;
    public static final byte FOV_MEDIUM = 1;
    public static final byte FOV_NARROW = 2;

    public static final byte VIDEO_MODE_VGA_60 = 0;
    public static final byte VIDEO_MODE_VGA_120 = 1;
    public static final byte VIDEO_MODE_720_30 = 2;
    public static final byte VIDEO_MODE_720_60 = 3;
    public static final byte VIDEO_MODE_960_30 = 4;
    public static final byte VIDEO_MODE_960_48 = 5;
    public static final byte VIDEO_MODE_1080_30 = 6;

    public static final byte TIME_LAPSE_HALFSECOND = 0;
    public static final byte TIME_LAPSE_1SECOND = 1;
    public static final byte TIME_LAPSE_2SECOND = 2;
    public static final byte TIME_LAPSE_5SECOND = 5;

    public static final byte PHOTO_MODE_11MPW = 0;
    public static final byte PHOTO_MODE_8MPM = 1;
    public static final byte PHOTO_MODE_5MPW = 2;
    public static final byte PHOTO_MODE_5MPM = 3;

    public static final byte LEDS_OFF = 0;
    public static final byte LEDS_2 = 1;
    public static final byte LEDS_4 = 2;

    public static class Fields {
        public static final int CAMERA_MODE_FIELD = 1;
        public static final int STARTUP_MODE_FIELD = 3;
        public static final int SPOT_METER_FIELD = 4;
        public static final int TIMELAPSE_INTERVAL_FIELD = 5;
        public static final int AUTIMATIC_POWEROFF_FIELD = 6;
        public static final int VIEW_ANGLE_FIELD = 7;
        public static final int PHOTO_MODE_FIELD = 8;
        public static final int VIDEO_MODE_FIELD = 9;
        public static final int RECORDING_MINUTES_FIELD = 13;
        public static final int RECORDING_SECONDS_FIELD = 14;
        public static final int BEEP_VOLUME_FIELD = 16;
        public static final int LEDS_FIELD = 17;
        public static final int BOOLEAN_STATUS_FIELD = 18;
        public static final int BATTERY_LEVEL_FIELD = 19;
        public static final int PHOTOS_AVAILABLE_LO_FIELD = 21;
        public static final int PHOTOS_AVAILABLE_HI_FIELD = 22;
        public static final int PHOTOS_COUNT_LO_FIELD = 23;
        public static final int PHOTOS_COUNT_HI_FIELD = 24;
        public static final int VIDEO_REMAINING_MINS_LO_FIELD = 25;
        public static final int VIDEO_REMAINING_MINS_HI_FIELD = 26;
        public static final int VIDEO_COUNT_MINS_LO_FIELD = 27;
        public static final int VIDEO_COUNT_MINS_HI_FIELD = 28;
        public static final int IS_RECORDING_FIELD = 29;
    }

    public static volatile byte[] LastCameraStatus = null;

    public final boolean SpotMeter;
    public final byte TimelapseInterval;
    public final byte CameraMode;
    public final byte StartupMode;
    public final byte AutomaticPowerOff;
    public final byte ViewAngle;
    public final byte PhotoMode;
    public final byte VideoMode;
    public final byte RecordingMinutes;
    public final byte RecordingSeconds;
    public final byte CurrentBeepVolume;
    public final byte Leds;
    public final byte BatteryLevel;
    public final short PhotosAvailable;
    public final short PhotoCount;
    public final short MinutesVideoRemaining;
    public final short VideoCount;
    public final boolean IsRecording;

    public final boolean IsPreviewOn;
    public final boolean IsUpsideDown;
    public final boolean IsOneButtonOn;
    public final boolean IsOsdOn;
    public final boolean IsPal;
    public final boolean IsBeeping;


    public GoProStatus(byte[] rawStatus) {

        if(rawStatus == null || rawStatus.length < RAW_STATUS_LEN) {
            CameraMode = UNKNOW;
            StartupMode = UNKNOW;
            SpotMeter = false;
            TimelapseInterval = UNKNOW;
            AutomaticPowerOff = UNKNOW;
            ViewAngle = UNKNOW;
            PhotoMode = UNKNOW;
            VideoMode = UNKNOW;
            RecordingMinutes = UNKNOW;
            RecordingSeconds = UNKNOW;
            CurrentBeepVolume = UNKNOW;
            Leds = UNKNOW;
            BatteryLevel = UNKNOW;
            PhotosAvailable = UNKNOW;
            PhotoCount = UNKNOW;
            MinutesVideoRemaining = UNKNOW;
            VideoCount = UNKNOW;
            IsRecording = false;
            IsPreviewOn = false;
            IsUpsideDown = false;
            IsOneButtonOn = false;
            IsOsdOn = false;
            IsPal = false;
            IsBeeping = false;
            return;
        }
        CameraMode = rawStatus[Fields.CAMERA_MODE_FIELD];
        StartupMode = rawStatus[Fields.STARTUP_MODE_FIELD];
        SpotMeter = rawStatus[Fields.SPOT_METER_FIELD] == 1;
        TimelapseInterval = rawStatus[Fields.TIMELAPSE_INTERVAL_FIELD];
        AutomaticPowerOff = rawStatus[Fields.AUTIMATIC_POWEROFF_FIELD];
        ViewAngle = rawStatus[Fields.VIEW_ANGLE_FIELD];
        PhotoMode = rawStatus[Fields.PHOTO_MODE_FIELD];
        VideoMode = rawStatus[Fields.VIDEO_MODE_FIELD];
        RecordingMinutes = rawStatus[Fields.RECORDING_MINUTES_FIELD];
        RecordingSeconds = rawStatus[Fields.RECORDING_SECONDS_FIELD];
        CurrentBeepVolume = rawStatus[Fields.BEEP_VOLUME_FIELD];
        Leds = rawStatus[Fields.LEDS_FIELD];
        BatteryLevel = rawStatus[Fields.BATTERY_LEVEL_FIELD];
        PhotosAvailable = (short) (((rawStatus[Fields.PHOTOS_AVAILABLE_LO_FIELD]&0xFF) << 8) | (rawStatus[Fields.PHOTOS_AVAILABLE_HI_FIELD] & 0xFF));
        PhotoCount = (short) (((rawStatus[Fields.PHOTOS_COUNT_LO_FIELD]&0xFF) << 8) | (rawStatus[Fields.PHOTOS_COUNT_HI_FIELD] & 0xFF));
        MinutesVideoRemaining = (short) (((rawStatus[Fields.VIDEO_REMAINING_MINS_LO_FIELD]&0xFF) << 8) | (rawStatus[Fields.VIDEO_REMAINING_MINS_HI_FIELD] & 0xFF));
        VideoCount = (short) (((rawStatus[Fields.VIDEO_COUNT_MINS_LO_FIELD]&0xFF) << 8) | (rawStatus[Fields.VIDEO_COUNT_MINS_HI_FIELD] & 0xFF));
        IsRecording = rawStatus[Fields.IS_RECORDING_FIELD] != 0;

        byte booleanBits = rawStatus[Fields.BOOLEAN_STATUS_FIELD];
        IsPreviewOn = (booleanBits & 1) != 0;
        IsUpsideDown =  (booleanBits & 4) != 0;
        IsOneButtonOn =  (booleanBits & 8) != 0;
        IsOsdOn =  (booleanBits & 0x10) != 0;
        IsPal =  (booleanBits & 0x20) != 0;
        IsBeeping =  (booleanBits & 0x40) != 0;
    }

    public static String getCameraName(String s) {
        int i = s.indexOf("\n");
        if(i > 0) {
            s = s.substring(i+1);
        }
        return s;
    }

    public String toString() {
        StringBuilder sb= new StringBuilder();
        sb.append("CameraMode=");sb.append(CameraMode);sb.append("\n");
        sb.append("StartupMode=");sb.append(StartupMode);sb.append("\n");
        sb.append("SpotMeter=");sb.append(SpotMeter);sb.append("\n");
        sb.append("TimelapseInterval=");sb.append(TimelapseInterval);sb.append("\n");
        sb.append("AutomaticPowerOff=");sb.append(AutomaticPowerOff);sb.append("\n");
        sb.append("ViewAngle=");sb.append(ViewAngle);sb.append("\n");
        sb.append("PhotoMode=");sb.append(PhotoMode);sb.append("\n");
        sb.append("VideoMode=");sb.append(VideoMode);sb.append("\n");
        sb.append("RecordingMinutes=");sb.append(RecordingMinutes);sb.append("\n");
        sb.append("RecordingSeconds=");sb.append(RecordingSeconds);sb.append("\n");
        sb.append("CurrentBeepVolume=");sb.append(CurrentBeepVolume);sb.append("\n");
        sb.append("Leds=");sb.append(Leds);sb.append("\n");
        sb.append("BatteryLevel=");sb.append(BatteryLevel);sb.append("\n");
        sb.append("PhotosAvailable=");sb.append(PhotosAvailable);sb.append("\n");
        sb.append("PhotoCount=");sb.append(PhotoCount);sb.append("\n");
        sb.append("MinutesVideoRemaining=");sb.append(MinutesVideoRemaining);sb.append("\n");
        sb.append("VideoCount=");sb.append(VideoCount);sb.append("\n");
        sb.append("IsRecording=");sb.append(IsRecording);sb.append("\n");
        return sb.toString();
    }
}
