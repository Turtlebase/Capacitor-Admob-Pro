package com.admobadvanced;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AdCacheManager — Lightning-fast ad preload pool.
 *
 * Core idea (same as top-grossing Android games):
 *  1. Load the NEXT ad WHILE the current one is showing (zero gap).
 *  2. Keep a "warm" ad ready at all times — show is then 0 ms latency.
 *  3. Track load timestamps; expire stale ads before Google does (1 hr safe margin).
 *  4. Parallel-load: start background reload the instant a show() is called.
 *  5. Auto-retry with exponential back-off on network failure.
 */
public class AdCacheManager {

    private static final String TAG       = "AdCache";
    private static final long   MAX_AGE_MS = 60 * 60 * 1000L;      // 1 hour (Google allows ~4h, we use 1h to be safe)
    private static final long   RETRY_BASE = 5_000L;                // 5 s first retry
    private static final int    MAX_RETRY  = 5;                     // stop after 5 retries
    private static final int    POOL_SIZE  = 2;                     // keep 2 ads warm per type

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Interstitial pool ─────────────────────────────────────────────────────

    private final InterstitialAd[]  interstitialPool     = new InterstitialAd[POOL_SIZE];
    private final long[]            interstitialLoadTime = new long[POOL_SIZE];
    private final AtomicBoolean[]   interstitialLoading  = new AtomicBoolean[]{
        new AtomicBoolean(false), new AtomicBoolean(false) };
    private String interstitialAdId;
    private int interstitialRetry = 0;

    // ── Rewarded pool ─────────────────────────────────────────────────────────

    private final RewardedAd[] rewardedPool     = new RewardedAd[POOL_SIZE];
    private final long[]       rewardedLoadTime = new long[POOL_SIZE];
    private final AtomicBoolean[] rewardedLoading = new AtomicBoolean[]{
        new AtomicBoolean(false), new AtomicBoolean(false) };
    private String rewardedAdId;

    // ── Rewarded Interstitial pool ────────────────────────────────────────────

    private final RewardedInterstitialAd[] riPool     = new RewardedInterstitialAd[POOL_SIZE];
    private final long[]                   riLoadTime = new long[POOL_SIZE];
    private final AtomicBoolean[] riLoading = new AtomicBoolean[]{
        new AtomicBoolean(false), new AtomicBoolean(false) };
    private String riAdId;

    // ── App Open ──────────────────────────────────────────────────────────────

    private AppOpenAd  appOpenAd;
    private long       appOpenLoadTime;
    private final AtomicBoolean appOpenLoading = new AtomicBoolean(false);
    private String     appOpenAdId;

    // ── Callbacks for plugin layer ────────────────────────────────────────────

    public interface LoadCallback {
        void onLoaded(String adUnitId);
        void onFailed(int code, String message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERSTITIAL
    // ─────────────────────────────────────────────────────────────────────────

    /** Begin pre-loading pool. Call this as early as possible (app init or level start). */
    public void preloadInterstitial(Activity activity, String adId, LoadCallback cb) {
        this.interstitialAdId = adId;
        for (int slot = 0; slot < POOL_SIZE; slot++) {
            loadInterstitialSlot(activity, adId, slot, cb);
        }
    }

    private void loadInterstitialSlot(Activity activity, String adId, int slot, LoadCallback cb) {
        if (adId == null || interstitialLoading[slot].getAndSet(true)) return;
        final int s = slot;
        mainHandler.post(() ->
            InterstitialAd.load(activity, adId, buildRequest(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialPool[s]     = ad;
                        interstitialLoadTime[s] = SystemClock.elapsedRealtime();
                        interstitialLoading[s].set(false);
                        interstitialRetry = 0;
                        Log.d(TAG, "Interstitial loaded slot=" + s);
                        if (cb != null) cb.onLoaded(adId);
                    }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                        interstitialPool[s] = null;
                        interstitialLoading[s].set(false);
                        Log.w(TAG, "Interstitial load failed slot=" + s + " err=" + e.getMessage());
                        scheduleRetry(() -> loadInterstitialSlot(activity, adId, s, cb));
                        if (cb != null) cb.onFailed(e.getCode(), e.getMessage());
                    }
                })
        );
    }

