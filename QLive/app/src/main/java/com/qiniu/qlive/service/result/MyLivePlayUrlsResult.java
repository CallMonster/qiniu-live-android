package com.qiniu.qlive.service.result;

import java.util.Map;

/**
 * Created by jemy on 12/3/15.
 */
public class MyLivePlayUrlsResult extends ApiResult {
    private Map<String, String> livePlayUrls;

    public MyLivePlayUrlsResult(int code, String desc, Map<String, String> livePlayUrls) {
        super(code, desc);
        this.livePlayUrls = livePlayUrls;
    }

    public Map<String, String> getLivePlayUrls() {
        return livePlayUrls;
    }
}
