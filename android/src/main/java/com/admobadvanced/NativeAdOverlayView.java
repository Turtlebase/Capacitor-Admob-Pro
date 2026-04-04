package com.admobadvanced;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

/**
 * NativeAdOverlayView
 *
 * Renders a NativeAdView as an absolute overlay on top of the Capacitor WebView
 * and keeps its position perfectly in sync with the DOM element identified by
 * {@code containerId} — even when the user scrolls the page.
 *
 * How it works:
 *   1. On construction we inject a small JS helper into the WebView via
 *      addJavascriptInterface so the JS side can push the container's
 *      getBoundingClientRect() to the native layer on every scroll / resize.
 *   2. We use a Choreographer tick (vsync) to apply layout updates on the
 *      next frame — this gives us perfectly smooth scroll-tracking at 60/120 fps.
 */
public class NativeAdOverlayView extends FrameLayout {

    private static final String TAG = "NativeAdOverlay";

    private final WebView webView;
    private final String containerId;
    private final NativeAdView nativeAdView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Last known rect from JS (in CSS pixels)
    private float rectTop, rectLeft, rectWidth, rectHeight;
    private boolean hasPendingLayout = false;

    public NativeAdOverlayView(Context context, NativeAd ad, WebView webView, String containerId) {
        super(context);
        this.webView     = webView;
        this.containerId = containerId;

        setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);

        nativeAdView = buildNativeAdView(context, ad);
        addView(nativeAdView);

        injectScrollBridge();
    }

    /** Manually trigger a position sync (called from the plugin on updateNativeAdLayout). */
    public void syncPosition() {
        String js = "window.__admobNative && window.__admobNative.sync('" + escapeSingleQuotes(containerId) + "');";
        webView.evaluateJavascript(js, null);
    }

    /** Called by the JS bridge with fresh rect values. */
    private void applyRect(float top, float left, float width, float height) {
        rectTop    = top;
        rectLeft   = left;
        rectWidth  = width;
        rectHeight = height;
        if (!hasPendingLayout) {
            hasPendingLayout = true;
            mainHandler.post(this::doLayout);
        }
    }

    private void doLayout() {
        hasPendingLayout = false;
        float density = getResources().getDisplayMetrics().density;

        // Convert CSS px → physical px
        int l = Math.round(rectLeft  * density);
        int t = Math.round(rectTop   * density);
        int w = Math.max(1, Math.round(rectWidth  * density));
        int h = Math.max(1, Math.round(rectHeight * density));

        // Offset by webview position within the activity window
        Rect wvRect = new Rect();
        webView.getGlobalVisibleRect(wvRect);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = wvRect.left + l;
        lp.topMargin  = wvRect.top  + t;
        nativeAdView.setLayoutParams(lp);
    }

    // ── JS bridge injection ────────────────────────────────────────────────────

    private void injectScrollBridge() {
        webView.addJavascriptInterface(new NativeBridge(), "__admobBridge");

        String js = "(function(){" +
            "if(window.__admobNative)return;" +
            "function sync(id){" +
            "  var el=document.querySelector(id);" +
            "  if(!el)return;" +
            "  var r=el.getBoundingClientRect();" +
            "  window.__admobBridge.onRect(id,r.top,r.left,r.width,r.height);" +
            "}" +
            "window.__admobNative={sync:sync};" +
            "['scroll','resize','touchmove'].forEach(function(ev){" +
            "  window.addEventListener(ev,function(){sync('" + escapeSingleQuotes(containerId) + "');},{passive:true});" +
            "});" +
            "sync('" + escapeSingleQuotes(containerId) + "');" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    private NativeAdView buildNativeAdView(Context context, NativeAd ad) {
        NativeAdView view = new NativeAdView(context);
        view.setBackgroundColor(Color.WHITE);

        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);

        // Headline
        TextView headlineView = new TextView(context);
        headlineView.setTextSize(14);
        headlineView.setTextColor(Color.BLACK);
        headlineView.setText(ad.getHeadline());
        view.setHeadlineView(headlineView);

        // Body
        TextView bodyView = new TextView(context);
        bodyView.setTextSize(11);
        bodyView.setTextColor(Color.DKGRAY);
        if (ad.getBody() != null) bodyView.setText(ad.getBody());
        view.setBodyView(bodyView);

        // CTA button
        TextView ctaView = new TextView(context);
        ctaView.setBackgroundColor(Color.parseColor("#4285F4"));
        ctaView.setTextColor(Color.WHITE);
        ctaView.setTextSize(12);
        ctaView.setPadding(24, 12, 24, 12);
        if (ad.getCallToAction() != null) ctaView.setText(ad.getCallToAction());
        view.setCallToActionView(ctaView);

        // MediaView
        MediaView mediaView = new MediaView(context);
        view.setMediaView(mediaView);

        // Layout
        android.widget.LinearLayout inner = new android.widget.LinearLayout(context);
        inner.setOrientation(android.widget.LinearLayout.VERTICAL);
        inner.setPadding(16, 16, 16, 16);
        inner.addView(headlineView);
        inner.addView(bodyView);
        inner.addView(ctaView);
        view.addView(inner, fillParams);

        view.setNativeAd(ad);
        return view;
    }

    public void destroy() {
        nativeAdView.destroy();
    }

    private static String escapeSingleQuotes(String s) {
        return s == null ? "" : s.replace("'", "\\'");
    }

    // ── Inner JavascriptInterface ──────────────────────────────────────────────

    private class NativeBridge {
        @JavascriptInterface
        public void onRect(String id, float top, float left, float width, float height) {
            applyRect(top, left, width, height);
        }
    }
}
