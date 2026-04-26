import { WebPlugin } from '@capacitor/core';
import { AdMobAdvancedPlugin } from './definitions';

export class AdMobAdvanced extends WebPlugin implements AdMobAdvancedPlugin {

  async initialize(): Promise<void> {
    console.log('AdMob initialized');
  }

  async requestConsentInfo(): Promise<any> {
    console.log('requestConsentInfo (web)');
    return {};
  }

  async showConsentForm(): Promise<any> {
    console.log('showConsentForm (web)');
    return {};
  }

  async resetConsent(): Promise<void> {
    console.log('resetConsent (web)');
  }

  async showBanner(options: { adId: string }): Promise<void> {
    console.log('Show banner:', options.adId);
  }

  async hideBanner(): Promise<void> {
    console.log('Hide banner');
  }

  async resumeBanner(): Promise<void> {
    console.log('Resume banner');
  }

  async removeBanner(): Promise<void> {
    console.log('Remove banner');
  }

  async setBannerPosition(options: { position: string; margin?: number }): Promise<void> {
    console.log('Set banner position:', options);
  }

  async prepareInterstitial(): Promise<any> {
    console.log('prepareInterstitial (web)');
    return {};
  }

  async showInterstitial(): Promise<void> {
    console.log('showInterstitial (web)');
  }

  async prepareRewarded(): Promise<any> {
    console.log('prepareRewarded (web)');
    return {};
  }

  async showRewarded(): Promise<any> {
    console.log('showRewarded (web)');
    return {};
  }

  async prepareRewardedInterstitial(): Promise<any> {
    console.log('prepareRewardedInterstitial (web)');
    return {};
  }

  async showRewardedInterstitial(): Promise<any> {
    console.log('showRewardedInterstitial (web)');
    return {};
  }

  async prepareAppOpen(): Promise<any> {
    console.log('prepareAppOpen (web)');
    return {};
  }

  async showAppOpen(): Promise<void> {
    console.log('showAppOpen (web)');
  }

  // 🔥 REQUIRED (your crash fix)
  async warmAll(): Promise<void> {
    console.log('warmAll (web fallback)');
  }

  async isGoogleMobileAdsReady(): Promise<{ value: boolean }> {
    return { value: true };
  }
}
