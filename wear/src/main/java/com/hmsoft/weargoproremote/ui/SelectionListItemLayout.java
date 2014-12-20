/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.wearable.view.WearableListView;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hmsoft.weargoproremote.R;

public class SelectionListItemLayout extends LinearLayout implements WearableListView.Item {

    private final float mFadedTextAlpha;
    private final int mFadedCircleColor;
    private final int mChosenCircleColor;
    private ImageView mCircle;
    private float mScale;
    private TextView mName;

    public SelectionListItemLayout(Context context) {
        this(context, null);
    }

    public SelectionListItemLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SelectionListItemLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mFadedTextAlpha = getResources().getInteger(R.integer.action_text_faded_alpha) / 100f;
        mFadedCircleColor = getResources().getColor(R.color.wl_gray);
        mChosenCircleColor = getResources().getColor(R.color.wl_blue);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCircle = (ImageView) findViewById(R.id.circle);
        mName = (TextView) findViewById(R.id.name);
    }

    @Override
    public float getProximityMinValue() {
        return 1f;
    }

    @Override
    public float getProximityMaxValue() {
        return 1.6f;
    }

    @Override
    public float getCurrentProximityValue() {
        return mScale;
    }

    @Override
    public void setScalingAnimatorValue(float scale) {
        mScale = scale;
        mCircle.setScaleX(scale);
        mCircle.setScaleY(scale);
    }

    @Override
    public void onScaleUpStart() {
        mName.setAlpha(1f);
        Typeface tf = mName.getTypeface();
        tf = Typeface.create(tf, Typeface.BOLD);
        mName.setTypeface(tf);
        Drawable drawable = mCircle.getDrawable();
        if(drawable instanceof GradientDrawable) {
            ((GradientDrawable) drawable).setColor(mChosenCircleColor);
        }
    }

    @Override
    public void onScaleDownStart() {
        Drawable drawable = mCircle.getDrawable();
        Typeface tf = mName.getTypeface();
        tf = Typeface.create(tf, Typeface.NORMAL);
        mName.setTypeface(tf);
        if(drawable instanceof GradientDrawable) {
            ((GradientDrawable) drawable).setColor(mFadedCircleColor);
        }
        mName.setAlpha(mFadedTextAlpha);
    }
}
