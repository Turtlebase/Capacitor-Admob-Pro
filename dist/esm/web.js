import { WebPlugin } from '@capacitor/core';
export class AdMobAdvanced extends WebPlugin {
    async initialize() {
        console.log('AdMob initialized');
    }
    async showBanner(options) {
        console.log('Show banner:', options.adId);
    }
    async hideBanner() {
        console.log('Hide banner');
    }
}
