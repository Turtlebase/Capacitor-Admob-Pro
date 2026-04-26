import { WebPlugin } from '@capacitor/core';
import { AdMobAdvancedPlugin } from './definitions';
export declare class AdMobAdvanced extends WebPlugin implements AdMobAdvancedPlugin {
    initialize(): Promise<void>;
    requestConsentInfo(): Promise<any>;
    showConsentForm(): Promise<any>;
    resetConsent(): Promise<void>;
    showBanner(options: {
        adId: string;
    }): Promise<void>;
    hideBanner(): Promise<void>;
    resumeBanner(): Promise<void>;
    removeBanner(): Promise<void>;
    setBannerPosition(options: {
        position: string;
        margin?: number;
    }): Promise<void>;
    prepareInterstitial(): Promise<any>;
    showInterstitial(): Promise<void>;
    prepareRewarded(): Promise<any>;
    showRewarded(): Promise<any>;
    prepareRewardedInterstitial(): Promise<any>;
    showRewardedInterstitial(): Promise<any>;
    prepareAppOpen(): Promise<any>;
    showAppOpen(): Promise<void>;
    warmAll(): Promise<void>;
    isGoogleMobileAdsReady(): Promise<{
        value: boolean;
    }>;
}
