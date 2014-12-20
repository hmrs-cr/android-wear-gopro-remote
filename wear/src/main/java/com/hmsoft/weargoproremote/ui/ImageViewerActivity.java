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

package com.hmsoft.weargoproremote.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.ImageReference;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hmsoft.libcommon.constants.WearMessages;
import com.hmsoft.libcommon.general.Logger;
import com.hmsoft.weargoproremote.BuildConfig;
import com.hmsoft.weargoproremote.R;
import com.hmsoft.weargoproremote.WearApplication;
import com.hmsoft.weargoproremote.cache.ThumbCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class ImageViewerActivity extends Activity implements View.OnClickListener,
        View.OnLongClickListener, GridViewPager.OnPageChangeListener {

    public static final String EXTRA_THUMBNAIL = BuildConfig.APPLICATION_ID + ".EXTRA_THUMBNAIL";
	public static final String EXTRA_THUMB_FILENAME = BuildConfig.APPLICATION_ID + ".EXTRA_THUMB_FILENAME";
    public static final String REFRESH_FILE_LIST = BuildConfig.APPLICATION_ID + ".REFRESH_FILE_LIST";
    private static final String TAG = "ImageViewerActivity";

    private GridViewPager mPager;
    private ImageView mImageViewAnim;
    private boolean mClearCache;

    public static void showImage(Context context, String thumbFileName, byte[] thumb) {
        Intent i = new Intent(context, ImageViewerActivity.class);
        if(thumb != null) i.putExtra(EXTRA_THUMBNAIL, thumb);
		if(thumbFileName != null) i.putExtra(EXTRA_THUMB_FILENAME, thumbFileName);
        context.startActivity(i);
    }

    public static void refreshImageList(Context context) {
        Intent i = new Intent(context, ImageViewerActivity.class);
        i.putExtra(REFRESH_FILE_LIST, 1);
        context.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_viewer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPager = (GridViewPager)findViewById(R.id.pagerView);
        mImageViewAnim = (ImageView)findViewById(R.id.imageViewAnim);
        setImageFromIntent(getIntent(), false);
        mPager.setOnPageChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        ThumbCache.clear();
        super.onDestroy();
    }

    void loadImagesAdapter(Bitmap image, int imageIndex) {
		mPager.setAdapter(new Adapter(this, image, imageIndex));
        onPageSelected(0, 0);
     }

    private void setImageFromIntent(Intent i, boolean animate) {
        if(i != null) {
            if(i.hasExtra(REFRESH_FILE_LIST)) {
                ThumbCache.clearThumbFileList();
                ThumbCache.getThumbFileList(this);
                loadImagesAdapter(null, 0);
                return;
            }

            byte[] thumb = i.getByteArrayExtra(EXTRA_THUMBNAIL);
            if(thumb != null && thumb.length > 0) {                
				final Bitmap bitmap = BitmapFactory.decodeByteArray(thumb, 0, thumb.length);
				String imageFileName = i.getStringExtra(EXTRA_THUMB_FILENAME);
				if(!TextUtils.isEmpty(imageFileName)) {
					File imageFile = new File(imageFileName);
					ThumbCache.saveThumbToMemCache(bitmap, imageFile);
					ThumbCache.addToThumbFileList(this, imageFile);
				}
				if(animate) {
                    mImageViewAnim.setImageBitmap(bitmap);
                    mImageViewAnim.setTranslationX(mPager.getWidth());
                    mImageViewAnim.setRotation(40);
                    mImageViewAnim.setVisibility(View.VISIBLE);
                    mImageViewAnim.animate().setDuration(400).translationX(0).rotation(0).withEndAction(new Runnable() {
                        public void run() {
							loadImagesAdapter(bitmap, 0);
                            mImageViewAnim.setVisibility(View.GONE);
                        }
                    });
                } else {
					loadImagesAdapter(bitmap, 0);
				}
                return;
            }
        }
        loadImagesAdapter(null, 0);
    }

    private void sendToWear(String message, byte[] data) {
        WearApplication app = (WearApplication)getApplication();
        if(app != null) {
            if(app.MessageSender != null) {
                app.MessageSender.sendWearMessage(message, data, null);

            } else {
                Logger.error(TAG, "WearApplication.MessageSender is null.");
            }
        } else {
            Logger.error(TAG, "Application is not WearApplication");
        }
    }

    public void onClick(View v) {
        if(Logger.DEBUG) {
            mClearCache = false;
            sendToWear(WearMessages.MESSAGE_SHUTTER, null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setImageFromIntent(intent, true);
        super.onNewIntent(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        List<File> files = ThumbCache.getThumbFileList(this);
        if(mClearCache) {
            boolean ok = false;
            for(File file : files) {
                ok = file.delete();
                if(Logger.DEBUG) {
                    if(ok) {
                        Logger.debug(TAG, "File %s deleted", file);
                    } else {
                        Logger.warning(TAG, "Failed to delete %s", file);
                    }
                }
            }
            if(ok) {
                ThumbCache.clear();
                loadImagesAdapter(null, 0);
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
            }
            mClearCache = false;
        } else {
            mClearCache = true;
            long size = 0;
            for(File file : files) {
                size += file.length();
            }
            Toast.makeText(this, String.format("%d files (%.2fMB)", files.size(), size / 1024f / 1024f),
                    Toast.LENGTH_LONG).show();
        }
        return false;
    }

    @Override
    public void onPageScrolled(int i, int i2, float v, float v2, int i3, int i4) {

    }

    @Override
    public void onPageSelected(int i, int i2) {
        mClearCache = false;
        Logger.debug(TAG, "onPageSelected:%d-%d", i, i2);
        List<File> files = ThumbCache.getThumbFileList(this);
        int d =  mPager.getAdapter().getRowCount() - 1;
        int start = files.size() ;
        int end = 11;
        if(d < 0 || d == i) {
            if (start <= (Byte.MAX_VALUE - end)) {
                boolean firstSync = d == 0;
                if(firstSync) {
                    start = 1;
                    end = 100;
                }
                byte[] data = new byte[3];
                data[0] = (byte) start;
                data[1] = (byte) (start + end);
                data[2] = (byte) (firstSync ? 1 : 0);
                sendToWear(WearMessages.MESSAGE_SYNC_THUMB, data);
            }
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }   

    private class Adapter extends GridPagerAdapter {

        private static final String TAG = "ImageViewerActivity";
        private final Context mContext;
		private final Bitmap mFirstImage;
        private final int mFirstIndex;
        private final List<File> mFileList;
        private final int mCount;

        public Adapter(Context context, Bitmap firstImage, int firstIndex) {
			mFirstImage = firstImage;
            mFirstIndex = firstIndex;
            mContext = context;
            mFileList = ThumbCache.getThumbFileList(context);
            mCount = mFileList.size();
            Logger.debug(TAG, "Adapter created");
        }


        @Override
        public int getRowCount() {
            int rc = mCount;
            if(rc == 0) return  1;
            return rc;
        }

        @Override
        public int getColumnCount(int row) {
            return 1;
        }

        @Override
        public ImageReference getBackground(int row, int column) {
            if(column == 1) {
                File file = mFileList.get(mCount - (row + 1));
                Bitmap bitmap = ThumbCache.getThumbFromMemCache(file);
                if(bitmap != null) {
                    return ImageReference. forBitmap(bitmap);
                }
            }
            return super.getBackground(row, column);
        }

        @Override
        protected Object instantiateItem(ViewGroup viewGroup, int row, int col) {
            View view = null;

            if(col == 0) {
                view = View.inflate(mContext, R.layout.image_viewer, null);
                ImageView imageView = (ImageView) view.findViewById(R.id.imageView);
                TextView statusText = (TextView) view.findViewById(R.id.textViewStatus);
                if (Logger.DEBUG) imageView.setOnClickListener(ImageViewerActivity.this);
                imageView.setOnLongClickListener(ImageViewerActivity.this);

                File file = null;
                if (mCount > 0) {
                    file = mFileList.get(mCount - (row + 1));
                }
                statusText.setText(String.format("%d/%d", row + 1, mCount));
                if (mFirstImage != null && row == mFirstIndex) {
                    imageView.setImageBitmap(mFirstImage);
                } else {
                    if (file != null) {
                        Bitmap thumb = ThumbCache.getThumbFromMemCache(file);
                        if (thumb != null) {
                            imageView.setImageBitmap(thumb);
                            Logger.debug(TAG, "Got thumb %s from memcache.", file);
                        } else {
                            new LoadThumbTask(imageView, file).execute();
                        }
                    } else {
                        TextView textView = new TextView(mContext);
                        textView.setText(R.string.label_no_images);
                        textView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                        FrameLayout frame = ((FrameLayout) view);
                        frame.addView(textView);
                        frame.removeView(imageView);
                    }
                }

                if (file != null) {
                    ImageView imageViewVideo = (ImageView) view.findViewById(R.id.imageViewVideo);
                    String fname = file.getName().toUpperCase();
                    if (fname.endsWith("MP4")) {
                        imageViewVideo.setImageResource(R.drawable.detail_mode_icon_video);
                    } else if (fname.endsWith("JPG")) {
                        imageViewVideo.setImageResource(R.drawable.detail_mode_icon_photo);
                    } else {
                        imageViewVideo.setImageResource(R.drawable.detail_mode_icon_burst);
                    }
                }
                viewGroup.addView(view);
            }
            if(Logger.DEBUG) Logger.debug(TAG, "View created: %d/%d", row, col);
            return view;
        }

        @Override
        protected void destroyItem(ViewGroup viewGroup, int i, int i2, Object o) {
            View view = (View)o;            
            if(view != null) viewGroup.removeView(view);
            if(Logger.DEBUG) Logger.debug(TAG, "View Destroyed: %d/%d", i, i2);
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view != null && view.equals(o);
        }

        private class LoadThumbTask extends AsyncTask<Void, Void, Bitmap> {
            private File mImageFile;
            private ImageView mImageView;

            public LoadThumbTask(ImageView imageView, File file) {
                mImageFile = file;
                mImageView = imageView;
            }

            @Override
            protected Bitmap doInBackground(Void... params) {
                try {
					int l = (int)mImageFile.length();
					if(l <= 0) return null;
					
					int r;
					byte[] buffer = new byte[l];
                    try (FileInputStream is = new FileInputStream(mImageFile)) {
                        r = is.read(buffer);
                    }
					
					if(r <= 0) return null;
                    return BitmapFactory.decodeByteArray(buffer, 0, r);
					
                } catch (IOException e) {
                    Logger.error(TAG, "Load thumb from disk failed.", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if(bitmap != null) {
					ThumbCache.saveThumbToMemCache(bitmap, mImageFile);
                    if(mImageView != null) mImageView.setImageBitmap(bitmap);
					if(Logger.DEBUG) Logger.debug(TAG, "Loaded thumb %s from disk.", mImageFile);
                }
            }
        }
    }
}
