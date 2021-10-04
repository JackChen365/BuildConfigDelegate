package com.android.library;

/**
 * Created on 2021/10/3.
 *
 * @author Jack Chen
 */
public class HttpServer {
    public static String getServer() {
        return BuildConfig.API_ANALYTICS_INVESTIGATION_URL;
    }

    public static String getUIUrl() {
        return BuildConfig.TV_UI_URL;
    }
}
