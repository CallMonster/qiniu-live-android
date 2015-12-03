package com.qiniu.qlive.activity;

import android.content.Context;
import android.content.Intent;

import com.qiniu.qlive.config.ActionID;
import com.qiniu.qlive.config.LiveTask;
import com.qiniu.qlive.service.MyLivePlayUrlsService;
import com.qiniu.qlive.service.result.MyLivePlayUrlsResult;
import com.qiniu.qlive.utils.AsyncRun;
import com.qiniu.qlive.utils.Tools;

public class MainActivityEventHandler implements ActionID, LiveTask {
    public Context context;

    public MainActivityEventHandler(Context context) {
        this.context = context;
    }

    public void runTask(String taskId) {
        switch (taskId) {
            case ACTION_START_PUBLISH_VIDEO_SW:
                this.startPublishVideoSW();
                break;
            case ACTION_LOAD_MY_VIDEO_LIST:
                this.switchToMyVideoListActivity();
                break;
            case ACTION_SHARE_MY_LIVE_URLS:
                this.switchToShareMyLivePlayUrls();
                break;
        }
    }

    public void startPublishVideoSW() {
        Intent intent = new Intent(this.context, LiveInfoActivity.class);
        intent.putExtra("LiveTask", RECORD_VIDEO_SW);
        this.context.startActivity(intent);
    }

    public void switchToMyVideoListActivity() {
        Intent intent = new Intent(this.context, MyLiveVideoListActivity.class);
        this.context.startActivity(intent);
    }


    public void switchToShareMyLivePlayUrls() {
        final String sessionId = Tools.getSession(this.context).getId();
        final Context ctx = this.context;

        new Thread(new Runnable() {
            @Override
            public void run() {
                MyLivePlayUrlsResult playResult = MyLivePlayUrlsService.getMyLivePlayUrls(sessionId);
                if (playResult != null) {

                    if (!playResult.getLivePlayUrls().isEmpty()) {
                        StringBuilder shareContent = new StringBuilder();
                        if (playResult.getLivePlayUrls().containsKey("RTMP")) {
                            shareContent.append("RTMP: ").append(playResult.getLivePlayUrls().get("RTMP")).append("\n");
                        }
                        if (playResult.getLivePlayUrls().containsKey("HLS")) {
                            shareContent.append("HLS: ").append(playResult.getLivePlayUrls().get("HLS")).append("\n");
                        }
                        if (playResult.getLivePlayUrls().containsKey("FLV")) {
                            shareContent.append("FLV: ").append(playResult.getLivePlayUrls().get("FLV")).append("\n");
                        }

                        final Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_TEXT, shareContent.toString());
                        intent.setType("text/plain");
                        AsyncRun.run(new Runnable() {
                            @Override
                            public void run() {
                                ctx.startActivity(Intent.createChooser(intent,
                                        ctx.getResources().getString(R.string.share_my_live_play_urls)));
                            }
                        });
                    } else {
                        Tools.showToast(ctx, "获取我的直播播放列表失败！" + playResult.getDesc());
                    }
                } else {
                    Tools.showToast(ctx, "请求失败，请检查网络状况！");
                }
            }
        }).start();

    }
}
