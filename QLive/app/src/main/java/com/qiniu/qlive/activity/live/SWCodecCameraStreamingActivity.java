package com.qiniu.qlive.activity.live;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.qiniu.pili.droid.streaming.AVCodecType;
import com.qiniu.pili.droid.streaming.CameraStreamingSetting;
import com.qiniu.pili.droid.streaming.MediaStreamingManager;
import com.qiniu.pili.droid.streaming.widget.AspectFrameLayout;
import com.qiniu.qlive.activity.R;
import com.qiniu.qlive.activity.widget.CameraPreviewFrameView;
import com.qiniu.qlive.config.APICode;
import com.qiniu.qlive.service.LiveStreamService;
import com.qiniu.qlive.service.result.StopPublishResult;
import com.qiniu.qlive.utils.AsyncRun;
import com.qiniu.qlive.utils.Tools;

public class SWCodecCameraStreamingActivity extends StreamingBaseActivity{
    private static final String TAG = "SWCodecCameraStreaming";
    private boolean mIsTorchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.FULL);
        CameraPreviewFrameView cameraPreviewFrameView = (CameraPreviewFrameView) findViewById(R.id.cameraPreview_surfaceView);
        cameraPreviewFrameView.setListener(this);

        mMediaStreamingManager = new MediaStreamingManager(this, afl, cameraPreviewFrameView, AVCodecType.SW_VIDEO_WITH_HW_AUDIO_CODEC);  // soft codec
        mMediaStreamingManager.prepare(mCameraStreamingSetting, mProfile);

        mMediaStreamingManager.setStreamingStateListener(this);
        mMediaStreamingManager.setSurfaceTextureCallback(this);
        mMediaStreamingManager.setStreamingSessionListener(this);
//        mMediaStreamingManager.setNativeLoggingEnabled(false);
        mMediaStreamingManager.setStreamStatusCallback(this);
        mMediaStreamingManager.setStreamingPreviewCallback(this);
        mMediaStreamingManager.setAudioSourceCallback(this);

        setFocusAreaIndicator();

        InitUI();
    }

    private void InitUI(){
        mSatusTextView = (TextView) findViewById(R.id.streamingStatus);
        mLogTextView = (TextView)findViewById(R.id.log_info);
        mStreamStatus = (TextView)findViewById(R.id.stream_status);
        mMuteButton = (Button)findViewById(R.id.mute_btn);
        mCaptureFrameBtn = (Button) findViewById(R.id.capture_btn);
        mCameraSwitchBtn = (Button) findViewById(R.id.camera_switch_btn);
//        mEncodingOrientationSwitcherBtn = (Button)findViewById(R.id.orientation_btn);
        mFaceBeautyBtn = (Button) findViewById(R.id.fb_btn);


        mShutterButton = (Button) findViewById(R.id.toggleRecording_button);
        mTorchBtn = (Button) findViewById(R.id.torch_btn);

        //mute
        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mHandler.hasMessages(MSG_MUTE)) {
                    mHandler.sendEmptyMessage(MSG_MUTE);
                }
            }
        });

        //torch
        mTorchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (!mIsTorchOn) {
                            mIsTorchOn = true;
                            mMediaStreamingManager.turnLightOn();
                        } else {
                            mIsTorchOn = false;
                            mMediaStreamingManager.turnLightOff();
                        }
                        setTorchEnabled(mIsTorchOn);
                    }
                }).start();
            }
        });

        //capture
        mCaptureFrameBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHandler.removeCallbacks(mScreenshooter);
                mHandler.postDelayed(mScreenshooter, 100);
            }
        });

        //camera switch
        mCameraSwitchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHandler.removeCallbacks(mSwitcher);
                mHandler.postDelayed(mSwitcher, 100);
            }
        });

        //orientation switch
//        mEncodingOrientationSwitcherBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mHandler.removeCallbacks(mEncodingOrientationSwitcher);
//                mHandler.postDelayed(mEncodingOrientationSwitcher,100);
//            }
//        });

        //face beauty
        mFaceBeautyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mHandler.hasMessages(MSG_FB)) {
                    mHandler.sendEmptyMessage(MSG_FB);
                }
            }
        });

        SeekBar seekBarBeauty = (SeekBar) findViewById(R.id.beautyLevel_seekBar);
        seekBarBeauty.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                CameraStreamingSetting.FaceBeautySetting fbSetting = mCameraStreamingSetting.getFaceBeautySetting();
                fbSetting.beautyLevel = progress / 100.0f;
                fbSetting.whiten = progress / 100.0f;
                fbSetting.redden = progress / 100.0f;

                mMediaStreamingManager.updateFaceBeautySetting(fbSetting);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mShutterButtonPressed) {
                    stopStreaming();
                } else {
                    startStreaming();
                }
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        mHandler.removeCallbacksAndMessages(null);
        mSwitcher = null;
        mScreenshooter = null;
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(context).setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("退出确认")
                .setMessage("直播推流中，确认退出？")
                .setPositiveButton("是", new QuitPublishHandler())
                .setNegativeButton("否", null).show();
    }

    class QuitPublishHandler implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //notify the end of stream
                    StopPublishResult stopResult = LiveStreamService.stopPublish(getSessionId(), getPublishId());
                    if (stopResult.getCode() == APICode.API_OK) {
                        Tools.showToast(context, "保存直播节目成功！");
                    } else {
                        //@TODO if notify failed, should record and notify next time
                        Tools.showToast(context, "保存直播节目失败！");
                    }
                    AsyncRun.run(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                }
            }).start();
        }
    }
}
