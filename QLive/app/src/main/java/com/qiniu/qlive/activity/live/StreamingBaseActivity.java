package com.qiniu.qlive.activity.live;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pili.pldroid.streaming.CameraStreamingManager;
import com.pili.pldroid.streaming.StreamStatusCallback;
import com.pili.pldroid.streaming.StreamingProfile;
import com.pili.pldroid.streaming.SurfaceTextureCallback;
import com.qiniu.android.dns.DnsManager;
import com.qiniu.android.dns.IResolver;
import com.qiniu.android.dns.NetworkInfo;
import com.qiniu.android.dns.http.DnspodFree;
import com.qiniu.android.dns.local.AndroidDnsServer;
import com.qiniu.android.dns.local.Resolver;
import com.qiniu.qlive.activity.MainActivity;
import com.qiniu.qlive.activity.R;
import com.qiniu.qlive.activity.widget.CameraPreviewFrameView;
import com.qiniu.qlive.activity.widget.FBO;
import com.qiniu.qlive.activity.widget.RotateLayout;
import com.qiniu.qlive.config.APICode;
import com.qiniu.qlive.service.LiveStreamService;
import com.qiniu.qlive.service.result.StopPublishResult;
import com.qiniu.qlive.utils.AsyncRun;
import com.qiniu.qlive.utils.Tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;


public class StreamingBaseActivity extends Activity implements CameraStreamingManager.StreamingStateListener,CameraPreviewFrameView.Listener,SurfaceTextureCallback,CameraStreamingManager.StreamingSessionListener,StreamStatusCallback{

    protected static final int MSG_UPDATE_SHUTTER_BUTTON_STATE = 0;
    private static final int MSG_SET_ZOOM           = 2;
    private static final int ZOOM_MINIMUM_WAIT_MILLIS = 33; //ms
    private static final String TAG = "StreamingBaseActivity";
    protected Button mShutterButton;
    protected boolean mShutterButtonPressed = false;
    protected String mStatusMsgContent;
    protected TextView mSatusTextView;
    protected CameraStreamingManager mCameraStreamingManager;
    protected JSONObject mJSONObject;
    private Context context;
    private String publishId;
    private String sessionId;
    private RotateLayout mRotateLayout;
    private int mCurrentZoom = 0;
    private int mMaxZoom = 0;
    protected boolean mIsReady = false;

