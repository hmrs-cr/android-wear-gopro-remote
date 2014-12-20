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

package com.hmsoft.libcommon.constants;

/***
 * Messages send to/from the wear application.
 */
public final class WearMessages {
    public static final String MESSAGE_CONNECT = "/connect";
    public static final String MESSAGE_DISCONNECT = "/disconnect";
    public static final String MESSAGE_STOP = "/stop";
    public static final String MESSAGE_SHUTTER = "/shutter";
    public static final String MESSAGE_CAMERA_NAME = "/camera/name";
    public static final String MESSAGE_CAMERA_STATUS = "/camera/status";
    public static final String MESSAGE_SET_CAMERA_MODE = "/camera/set/mode";
    public static final String MESSAGE_SET_VIDEO_MODE = "/camera/set/video/mode";
    public static final String MESSAGE_SET_TIMELAPSE_INTERVAL = "/camera/set/timelapse/interval";
    public static final String MESSAGE_SET_WEAR_SETTINGS = "/set/settings";
    public static final String MESSAGE_LAUNCH_ACTIVITY = "/launch/activity";
    public static final String MESSAGE_SET_SPOTMETTER = "/camera/set/spotmetter";
    public static final String MESSAGE_SET_BEEP_VOLUME = "/camera/set/beepvolume";
    public static final String MESSAGE_SET_UPSIDEDOWN = "/camera/set/upsidedown";
    public static final String MESSAGE_SET_LEDS = "/camera/set/leds";
    public static final String MESSAGE_SET_OSD = "/camera/set/osd";
    public static final String MESSAGE_SET_BEEPING = "/camera/set/beeping";
    public static final String MESSAGE_POWEROFF = "/camera/poweroff";
    public static final String MESSAGE_LAST_IMG_THUMB = "/camera/lastimgthumb";
    public static final String MESSAGE_SYNC_THUMB = "/camera/syncthumbs";
    public static final String MESSAGE_SYNC_THUMB_FINISHED = "/camera/syncthumbsfinished";

    public static final byte PARAM_UNKNOW = -1;

    private static final String PARAM_SEPARATOR = ":";

    public static String getMessageParam(String path) {
        int i = path.indexOf(PARAM_SEPARATOR);
        if(i > 0) {
            return path.substring(i + 1);
        }
        return "";
    }

    public static String getMessage(String path) {
        int i = path.indexOf(PARAM_SEPARATOR);
        if(i > 0) {
            return path.substring(0, i);
        }
        return path;
    }

    public static String addParamToMessage(String path, String param) {
        return  path + PARAM_SEPARATOR + param;
    }

    private WearMessages() {}
}
