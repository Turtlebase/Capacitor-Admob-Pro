package com.admobadvanced;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.AdLoader;

import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;
import java.util.List;

/**
 * AdMobAdvancedPlugin — Lightning-fast ad engine for Capacitor.
 *
 * Speed philosophy:
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  prepareInterstitial()  →  fills a 2-slot warm pool immediately  │
 * │  showInterstitial()     →  grabs from pool = 0 ms display delay  │
 * │                            + fires background reload instantly   │
 * │  Back-press / tap       →  ad shows in same frame as the action  │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * This mirrors exactly what top native Android ad-heavy apps do.
 */
@CapacitorPlugin(name = "AdMobAdvanced")
public class AdMobAdvancedPlugin extends Plugin {

    private static final String TAG = "AdMobAdvanced";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Core cache engine ─────────────────────────────────────────────────────
    private final AdCacheManager cache = new AdCacheManager();

    // ── Banner ────────────────────────────────────────────────────────────────
    private AdView         bannerAdView;
    private RelativeLayout bannerLayout;

    // ── Native ────────────────────────────────────────────────────────────────
    private NativeAd            nativeAd;
    private NativeAdOverlayView nativeOverlayView;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isMobileAdsInitialized = false;
    private boolean isShowingFullscreen    = false;

    // ─────────────────────────────────────────────────────────────────────────
    // INITIALIZE
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void initialize(PluginCall call) {
        JSArray testingDevices = call.getArray("testingDevices", new JSArray());
        List<String> testDeviceIds = new ArrayList<>();
        testDeviceIds.add(AdRequest.DEVICE_ID_EMULATOR);
        try {
            for (int i = 0; i < testingDevices.length(); i++)
                testDeviceIds.add(testingDevices.getString(i));
        } catch (Exception e) { Log.e(TAG, "testingDevices parse error", e); }

        RequestConfiguration config = new RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds).build();
        MobileAds.setRequestConfiguration(config);

        Activity activity = getActivity();
        mainHandler.post(() -> MobileAds.initialize(activity, status -> {
            isMobileAdsInitialized = true;
            Log.d(TAG, "MobileAds initialized");
            call.resolve();
        }));
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // CONSENT
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void requestConsentInfo(PluginCall call) {
        Activity activity = getActivity();
        ConsentInformation ci = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        ci.requestConsentInfoUpdate(activity, params, () -> {
            JSObject result = new JSObject();
            result.put("status", consentStatusString(ci.getConsentStatus()));
            result.put("isConsentFormAvailable", ci.isConsentFormAvailable());
            call.resolve(result);
        }, err -> call.reject(err.getMessage()));
    }

