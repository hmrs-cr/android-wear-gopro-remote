/*
 * Copyright (C) 2013 Jorrit "Chainfire" Jongma
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

import com.hmsoft.libcommon.BuildConfig;

public class CrashCatcher implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler oldHandler;

    private CrashCatcher() {
        if (BuildConfig.DEBUG) {
            oldHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(this);
        }
    }

    public static void init() {
        if (BuildConfig.DEBUG) {
            new CrashCatcher();
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            StackTraceElement[] arr = ex.getStackTrace();

            String report =	ex.toString() + "\n\n";
            report += "--------- Stack trace ---------\n\n";
            report += thread.toString() + "\n\n";
            for (StackTraceElement anArr : arr) {
                report += "    " + anArr.toString() + "\n";
            }
            report += "-------------------------------\n\n";

            report += "--------- Cause ---------\n\n";
            Throwable cause = ex.getCause();
            if(cause != null) {
                report += cause.toString() + "\n\n";
                arr = cause.getStackTrace();
                for (StackTraceElement anArr : arr) {
                    report += "    " + anArr.toString() + "\n";
                }
            }
            report += "-------------------------------\n\n";

            Logger.error("FATAL_EXCEPTION", report);

        } catch (Exception e) {
            // Ignore
        }

        if (oldHandler != null)	oldHandler.uncaughtException(thread, ex);
    }
}
