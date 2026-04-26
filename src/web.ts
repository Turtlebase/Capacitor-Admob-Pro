import { WebPlugin } from '@capacitor/core';
import { AdMobAdvancedPlugin } from './definitions';

export class AdMobAdvanced extends WebPlugin implements AdMobAdvancedPlugin {
  async initialize(): Promise<void> {
    console.log('AdMob initialized');
  }

  async showBanner(options: { adId: string }): Promise<void> {
    console.log('Show banner:', options.adId);
  }

  async hideBanner(): Promise<void> {
    console.log('Hide banner');
  }
  async warmAll(): Promise<void> {
  console.log('warmAll (web fallback)');
}

async isGoogleMobileAdsReady(): Promise<{ value: boolean }> {
  return { value: true };
  
}
