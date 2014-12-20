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

package com.hmsoft.weargoproremote.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import com.hmsoft.libcommon.general.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ThumbCache {

    private static final String TAG = "ThumbCache";
    private static LruCache<String, Bitmap> sMemoryCache;

    private static final int MAX = 11;
    private static ArrayList<File> sThumbFileList;
    private static File sThumbCacheFolderFile;

    public static Bitmap getThumbFromMemCache(File file) {
        if(sMemoryCache == null) return null;
        return sMemoryCache.get(file.getName());
    }

    public static void saveThumbToMemCache(Bitmap thumb, File file) {
        if(sMemoryCache  == null) {
            sMemoryCache = new LruCache<>(11);
        }
        sMemoryCache.put(file.getName(), thumb);
    }

    public static void clear() {
        if(sMemoryCache != null) {
            sMemoryCache.evictAll();
            sMemoryCache = null;
        }
        sThumbFileList = null;
        sThumbCacheFolderFile = null;
    }

    public static void clearThumbFileList() {
        sThumbFileList = null;
    }

    public static void addToThumbFileList(Context context, File file) {
        List<File> files = getThumbFileList(context);
        int s = files.size();
        String name = file.getName();
        if(s == 0 || !files.get(s - 1).getName().equals(name)) {
            files.add(new File(getThumbCacheFolderFile(context), name));
        }
    }

    public static List<File> getThumbFileList(Context context) {
        if(sThumbFileList == null) {
            File[] files = getThumbCacheFolderFile(context).listFiles();
            int s = MAX;
            if(files != null && files.length > MAX) s = files.length + MAX;
            sThumbFileList = new ArrayList<>(s);
            if(files != null) {
                Arrays.sort(files);
                Collections.addAll(sThumbFileList, files);
            }
            Logger.debug(TAG, "Thumb cache fileslisted");
        }
        return sThumbFileList;
    }

    public static File getThumbCacheFolderFile(Context context) {
        if(sThumbCacheFolderFile == null) {
            sThumbCacheFolderFile = new File(context.getCacheDir(), "thumbs");
        }
        return sThumbCacheFolderFile;
    }
}
