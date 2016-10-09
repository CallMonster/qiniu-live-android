package com.qiniu.qlive.activity.live;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.android.dns.DnsManager;
import com.qiniu.android.dns.IResolver;
import com.qiniu.android.dns.NetworkInfo;
import com.qiniu.android.dns.http.DnspodFree;
import com.qiniu.android.dns.local.AndroidDnsServer;
import com.qiniu.android.dns.local.Resolver;
import com.qiniu.pili.droid.streaming.AVCodecType;
import com.qiniu.pili.droid.streaming.AudioSourceCallback;
import com.qiniu.pili.droid.streaming.CameraStreamingSetting;
import com.qiniu.pili.droid.streaming.FrameCapturedCallback;
import com.qiniu.pili.droid.streaming.MediaStreamingManager;
import com.qiniu.pili.droid.streaming.StreamStatusCallback;
import com.qiniu.pili.droid.streaming.StreamingPreviewCallback;
import com.qiniu.pili.droid.streaming.StreamingProfile;
import com.qiniu.pili.droid.streaming.StreamingSessionListener;
import com.qiniu.pili.droid.streaming.StreamingState;
import com.qiniu.pili.droid.streaming.StreamingStateChangedListener;
import com.qiniu.pili.droid.streaming.SurfaceTextureCallback;
import com.qiniu.qlive.activity.MainActivity;
import com.qiniu.qlive.activity.R;
import com.qiniu.qlive.activity.widget.CameraPreviewFrameView;
import com.qiniu.qlive.activity.widget.FBO;
import com.qiniu.qlive.activity.widget.RotateLayout;
import com.qiniu.qlive.config.APICode;
import com.qiniu.qlive.config.StreamQuality;
import com.qiniu.qlive.service.LiveStreamService;
import com.qiniu.qlive.service.result.StopPublishResult;
import com.qiniu.qlive.utils.AsyncRun;
import com.qiniu.qlive.utils.Tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;


