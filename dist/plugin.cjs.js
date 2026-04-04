'use strict';

var core = require('@capacitor/core');

class AdMobAdvanced extends core.WebPlugin {
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

exports.AdMobAdvanced = AdMobAdvanced;
//# sourceMappingURL=plugin.cjs.js.map
