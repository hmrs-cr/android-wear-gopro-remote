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

import android.os.Handler;

public class Timer {

    Handler mHandler;
    long mInterval;
    int mTicks;
    private TimerTask mTask;
    volatile TimerImpl mTimerImpl;

    public Timer(long interval, Handler handler, TimerTask task) {
        mHandler = handler;
        mInterval = interval;
        mTask = task;
        if(mHandler == null) {
            mHandler = new Handler();
        }
    }

    public Timer(long interval, TimerTask task) {
        this(interval, null, task);
    }

    public void setInterval(long interval) {
        this.mInterval = interval;
    }

    public void start() {
        if(mTimerImpl == null) {
            mTimerImpl = new TimerImpl();
            mTimerImpl.Stoped = false;
            mHandler.postDelayed(mTimerImpl, mInterval);
        }
    }

    public void stop() {
        if(mTimerImpl != null) {
            mTimerImpl.Stoped = true;
            mHandler.removeCallbacksAndMessages(null);
            mTimerImpl = null;
        }
    }

    private class TimerImpl implements Runnable {

        public boolean Stoped;

        @Override
        public void run() {
            if(!Stoped) {
                mTicks++;
                if (mTask != null) mTask.onTick(mTicks);
                if(!Stoped) mHandler.postDelayed(this, mInterval);
            }
        }
    }

    public interface TimerTask {
        public void onTick(int ticks);
    }
}
