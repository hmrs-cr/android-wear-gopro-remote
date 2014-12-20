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

import android.app.Application;

public class BaseApp extends Application {
	
	private static final String TAG = "BaseApp";

	@Override 
	public void onCreate() {
		super.onCreate();
	    CrashCatcher.init();
        Logger.init(getApplicationContext());
		if(Logger.DEBUG)  Logger.debug(TAG, "onCreate");
	}
}
