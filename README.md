# capacitor-admob-advanced

> **Full-featured AdMob plugin for Capacitor 6 — Lightning-fast ad engine**  
> 0 ms interstitial delay · 2-slot warm pool · Back-press instant show · Auto-reload

---

## Why this is instant — and others aren't

The delay you see in most Capacitor ad plugins is **not Capacitor's fault** — it's a preloading strategy problem.

```
❌ Slow pattern (most plugins):
   User taps back → call loadAd() → wait 200-800ms → show ad

✅ Lightning pattern (this plugin):
   App boots       → fills 2-slot warm pool in background (100ms)
   User taps back  → grabs warm ad from pool = 0ms display delay
                  → simultaneously reloads consumed slot in background
   Next tap        → slot 1 is already warm = 0ms again
```

This is exactly how Subway Surfers, Candy Crush, and every top ad-heavy Android game works. Your Capacitor app now does the same.

---

## Feature matrix

| Format | Android | iOS | Web stub | Instant show |
|---|---|---|---|---|
| Banner (all sizes + Adaptive) | ✅ | ✅ | ✅ | N/A |
| Interstitial | ✅ | ✅ | ✅ | ✅ 0ms |
| Rewarded | ✅ | ✅ | ✅ | ✅ 0ms |
| Rewarded Interstitial | ✅ | ✅ | ✅ | ✅ 0ms |
| App Open | ✅ | ✅ | ✅ | ✅ 0ms |
| Native Advanced + scroll sync | ✅ | ✅ | ✅ | – |
| UMP Consent (GDPR/CCPA) | ✅ | ✅ | – | – |
| 2-slot warm pool per format | ✅ | ✅ | – | – |
| Auto-reload on dismiss | ✅ | ✅ | – | – |
| Stale-cache expiry (1 hr) | ✅ | ✅ | – | – |
| Exponential back-off retry | ✅ | ✅ | – | – |
| warmAll() on app resume | ✅ | ✅ | – | – |

---

## Installation

```bash
npm install capacitor-admob-advanced
npx cap sync
```

---

## Android setup

### 1. `AndroidManifest.xml`

```xml
<application>
  <meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"/>
</application>
```

### 2. Register plugin in `MainActivity.java`

```java
import com.admobadvanced.AdMobAdvancedPlugin;

public class MainActivity extends BridgeActivity {
  @Override public void onCreate(Bundle savedInstanceState) {
    registerPlugin(AdMobAdvancedPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
```

### 3. Proguard (`android/app/proguard-rules.pro`)

```pro
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.admobadvanced.** { *; }
```

---

## iOS setup

```bash
cd ios/App && pod install
```

**`Info.plist`**
```xml
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX</string>
<key>NSUserTrackingUsageDescription</key>
<string>This app uses tracking to provide relevant ads.</string>
```

---

## Quick start — lightning pattern

```typescript
import { AdMobAdvanced, BannerAdSize, BannerAdPosition } from 'capacitor-admob-advanced';
import { App } from '@capacitor/app';

// ── 1. Boot: initialize + preload pool IN PARALLEL (don't await preloads)
await AdMobAdvanced.initialize({ testingDevices: [] });

AdMobAdvanced.prepareInterstitial({ adId: 'ca-app-pub-3940256099942544/1033173712' });
AdMobAdvanced.prepareRewarded({ adId: 'ca-app-pub-3940256099942544/5224354917' });
AdMobAdvanced.prepareAppOpen({ adId: 'ca-app-pub-3940256099942544/9257395921' });

// ── 2. Show on back press — INSTANT because pool is already warm
App.addListener('backButton', async ({ canGoBack }) => {
  const { interstitialReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
  if (interstitialReady) {
    await AdMobAdvanced.showInterstitial(); // ← 0 ms delay
  }
  if (canGoBack) history.back(); else App.exitApp();
});

// ── 3. Re-warm on every resume / navigation so pool never goes cold
App.addListener('appStateChange', ({ isActive }) => {
  if (isActive) AdMobAdvanced.warmAll();
});
```

---

## isGoogleMobileAdsReady() — per-format readiness

```typescript
const status = await AdMobAdvanced.isGoogleMobileAdsReady();
// {
//   ready: true,
//   interstitialReady: true,        ← warm ad in pool
//   rewardedReady: true,
//   rewardedInterstitialReady: false,
//   appOpenReady: true,
// }

// Guard before showing:
if (status.interstitialReady) {
  await AdMobAdvanced.showInterstitial(); // instant
} else {
  // Skip or queue — pool will reload automatically
}
```

---

