var capacitorAdmobAdvanced = (function (exports, core) {
    'use strict';

    const AdMobAdvanced$1 = core.registerPlugin('AdMobAdvanced', {
        web: () => Promise.resolve().then(function () { return web; }).then(m => new m.AdMobAdvanced()),
    });

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

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        AdMobAdvanced: AdMobAdvanced
    });

    exports.AdMobAdvanced = AdMobAdvanced$1;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
