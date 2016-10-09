package com.qiniu.qlive;

import android.app.Application;

import com.qiniu.pili.droid.streaming.StreamingEnv;

/**
 * Created by Misty on 16/8/8.
 */
public class QLiveApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        StreamingEnv.init(getApplicationContext());
    }
}