## warmAll()

Call this on every SPA route change or app foreground event. It silently re-fills any stale or consumed slots.

```typescript
// Ionic lifecycle
ionViewWillEnter() {
  AdMobAdvanced.warmAll();
}

// React useEffect
useEffect(() => {
  AdMobAdvanced.warmAll();
}, [location.pathname]);

// App resume
App.addListener('appStateChange', ({ isActive }) => {
  if (isActive) AdMobAdvanced.warmAll();
});
```

---

## How the warm pool works

```
prepareInterstitial('ca-app-pub-xxx/yyy')
  │
  ├─ slot 0: loadAd() → ✅ warm (loads in ~200-600ms)
  └─ slot 1: loadAd() → ✅ warm (loads in ~200-600ms)

User taps back (500ms later)
  │
  ├─ getInterstitialSlotIndex() → slot 0
  ├─ getInterstitial()          → pops slot 0 (instant, no network)
  ├─ ad.show()                  → displays IMMEDIATELY
  └─ reloadInterstitialSlot(0)  → background reload starts NOW
                                   (slot 1 still warm for next tap)

User taps back again (2 seconds later)
  │
  ├─ getInterstitial()          → slot 1 still warm → instant again
  └─ reloadInterstitialSlot(1)  → background reload
```

---

## Back press / content tap patterns

```typescript
// Pattern A — every N back presses
let n = 0;
App.addListener('backButton', async ({ canGoBack }) => {
  if (++n % 3 === 0) {
    const { interstitialReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
    if (interstitialReady) await AdMobAdvanced.showInterstitial();
  }
  if (canGoBack) history.back(); else App.exitApp();
});

// Pattern B — tap to open wallpaper / download
async function onItemTap(openContent: () => void) {
  const { interstitialReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
  if (interstitialReady) await AdMobAdvanced.showInterstitial();
  openContent(); // always opens, ad is non-blocking
}

// Pattern C — App Open on foreground
let isFirstForeground = true;
App.addListener('appStateChange', async ({ isActive }) => {
  if (!isActive) return;
  if (isFirstForeground) { isFirstForeground = false; return; }
  const { appOpenReady } = await AdMobAdvanced.isGoogleMobileAdsReady();
  if (appOpenReady) await AdMobAdvanced.showAppOpen();
});
```

---

## Banner

```typescript
await AdMobAdvanced.showBanner({
  adId: 'ca-app-pub-3940256099942544/6300978111',
  adSize: BannerAdSize.ADAPTIVE_BANNER,
  position: BannerAdPosition.BOTTOM_CENTER,
});

// Auto-adjust content padding
AdMobAdvanced.addListener('bannerAdSizeChanged', ({ height }) => {
  document.body.style.paddingBottom = `${height}px`;
});
```

---

## Rewarded

```typescript
await AdMobAdvanced.prepareRewarded({ adId: 'ca-app-pub-3940256099942544/5224354917' });

// Later — instant if pool is warm
const reward = await AdMobAdvanced.showRewarded();
grantCoins(reward.amount);
```

---

## Native Advanced (scroll-sync overlay)

```html
<div id="native-ad-slot" style="width:100%;height:300px;"></div>
```

```typescript
await AdMobAdvanced.loadNativeAd({
  adId: 'ca-app-pub-3940256099942544/2247696110',
  containerId: '#native-ad-slot',
});
await AdMobAdvanced.showNativeAd({ containerId: '#native-ad-slot' });

// Sync on scroll (auto-synced on device; useful for custom scroll containers)
window.addEventListener('scroll', () =>
  AdMobAdvanced.updateNativeAdLayout({ containerId: '#native-ad-slot' })
, { passive: true });
```

---

## Test ad unit IDs

| Format | ID |
|---|---|
| Banner | `ca-app-pub-3940256099942544/6300978111` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded | `ca-app-pub-3940256099942544/5224354917` |
| Rewarded Interstitial | `ca-app-pub-3940256099942544/5354046379` |
| App Open | `ca-app-pub-3940256099942544/9257395921` |
| Native | `ca-app-pub-3940256099942544/2247696110` |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Still seeing delay | Make sure `prepareInterstitial` is called at app boot, not right before `showInterstitial` |
| Ad not showing after back press | Check `isGoogleMobileAdsReady().interstitialReady` — if false, pool hasn't loaded yet |
| Pool goes cold after long idle | Call `warmAll()` on `appStateChange` and route change |
| iOS pod error | Run `pod install` in `ios/App` |
| Android ClassNotFound | Register plugin in `MainActivity` |

---

## License

MIT