    /** Returns the freshest ready interstitial, or null if none warm. */
    public InterstitialAd getInterstitial() {
        for (int s = 0; s < POOL_SIZE; s++) {
            if (interstitialPool[s] != null && !isStale(interstitialLoadTime[s])) {
                InterstitialAd ad = interstitialPool[s];
                interstitialPool[s] = null;          // consume
                return ad;
            }
        }
        return null;
    }

    /** Called right after show() — immediately reloads the consumed slot in background. */
    public void reloadInterstitialSlot(Activity activity, int slot) {
        loadInterstitialSlot(activity, interstitialAdId, slot, null);
    }

    /** Which slot was just consumed (call before getInterstitial clears it). */
    public int getInterstitialSlotIndex() {
        for (int s = 0; s < POOL_SIZE; s++) {
            if (interstitialPool[s] != null && !isStale(interstitialLoadTime[s])) return s;
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REWARDED
    // ─────────────────────────────────────────────────────────────────────────

    public void preloadRewarded(Activity activity, String adId, LoadCallback cb) {
        this.rewardedAdId = adId;
        for (int slot = 0; slot < POOL_SIZE; slot++) {
            loadRewardedSlot(activity, adId, slot, cb);
        }
    }

    private void loadRewardedSlot(Activity activity, String adId, int slot, LoadCallback cb) {
        if (adId == null || rewardedLoading[slot].getAndSet(true)) return;
        final int s = slot;
        mainHandler.post(() ->
            RewardedAd.load(activity, adId, buildRequest(),
                new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedPool[s]     = ad;
                        rewardedLoadTime[s] = SystemClock.elapsedRealtime();
                        rewardedLoading[s].set(false);
                        if (cb != null) cb.onLoaded(adId);
                    }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                        rewardedPool[s] = null;
                        rewardedLoading[s].set(false);
                        scheduleRetry(() -> loadRewardedSlot(activity, adId, s, cb));
                        if (cb != null) cb.onFailed(e.getCode(), e.getMessage());
                    }
                })
        );
    }

    public RewardedAd getRewarded() {
        for (int s = 0; s < POOL_SIZE; s++) {
            if (rewardedPool[s] != null && !isStale(rewardedLoadTime[s])) {
                RewardedAd ad = rewardedPool[s];
                rewardedPool[s] = null;
                return ad;
            }
        }
        return null;
    }

    public void reloadRewardedSlot(Activity activity, int slot) {
        loadRewardedSlot(activity, rewardedAdId, slot, null);
    }

    public int getRewardedSlotIndex() {
        for (int s = 0; s < POOL_SIZE; s++) {
            if (rewardedPool[s] != null && !isStale(rewardedLoadTime[s])) return s;
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REWARDED INTERSTITIAL
    // ─────────────────────────────────────────────────────────────────────────

    public void preloadRewardedInterstitial(Activity activity, String adId, LoadCallback cb) {
        this.riAdId = adId;
        for (int slot = 0; slot < POOL_SIZE; slot++) {
            loadRISlot(activity, adId, slot, cb);
        }
    }

    private void loadRISlot(Activity activity, String adId, int slot, LoadCallback cb) {
        if (adId == null || riLoading[slot].getAndSet(true)) return;
        final int s = slot;
        mainHandler.post(() ->
            RewardedInterstitialAd.load(activity, adId, buildRequest(),
                new RewardedInterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                        riPool[s]     = ad;
                        riLoadTime[s] = SystemClock.elapsedRealtime();
                        riLoading[s].set(false);
                        if (cb != null) cb.onLoaded(adId);
                    }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                        riPool[s] = null;
                        riLoading[s].set(false);
                        scheduleRetry(() -> loadRISlot(activity, adId, s, cb));
                        if (cb != null) cb.onFailed(e.getCode(), e.getMessage());
                    }
                })
        );
    }

    public RewardedInterstitialAd getRewardedInterstitial() {
        for (int s = 0; s < POOL_SIZE; s++) {
            if (riPool[s] != null && !isStale(riLoadTime[s])) {
                RewardedInterstitialAd ad = riPool[s];
                riPool[s] = null;
                return ad;
            }
        }
        return null;
    }

    public void reloadRISlot(Activity activity, int slot) {
        loadRISlot(activity, riAdId, slot, null);
    }

    public int getRISlotIndex() {
        for (int s = 0; s < POOL_SIZE; s++) {
            if (riPool[s] != null && !isStale(riLoadTime[s])) return s;
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APP OPEN
    // ─────────────────────────────────────────────────────────────────────────

    public void preloadAppOpen(Activity activity, String adId, LoadCallback cb) {
        this.appOpenAdId = adId;
        if (appOpenLoading.getAndSet(true)) return;
        mainHandler.post(() ->
            AppOpenAd.load(activity, adId, buildRequest(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd       = ad;
                        appOpenLoadTime = SystemClock.elapsedRealtime();
                        appOpenLoading.set(false);
                        if (cb != null) cb.onLoaded(adId);
                    }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                        appOpenAd = null;
                        appOpenLoading.set(false);
                        scheduleRetry(() -> preloadAppOpen(activity, adId, null));
                        if (cb != null) cb.onFailed(e.getCode(), e.getMessage());
                    }
                })
        );
    }

    public AppOpenAd getAppOpen() {
        if (appOpenAd != null && !isStale(appOpenLoadTime)) {
            AppOpenAd ad = appOpenAd;
            appOpenAd = null;
            return ad;
        }
        return null;
    }

    public void reloadAppOpen(Activity activity) {
        preloadAppOpen(activity, appOpenAdId, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READINESS CHECKS (JS can poll these for instant-show decisions)
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isInterstitialReady() {
        for (int s = 0; s < POOL_SIZE; s++)
            if (interstitialPool[s] != null && !isStale(interstitialLoadTime[s])) return true;
        return false;
    }

    public boolean isRewardedReady() {
        for (int s = 0; s < POOL_SIZE; s++)
            if (rewardedPool[s] != null && !isStale(rewardedLoadTime[s])) return true;
        return false;
    }

    public boolean isRewardedInterstitialReady() {
        for (int s = 0; s < POOL_SIZE; s++)
            if (riPool[s] != null && !isStale(riLoadTime[s])) return true;
        return false;
    }

    public boolean isAppOpenReady() {
        return appOpenAd != null && !isStale(appOpenLoadTime);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private AdRequest buildRequest() {
        return new AdRequest.Builder().build();
    }

    private boolean isStale(long loadTime) {
        return loadTime == 0 || (SystemClock.elapsedRealtime() - loadTime) > MAX_AGE_MS;
    }

    private int retryCount = 0;

    private void scheduleRetry(Runnable task) {
        if (retryCount >= MAX_RETRY) { retryCount = 0; return; }
        long delay = RETRY_BASE * (1L << Math.min(retryCount, 4)); // 5s, 10s, 20s, 40s, 80s
        retryCount++;
        mainHandler.postDelayed(task, delay);
        Log.d(TAG, "Retry #" + retryCount + " in " + delay + "ms");
    }

    /** Eagerly warm all stale slots — call on app resume or navigation events. */
    public void warmAll(Activity activity) {
        if (interstitialAdId != null) {
            for (int s = 0; s < POOL_SIZE; s++) {
                if (interstitialPool[s] == null || isStale(interstitialLoadTime[s])) {
                    loadInterstitialSlot(activity, interstitialAdId, s, null);
                }
            }
        }
        if (rewardedAdId != null) {
            for (int s = 0; s < POOL_SIZE; s++) {
                if (rewardedPool[s] == null || isStale(rewardedLoadTime[s])) {
                    loadRewardedSlot(activity, rewardedAdId, s, null);
                }
            }
        }
        if (riAdId != null) {
            for (int s = 0; s < POOL_SIZE; s++) {
                if (riPool[s] == null || isStale(riLoadTime[s])) {
                    loadRISlot(activity, riAdId, s, null);
                }
            }
        }
        if (appOpenAdId != null && !isAppOpenReady()) {
            reloadAppOpen(activity);
        }
    }
}
