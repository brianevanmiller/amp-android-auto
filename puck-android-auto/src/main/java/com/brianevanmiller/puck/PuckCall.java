package com.brianevanmiller.puck;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the single WebView that holds ampcode.com/car and the call state.
 *
 * The WebView lives for the whole process so a call keeps going while the phone
 * activity is closed and only the car screen (or nothing) is visible. All methods
 * must be called on the main thread.
 */
final class PuckCall {
    static final String CAR_URL = "https://ampcode.com/car";

    enum State { IDLE, CALLING, ACTIVE }

    interface Listener {
        void onPuckCallChanged();
    }

    // Finds the page's call / hang-up control by its label. ampcode.com/car is "just a
    // call button", so a lone visible button counts as the call button too.
    private static final String FIND_JS =
            "(function(want, avoid, loneOk) {"
            + "  function label(e) { return ((e.getAttribute('aria-label') || '') + ' ' + (e.title || '') + ' ' + (e.innerText || '')).trim(); }"
            + "  var els = Array.prototype.slice.call(document.querySelectorAll('button,[role=button],a'))"
            + "    .filter(function(e) { return !e.disabled && e.getClientRects().length; });"
            + "  var hit = els.filter(function(e) { var l = label(e); return want.test(l) && !(avoid && avoid.test(l)); })[0];"
            + "  if (!hit && loneOk && els.length === 1) hit = els[0];"
            + "  if (!hit) return 'none';"
            + "  hit.click();"
            + "  return 'clicked';"
            + "})(%s)";
    private static final String CALL_JS = String.format(FIND_JS,
            "/\\b(call|talk|start|speak|connect)\\b/i, /\\b(end|hang|stop|leave|disconnect|mute)\\b/i, true");
    private static final String HANG_UP_JS = String.format(FIND_JS,
            "/\\b(end|hang|stop|leave|disconnect)\\b/i, null, false");

    private static final int CLICK_ATTEMPTS = 20;
    private static final long CLICK_RETRY_MS = 500;

    private static PuckCall instance;

    static PuckCall get(Context context) {
        if (instance == null) instance = new PuckCall(context.getApplicationContext());
        return instance;
    }

    private final Context appContext;
    private final MutableContextWrapper webContext;
    private final WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private State state = State.IDLE;
    private String status = "Ready. Tap Call to talk to Puck.";
    private boolean pageReady;
    private boolean callPending;

    @SuppressLint("SetJavaScriptEnabled")
    private PuckCall(Context appContext) {
        this.appContext = appContext;
        webContext = new MutableContextWrapper(appContext);
        webView = new WebView(webContext);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Present as regular Chrome so sign-in providers don't block the embedded browser.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = isCarPage(url);
                if (!pageReady) {
                    if (callPending) fail("Sign in to Amp in the Puck app on your phone first.");
                    return;
                }
                if (callPending) {
                    callPending = false;
                    clickCall(CLICK_ATTEMPTS);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    pageReady = false;
                    if (callPending || state != State.IDLE) fail("Can't reach ampcode.com. Check your connection.");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                boolean fromAmp = "ampcode.com".equals(request.getOrigin().getHost());
                boolean wantsMic = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                }
                if (!fromAmp || !wantsMic || !hasMicPermission(PuckCall.this.appContext)) {
                    request.deny();
                    return;
                }
                request.grant(new String[] {PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                // The page opened the mic: we're on a call, whether it started from the car or
                // the phone. Keep the mic alive in the background with the foreground service.
                PuckCallService.keepAlive(PuckCall.this.appContext);
                setState(State.ACTIVE, "On a call with Puck.");
            }
        });

        layoutOffscreen();
        webView.loadUrl(CAR_URL);
    }

    static boolean hasMicPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    State state() {
        return state;
    }

    String status() {
        return status;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Starts a call by pressing the page's call button, loading the page first if needed. */
    void call() {
        if (state != State.IDLE) return;
        setState(State.CALLING, "Calling Puck…");
        if (pageReady) {
            clickCall(CLICK_ATTEMPTS);
        } else {
            callPending = true;
            webView.loadUrl(CAR_URL);
        }
    }

    /** Ends the call via the page's hang-up button, or by reloading the page if there isn't one. */
    void hangUp() {
        callPending = false;
        webView.evaluateJavascript(HANG_UP_JS, result -> {
            if (!"\"clicked\"".equals(result)) webView.loadUrl(CAR_URL);
        });
        PuckCallService.stop(appContext);
        setState(State.IDLE, "Call ended. Tap Call to talk to Puck again.");
    }

    void fail(String message) {
        callPending = false;
        PuckCallService.stop(appContext);
        setState(State.IDLE, message);
    }

    /** Shows the WebView in the phone activity. */
    WebView attach(Context activity, ViewGroup parent) {
        ViewGroup old = (ViewGroup) webView.getParent();
        if (old != null) old.removeView(webView);
        webContext.setBaseContext(activity);
        parent.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return webView;
    }

    /** Takes the WebView off screen again without stopping the page (or a call in progress). */
    void detach(ViewGroup from) {
        if (webView.getParent() != from) return;
        from.removeView(webView);
        webContext.setBaseContext(appContext);
        layoutOffscreen();
        CookieManager.getInstance().flush();
    }

    // A detached WebView is never measured; give the page a phone-sized viewport anyway.
    private void layoutOffscreen() {
        int w = 1080, h = 1920;
        webView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        webView.layout(0, 0, w, h);
    }

    private void clickCall(int attemptsLeft) {
        if (state != State.CALLING) return;
        webView.evaluateJavascript(CALL_JS, result -> {
            if (state != State.CALLING) return;
            if ("\"clicked\"".equals(result)) {
                setState(State.CALLING, "Connecting to Puck…");
            } else if (attemptsLeft > 1) {
                // The page may still be rendering its button.
                handler.postDelayed(() -> clickCall(attemptsLeft - 1), CLICK_RETRY_MS);
            } else {
                fail("Couldn't find the call button on ampcode.com/car. Open Puck on your phone.");
            }
        });
    }

    private static boolean isCarPage(String url) {
        Uri uri = Uri.parse(url);
        return "ampcode.com".equals(uri.getHost()) && uri.getPath() != null && uri.getPath().startsWith("/car");
    }

    private void setState(State state, String status) {
        this.state = state;
        this.status = status;
        for (Listener l : new ArrayList<>(listeners)) l.onPuckCallChanged();
    }
}
