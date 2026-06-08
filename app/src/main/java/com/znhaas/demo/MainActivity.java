package com.znhaas.demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.znhaas.sdk.appbridge.ZnhaasAppJsBridge;
import com.znhaas.sdk.bridge.ZnhaasBleJsBridge;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ZnhaasBleJsBridge bleJsBridge;
    private ZnhaasAppJsBridge appJsBridge;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (bleJsBridge != null) {
                    bleJsBridge.installJavascriptFacade();
                }
                if (appJsBridge != null) {
                    appJsBridge.installJavascriptFacade();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        bleJsBridge = new ZnhaasBleJsBridge(this, webView);
        bleJsBridge.attach();
        appJsBridge = new ZnhaasAppJsBridge(this, webView);
        appJsBridge.attach();
        // BLE Demo：file:///android_asset/znhaas_ble_demo.html
        // 生产环境地址：https://sso.longfor.com/cas/h5/login/?service=https://aiss.wan-prod.longfor.com/#/homeLIst
        // 测试环境地址 https://sso-uat.longfor.com/cas/h5/login/?service=https://aiss.h5-uat.longfor.com/m/#/homeList
        webView.loadUrl("file:///android_asset/znhaas_app_tests.html");
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean handled = false;
        if (appJsBridge != null) {
            handled = appJsBridge.onActivityResult(requestCode, resultCode, data);
        }
        if (!handled && bleJsBridge != null) {
            bleJsBridge.onActivityResult(requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean handled = false;
        if (appJsBridge != null) {
            handled = appJsBridge.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        if (!handled && bleJsBridge != null) {
            bleJsBridge.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        if (appJsBridge != null) {
            appJsBridge.release();
            appJsBridge = null;
        }
        if (bleJsBridge != null) {
            bleJsBridge.release();
            bleJsBridge = null;
        }
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