    @PluginMethod
    public void showConsentForm(PluginCall call) {
        Activity activity = getActivity();
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, err -> {
            if (err != null) { call.reject(err.getMessage()); return; }
            ConsentInformation ci = UserMessagingPlatform.getConsentInformation(activity);
            JSObject result = new JSObject();
            result.put("status", consentStatusString(ci.getConsentStatus()));
            result.put("isConsentFormAvailable", ci.isConsentFormAvailable());
            call.resolve(result);
        });
    }

    @PluginMethod
    public void resetConsent(PluginCall call) {
        UserMessagingPlatform.getConsentInformation(getActivity()).reset();
        call.resolve();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BANNER
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void showBanner(PluginCall call) {
        String adId     = call.getString("adId", "");
        String adSize   = call.getString("adSize", "BANNER");
        String position = call.getString("position", "BOTTOM_CENTER");
        int margin      = call.getInt("margin", 0);

        mainHandler.post(() -> {
            if (bannerAdView != null) destroyBanner();
            Activity activity = getActivity();
            AdSize size = resolveAdSize(adSize);
            bannerAdView = new AdView(activity);
            bannerAdView.setAdUnitId(adId);
            bannerAdView.setAdSize(size);
            bannerAdView.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    JSObject info = new JSObject(); info.put("adUnitId", adId);
                    notifyListeners("bannerAdLoaded", info);
                    JSObject sz = new JSObject();
                    sz.put("width", size.getWidth()); sz.put("height", size.getHeight());
                    notifyListeners("bannerAdSizeChanged", sz);
                    call.resolve(info);
                }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    notifyListeners("bannerAdFailedToLoad", adErrorObj(e)); call.reject(e.getMessage());
                }
                @Override public void onAdOpened()      { notifyListeners("bannerAdOpened",     new JSObject()); }
                @Override public void onAdClosed()      { notifyListeners("bannerAdClosed",     new JSObject()); }
                @Override public void onAdImpression()  { notifyListeners("bannerAdImpression", new JSObject()); }
            });

            bannerLayout = new RelativeLayout(activity);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            applyBannerPosition(lp, position, margin);
            bannerLayout.addView(bannerAdView, lp);

            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView().getRootView();
            root.addView(bannerLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            bannerAdView.loadAd(new AdRequest.Builder().build());
        });
    }

    @PluginMethod public void hideBanner(PluginCall call)   { mainHandler.post(() -> { if (bannerAdView != null) bannerAdView.setVisibility(View.GONE);    call.resolve(); }); }
    @PluginMethod public void resumeBanner(PluginCall call) { mainHandler.post(() -> { if (bannerAdView != null) { bannerAdView.setVisibility(View.VISIBLE); bannerAdView.resume(); } call.resolve(); }); }
    @PluginMethod public void removeBanner(PluginCall call) { mainHandler.post(() -> { destroyBanner(); call.resolve(); }); }

    @PluginMethod
    public void setBannerPosition(PluginCall call) {
        mainHandler.post(() -> {
            if (bannerAdView == null) { call.reject("No banner"); return; }
            String position = call.getString("position", "BOTTOM_CENTER");
            int margin = call.getInt("margin", 0);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) bannerAdView.getLayoutParams();
            lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.removeRule(RelativeLayout.CENTER_IN_PARENT);
            lp.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            applyBannerPosition(lp, position, margin);
            bannerAdView.setLayoutParams(lp);
            call.resolve();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERSTITIAL — INSTANT SHOW via warm pool
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void prepareInterstitial(PluginCall call) {
        String adId = call.getString("adId", "");
        cache.preloadInterstitial(getActivity(), adId, new AdCacheManager.LoadCallback() {
            @Override public void onLoaded(String id) {
                JSObject info = new JSObject(); info.put("adUnitId", id);
                notifyListeners("interstitialAdLoaded", info);
                if (!call.isKeptAlive()) return;
                call.resolve(info);
            }
            @Override public void onFailed(int code, String msg) {
                notifyListeners("interstitialAdFailedToLoad", errObj(code, msg));
                if (!call.isKeptAlive()) return;
                call.reject(msg);
            }
        });
    }

    @PluginMethod
    public void showInterstitial(PluginCall call) {
        if (isShowingFullscreen) { call.reject("Already showing"); return; }
        mainHandler.post(() -> {
            int slot = cache.getInterstitialSlotIndex();
            InterstitialAd ad = cache.getInterstitial();

            if (ad != null) {
                // INSTANT PATH — warm ad from pool, 0 ms latency
                isShowingFullscreen = true;
                ad.setFullScreenContentCallback(interstitialCallback(call));
                ad.show(getActivity());
                // Reload in background immediately (don't wait for dismiss)
                cache.reloadInterstitialSlot(getActivity(), slot);
            } else {
                // COLD FALLBACK — load on-demand (only if prepare was not called)
                Log.w(TAG, "No warm interstitial — cold load");
                String adId = call.getString("adId", "");
                InterstitialAd.load(getActivity(), adId, new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override public void onAdLoaded(@NonNull InterstitialAd freshAd) {
                            isShowingFullscreen = true;
                            freshAd.setFullScreenContentCallback(interstitialCallback(call));
                            freshAd.show(getActivity());
                            cache.reloadInterstitialSlot(getActivity(), 0);
                            cache.reloadInterstitialSlot(getActivity(), 1);
                        }
                        @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                            notifyListeners("interstitialAdFailedToShow", adErrorObj(e));
                            call.reject(e.getMessage());
                        }
                    });
            }
        });
    }

    private FullScreenContentCallback interstitialCallback(PluginCall call) {
        return new FullScreenContentCallback() {
            @Override public void onAdShowedFullScreenContent() { notifyListeners("interstitialAdShowed",     new JSObject()); }
            @Override public void onAdDismissedFullScreenContent() {
                isShowingFullscreen = false;
                notifyListeners("interstitialAdDismissed", new JSObject());
                call.resolve();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) {
                isShowingFullscreen = false;
                notifyListeners("interstitialAdFailedToShow", adErrorObj(e));
                call.reject(e.getMessage());
            }
            @Override public void onAdClicked()    { notifyListeners("interstitialAdClicked",    new JSObject()); }
            @Override public void onAdImpression() { notifyListeners("interstitialAdImpression", new JSObject()); }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REWARDED — INSTANT SHOW
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void prepareRewarded(PluginCall call) {
        String adId = call.getString("adId", "");
        cache.preloadRewarded(getActivity(), adId, new AdCacheManager.LoadCallback() {
            @Override public void onLoaded(String id) {
                JSObject info = new JSObject(); info.put("adUnitId", id);
                notifyListeners("rewardedAdLoaded", info);
                if (!call.isKeptAlive()) return;
                call.resolve(info);
            }
            @Override public void onFailed(int code, String msg) {
                notifyListeners("rewardedAdFailedToLoad", errObj(code, msg));
                if (!call.isKeptAlive()) return;
                call.reject(msg);
            }
        });
    }

    @PluginMethod
    public void showRewarded(PluginCall call) {
        if (isShowingFullscreen) { call.reject("Already showing"); return; }
        mainHandler.post(() -> {
            int slot = cache.getRewardedSlotIndex();
            RewardedAd ad = cache.getRewarded();
            if (ad != null) {
                isShowingFullscreen = true;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdShowedFullScreenContent()    { notifyListeners("rewardedAdShowed",    new JSObject()); }
                    @Override public void onAdDismissedFullScreenContent() { isShowingFullscreen = false; notifyListeners("rewardedAdDismissed", new JSObject()); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError e) { isShowingFullscreen = false; call.reject(e.getMessage()); }
                    @Override public void onAdImpression() { notifyListeners("rewardedAdImpression", new JSObject()); }
                });
                ad.show(getActivity(), rewardItem -> {
                    JSObject reward = new JSObject();
                    reward.put("type", rewardItem.getType()); reward.put("amount", rewardItem.getAmount());
                    notifyListeners("rewardedAdRewarded", reward);
                    call.resolve(reward);
                });
                cache.reloadRewardedSlot(getActivity(), slot);
            } else {
                call.reject("Rewarded ad not ready — call prepareRewarded first");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REWARDED INTERSTITIAL
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void prepareRewardedInterstitial(PluginCall call) {
        String adId = call.getString("adId", "");
        cache.preloadRewardedInterstitial(getActivity(), adId, new AdCacheManager.LoadCallback() {
            @Override public void onLoaded(String id) {
                JSObject info = new JSObject(); info.put("adUnitId", id);
                notifyListeners("rewardedInterstitialAdLoaded", info);
                if (!call.isKeptAlive()) return;
                call.resolve(info);
            }
            @Override public void onFailed(int code, String msg) {
                notifyListeners("rewardedInterstitialAdFailedToLoad", errObj(code, msg));
                if (!call.isKeptAlive()) return;
                call.reject(msg);
            }
        });
    }

    @PluginMethod
    public void showRewardedInterstitial(PluginCall call) {
        if (isShowingFullscreen) { call.reject("Already showing"); return; }
        mainHandler.post(() -> {
            int slot = cache.getRISlotIndex();
            RewardedInterstitialAd ad = cache.getRewardedInterstitial();
            if (ad != null) {
                isShowingFullscreen = true;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdShowedFullScreenContent()    { notifyListeners("rewardedInterstitialAdShowed", new JSObject()); }
                    @Override public void onAdDismissedFullScreenContent() { isShowingFullscreen = false; notifyListeners("rewardedInterstitialAdDismissed", new JSObject()); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError e) { isShowingFullscreen = false; call.reject(e.getMessage()); }
                });
                ad.show(getActivity(), rewardItem -> {
                    JSObject reward = new JSObject();
                    reward.put("type", rewardItem.getType()); reward.put("amount", rewardItem.getAmount());
                    notifyListeners("rewardedInterstitialAdRewarded", reward);
                    call.resolve(reward);
                });
                cache.reloadRISlot(getActivity(), slot);
            } else {
                call.reject("Rewarded interstitial not ready");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APP OPEN
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void prepareAppOpen(PluginCall call) {
        String adId = call.getString("adId", "");
        cache.preloadAppOpen(getActivity(), adId, new AdCacheManager.LoadCallback() {
            @Override public void onLoaded(String id) {
                JSObject info = new JSObject(); info.put("adUnitId", id);
                notifyListeners("appOpenAdLoaded", info);
                if (!call.isKeptAlive()) return;
                call.resolve(info);
            }
            @Override public void onFailed(int code, String msg) {
                notifyListeners("appOpenAdFailedToLoad", errObj(code, msg));
                if (!call.isKeptAlive()) return;
                call.reject(msg);
            }
        });
    }

    @PluginMethod
    public void showAppOpen(PluginCall call) {
        mainHandler.post(() -> {
            AppOpenAd ad = cache.getAppOpen();
            if (ad != null) {
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdShowedFullScreenContent()    { notifyListeners("appOpenAdShowed",    new JSObject()); }
                    @Override public void onAdDismissedFullScreenContent() {
                        notifyListeners("appOpenAdDismissed", new JSObject());
                        cache.reloadAppOpen(getActivity());
                        call.resolve();
                    }
                    @Override public void onAdFailedToShowFullScreenContent(AdError e) {
                        notifyListeners("appOpenAdFailedToShow", adErrorObj(e));
                        cache.reloadAppOpen(getActivity());
                        call.reject(e.getMessage());
                    }
                });
                ad.show(getActivity());
            } else {
                call.reject("App open ad not ready");
                cache.reloadAppOpen(getActivity());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NATIVE ADVANCED
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void loadNativeAd(PluginCall call) {
        String adId = call.getString("adId", "");
        mainHandler.post(() -> {
            AdLoader loader = new AdLoader.Builder(getActivity(), adId)
                .forNativeAd(ad -> {
                    if (nativeAd != null) nativeAd.destroy();
                    nativeAd = ad;
                    JSObject info = new JSObject(); info.put("adUnitId", adId);
                    JSObject assets = new JSObject();
                    assets.put("headline",    ad.getHeadline());
                    assets.put("body",        ad.getBody());
                    assets.put("callToAction",ad.getCallToAction());
                    assets.put("advertiser",  ad.getAdvertiser());
                    if (ad.getStarRating() != null) assets.put("starRating", ad.getStarRating());
                    info.put("assets", assets);
                    notifyListeners("nativeAdLoaded", info);
                    call.resolve(info);
                })
                .withAdListener(new AdListener() {
                    @Override public void onAdFailedToLoad(LoadAdError e) { notifyListeners("nativeAdFailedToLoad", adErrorObj(e)); call.reject(e.getMessage()); }
                    @Override public void onAdClicked()    { notifyListeners("nativeAdClicked",    new JSObject()); }
                    @Override public void onAdImpression() { notifyListeners("nativeAdImpression", new JSObject()); }
                    @Override public void onAdClosed()     { notifyListeners("nativeAdClosed",     new JSObject()); }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder().build())
                .build();
            loader.loadAd(new AdRequest.Builder().build());
        });
    }

    @PluginMethod
    public void showNativeAd(PluginCall call) {
        String containerId = call.getString("containerId", "");
        mainHandler.post(() -> {
            if (nativeAd == null) { call.reject("Native ad not loaded"); return; }
            Activity activity = getActivity();
            if (nativeOverlayView != null) {
                nativeOverlayView.destroy();
                ViewGroup p = (ViewGroup) nativeOverlayView.getParent();
                if (p != null) p.removeView(nativeOverlayView);
            }
            nativeOverlayView = new NativeAdOverlayView(activity, nativeAd, getBridge().getWebView(), containerId);
            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView().getRootView();
            root.addView(nativeOverlayView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            call.resolve();
        });
    }

    @PluginMethod public void hideNativeAd(PluginCall call)      { mainHandler.post(() -> { if (nativeOverlayView != null) nativeOverlayView.setVisibility(View.GONE); call.resolve(); }); }
    @PluginMethod public void updateNativeAdLayout(PluginCall call) { mainHandler.post(() -> { if (nativeOverlayView != null) nativeOverlayView.syncPosition(); call.resolve(); }); }

    @PluginMethod
    public void removeNativeAd(PluginCall call) {
        mainHandler.post(() -> {
            if (nativeOverlayView != null) {
                nativeOverlayView.destroy();
                ViewGroup p = (ViewGroup) nativeOverlayView.getParent();
                if (p != null) p.removeView(nativeOverlayView);
                nativeOverlayView = null;
            }
            if (nativeAd != null) { nativeAd.destroy(); nativeAd = null; }
            call.resolve();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READINESS + WARM ALL
    // ─────────────────────────────────────────────────────────────────────────

    @PluginMethod
    public void isGoogleMobileAdsReady(PluginCall call) {
        JSObject res = new JSObject();
        res.put("ready",                     isMobileAdsInitialized);
        res.put("interstitialReady",         cache.isInterstitialReady());
        res.put("rewardedReady",             cache.isRewardedReady());
        res.put("rewardedInterstitialReady", cache.isRewardedInterstitialReady());
        res.put("appOpenReady",              cache.isAppOpenReady());
        call.resolve(res);
    }

    /** Re-warms all stale pool slots. Call on page navigation or app resume. */
    @PluginMethod
    public void warmAll(PluginCall call) {
        cache.warmAll(getActivity());
        call.resolve();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    @Override protected void handleOnPause()   { if (bannerAdView != null) bannerAdView.pause(); }
    @Override protected void handleOnResume()  { if (bannerAdView != null) bannerAdView.resume(); cache.warmAll(getActivity()); }
    @Override protected void handleOnDestroy() { destroyBanner(); if (nativeAd != null) { nativeAd.destroy(); nativeAd = null; } }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void destroyBanner() {
        if (bannerAdView != null) {
            bannerAdView.destroy();
            if (bannerLayout != null && bannerLayout.getParent() != null)
                ((ViewGroup) bannerLayout.getParent()).removeView(bannerLayout);
            bannerAdView = null; bannerLayout = null;
        }
    }

    private void applyBannerPosition(RelativeLayout.LayoutParams lp, String position, int margin) {
        switch (position) {
            case "TOP_CENTER":
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP); lp.addRule(RelativeLayout.CENTER_HORIZONTAL); lp.topMargin = margin; break;
            case "CENTER":
                lp.addRule(RelativeLayout.CENTER_IN_PARENT); break;
            default:
                lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); lp.addRule(RelativeLayout.CENTER_HORIZONTAL); lp.bottomMargin = margin; break;
        }
    }

    private AdSize resolveAdSize(String name) {
        switch (name) {
            case "LARGE_BANNER":      return AdSize.LARGE_BANNER;
            case "MEDIUM_RECTANGLE":  return AdSize.MEDIUM_RECTANGLE;
            case "FULL_BANNER":       return AdSize.FULL_BANNER;
            case "LEADERBOARD":       return AdSize.LEADERBOARD;
            case "ADAPTIVE_BANNER": {
                int w = getActivity().getResources().getDisplayMetrics().widthPixels;
                return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(getActivity(), w);
            }
            default: return AdSize.BANNER;
        }
    }

    private JSObject adErrorObj(AdError e) {
        JSObject o = new JSObject(); o.put("code", e.getCode()); o.put("message", e.getMessage()); o.put("domain", e.getDomain()); return o;
    }
    private JSObject adErrorObj(LoadAdError e) {
        JSObject o = new JSObject(); o.put("code", e.getCode()); o.put("message", e.getMessage()); o.put("domain", e.getDomain()); return o;
    }
    private JSObject errObj(int code, String msg) {
        JSObject o = new JSObject(); o.put("code", code); o.put("message", msg); return o;
    }
    private String consentStatusString(int s) {
        switch (s) { case 1: return "REQUIRED"; case 2: return "NOT_REQUIRED"; case 3: return "OBTAINED"; default: return "UNKNOWN"; }
    }
}