    private FBO mFBO = new FBO();

    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_SHUTTER_BUTTON_STATE:
                    if (!mShutterButtonPressed) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // disable the shutter button before startStreaming
                                setShutterButtonEnabled(false);
                                boolean res = mCameraStreamingManager.startStreaming();
                                mShutterButtonPressed = true;
                                Log.i(TAG, "res:" + res);
                                if (!res) {
                                    mShutterButtonPressed = false;
                                    setShutterButtonEnabled(true);
                                }
                                setShutterButtonPressed(mShutterButtonPressed);
                            }
                        }).start();
                    } else {
                        // disable the shutter button before stopStreaming
                        setShutterButtonEnabled(false);
                        mCameraStreamingManager.stopStreaming();
                        setShutterButtonPressed(false);
                        Log.i(TAG, "fire the stream stop publishing");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                StopPublishResult stopResult = LiveStreamService.stopPublish(sessionId, publishId);
                                int code = 0;
                                String desc = "";
                                if (stopResult != null) {
                                    if (stopResult.getCode() == APICode.API_OK) {
                                        code = stopResult.getCode();
                                        desc = "停止推流请求成功";
                                    } else {
                                        code = 0;
                                        desc = String.format("%s:%s", stopResult.getDesc(), stopResult.getDesc());
                                    }
                                } else {
                                    code = 0;
                                    desc = "请求失败，停止推流请求失败";
                                }
                                Log.d(TAG,"--->code : " + code +";desc :" + desc);
                                AsyncRun.run(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent intent = new Intent(context, MainActivity.class);
                                        startActivity(intent);
                                        finish();
                                    }
                                });
                            }
                        }).start();

                    }
                    break;
                case MSG_SET_ZOOM:
                    mCameraStreamingManager.setZoomValue(mCurrentZoom);
                    break;
                default:
                    Log.e(TAG, "Invalid message");
            }
        }
    };

    public String getSessionId() {
        return sessionId;
    }

    public String getPublishId() {
        return publishId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = this;
        this.sessionId = Tools.getSession(this.context).getId();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        String streamJsonStrFromServer = getIntent().getStringExtra("stream_json_str");
        this.publishId = getIntent().getStringExtra("publish_id");
        Log.i(TAG, "streamJsonStrFromServer:" + streamJsonStrFromServer);
        try {
            mJSONObject = new JSONObject(streamJsonStrFromServer);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "tid:" + Thread.currentThread().getId());
        try {
            mCameraStreamingManager.resume();
        } catch (Exception e) {
            Toast.makeText(StreamingBaseActivity.this, "Device open error!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mIsReady = false;
        mShutterButtonPressed = false;
        mHandler.removeCallbacksAndMessages(null);
        mCameraStreamingManager.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraStreamingManager.destroy();
    }

    @Override
    public void onStateChanged(final int state, Object extra) {
        Log.i(TAG, "onStateChanged state:" + state);
        switch (state) {
            case CameraStreamingManager.STATE.PREPARING:
                mStatusMsgContent = getString(R.string.string_state_preparing);
                break;
            case CameraStreamingManager.STATE.READY:
                mStatusMsgContent = getString(R.string.string_state_ready);
                // start streaming when READY
                onShutterButtonClick();
                break;
            case CameraStreamingManager.STATE.CONNECTING:
                mStatusMsgContent = getString(R.string.string_state_connecting);
                break;
            case CameraStreamingManager.STATE.STREAMING:
                mStatusMsgContent = getString(R.string.string_state_streaming);
                setShutterButtonEnabled(true);
                break;
            case CameraStreamingManager.STATE.SHUTDOWN:
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                setShutterButtonPressed(false);
                break;
            case CameraStreamingManager.STATE.IOERROR:
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                break;
            case CameraStreamingManager.STATE.UNKNOWN:
                mStatusMsgContent = getString(R.string.string_state_ready);
                break;
            case CameraStreamingManager.STATE.SENDING_BUFFER_EMPTY:
                break;
            case CameraStreamingManager.STATE.SENDING_BUFFER_FULL:
                break;
            case CameraStreamingManager.STATE.AUDIO_RECORDING_FAIL:
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSatusTextView.setText(mStatusMsgContent);
            }
        });
    }

    @Override
    public boolean onStateHandled(final int state, Object extra) {
        Log.i(TAG, "onStateHandled state:" + state);
        return false;
    }

    protected void setShutterButtonPressed(final boolean pressed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutterButtonPressed = pressed;
                mShutterButton.setPressed(pressed);
            }
        });
    }

    protected void setShutterButtonEnabled(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutterButton.setFocusable(enable);
                mShutterButton.setClickable(enable);
                mShutterButton.setEnabled(enable);
            }
        });
    }

    protected void onShutterButtonClick() {
        mHandler.removeMessages(MSG_UPDATE_SHUTTER_BUTTON_STATE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SHUTTER_BUTTON_STATE), 50);
    }

    protected void setFocusAreaIndicator() {
        if (mRotateLayout == null) {
            mRotateLayout = (RotateLayout)findViewById(R.id.focus_indicator_rotate_layout);
            mCameraStreamingManager.setFocusAreaIndicator(mRotateLayout,
                    mRotateLayout.findViewById(R.id.focus_indicator));
        }
    }

    protected static DnsManager getMyDnsManager() {
        IResolver r0 = new DnspodFree();
        IResolver r1 = AndroidDnsServer.defaultResolver();
        IResolver r2 = null;
        try {
            r2 = new Resolver(InetAddress.getByName("119.29.29.29"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return new DnsManager(NetworkInfo.normal, new IResolver[]{r0, r1, r2});
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        Log.i(TAG, "onSingleTapUp X:" + e.getX() + ",Y:" + e.getY());

        if (mIsReady) {
            setFocusAreaIndicator();
            mCameraStreamingManager.doSingleTapUp((int) e.getX(), (int) e.getY());
            return true;
        }
        return false;
    }

    @Override
    public boolean onZoomValueChanged(float factor) {
        if (mIsReady && mCameraStreamingManager.isZoomSupported()) {
            mCurrentZoom = (int) (mMaxZoom * factor);
            mCurrentZoom = Math.min(mCurrentZoom, mMaxZoom);
            mCurrentZoom = Math.max(0, mCurrentZoom);

            Log.d(TAG, "zoom ongoing, scale: " + mCurrentZoom + ",factor:" + factor + ",maxZoom:" + mMaxZoom);
            if (!mHandler.hasMessages(MSG_SET_ZOOM)) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SET_ZOOM), ZOOM_MINIMUM_WAIT_MILLIS);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onRecordAudioFailedHandled(int i) {
        mCameraStreamingManager.updateEncodingType(CameraStreamingManager.EncodingType.SW_VIDEO_CODEC);
        mCameraStreamingManager.startStreaming();
        return true;
    }

    @Override
    public boolean onRestartStreamingHandled(int i) {
        Log.i(TAG, "onRestartStreamingHandled");
        return mCameraStreamingManager.startStreaming();
    }

    @Override
    public Camera.Size onPreviewSizeSelected(List<Camera.Size> list) {
        return null;
    }

    @Override
    public void onSurfaceCreated() {
        Log.i(TAG, "onSurfaceCreated");
        mFBO.initialize(this);
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged width:" + width + ",height:" + height);
        mFBO.updateSurfaceSize(width, height);
    }

    @Override
    public void onSurfaceDestroyed() {
        Log.i(TAG, "onSurfaceDestroyed");
        mFBO.release();
    }

    @Override
    public int onDrawFrame(int texId, int texWidth, int texHeight, float[] floats) {
        int newTexId = mFBO.drawFrame(texId, texWidth, texHeight);
//        Log.i(TAG, "onDrawFrame texId:" + texId + ",newTexId:" + newTexId + ",texWidth:" + texWidth + ",texHeight:" + texHeight);
        return newTexId;
    }

    @Override
    public void notifyStreamStatusChanged(StreamingProfile.StreamStatus streamStatus) {
        Log.d(TAG,"--->bitrate:" + streamStatus.totalAVBitrate / 1024 + " kbps"
                + "\naudio:" + streamStatus.audioFps + " fps"
                + "\nvideo:" + streamStatus.videoFps + " fps");
    }
}
