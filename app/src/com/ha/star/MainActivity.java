package com.ha.star;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * HA star 的外壳。
 *
 * 只做四件事：
 *   1. 把 assets/ha_star.html 装进 WebView 打开
 *   2. 网页要麦克风时，向系统申请 RECORD_AUDIO 并放行
 *   3. 网页要选文件（发图、导备份）时，拉系统选择器回来
 *   4. 返回键先回网页上一页，再按两次退出
 *
 * 另外把 file:// 页面的跨域限制关掉（targetSdk 29 以下这个开关还有效），
 * 这样网页里连第三方接口不会被浏览器那套同源策略挡。
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_MIC = 1002;
    private static final String HOME = "file:///android_asset/ha_star.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private PermissionRequest micReq;
    private long lastBack = 0L;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        web = new WebView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // 状态栏、导航栏透明，页面铺到它们下面；图标用深色，浅底上看得见
        Window win = getWindow();
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);
        int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        f |= 0x00000010; // LIGHT_NAVIGATION_BAR，API26 起有效
        win.getDecorView().setSystemUiVisibility(f);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        // 让网页能直接连任意接口，不受同源策略限制
        try {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) {
        }

        web.setBackgroundColor(0xffeaeffd);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                injectInsets();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url != null && !url.startsWith("file://")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String[] res = request.getResources();
                        boolean wantMic = false;
                        for (int i = 0; i < res.length; i++) {
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res[i])) {
                                wantMic = true;
                            }
                        }
                        if (!wantMic) {
                            request.deny();
                            return;
                        }
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                            request.grant(res);
                        } else {
                            micReq = request;
                            requestPermissions(
                                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                        }
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                fileCb = cb;
                Intent intent = null;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    intent = null;
                }
                if (intent == null) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "选一张"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl(HOME);
    }

    /** 把状态栏、导航条的真实高度告诉网页，网页靠 --ha-inset-top / --ha-inset-bottom 让位 */
    private void injectInsets() {
        if (web == null) {
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        if (d <= 0f) {
            d = 1f;
        }
        int topPx = dimenPx("status_bar_height");
        int botPx = dimenPx("navigation_bar_height");
        String top = String.format(java.util.Locale.US, "%.1fpx", topPx / d);
        String bot = String.format(java.util.Locale.US, "%.1fpx", botPx / d);
        String js = "(function(){var r=document.documentElement;"
                + "r.style.setProperty('--ha-inset-top','" + top + "');"
                + "r.style.setProperty('--ha-inset-bottom','" + bot + "');})();";
        web.evaluateJavascript(js, null);
    }

    private int dimenPx(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_MIC && micReq != null) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                micReq.grant(micReq.getResources());
            } else {
                micReq.deny();
                Toast.makeText(this, "没给麦克风权限，说话就听不到了", Toast.LENGTH_SHORT).show();
            }
            micReq = null;
        }
        super.onRequestPermissionsResult(code, perms, res);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (fileCb != null) {
                Uri[] out = null;
                if (res == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        out = new Uri[n];
                        for (int i = 0; i < n; i++) {
                            out[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        out = new Uri[]{data.getData()};
                    }
                }
                fileCb.onReceiveValue(out);
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (web != null && web.canGoBack()) {
                web.goBack();
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastBack < 2000L) {
                finish();
            } else {
                lastBack = now;
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}