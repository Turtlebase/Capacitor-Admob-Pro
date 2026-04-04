var capacitorAdmobAdvanced = (function (exports, core) {
    'use strict';

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

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
