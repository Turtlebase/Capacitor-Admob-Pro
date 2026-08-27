package com.admobadvanced;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

/** A bounded native ad surface anchored to the bottom of the Capacitor window. */
public class NativeAdOverlayView extends FrameLayout {

    private final NativeAdView nativeAdView;

    public NativeAdOverlayView(Context context, NativeAd ad, int widthDp, int heightDp, int bottomMarginDp) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);

        nativeAdView = buildNativeAdView(context, ad);
        addView(nativeAdView, new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        int maxWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(16));
        int width = Math.min(dp(Math.max(1, widthDp)), maxWidth);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width, dp(Math.max(1, heightDp)), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(Math.max(0, bottomMarginDp));
        setLayoutParams(params);
    }

    private NativeAdView buildNativeAdView(Context context, NativeAd ad) {
        NativeAdView view = new NativeAdView(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(6));
        view.setBackground(background);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setElevation(dp(3));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);

        MediaView mediaView = new MediaView(context);
        mediaView.setId(View.generateViewId());
        view.setMediaView(mediaView);
        content.addView(mediaView, new LinearLayout.LayoutParams(dp(112), LayoutParams.MATCH_PARENT));

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(8), 0, 0, 0);

        TextView adLabel = textView(context, 10, Color.DKGRAY);
        adLabel.setText("Ad");
        details.addView(adLabel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(16)));

        TextView headline = textView(context, 14, Color.BLACK);
        headline.setMaxLines(2);
        headline.setText(ad.getHeadline());
        view.setHeadlineView(headline);
        details.addView(headline, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        TextView body = textView(context, 11, Color.DKGRAY);
        body.setMaxLines(2);
        if (ad.getBody() != null) body.setText(ad.getBody());
        view.setBodyView(body);
        details.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        TextView cta = textView(context, 12, Color.WHITE);
        cta.setGravity(Gravity.CENTER);
        cta.setBackgroundColor(Color.rgb(66, 133, 244));
        cta.setPadding(dp(12), dp(4), dp(12), dp(4));
        if (ad.getCallToAction() != null) cta.setText(ad.getCallToAction());
        view.setCallToActionView(cta);
        details.addView(cta, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(32)));

        content.addView(details, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        view.addView(content, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.setNativeAd(ad);
        return view;
    }

    private TextView textView(Context context, int sizeSp, int color) {
        TextView result = new TextView(context);
        result.setTextSize(sizeSp);
        result.setTextColor(color);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void destroy() {
        nativeAdView.destroy();
    }
}
