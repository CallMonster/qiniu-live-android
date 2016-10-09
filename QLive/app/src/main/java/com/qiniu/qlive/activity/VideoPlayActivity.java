package com.qiniu.qlive.activity;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.pili.pldroid.player.AVOptions;
import com.pili.pldroid.player.PLMediaPlayer;
import com.pili.pldroid.player.widget.PLVideoView;
import com.qiniu.qlive.activity.widget.MediaController;

public class VideoPlayActivity extends AppCompatActivity {
    private static final String TAG = "VideoPlayActivity";

    private static final int RESTART_PLAYER = 1;

    private PLVideoView videoPlayView;
    private Toast mToast = null;
    private MediaController videoPlayController;
    private int mOrientation;
    private String videoTitle;
    private String videoUrl;
    private boolean mIsActivityPaused = true;
    private long pos;
    private int isLiveStreaming;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case RESTART_PLAYER:
                    videoPlayView.setVideoPath(videoUrl);
                    videoPlayView.start();
                    if (isLiveStreaming == 0)
                    {
                        videoPlayView.seekTo(pos);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        int videoOrient = this.getIntent().getIntExtra("VideoOrientation", 0);
        this.mOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (videoOrient == 1) {
            this.mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        videoTitle = this.getIntent().getStringExtra("VideoTitle");
        videoUrl = this.getIntent().getStringExtra("VideoUrl");

        this.setRequestedOrientation(this.mOrientation);
        this.getSupportActionBar().hide();

        setContentView(R.layout.activity_video_play);
        this.initVideoPlay();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsActivityPaused = false;
        videoPlayView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mToast = null;
        mIsActivityPaused = true;
        videoPlayView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoPlayView.stopPlayback();
    }

    private void initVideoPlay() {
        this.videoPlayController = new MediaController(this);
        this.videoPlayView = (PLVideoView) this
                .findViewById(R.id.pili_video_play_pldplayer);
        videoPlayView.setMediaController(videoPlayController);
        videoPlayController.setMediaPlayer(videoPlayView);
        videoPlayController.setAnchorView(videoPlayView);

        this.setTitle(videoTitle);

        AVOptions options = new AVOptions();

        isLiveStreaming = getIntent().getIntExtra("liveStreaming", 1);
        // the unit of timeout is ms
        options.setInteger(AVOptions.KEY_PREPARE_TIMEOUT, 10 * 1000);
        options.setInteger(AVOptions.KEY_GET_AV_FRAME_TIMEOUT, 10 * 1000);
        // Some optimization with buffering mechanism when be set to 1
        options.setInteger(AVOptions.KEY_LIVE_STREAMING, isLiveStreaming);
        if (isLiveStreaming == 1) {
            options.setInteger(AVOptions.KEY_DELAY_OPTIMIZATION, 1);
        }

        // 1 -> hw codec enable, 0 -> disable [recommended]
        int codec = getIntent().getIntExtra("mediaCodec", 0);
        options.setInteger(AVOptions.KEY_MEDIACODEC, codec);

        // whether start play automatically after prepared, default value is 1
        options.setInteger(AVOptions.KEY_START_ON_PREPARED, 0);

        videoPlayView.setAVOptions(options);

        // Set some listeners
        videoPlayView.setOnInfoListener(mOnInfoListener);
        videoPlayView.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
        videoPlayView.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        videoPlayView.setOnCompletionListener(mOnCompletionListener);
        videoPlayView.setOnSeekCompleteListener(mOnSeekCompleteListener);
        videoPlayView.setOnErrorListener(mOnErrorListener);

        videoPlayView.setVideoPath(videoUrl);
    }

    private PLMediaPlayer.OnInfoListener mOnInfoListener = new PLMediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(PLMediaPlayer plMediaPlayer, int what, int extra) {
            Log.d(TAG, "onInfo: " + what + ", " + extra);
            return false;
        }
    };

    private PLMediaPlayer.OnErrorListener mOnErrorListener = new PLMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(PLMediaPlayer plMediaPlayer, int errorCode) {
            Log.e(TAG, "Error happened, errorCode = " + errorCode);
            switch (errorCode) {
                case PLMediaPlayer.ERROR_CODE_INVALID_URI:
                    showToastTips("Invalid URL !");
                    break;
                case PLMediaPlayer.ERROR_CODE_404_NOT_FOUND:
                    showToastTips("404 resource not found !");
                    break;
                case PLMediaPlayer.ERROR_CODE_CONNECTION_REFUSED:
                    showToastTips("Connection refused !");
                    break;
                case PLMediaPlayer.ERROR_CODE_CONNECTION_TIMEOUT:
                    showToastTips("Connection timeout !");
                    restart();
                    break;
                case PLMediaPlayer.ERROR_CODE_EMPTY_PLAYLIST:
                    showToastTips("Empty playlist !");
                    break;
                case PLMediaPlayer.ERROR_CODE_STREAM_DISCONNECTED:
                    showToastTips("Stream disconnected !");
                    restart();
                    break;
                case PLMediaPlayer.ERROR_CODE_IO_ERROR:
                    showToastTips("Network IO Error !");
                    restart();
                    break;
                case PLMediaPlayer.ERROR_CODE_UNAUTHORIZED:
                    showToastTips("Unauthorized Error !");
                    break;
                case PLMediaPlayer.ERROR_CODE_PREPARE_TIMEOUT:
                    showToastTips("Prepare timeout !");
                    restart();
                    break;
                case PLMediaPlayer.ERROR_CODE_READ_FRAME_TIMEOUT:
                    showToastTips("Read frame timeout !");
                    restart();
                    break;
                case PLMediaPlayer.MEDIA_ERROR_UNKNOWN:
                default:
                    showToastTips("unknown error !");
                    restart();
                    break;
            }
            // Todo pls handle the error status here, retry or call finish()
//            finish();
            // If you want to retry, do like this:
            // mVideoView.setVideoPath(mVideoPath);
            // mVideoView.start();
            // Return true means the error has been handled
            // If return false, then `onCompletion` will be called
            return true;
        }
    };

    private PLMediaPlayer.OnCompletionListener mOnCompletionListener = new PLMediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(PLMediaPlayer plMediaPlayer) {
            Log.d(TAG, "Play Completed !");
            showToastTips("Play Completed !");
//            restart();
            finish();
        }
    };

    private void restart()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handler.removeMessages(RESTART_PLAYER);
                handler.sendEmptyMessageDelayed(RESTART_PLAYER,1000 * 5);
            }
        });
    }

    private PLMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = new PLMediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(PLMediaPlayer plMediaPlayer, int precent) {
            pos = plMediaPlayer.getCurrentPosition();
            Log.d(TAG, "onBufferingUpdate: " + precent);
        }
    };

    private PLMediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener = new PLMediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(PLMediaPlayer plMediaPlayer) {
            Log.d(TAG, "onSeekComplete !");
        }
    };

    private PLMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener = new PLMediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(PLMediaPlayer plMediaPlayer, int width, int height) {
            Log.d(TAG, "onVideoSizeChanged: " + width + "," + height);
        }
    };

    private void showToastTips(final String tips) {
        if (mIsActivityPaused) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(VideoPlayActivity.this, tips, Toast.LENGTH_SHORT);
                mToast.show();
            }
        });
    }
}