public class StreamingBaseActivity extends Activity implements
        StreamQuality,
        View.OnLayoutChangeListener,
        StreamStatusCallback,
        StreamingPreviewCallback,
        SurfaceTextureCallback,
        AudioSourceCallback,
        CameraPreviewFrameView.Listener,
        StreamingSessionListener,
        StreamingStateChangedListener {

    private static final String TAG = "StreamingBaseActivity";

//    protected static final int MSG_UPDATE_SHUTTER_BUTTON_STATE = 0;
    protected static final int MSG_MUTE             = 1;
    protected static final int MSG_SET_ZOOM           = 2;
    protected static final int MSG_START_STREAMING    = 3;
    protected static final int MSG_STOP_STREAMING     = 4;
    protected static final int MSG_FB               = 5;
    private static final int ZOOM_MINIMUM_WAIT_MILLIS = 33; //ms

    //view
    protected TextView mLogTextView;//log textview
    protected TextView mSatusTextView;//status textview
    protected TextView mStreamStatus; //stream bitrate textview
    protected Button mMuteButton;//mute button
    protected Button mTorchBtn;//torch button
    protected Button mCaptureFrameBtn; //capture picture
    protected Button mCameraSwitchBtn; //switch camera
//    protected Button mEncodingOrientationSwitcherBtn;//orientation switch button
    protected Button mFaceBeautyBtn;//



    //
    private boolean mIsNeedMute = false;
    private boolean isEncOrientationPort = true;
//    private boolean mOrientationChanged = false;
    private boolean mIsNeedFB = false;


    protected Button mShutterButton;
    protected boolean mShutterButtonPressed = false;
    protected String mStatusMsgContent;
    protected String mLogContent = "\n";



    protected MediaStreamingManager mMediaStreamingManager;
    protected JSONObject mJSONObject;
    protected Context context;
    private String publishId;
    private String sessionId;
    private RotateLayout mRotateLayout;
    private int mCurrentZoom = 0;
    private int mMaxZoom = 0;
    protected boolean mIsReady = false;
    private int mCurrentCamFacingIndex;

    protected Switcher mSwitcher = new Switcher();
    protected Screenshooter mScreenshooter = new Screenshooter();
//    protected EncodingOrientationSwitcher mEncodingOrientationSwitcher = new EncodingOrientationSwitcher();


    private FBO mFBO = new FBO();

    protected StreamingProfile mProfile;
    protected CameraStreamingSetting mCameraStreamingSetting;

    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_STREAMING:
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // disable the shutter button before startStreaming
                            setShutterButtonEnabled(false);
                            boolean res = mMediaStreamingManager.startStreaming();
                            mShutterButtonPressed = true;
                            Log.i(TAG, "res:" + res);
                            if (!res) {
                                mShutterButtonPressed = false;
                                setShutterButtonEnabled(true);
                            }
                            setShutterButtonPressed(mShutterButtonPressed);
                        }
                    }).start();
                    break;
                case MSG_STOP_STREAMING:
                    if (mShutterButtonPressed) {
                        // disable the shutter button before stopStreaming
                        setShutterButtonEnabled(false);
                        boolean res = mMediaStreamingManager.stopStreaming();
                        if (!res) {
                            mShutterButtonPressed = true;
                            setShutterButtonEnabled(true);
                        }
                        setShutterButtonPressed(mShutterButtonPressed);
                    }
                    break;
                case MSG_FB:
                    mIsNeedFB = !mIsNeedFB;
                    mMediaStreamingManager.setVideoFilterType(mIsNeedFB ?
                            CameraStreamingSetting.VIDEO_FILTER_TYPE.VIDEO_FILTER_BEAUTY
                            : CameraStreamingSetting.VIDEO_FILTER_TYPE.VIDEO_FILTER_NONE);
                    setFBEnables();
                    break;
                case MSG_MUTE:
                    mIsNeedMute = !mIsNeedMute;
                    mMediaStreamingManager.mute(mIsNeedMute);
                    setMuteButtonPressed();
                    break;
                case MSG_SET_ZOOM:
                    mMediaStreamingManager.setZoomValue(mCurrentZoom);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //set activity orientation
        int streamOrientation = this.getIntent().getIntExtra("stream_orientation", 0);
        int orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        StreamingProfile.ENCODING_ORIENTATION streamingOrientation = StreamingProfile.ENCODING_ORIENTATION.PORT;
        switch (streamOrientation) {
            case 0:
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                streamingOrientation=StreamingProfile.ENCODING_ORIENTATION.LAND;
                isEncOrientationPort = false;
                break;
            case 1:
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                streamingOrientation=StreamingProfile.ENCODING_ORIENTATION.PORT;
                isEncOrientationPort = true;
                break;
        }
        setRequestedOrientation(orientation);

        setContentView(R.layout.activity_camera_streaming);

        //set video and audio quality
        int streamQuality = this.getIntent().getIntExtra("stream_quality", StreamQuality.LOW_QUALITY);
        int videoQuality = 0;
        int audioQuality = 0;
        int encodingLevel = 0;
        switch (streamQuality) {
            case LOW_QUALITY:
                videoQuality = StreamingProfile.VIDEO_QUALITY_LOW3;
                audioQuality = StreamingProfile.AUDIO_QUALITY_LOW1;
                encodingLevel = StreamingProfile.VIDEO_ENCODING_HEIGHT_240;
                break;
            case STANDARD_QUALITY:
                videoQuality = StreamingProfile.VIDEO_QUALITY_MEDIUM3;
                audioQuality = StreamingProfile.AUDIO_QUALITY_MEDIUM2;
                encodingLevel = StreamingProfile.VIDEO_ENCODING_HEIGHT_480;
                break;
            case HIGH_QUALITY:
                videoQuality = StreamingProfile.VIDEO_QUALITY_HIGH1;
                audioQuality = StreamingProfile.AUDIO_QUALITY_HIGH1;
                encodingLevel = StreamingProfile.VIDEO_ENCODING_HEIGHT_544;
                break;
            case SUPER_QUALITY:
                videoQuality = StreamingProfile.VIDEO_QUALITY_HIGH3;
                audioQuality = StreamingProfile.AUDIO_QUALITY_HIGH2;
                encodingLevel = StreamingProfile.VIDEO_ENCODING_HEIGHT_720;
                break;
        }

        this.context = this;
        this.sessionId = Tools.getSession(this.context).getId();

        String streamJsonStrFromServer = getIntent().getStringExtra("stream_json_str");
        this.publishId = getIntent().getStringExtra("publish_id");
        Log.i(TAG, "streamJsonStrFromServer:" + streamJsonStrFromServer);

        mProfile = new StreamingProfile();
        try {
            mJSONObject = new JSONObject(streamJsonStrFromServer);
            StreamingProfile.Stream stream = new StreamingProfile.Stream(mJSONObject);
            mProfile.setStream(stream);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mProfile.setVideoQuality(videoQuality)
                .setAudioQuality(audioQuality)
                .setEncodingSizeLevel(encodingLevel)
                .setEncoderRCMode(StreamingProfile.EncoderRCModes.QUALITY_PRIORITY)
                .setDnsManager(getMyDnsManager())
                .setStreamStatusConfig(new StreamingProfile.StreamStatusConfig(3))
                .setEncodingOrientation(streamingOrientation)
                .setSendingBufferProfile(new StreamingProfile.SendingBufferProfile(0.2f, 0.8f, 3.0f, 20 * 1000));

        CameraStreamingSetting.CAMERA_FACING_ID cameraFacingId = chooseCameraFacingId();
        mCurrentCamFacingIndex = cameraFacingId.ordinal();
        mCameraStreamingSetting = new CameraStreamingSetting();
        mCameraStreamingSetting.setCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)
                .setContinuousFocusModeEnabled(true)
                .setRecordingHint(false)
                .setCameraFacingId(cameraFacingId)
                .setBuiltInFaceBeautyEnabled(true)
                .setResetTouchFocusDelayInMs(3000)
