'use strict';

var core = require('@capacitor/core');

const AdMobAdvanced$1 = core.registerPlugin('AdMobAdvanced', {
    web: () => Promise.resolve().then(function () { return web; }).then(m => new m.AdMobAdvanced()),
});

class AdMobAdvanced extends core.WebPlugin {
    async initialize() {
        console.log('AdMob initialized');
    }
    async requestConsentInfo() {
        console.log('requestConsentInfo (web)');
        return {};
    }
    async showConsentForm() {
        console.log('showConsentForm (web)');
        return {};
    }
    async resetConsent() {
        console.log('resetConsent (web)');
    }
    async showBanner(options) {
        console.log('Show banner:', options.adId);
    }
    async hideBanner() {
        console.log('Hide banner');
    }
    async resumeBanner() {
        console.log('Resume banner');
    }
    async removeBanner() {
        console.log('Remove banner');
    }
    async setBannerPosition(options) {
        console.log('Set banner position:', options);
    }
    async prepareInterstitial() {
        console.log('prepareInterstitial (web)');
        return {};
    }
    async showInterstitial() {
        console.log('showInterstitial (web)');
    }
    async prepareRewarded() {
        console.log('prepareRewarded (web)');
        return {};
    }
    async showRewarded() {
        console.log('showRewarded (web)');
        return {};
    }
    async prepareRewardedInterstitial() {
        console.log('prepareRewardedInterstitial (web)');
        return {};
    }
    async showRewardedInterstitial() {
        console.log('showRewardedInterstitial (web)');
        return {};
    }
    async prepareAppOpen() {
        console.log('prepareAppOpen (web)');
        return {};
    }
    async showAppOpen() {
        console.log('showAppOpen (web)');
    }
    // 🔥 REQUIRED (your crash fix)
    async warmAll() {
        console.log('warmAll (web fallback)');
    }
    async isGoogleMobileAdsReady() {
        return { value: true };
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    AdMobAdvanced: AdMobAdvanced
});

exports.AdMobAdvanced = AdMobAdvanced$1;
//# sourceMappingURL=plugin.cjs.js.map
