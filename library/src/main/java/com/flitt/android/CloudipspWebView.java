package com.flitt.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;

/**
 * Created by vberegovoy on 28.11.15.
 */
public class CloudipspWebView extends WebView implements CloudipspView {
    private static final String URL_START_PATTERN = "http://secure-redirect.flitt.com/submit/#";

    public CloudipspWebView(Context context) {
        super(context);
        init();
    }

    public CloudipspWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CloudipspWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        final WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setDatabaseEnabled(true);

        setVisibility(View.GONE);
    }

    public final boolean waitingForConfirm() {
        return getVisibility() == View.VISIBLE;
    }

    public final void skipConfirm() {
        stopLoading();
        setVisibility(View.GONE);
    }

    public final void confirm(final PayConfirmation confirmation) {
        if (confirmation == null) {
            throw new NullPointerException("confirmation should be not null");
        }
        setVisibility(View.VISIBLE);

        setWebViewClient(new WebViewClient() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                String url = request.getUrl().toString();
                if (isCallbackUrl(url, confirmation)) {
                    // parse JSON/token off-thread
                    final JSONObject payload = detectCallbackResponse(url, confirmation);

                    // do the UI work on the main thread
                    view.post(() -> {
                        blankPage();
                        confirmation.listener.onConfirmed(payload);
                        setVisibility(View.GONE);
                    });

                    // short-circuit the load
                    return new WebResourceResponse(
                            "text/html", "UTF-8",
                            new ByteArrayInputStream("<html></html>".getBytes())
                    );
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    String url
            ) {
                if (isCallbackUrl(url, confirmation)) {
                    final JSONObject payload = detectCallbackResponse(url, confirmation);
                    view.post(() -> {
                        blankPage();
                        confirmation.listener.onConfirmed(payload);
                        setVisibility(View.GONE);
                    });
                    return new WebResourceResponse(
                            "text/html", "UTF-8",
                            new ByteArrayInputStream("<html></html>".getBytes())
                    );
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                checkUrl(url, confirmation);
                super.onLoadResource(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (checkUrl(url, confirmation)) return true;
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (checkUrl(url, confirmation)) return true;
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                checkUrl(url, confirmation);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.startsWith(confirmation.callbackUrl)) return;
                handleError(errorCode, description);
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                String url = request.getUrl().toString();
                if (url.startsWith(confirmation.callbackUrl)) return;
                handleError(error.getErrorCode(), error.getDescription().toString());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                confirmation.listener.onNetworkSecurityError(error.toString());
                handleError();
            }

            private boolean checkUrl(String url, PayConfirmation confirmation) {
                if (BuildConfig.DEBUG) {
                    Log.i("Cloudipsp", "WebUrl: " + url);
                }
                boolean detectsStartPattern = url.startsWith(URL_START_PATTERN);
                boolean detectsApiToken = url.startsWith(confirmation.host + "/api/checkout?token=");

                boolean detectsCallbackUrl = false;
                try {
                    Uri incoming = Uri.parse(url);
                    Uri cb = Uri.parse(confirmation.callbackUrl);
                    detectsCallbackUrl = incoming.getScheme().equalsIgnoreCase(cb.getScheme())
                            && incoming.getHost().equalsIgnoreCase(cb.getHost());
                } catch (Exception ignored) {
                }

                if (detectsStartPattern || detectsCallbackUrl || detectsApiToken) {
                    blankPage();
                    JSONObject response = null;
                    if (detectsStartPattern) {
                        String jsonPart = url.substring(URL_START_PATTERN.length());
                        try {
                            response = new JSONObject(jsonPart);
                        } catch (JSONException jsEx) {
                            try {
                                response = new JSONObject(URLDecoder.decode(jsonPart, "UTF-8"));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    confirmation.listener.onConfirmed(response);
                    setVisibility(View.GONE);
                    return true;
                }
                return false;
            }

            private void handleError(int errorCode, String description) {
                switch (errorCode) {
                    case WebViewClient.ERROR_HOST_LOOKUP:
                    case WebViewClient.ERROR_IO:
                    case WebViewClient.ERROR_CONNECT:
                        confirmation.listener.onNetworkAccessError(description);
                        handleError();
                        break;
                    case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                        confirmation.listener.onNetworkSecurityError(description);
                        handleError();
                        break;
                }
            }

            private void handleError() {
                blankPage();
                skipConfirm();
            }


            private JSONObject detectCallbackResponse(String url, PayConfirmation confirmation) {
                if (url.startsWith(URL_START_PATTERN)) {
                    String jsonPart = url.substring(URL_START_PATTERN.length());
                    try {
                        return new JSONObject(jsonPart);
                    } catch (JSONException jsEx) {
                        try {
                            return new JSONObject(URLDecoder.decode(jsonPart, "UTF-8"));
                        } catch (Exception ignored) {
                        }
                    }
                }
                return null;
            }

            private boolean isCallbackUrl(String url, PayConfirmation confirmation) {
                if (url.startsWith(URL_START_PATTERN)) return true;
                if (url.startsWith(confirmation.host + "/api/checkout?token=")) return true;
                try {
                    Uri incoming = Uri.parse(url);
                    Uri cb = Uri.parse(confirmation.callbackUrl);
                    return incoming.getScheme().equalsIgnoreCase(cb.getScheme())
                            && incoming.getHost().equalsIgnoreCase(cb.getHost());
                } catch (Exception ignored) {
                    return false;
                }
            }
        });


        if (Tls12SocketFactory.needHere()) {
            loadProxy(confirmation);
        } else {
            final Runnable l = new Runnable() {
                @Override
                public void run() {
                    loadDataWithBaseURL(confirmation.url, confirmation.htmlPageContent, confirmation.contentType, encoding(confirmation.contentType), null);
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && confirmation.cookie != null) {
                CookieManager.getInstance().setCookie(confirmation.url, confirmation.cookie, new ValueCallback<Boolean>() {
                    @Override
                    public void onReceiveValue(Boolean value) {
                        l.run();
                    }
                });
            } else {
                l.run();
            }
        }
    }

    private void blankPage() {
        loadDataWithBaseURL(null, "<html></html>", "text/html", "UTF-8", null);
        invalidate();
    }

    private static String encoding(String contentType) {
        String[] parts = contentType.split("charset\\=");
        if (parts.length < 2) {
            return "UTF-8";
        } else {
            return parts[1];
        }
    }

    private void loadProxy(PayConfirmation confirmation) {
        try {
            final Uri uri = Uri.parse(confirmation.url);
            final String oldHost = uri.getAuthority();
            final String newHost = "3dse.flitt.com";

            final Uri.Builder uriBuilder = uri.buildUpon()
                    .authority(newHost)
                    .path(uri.getPath())
                    .appendQueryParameter("jd91mx8", oldHost);
            final String url = uriBuilder.toString();
            String htmlPageContent = confirmation.htmlPageContent;

            final String quoted = oldHost.replace(".", "\\.");
            htmlPageContent = htmlPageContent.replaceAll(quoted, newHost);


            clearProxy();
            loadDataWithBaseURL(url, htmlPageContent, confirmation.contentType, encoding(confirmation.contentType), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void clearProxy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(getContext());
            cookieSyncMngr.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }

        clearCache(true);
    }
}
