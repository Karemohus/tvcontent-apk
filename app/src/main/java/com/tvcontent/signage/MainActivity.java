package com.tvcontent.signage;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TVContent";
    private static final String PREFS_NAME = "TVContentPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "https://aljabr.duckdns.org/display";
    private static final String SECRET_CODE = "9999";

    private WebView webView;
    private TextView statusText;
    private LinearLayout splashLayout;
    private FrameLayout customViewContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private PowerManager.WakeLock wakeLock;
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private int settingsTaps = 0;
    private long lastTapTime = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TVContent::WakeLock");
            wakeLock.acquire();
        }

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        statusText = findViewById(R.id.status_text);
        splashLayout = findViewById(R.id.splash_layout);
        customViewContainer = findViewById(R.id.custom_view_container);

        findViewById(R.id.app_logo).setOnClickListener(v -> handleSettingsTap());

        setupWebView();
        loadServerUrl();
        hideSystemUI();
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setUserAgentString(settings.getUserAgentString() + " TVContent/1.1.0 Android");

        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                statusText.setText("جاري التحميل...");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideSplash();
                hideSystemUI();
                injectFullscreenCSS(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    statusText.setText("لا يوجد اتصال — إعادة المحاولة...");
                    showSplash();
                    scheduleRetry();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                customViewContainer.setVisibility(View.VISIBLE);
                customViewContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                hideSystemUI();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                customViewContainer.removeView(customView);
                customViewContainer.setVisibility(View.GONE);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
                hideSystemUI();
            }
        });
    }

    private void injectFullscreenCSS(WebView view) {
        String js = 
            "(function(){" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = '" +
            "    html, body { margin: 0 !important; padding: 0 !important; background: #000 !important; overflow: hidden !important; width: 100vw !important; height: 100vh !important; }" +
            "    body * { cursor: none !important; }" +
            "    video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; object-fit: cover !important; background: #000 !important; z-index: 999998 !important; }" +
            "    img.media-item, .display-media img, [class*=\"display\"] img { width: 100vw !important; height: 100vh !important; object-fit: cover !important; }" +
            "    .fullscreen-btn, .fs-btn, [data-action=\"fullscreen\"], button[onclick*=\"fullscreen\"] { display: none !important; }" +
            "  ';" +
            "  document.head.appendChild(style);" +
            "  function setupVideos() {" +
            "    document.querySelectorAll('video').forEach(function(v) {" +
            "      v.setAttribute('playsinline', 'true');" +
            "      v.setAttribute('webkit-playsinline', 'true');" +
            "      v.setAttribute('x5-playsinline', 'true');" +
            "      v.style.objectFit = 'cover';" +
            "      v.style.width = '100vw';" +
            "      v.style.height = '100vh';" +
            "      v.style.position = 'fixed';" +
            "      v.style.top = '0';" +
            "      v.style.left = '0';" +
            "      v.style.background = '#000';" +
            "      if (v.paused) v.play().catch(function(){});" +
            "    });" +
            "  }" +
            "  setupVideos();" +
            "  var observer = new MutationObserver(function(mutations) {" +
            "    setupVideos();" +
            "  });" +
            "  observer.observe(document.body, { childList: true, subtree: true });" +
            "  if (Element.prototype.requestFullscreen) {" +
            "    Element.prototype.requestFullscreen = function() { return Promise.resolve(); };" +
            "  }" +
            "  if (Element.prototype.webkitRequestFullscreen) {" +
            "    Element.prototype.webkitRequestFullscreen = function() {};" +
            "  }" +
            "})();";

        view.evaluateJavascript(js, null);
    }

    private void loadServerUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_SERVER_URL, DEFAULT_URL);

        if (isNetworkAvailable()) {
            statusText.setText("جاري الاتصال بالخادم...");
            webView.loadUrl(url);
        } else {
            statusText.setText("لا يوجد اتصال بالإنترنت — إعادة المحاولة...");
            scheduleRetry();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void scheduleRetry() {
        retryHandler.postDelayed(this::loadServerUrl, 5000);
    }

    private void showSplash() {
        splashLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void hideSplash() {
        splashLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void handleSettingsTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime > 2000) {
            settingsTaps = 0;
        }
        lastTapTime = now;
        settingsTaps++;

        if (settingsTaps >= 10) {
            settingsTaps = 0;
            showSettingsDialog();
        }
    }

    private void showSettingsDialog() {
        EditText pinInput = new EditText(this);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                              android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN");

        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setMessage("أدخل الرمز السري")
                .setView(pinInput)
                .setPositiveButton("تأكيد", (d, w) -> {
                    if (SECRET_CODE.equals(pinInput.getText().toString())) {
                        showServerUrlDialog();
                    } else {
                        Toast.makeText(this, "رمز خاطئ", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showServerUrlDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        EditText urlInput = new EditText(this);
        urlInput.setText(prefs.getString(KEY_SERVER_URL, DEFAULT_URL));

        new AlertDialog.Builder(this)
                .setTitle("عنوان الخادم")
                .setView(urlInput)
                .setPositiveButton("حفظ وإعادة تشغيل", (d, w) -> {
                    String newUrl = urlInput.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        prefs.edit().putString(KEY_SERVER_URL, newUrl).apply();
                        webView.clearCache(true);
                        loadServerUrl();
                    }
                })
                .setNeutralButton("مسح الذاكرة المؤقتة", (d, w) -> {
                    webView.clearCache(true);
                    Toast.makeText(this, "تم مسح الذاكرة المؤقتة", Toast.LENGTH_SHORT).show();
                    loadServerUrl();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
