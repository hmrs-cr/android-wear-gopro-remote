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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.R;

public class SelectionListActivity extends Activity implements WearableListView.ClickListener {

    public static final String EXTRA_ITEMS = BuildConfig.APPLICATION_ID + ".EXTRA_ITEMS";
    public static final String EXTRA_SELECTED_ITEM = BuildConfig.APPLICATION_ID + ".EXTRA_SELECTED_ITEM";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection_list);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent i = getIntent();
        if(i != null) {
            String[] items = i.getStringArrayExtra(EXTRA_ITEMS);
            String selected = i.getStringExtra(EXTRA_SELECTED_ITEM);
            WearableListView listView = (WearableListView) findViewById(R.id.list);
            Adapter adapter = new Adapter(this, items);
            listView.setAdapter(adapter);
            listView.setClickListener(this);
            listView.scrollToPosition(adapter.getPositionByValue(selected));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(WearableListView.ViewHolder v) {
        Intent i = new Intent();
        i.putExtra(EXTRA_SELECTED_ITEM, (String)v.itemView.getTag());
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void onTopEmptyRegionClick() {
    }

    private static final class Adapter extends WearableListView.Adapter {
        private final LayoutInflater mInflater;
        private final String[] labels;
        private final String[] values;
        private final int[] imageResources;
        private final int count;

        private Adapter(Context context, String[] items) {
            mInflater = LayoutInflater.from(context);

            count = items.length;
            labels = new String[count];
            values = new String[count];
            imageResources = new int[count];

            for(int c = 0; c < count; c++) {
                String[] parts = items[c].split("\\|", 3);
                labels[c] = parts[0];
                values[c] = parts[1];
                imageResources[c] = Integer.parseInt(parts[2]);
            }
        }

        public int getPositionByValue(String value) {
            if(value != null) {
                for (int c = 0; c < values.length; c++) {
                    if (values[c].equals(value)) {
                        return c;
                    }
                }
            }
            return 0;
        }

        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new WearableListView.ViewHolder(
                    mInflater.inflate(R.layout.selection_list_item, null));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
            TextView view = (TextView) holder.itemView.findViewById(R.id.name);
            view.setText(labels[position]);
            holder.itemView.setTag(values[position]);
            if(imageResources[position] > 0) {
                ImageView imageView = (ImageView) holder.itemView.findViewById(R.id.circle);
                imageView.setImageResource(imageResources[position]);
            }
        }

        @Override
        public int getItemCount() {
            return count;
        }
    }
}
