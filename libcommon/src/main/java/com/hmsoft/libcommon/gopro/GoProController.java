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

import android.text.TextUtils;
import android.util.Log;

import com.hmsoft.libcommon.BuildConfig;
import com.hmsoft.libcommon.general.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class GoProController {

    private static final String TAG = "GoProController";

    public static final String DEFAULT_GOPROADDRESS = "10.5.5.9";
    private static final boolean DEBUG = Logger.DEBUG;

    private static volatile GoProController sDefaultInstance = null;

    private static final byte[] RESPONSE_NOT_FOUND = new byte[0];

    private final String fCameraAddress;
    private final String fPassword;    

    public GoProController(String cameraAddress, String password) {
        fCameraAddress = cameraAddress;
        fPassword = password;
    }

    public GoProController(String password) {
        this(DEFAULT_GOPROADDRESS, password);
    }

    public static GoProController getDefaultInstance(String pass) {
        if(sDefaultInstance == null || !sDefaultInstance.fPassword.equals(pass)) {
            sDefaultInstance = new GoProController(pass);
        }
        return sDefaultInstance;
    }

    public static void clear() {
        sDefaultInstance = null;
        GoProStatus.LastCameraStatus = null;
    }

    private static boolean responseOk(byte[] response) {
        return response != null && response != RESPONSE_NOT_FOUND;
    }

    private HttpURLConnection getHttpURLConnection(String urlStr) {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            Log.wtf(TAG, e);
            return null;
        }

        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            Logger.error(TAG, "Can't connect to camera.", e);
            return null;
        }

        if(BuildConfig.DEBUG) Logger.debug(TAG, "Connected to URL: %s", urlStr);

        urlConnection.setConnectTimeout(2000);
        urlConnection.setReadTimeout(2000);

        return urlConnection;
    }

    private HttpURLConnection getMediaConnection(String command, String param) {
        String urlStr = "http://" + fCameraAddress + ":8080/gp/" + command;
        if(param != null) {
            urlStr += "?p=" + param;
        }

        return getHttpURLConnection(urlStr);
    }

    private byte[] execCommand(String controller, String command, String param) {
        String urlStr = "http://" + fCameraAddress + "/" + controller + "/" + command + "?t=" + fPassword;
        if (param != null) {
            urlStr += "&p=" + param;
        }

        HttpURLConnection urlConnection = getHttpURLConnection(urlStr);
        if(urlConnection == null) return null;
        try {
            InputStream in = urlConnection.getInputStream();
            int cl = urlConnection.getContentLength();
            byte[] buffer = new byte[cl];
            int r = in.read(buffer);
            if(DEBUG) Logger.debug(TAG, "Command " + urlStr + " executed. " + r);
            return buffer;
        } catch (FileNotFoundException e) {
            Logger.warning(TAG, "Not found: " + urlStr);
            return RESPONSE_NOT_FOUND;
        } catch (IOException e) {
            Logger.warning(TAG, "Can't connect to camera", e);
            return null;
        } finally {
            urlConnection.disconnect();
        }
    }

    private byte[] execCameraCommand(String command, String param) {
        return execCommand("camera", command, param);
    }

    private byte[] execBackpackCommand(String command, String param) {
        return execCommand("bacpac", command, param);
    }

    public GoProStatus getStatus() {
        byte[] response = getRawStatus();
        GoProStatus.LastCameraStatus = response;
        return new GoProStatus(response);
    }

    public byte[] getRawStatus() {
        byte[] response = execCameraCommand("se", null);
        if(response == RESPONSE_NOT_FOUND) {
            response = new byte[GoProStatus.RAW_STATUS_LEN];
            Arrays.fill(response, GoProStatus.UNKNOW);
            response[GoProStatus.Fields.CAMERA_MODE_FIELD] = GoProStatus.CAMERA_MODE_OFF_WIFION;
        }
        GoProStatus.LastCameraStatus = response;
        return response;
    }

    public byte[] getLastStatus() {
        if(GoProStatus.LastCameraStatus == null) {
            GoProStatus.LastCameraStatus = getRawStatus();
        }
        return GoProStatus.LastCameraStatus;
    }

    public String getCameraName() {
        byte[] response = execCameraCommand("cv", null);
        if(responseOk(response)) {
            return (new String(response)).trim();
        }
        return null;
    }

    public byte[] getRawCameraName() {
        byte[] response = execCameraCommand("cv", null);
        if(response == RESPONSE_NOT_FOUND) response = null;
        return response;
    }

    public boolean shutter(boolean on) {
        String param = on ? "%01" : "%00";
        byte[] response = execBackpackCommand("SH", param);
        boolean ok = responseOk(response);
        if(ok) mLastMediaList = null;
        return ok;
    }

    public boolean setCameraMode(byte cameraMode) {
        byte[] response = execCameraCommand("CM", "%0" + cameraMode);
        return responseOk(response);
    }

    public boolean setOrientationUpDown(boolean up) {
        String param = up ? "%01" : "%00";
        byte[] response = execCameraCommand("UP", param);
        return responseOk(response);
    }

    public boolean setVideoMode(byte videoMode) {
        byte[] response = execCameraCommand("VR", "%0" + videoMode);
        return responseOk(response);
    }

    public boolean setTimelapseInterval(byte interval) {
        byte[] response = execCameraCommand("TI", "%0" + interval);
        return responseOk(response);
    }

    public boolean turnOn() {
        byte[] response = execBackpackCommand("PW", "%01");
        return responseOk(response);
    }

    public boolean turnOff() {
        byte[] response = execBackpackCommand("PW", "%00");
        return responseOk(response);
    }

    public boolean setVolume(byte volume) {
        byte[] response = execCameraCommand("BS", "%0" + volume);
        return responseOk(response);
    }

    public boolean setLeds(byte leds) {
        byte[] response = execCameraCommand("LB", "%0" + leds);
        return responseOk(response);
    }

    public boolean setSpotMeter(boolean on) {
        String param = on ? "%01" : "%00";
        byte[] response = execCameraCommand("EX", param);
        return responseOk(response);
    }

    public boolean setOnScreenDisplay(boolean on) {
        String param = on ? "%01" : "%00";
        byte[] response = execCameraCommand("OS", param);
        return responseOk(response);
    }

    public boolean locate(boolean on) {
        String param = on ? "%01" : "%00";
        byte[] response = execCameraCommand("LL", param);
        return responseOk(response);
    }

    public boolean setPreviewOn(boolean on) {
        String param = on ? "%02" : "%00";
        byte[] response = execCameraCommand("PV", param);
        return responseOk(response);
    }

    // **************************************************************** //
    // ************************** Media ******************************* //
    // **************************************************************** //

    private File mThumbnailCacheDirectory;
    private JSONArray mLastMediaList;

    private JSONArray getMediaJSONList() {
        HttpURLConnection connection = getMediaConnection("gpMediaList", null);
        if(connection == null) return null;
        try {
            long start = 0;
            if(DEBUG) start = System.currentTimeMillis();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            int cl = connection.getContentLength();
            StringBuilder builder = cl > 0 ? new StringBuilder(cl) : new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            if(DEBUG) {
                long time = System.currentTimeMillis() - start;
                Logger.debug(TAG, "GetFileList http request handled in %ds (%d)", time/1000, time);
            }

            JSONObject json = new JSONObject(builder.toString());
            mLastMediaList = json.optJSONArray("media");
            return mLastMediaList;
        } catch (IOException e) {
            Logger.warning(TAG, "gpMediaList: Failed to get media list", e);
        } catch (JSONException e) {
            Logger.error(TAG, "gpMediaList: Failed to parse JSON", e);
        } finally {
            connection.disconnect();
        }
        return null;
    }

    public String getMediaFileNameAtReverseIndex(int index) {
        if(mLastMediaList == null) {
            if((mLastMediaList = getMediaJSONList()) == null) {
                return "";
            }
        }
        try {
            int len = mLastMediaList.length();
        
            for(int c = 1; c <= len; c++) {
                JSONObject folder = mLastMediaList.getJSONObject(len - c);
                JSONArray entries = folder.getJSONArray("fs");

                int entLen = entries.length();
                if(entLen <= 0) continue;

                if(index > entLen) {
                    index -= entLen;
                    continue;
                }

                if(index <= 0) index = 1;
                else if(index > entLen) return "";

                String folderName = folder.optString("d");
                JSONObject image = entries.getJSONObject(entLen - index);
                String imageName = image.getString("n");
                return "/" + folderName + "/" + imageName;
            }
        } catch (JSONException e) {
            Logger.error(TAG, "", e);
        }        
        return "";
    }    

    public static void saveThumbnailToCache(File thumbnailCacheDirectory, String fileName,
                                            byte[] thumb) {
        if(thumbnailCacheDirectory != null) {
            File cacheFile = new File(thumbnailCacheDirectory, fileName);
            File directory = cacheFile.getParentFile();

            if(!directory.isDirectory() && !directory.mkdirs()) {
                Logger.error(TAG, "saveThumbnailToCache: Failed to create directory.");
                return;
            }
            try {
                try (FileOutputStream os = new FileOutputStream(cacheFile)) {
                    os.write(thumb);
                }
                if(Logger.DEBUG) Logger.debug(TAG, "%s saved to cache", fileName);
            } catch (IOException e) {
                Logger.error(TAG, "saveThumbnailToCache", e);
            }
        }else {
            Logger.warning(TAG, "thumbnailCacheDirectory is null");
        }
    }

    public static byte[] getImgThumbnailFromCache(File thumbnailCacheDirectory, String fileName) {
        if(thumbnailCacheDirectory == null) return null;

        File cacheFile = new File(thumbnailCacheDirectory, fileName);
        if(!cacheFile.exists()) return null;
        try {
            byte[] buffer = new byte[(int)cacheFile.length()];
            try (FileInputStream is = new FileInputStream(cacheFile)) {
                is.read(buffer);
            }
            if(Logger.DEBUG)Logger.debug(TAG, "Got %s from cache", fileName);
            return buffer;
        } catch (IOException e) {
            Logger.error(TAG, "getImgThumbnailFromCache failed:", e);
        }
        return null;
    }

    public void setThumbnailCacheDirectory(File thumbnailCacheDirectory) {
        mThumbnailCacheDirectory = thumbnailCacheDirectory;
    }

    public byte[] getImgThumbnail(String fileName) {

        byte[] fromCache = getImgThumbnailFromCache(mThumbnailCacheDirectory, fileName);
        if(fromCache != null) return fromCache;

        long start = 0;
        if(DEBUG) start = System.currentTimeMillis();

        HttpURLConnection connection = getMediaConnection("gpMediaMetadata", fileName);
        if(connection == null) return null;
        try {
            InputStream in = connection.getInputStream();
            int cl = connection.getContentLength();

            if(cl <= 0) cl = 512;
            ByteArrayOutputStream os = new ByteArrayOutputStream(cl);

            byte[] buffer = new byte[cl];
            int i;
            while((i = in.read(buffer)) > -1) {
                os.write(buffer, 0, i);
            }

            byte[] thumb = os.toByteArray();
            if(Logger.DEBUG)Logger.debug(TAG, "Got %s from camera", fileName);           

            if(DEBUG) {
                long time = System.currentTimeMillis() - start;
                Logger.debug(TAG, "getLastImgThumbnail handled in %ds (%d)", time/1000, time);
            }

            saveThumbnailToCache(mThumbnailCacheDirectory, fileName, thumb);

            return thumb;
        } catch (IOException e) {
            Logger.warning(TAG, "Failed to get thumbnail");
        } finally {
            connection.disconnect();
        }
        return null;
    }

    public Thumbnail getImgThumbnailAtReverseIndex(int index) {
        String mediaFileName = getMediaFileNameAtReverseIndex(index);
        if(TextUtils.isEmpty(mediaFileName)) return null;
        byte[] bitmapData = getImgThumbnail(mediaFileName);
        return new Thumbnail(mediaFileName, bitmapData);
    }

    public void refreshMediaCachedList() {
        mLastMediaList = null; // Force refresh
    }

    public Thumbnail getLastImgThumbnail() {
        refreshMediaCachedList();
        return getImgThumbnailAtReverseIndex(1);
    }

    public static class Thumbnail {
        public final String Name;
        public final byte[] BitmapData;

        public Thumbnail(String name, byte[] bitmapData) {
            Name = name;
            BitmapData = bitmapData;
        }

        public final static char WEAR_SEPARATOR = '_';

        public String getWearName() {
            return getWearName(Name);
        }

        public static String getWearName(String name) {
            name = name.replace(File.separatorChar, WEAR_SEPARATOR);

            int separatorIndex = name.lastIndexOf(WEAR_SEPARATOR);
            String fName = (separatorIndex < 0) ? name : name.substring(separatorIndex + 1, name.length());
            boolean isTimelapse = !fName.startsWith("GOPR");

            if(isTimelapse) {
                char[] carray = fName.toCharArray();
                String group = new String(carray, 1, 3);
                carray[1] = 'O';
                carray[2] = 'P';
                carray[3] = 'R';
                String dirName = (separatorIndex < 0) ? "" : name.substring(0, separatorIndex + 1);
                name = dirName + new String(carray) + "-" + group;
            }
            return name;
        }
    }
}
