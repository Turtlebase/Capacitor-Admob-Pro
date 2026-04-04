import { WebPlugin } from '@capacitor/core';
import { AdMobAdvancedPlugin } from './definitions';
export declare class AdMobAdvanced extends WebPlugin implements AdMobAdvancedPlugin {
    initialize(): Promise<void>;
    showBanner(options: {
        adId: string;
    }): Promise<void>;
    hideBanner(): Promise<void>;
}
