package com.hmsoft.libcommon.sensors;


import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.hmsoft.libcommon.general.Logger;


/**
 *
 * A shake detection library.
 * To use, have your Activity create an instance of the class and set a listener using addListener();
 * Make sure you call onResume() and onPause() on from the activity
 *
 * @author James / Modified by HMRS
 * @copyright 2013 JMB Technology Limited
 * @license Open Source; 3-clause BSD
 */
public class ShakeDetectEventListener implements SensorEventListener {

    private static final String TAG = "ShakeDetectEventListener";
    private static final boolean DEBUG = Logger.DEBUG;


    SensorManager sensorMgr;

    private int mMinimumEachDirection = 3;
    private boolean mListenerRegistered = false;

    public ShakeDetectEventListener(Context context) {
        sensorMgr = (SensorManager) context.getSystemService(Activity.SENSOR_SERVICE);
        startDetecting();
    }

    public void startDetecting() {
        if(mMinimumEachDirection > 0 && !mListenerRegistered) {
            if(DEBUG) Logger.debug(TAG, "startDetecting");
            mListenerRegistered = sensorMgr.registerListener(this,
                    sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stopDetecting() {
        if(mListenerRegistered) {
            sensorMgr.unregisterListener(this);
            mListenerRegistered = false;
            if(DEBUG) Logger.debug(TAG, "stopDetecting");
        }
    }

    public void setMinimumEachDirection(int minimumEachDirection) {
        mMinimumEachDirection = minimumEachDirection;
        if(mMinimumEachDirection < 1) {
            stopDetecting();
        } else {
            startDetecting();
        }
    }

    private class DataPoint {
        public float x, y, z;
        public long atTimeMilliseconds;
        public DataPoint(float x, float y, float z, long atTimeMilliseconds) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.atTimeMilliseconds = atTimeMilliseconds;
        }
    }

    private List<DataPoint> dataPoints = new ArrayList<>();

    private static final int SHAKE_CHECK_THRESHOLD = 100;

    /**
     * After we detect a shake, we ignore any events for a bit of time. We don't want two shakes to close together.
     */
    private static final int IGNORE_EVENTS_AFTER_SHAKE = 1000;
    private long lastUpdate;
    private long lastShake = 0;

    private float last_x = 0, last_y=0, last_z=0;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            long curTime = System.currentTimeMillis();
            // if a shake in last X seconds ignore.
            if (lastShake != 0 && (curTime - lastShake) < IGNORE_EVENTS_AFTER_SHAKE) return;

            float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            float z = event.values[SensorManager.DATA_Z];
            if (last_x != 0 && last_y != 0 && last_z != 0 && (last_x != x || last_y != y || last_z != z)) {
                DataPoint dp = new DataPoint(last_x-x, last_y-y, last_z-z, curTime);
                //Log.i("XYZ",Float.toString(dp.x)+"   "+Float.toString(dp.y)+"   "+Float.toString(dp.z)+"   ");
                dataPoints.add(dp);

                if ((curTime - lastUpdate) > SHAKE_CHECK_THRESHOLD) {
                    lastUpdate = curTime;
                    checkForShake();
                }
            }
            last_x = x;
            last_y = y;
            last_z = z;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private static final long KEEP_DATA_POINTS_FOR = 1500;
    //private static final long MINIMUM_EACH_DIRECTION = 3;
    private static final float POSITIVE_COUNTER_THRESHHOLD = (float) 2.0;
    private static final float NEGATIVE_COUNTER_THRESHHOLD = (float) -2.0;


    public void checkForShake() {
        long curTime = System.currentTimeMillis();
        long cutOffTime = curTime - KEEP_DATA_POINTS_FOR;
        while(dataPoints.size() > 0 && dataPoints.get(0).atTimeMilliseconds < cutOffTime) dataPoints.remove(0);

        int x_pos =0, x_neg=0, x_dir = 0, y_pos=0, y_neg=0, y_dir=0, z_pos=0, z_neg = 0, z_dir = 0;
        for(DataPoint dp: dataPoints){
            if (dp.x > POSITIVE_COUNTER_THRESHHOLD && x_dir < 1) {
                ++x_pos;
                x_dir = 1;
            }
            if (dp.x < NEGATIVE_COUNTER_THRESHHOLD && x_dir > -1) {
                ++x_neg;
                x_dir = -1;
            }
            if (dp.y > POSITIVE_COUNTER_THRESHHOLD && y_dir < 1) {
                ++y_pos;
                y_dir = 1;
            }
            if (dp.y < NEGATIVE_COUNTER_THRESHHOLD && y_dir > -1) {
                ++y_neg;
                y_dir = -1;
            }
            if (dp.z > POSITIVE_COUNTER_THRESHHOLD && z_dir < 1) {
                ++z_pos;
                z_dir = 1;
            }
            if (dp.z < NEGATIVE_COUNTER_THRESHHOLD && z_dir > -1) {
                ++z_neg;
                z_dir = -1;
            }
        }
        //Log.i("CHANGE",Integer.toString(x_pos)+" - "+Integer.toString(x_neg)+"  "+Integer.toString(y_pos)+" - "+Integer.toString(y_neg)+"  "+Integer.toString(z_pos)+" - "+Integer.toString(z_neg));

        if ((x_pos >= mMinimumEachDirection && x_neg >= mMinimumEachDirection) ||
                (y_pos >= mMinimumEachDirection && y_neg >= mMinimumEachDirection) ||
                (z_pos >= mMinimumEachDirection && z_neg >= mMinimumEachDirection) ) {
            lastShake = System.currentTimeMillis();
            last_x = 0; last_y=0; last_z=0;
            dataPoints.clear();
            triggerShakeDetected();
            return;
        }

    }

    ArrayList<ShakeDetectActivityListener> listeners = new ArrayList<ShakeDetectActivityListener>();

    public void addListener(ShakeDetectActivityListener listener) {
        listeners.add(listener);
    }
	
	public void clear() {
		listeners.clear();
		dataPoints.clear();
	}

    protected void triggerShakeDetected() {
        for(ShakeDetectActivityListener listener: listeners) {
            listener.shakeDetected();
        }
    }

    public interface ShakeDetectActivityListener {
        public void shakeDetected();
    }
}
