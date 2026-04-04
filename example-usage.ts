/**
 * capacitor-admob-advanced — Lightning-Fast Ad Patterns
 *
 * The rule for instant ads: by the time the user CAN trigger an ad,
 * the ad must ALREADY be loaded and sitting warm in the pool.
 *
 * These examples cover every real-world trigger pattern.
 */

import { AdMobAdvanced, BannerAdSize, BannerAdPosition, AdsReadyInfo } from 'capacitor-admob-advanced';
import { App } from '@capacitor/app';

// ─── YOUR AD UNIT IDs ────────────────────────────────────────────────────────
const ADS = {
  banner:               'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
  interstitial:         'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
  rewarded:             'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
  rewardedInterstitial: 'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
  appOpen:              'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
  native:               'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
};

// ════════════════════════════════════════════════════════════════════════════
// STEP 1 — Initialize once at app boot (before any UI renders)
// ════════════════════════════════════════════════════════════════════════════

export async function bootAds() {
  await AdMobAdvanced.initialize({ testingDevices: [] });

  // Fire all preloads in parallel — don't await, let them warm in background
  AdMobAdvanced.prepareInterstitial({ adId: ADS.interstitial }).catch(() => {});
  AdMobAdvanced.prepareRewarded({ adId: ADS.rewarded }).catch(() => {});
  AdMobAdvanced.prepareRewardedInterstitial({ adId: ADS.rewardedInterstitial }).catch(() => {});
  AdMobAdvanced.prepareAppOpen({ adId: ADS.appOpen }).catch(() => {});

  // Banner can start loading now too
  AdMobAdvanced.showBanner({
    adId: ADS.banner,
    adSize: BannerAdSize.ADAPTIVE_BANNER,
    position: BannerAdPosition.BOTTOM_CENTER,
  }).catch(() => {});
}

// ════════════════════════════════════════════════════════════════════════════
// PATTERN A — Back button / hardware back press (most common trigger)
// ════════════════════════════════════════════════════════════════════════════

let backPressCount = 0;

export function setupBackPressAd() {
  App.addListener('backButton', async ({ canGoBack }) => {
    backPressCount++;

    if (backPressCount % 3 === 0) {
      // Check pool first — if warm, show is INSTANT (0 ms gap)
      const ready = await AdMobAdvanced.isGoogleMobileAdsReady();
      if (ready.interstitialReady) {
        await AdMobAdvanced.showInterstitial();
        // Pool reloads itself in background — next back press is also instant
      }
    }

    if (canGoBack) history.back();
    else App.exitApp();
  });
}

// ════════════════════════════════════════════════════════════════════════════
// PATTERN B — Tap / click to open wallpaper (or any content reveal)
// ════════════════════════════════════════════════════════════════════════════

export async function onWallpaperTap(openWallpaper: () => void) {
  const ready = await AdMobAdvanced.isGoogleMobileAdsReady();

  if (ready.interstitialReady) {
    // Ad is warm → show immediately, THEN open content after dismiss
    await AdMobAdvanced.showInterstitial();
    openWallpaper();
  } else {
    // Pool not warm (first run, bad network) → open content without delay
    // Ad is reloading in background for next tap
    openWallpaper();
  }
}

// ════════════════════════════════════════════════════════════════════════════
// PATTERN C — Navigation between pages / routes
// ════════════════════════════════════════════════════════════════════════════

let pageViewCount = 0;

export async function onPageChange(navigate: () => void) {
  pageViewCount++;

  // Re-warm the pool on every navigation (keeps ads fresh)
  AdMobAdvanced.warmAll().catch(() => {});

  if (pageViewCount % 4 === 0) {
    const { interstitialReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
    if (interstitialReady) {
      await AdMobAdvanced.showInterstitial();
    }
  }

  navigate();
}

// ════════════════════════════════════════════════════════════════════════════
// PATTERN D — App Open ad on foreground (no delay at all)
// ════════════════════════════════════════════════════════════════════════════

let isFirstForeground = true;

export function setupAppOpenAd() {
  App.addListener('appStateChange', async ({ isActive }) => {
    if (!isActive) return;
    if (isFirstForeground) { isFirstForeground = false; return; } // skip initial launch

    const { appOpenReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
    if (appOpenReady) {
      await AdMobAdvanced.showAppOpen();
      // showAppOpen auto-reloads for next foreground event
    }
  });
}

// ════════════════════════════════════════════════════════════════════════════
// PATTERN E — Rewarded ad before unlocking feature (non-blocking UX)
// ════════════════════════════════════════════════════════════════════════════

export async function watchAdForCoins(): Promise<number> {
  const { rewardedReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
  if (!rewardedReady) {
    console.log('Ad not ready yet — try again shortly');
    return 0;
  }
  const reward = await AdMobAdvanced.showRewarded();
  return reward.amount; // grant coins / unlock feature
}

// ════════════════════════════════════════════════════════════════════════════
// PATTERN F — Ionic / Angular service (drop-in class)
// ════════════════════════════════════════════════════════════════════════════

export class LightningAdService {
  private interstitialAdId = '';
  private tapCount = 0;

  async init(interstitialAdId: string, rewardedAdId: string) {
    this.interstitialAdId = interstitialAdId;
    await AdMobAdvanced.initialize({});

    // Parallel preload — fires & forgets
    AdMobAdvanced.prepareInterstitial({ adId: interstitialAdId });
    AdMobAdvanced.prepareRewarded({ adId: rewardedAdId });

    // Re-warm on resume
    App.addListener('appStateChange', ({ isActive }) => {
      if (isActive) AdMobAdvanced.warmAll();
    });
  }

  /** Call on every back-press or content tap. Shows ad every N calls. */
  async maybeShowInterstitial(every = 3): Promise<boolean> {
    this.tapCount++;
    if (this.tapCount % every !== 0) return false;

    const { interstitialReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
    if (!interstitialReady) return false;

    await AdMobAdvanced.showInterstitial();
    return true;
  }

  /** Guaranteed instant show — throws if not ready */
  async showInterstitialNow(): Promise<void> {
    const { interstitialReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
    if (!interstitialReady) throw new Error('Ad not ready');
    await AdMobAdvanced.showInterstitial();
  }

  async showRewarded(): Promise<number> {
    const reward = await AdMobAdvanced.showRewarded();
    return reward.amount;
  }

  cleanup() {
    AdMobAdvanced.removeAllListeners();
    AdMobAdvanced.removeBanner().catch(() => {});
  }
}

// ════════════════════════════════════════════════════════════════════════════
// WHY IS THIS INSTANT?  (explanation comment)
// ════════════════════════════════════════════════════════════════════════════
//
// Traditional (SLOW) pattern — what most plugins do:
//   User taps → call loadAd() → wait 200-800ms network → show ad
//
// Lightning pattern used here:
//   App boots → loadAd() starts in background (100ms after init)
//   User taps  → pool already warm → show() grabs cached ad = 0ms delay
//               → simultaneously fires loadAd() for NEXT show in background
//
// The native layer (AdCacheManager.java) keeps 2 slots warm per format.
// If slot 0 is consumed on tap, slot 1 shows next tap while slot 0 reloads.
// This is exactly how top Android games (Subway Surfers, Candy Crush etc.)
// achieve seamless instant interstitials — now your Capacitor app does too.
