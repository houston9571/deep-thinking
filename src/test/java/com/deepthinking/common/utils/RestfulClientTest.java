package com.deepthinking.common.utils;

import com.alibaba.fastjson2.JSONObject;
import com.deepthinking.BaseTest;
import org.junit.Test;

public class RestfulClientTest extends BaseTest {


    @Test
    public void test() {
        String url = "https://api.telegram.org/bot8724358936:AAHNGSc-r4ax6Hc7BpsVYHR_Og3YQOaPGRg/sendmessage";
        JSONObject body = JSONObject.of("chat_id", "-5145037560", "text", "bot test...");
        String re = RestfulClient.postAsJson(url, null, null, body.toJSONString());
        System.out.println(re);
    }
}
