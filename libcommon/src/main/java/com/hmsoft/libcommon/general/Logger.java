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

package com.hmsoft.libcommon.general;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.hmsoft.libcommon.BuildConfig;

public final class Logger {

    //private static final String TAG = "Logger";
    private static final String APP_TAG = "HMSOFT:";

    public static final boolean DEBUG = BuildConfig.DEBUG;
    public static final boolean WARNING = true;
    //public static final boolean INFO = true;
    public static final boolean ERROR = true;

    public static  final String DEBUG_TAG = "DEBUG";
    public static  final String WARNING_TAG = "WARNING";
    //public static  final String INFO_TAG = "INFO";
    public static  final String ERROR_TAG = "ERROR";

    private static final String LOG_FILE = "log-%s.log";
    private static final String LOGS_FOLDER = "logs";

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);

    private static File sLogsFolder = null;

    public static void init(Context context) {
        sLogsFolder = context.getExternalFilesDir(LOGS_FOLDER);
    }

    public static void log2file(String tag, String msg, String fileName, Throwable e) {

        try {
            if(sLogsFolder == null) {
                sLogsFolder = new File(Environment.getExternalStorageDirectory() +
                        "/Android/data/" + BuildConfig.APPLICATION_ID, LOGS_FOLDER);
            }

            Date now = new Date();

            File file = new File(sLogsFolder, String.format(fileName, LOG_DATE_FORMAT.format(now)));

            FileOutputStream os = new FileOutputStream(file, true);
            try (OutputStreamWriter writer = new OutputStreamWriter(os)) {
                writer.append(SIMPLE_DATE_FORMAT.format(now));
                writer.append("\t");
                writer.append(tag);
                writer.append("\t");
                writer.append(msg);
                writer.append("\t");
                if (e != null)
                    writer.append(e.toString());
                writer.append("\n");
                writer.flush();
            }
        } catch (IOException ex) {
            //Log.w(TAG, "log2file failed:", ex);
        }
    }

    public static void debug(String tag, String msg) {
        if(DEBUG) {
            Log.d(APP_TAG + tag, msg);
            log2file(tag + "\t" + DEBUG_TAG, msg, LOG_FILE, null);
        }
    }

    public static void debug(String tag, String msg, Throwable e) {
        if(DEBUG) {
            Log.d(APP_TAG + tag, msg, e);
            log2file(tag + "\t" + DEBUG_TAG, msg, LOG_FILE, e);
        }
    }

    public static void debug(String tag, String msg, Object... args) {
        if(DEBUG) {
            msg = String.format(msg,  args);
            Log.d(APP_TAG + tag, String.format(msg,  args));
            log2file(tag + "\t" + DEBUG_TAG, msg, LOG_FILE, null);
        }
    }

    public static void error(String tag, String msg) {
        if(ERROR) {
            Log.e(tag, msg);
            log2file(tag + "\t" + ERROR_TAG, msg, LOG_FILE, null);
        }
    }

    public static void error(String tag, String msg, Throwable e) {
        if(ERROR) {
            Log.e(tag, msg, e);
            log2file(tag + "\t" + ERROR_TAG, msg, LOG_FILE, e);
        }
    }
//
//	public static void error(String tag, String msg, Object... args) {
//		if(ERROR) {
//			msg = String.format(msg,  args);
//			Log.e(APP_TAG + tag, String.format(msg,  args));
//			log2file(tag + "\t" + ERROR_TAG, msg, LOG_FILE, null);
//		}
//	}

    public static void warning(String tag, String msg, Throwable e) {
        if(WARNING) {
            Log.w(APP_TAG + tag, msg, e);
            log2file(tag + "\t" + WARNING_TAG, msg, LOG_FILE, e);
        }
    }

    public static void warning(String tag, String msg, Object... args) {
        if(WARNING) {
            msg = String.format(msg,  args);
            Log.w(APP_TAG + tag, String.format(msg,  args));
            log2file(tag + "\t" + WARNING_TAG, msg, LOG_FILE, null);
        }
    }


//    public static void info(String tag, String msg) {
//        if(INFO) {
//            Log.i(APP_TAG + tag, msg);
//            log2file(tag + "\t" + INFO_TAG, msg, LOG_FILE, null);
//        }
//    }
//
//    public static void info(String tag, String msg, Object... args) {
//        if(INFO) {
//            msg = String.format(msg,  args);
//            Log.i(APP_TAG + tag, String.format(msg,  args));
//            log2file(tag + "\t" + INFO_TAG, msg, LOG_FILE, null);
//        }
//    }
}