//                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.SMALL)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_16_9)
                .setFaceBeautySetting(new CameraStreamingSetting.FaceBeautySetting(1.0f, 1.0f, 0.8f))
                .setVideoFilter(CameraStreamingSetting.VIDEO_FILTER_TYPE.VIDEO_FILTER_BEAUTY);
        mIsNeedFB = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "tid:" + Thread.currentThread().getId());
        try {
            mMediaStreamingManager.resume();
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
        mMediaStreamingManager.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaStreamingManager.destroy();
    }

    @Override
    public void onStateChanged(StreamingState streamingState, Object extra) {
        Log.i(TAG, "onStateChanged state:" + streamingState);
        switch (streamingState) {
            case PREPARING:
                mStatusMsgContent = getString(R.string.string_state_preparing);
                break;
            case READY:
                mStatusMsgContent = getString(R.string.string_state_ready);
                mIsReady = true;
                mMaxZoom = mMediaStreamingManager.getMaxZoom();
                mStatusMsgContent = getString(R.string.string_state_ready);
                // start streaming when READY
                startStreaming();
                break;
            case CONNECTING:
                mStatusMsgContent = getString(R.string.string_state_connecting);
                break;
            case STREAMING:
                mStatusMsgContent = getString(R.string.string_state_streaming);
                setShutterButtonEnabled(true);
                break;
            case SHUTDOWN:
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                setShutterButtonPressed(false);
                break;
            case IOERROR:
                mLogContent += "IOERROR\n";
                mStatusMsgContent = getString(R.string.string_state_ready);
                setShutterButtonEnabled(true);
                break;
            case UNKNOWN:
                mStatusMsgContent = getString(R.string.string_state_ready);
                break;
            case SENDING_BUFFER_EMPTY:
                break;
            case SENDING_BUFFER_FULL:
                break;
            case AUDIO_RECORDING_FAIL:
                break;
            case OPEN_CAMERA_FAIL:
                mStatusMsgContent = getString(R.string.string_state_open_camera_fail);
                break;
            case DISCONNECTED:
                mLogContent += "DISCONNECTED\n";
                mStatusMsgContent = getString(R.string.string_state_disconnet);
                break;
            case INVALID_STREAMING_URL:
                mStatusMsgContent = getString(R.string.string_state_invalid_url);
                break;
            case UNAUTHORIZED_STREAMING_URL:
                mLogContent += "Unauthorized Url\n";
                mStatusMsgContent = getString(R.string.string_state_invalid_url);
                break;
            case CAMERA_SWITCHED:
                if (extra != null) {
                    Log.i(TAG, "current camera id:" + extra);
                }
                break;
            case TORCH_INFO:
                if (extra != null) {
                    final boolean isSupportedTorch = (Boolean) extra;
                    Log.i(TAG, "isSupportedTorch=" + isSupportedTorch);
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //快关闪关灯
                            if (isSupportedTorch) {
                                mTorchBtn.setVisibility(View.VISIBLE);
                            } else {
                                mTorchBtn.setVisibility(View.GONE);
                            }
                        }
                    });
                }
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLogTextView != null) {
                    mLogTextView.setText(mLogContent);
                }
                mSatusTextView.setText(mStatusMsgContent);
            }
        });
    }

    protected void setMuteButtonPressed(){
        if (mMuteButton != null) {
            mMuteButton.setText(mIsNeedMute ? "Unmute" : "Mute");
        }
    }

    protected void setTorchEnabled(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String flashlight = enabled ? getString(R.string.flash_light_off) : getString(R.string.flash_light_on);
                mTorchBtn.setText(flashlight);
            }
        });
    }

    protected void setFBEnables(){
        if (mFaceBeautyBtn != null) {
            mFaceBeautyBtn.setText(mIsNeedFB ? "FB Off" : "FB On");
        }
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

    protected void setFocusAreaIndicator() {
        if (mRotateLayout == null) {
            mRotateLayout = (RotateLayout)findViewById(R.id.focus_indicator_rotate_layout);
            mMediaStreamingManager.setFocusAreaIndicator(mRotateLayout,
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
            mMediaStreamingManager.doSingleTapUp((int) e.getX(), (int) e.getY());
            return true;
        }
        return false;
    }

    @Override
    public boolean onZoomValueChanged(float factor) {
        if (mIsReady && mMediaStreamingManager.isZoomSupported()) {
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
        mMediaStreamingManager.updateEncodingType(AVCodecType.SW_VIDEO_CODEC);
        mMediaStreamingManager.startStreaming();
        return true;
    }

    @Override
    public boolean onRestartStreamingHandled(int i) {
        Log.i(TAG, "onRestartStreamingHandled");
        return mMediaStreamingManager.startStreaming();
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
    public void notifyStreamStatusChanged(final StreamingProfile.StreamStatus streamStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamStatus.setText("bitrate:" + streamStatus.totalAVBitrate / 1024 + " kbps"
                        + "\naudio:" + streamStatus.audioFps + " fps"
                        + "\nvideo:" + streamStatus.videoFps + " fps");
            }
        });
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

    }

    @Override
    public boolean onPreviewFrame(byte[] bytes, int i, int i1) {
        return false;
    }

    @Override
    public void onAudioSourceAvailable(ByteBuffer byteBuffer, int i, long l, boolean b) {

    }

    //switch camera
    private class Switcher implements Runnable {
        @Override
        public void run() {
            mCurrentCamFacingIndex = (mCurrentCamFacingIndex + 1) % CameraStreamingSetting.getNumberOfCameras();

            CameraStreamingSetting.CAMERA_FACING_ID facingId;
            if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK.ordinal()) {
                facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
            } else if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT.ordinal()) {
                facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
            } else {
                facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
            }
            Log.i(TAG, "switchCamera:" + facingId);
            mMediaStreamingManager.switchCamera(facingId);
        }
    }

    //switch orientation
    private class EncodingOrientationSwitcher implements Runnable {

        @Override
        public void run() {
            Log.i(TAG, "----->isEncOrientationPort:" + isEncOrientationPort);
            stopStreaming();
            isEncOrientationPort = !isEncOrientationPort;
            Log.i(TAG, "----->isEncOrientationPort:" + isEncOrientationPort);
            mProfile.setEncodingOrientation(isEncOrientationPort ? StreamingProfile.ENCODING_ORIENTATION.PORT : StreamingProfile.ENCODING_ORIENTATION.LAND);
            mMediaStreamingManager.setStreamingProfile(mProfile);
            setRequestedOrientation(isEncOrientationPort ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mMediaStreamingManager.notifyActivityOrientationChanged();
            Toast.makeText(StreamingBaseActivity.this, R.string.switch_orientation_hint, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "EncodingOrientationSwitcher -");
        }
    }

    protected void startStreaming(){
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_STREAMING), 50);
    }

    protected void stopStreaming(){
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_STOP_STREAMING), 50);
    }


    //capture picture
    private class Screenshooter implements Runnable {
        @Override
        public void run() {
            final String fileName = "PLStreaming_" + System.currentTimeMillis() + ".jpg";
            mMediaStreamingManager.captureFrame(100, 100, new FrameCapturedCallback() {
                private Bitmap bitmap;

                @Override
                public void onFrameCaptured(Bitmap bmp) {
                    bitmap = bmp;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                saveToSDCard(fileName, bitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                if (bitmap != null) {
                                    bitmap.recycle();
                                    bitmap = null;
                                }
                            }
                        }
                    }).start();
                }
            });
        }
    }

    public void saveToSDCard(String filename, Bitmap bmp) throws IOException {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(Environment.getExternalStorageDirectory(), filename);
            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(file));
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
                bmp.recycle();
                bmp = null;
            } finally {
                if (bos != null) bos.close();
            }

            final String info = "Save frame to:" + Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, info, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private CameraStreamingSetting.CAMERA_FACING_ID chooseCameraFacingId() {
        if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD)) {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
        } else if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT)) {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
        } else {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
        }
    }
}
