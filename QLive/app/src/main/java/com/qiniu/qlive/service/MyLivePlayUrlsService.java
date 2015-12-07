package com.qiniu.qlive.service;

import com.qiniu.qlive.config.Remote;
import com.qiniu.qlive.service.result.MyLivePlayUrlsResult;
import com.qiniu.qlive.utils.Tools;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jemy on 12/3/15.
 */
public class MyLivePlayUrlsService {
    public static MyLivePlayUrlsResult getMyLivePlayUrls(String sessionId) {
        MyLivePlayUrlsResult result = null;
        String accessToken = Tools.createAccessToken(sessionId);
        OkHttpClient client = new OkHttpClient();
        try {
            RequestBody reqBody = new FormEncodingBuilder()
                    .add("sessionId", sessionId)
                    .add("accessToken", accessToken).build();
            Request req = new Request.Builder().url(Remote.url(Remote.MY_LIVE_PLAY_URLS_SERVICE))
                    .method("POST", reqBody).build();
            Response resp = client.newCall(req).execute();
            if (resp.isSuccessful()) {
                String respBody = resp.body().string();
                JSONObject jsonObject = new JSONObject(respBody);
                int apiCode = jsonObject.getInt("code");
                String apiDesc = jsonObject.getString("desc");

                Map<String, String> livePlayUrls = new HashMap<String, String>();
                try {
                    JSONObject livePlayUrlsObj = jsonObject.getJSONObject("livePlayUrls");
                    if (livePlayUrlsObj != null) {
                        if (livePlayUrlsObj.has("RTMP")) {
                            livePlayUrls.put("RTMP", livePlayUrlsObj.getString("RTMP"));
                        }

                        if (livePlayUrlsObj.has("HLS")) {
                            livePlayUrls.put("HLS", livePlayUrlsObj.getString("HLS"));
                        }

                        if (livePlayUrlsObj.has("FLV")) {
                            livePlayUrls.put("FLV", livePlayUrlsObj.getString("FLV"));
                        }
                    }
                } catch (JSONException ex) {

                }
                result = new MyLivePlayUrlsResult(apiCode, apiDesc, livePlayUrls);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }
}
