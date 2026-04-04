#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

CAP_PLUGIN(AdMobAdvancedPlugin, "AdMobAdvanced",
    CAP_PLUGIN_METHOD(initialize,               CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestConsentInfo,        CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showConsentForm,           CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(resetConsent,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showBanner,                CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(hideBanner,                CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(resumeBanner,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(removeBanner,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(setBannerPosition,         CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(prepareInterstitial,       CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showInterstitial,          CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(prepareRewarded,           CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showRewarded,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(prepareRewardedInterstitial, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showRewardedInterstitial,  CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(prepareAppOpen,            CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showAppOpen,               CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(loadNativeAd,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showNativeAd,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(hideNativeAd,              CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(removeNativeAd,            CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(updateNativeAdLayout,      CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(isGoogleMobileAdsReady,    CAPPluginReturnPromise);
)
