import Foundation
import Capacitor
import GoogleMobileAds
import UserMessagingPlatform
import WebKit

// ─────────────────────────────────────────────────────────────────────────────
// AdMobAdvancedPlugin.swift
// Full Capacitor plugin: Banner · Interstitial · Rewarded · Rewarded Interstitial
//                        App Open · Native Advanced (with WebView scroll sync)
// ─────────────────────────────────────────────────────────────────────────────

@objc(AdMobAdvancedPlugin)
public class AdMobAdvancedPlugin: CAPPlugin,
    GADBannerViewDelegate,
    GADFullScreenContentDelegate {

    // ── Ad instances ──────────────────────────────────────────────────────────
    private var bannerView: GADBannerView?
    private var bannerOptions: [String: Any]?

    private var interstitialAd: GADInterstitialAd?
    private var rewardedAd: GADRewardedAd?
    private var rewardedInterstitialAd: GADRewardedInterstitialAd?
    private var appOpenAd: GADAppOpenAd?

    private var nativeAd: GADNativeAd?
    private var nativeOverlay: NativeAdOverlayView?

    private var rewardedCall: CAPPluginCall?
    private var rewardedInterstitialCall: CAPPluginCall?

    private var isInitialized = false

    // ── Initialize ─────────────────────────────────────────────────────────────

    @objc func initialize(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            GADMobileAds.sharedInstance().start { [weak self] _ in
                self?.isInitialized = true
                call.resolve()
            }
        }
    }

    // ── Consent ────────────────────────────────────────────────────────────────

    @objc func requestConsentInfo(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController else { call.reject("No view controller"); return }
        let params = UMPRequestParameters()
        UMPConsentInformation.sharedInstance.requestConsentInfoUpdate(with: params) { error in
            if let error = error {
                call.reject(error.localizedDescription); return
            }
            let ci = UMPConsentInformation.sharedInstance
            call.resolve([
                "status": self.consentStatusString(ci.consentStatus),
                "isConsentFormAvailable": ci.isConsentFormAvailable
            ])
        }
    }

    @objc func showConsentForm(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController else { call.reject("No view controller"); return }
        UMPConsentForm.loadAndPresentIfRequired(from: vc) { error in
            if let error = error { call.reject(error.localizedDescription); return }
            let ci = UMPConsentInformation.sharedInstance
            call.resolve([
                "status": self.consentStatusString(ci.consentStatus),
                "isConsentFormAvailable": ci.isConsentFormAvailable
            ])
        }
    }

    @objc func resetConsent(_ call: CAPPluginCall) {
        UMPConsentInformation.sharedInstance.reset()
        call.resolve()
    }

    // ── Banner ─────────────────────────────────────────────────────────────────

    @objc func showBanner(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController else { call.reject("No vc"); return }
        bannerOptions = call.options
        let adId     = call.getString("adId") ?? "ca-app-pub-3940256099942544/6300978111"
        let sizeStr  = call.getString("adSize") ?? "BANNER"
        let position = call.getString("position") ?? "BOTTOM_CENTER"
        let margin   = call.getInt("margin") ?? 0

        DispatchQueue.main.async {
            self.bannerView?.removeFromSuperview()
            let banner = GADBannerView(adSize: self.resolveAdSize(sizeStr, vc: vc))
            banner.adUnitID  = adId
            banner.rootViewController = vc
            banner.delegate  = self
            banner.translatesAutoresizingMaskIntoConstraints = false
            vc.view.addSubview(banner)
            self.applyBannerConstraints(banner, in: vc.view, position: position, margin: margin)
            banner.load(GADRequest())
            self.bannerView = banner
            call.resolve()
        }
    }

    @objc func hideBanner(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.bannerView?.isHidden = true; call.resolve() }
    }

    @objc func resumeBanner(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.bannerView?.isHidden = false; call.resolve() }
    }

    @objc func removeBanner(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.bannerView?.removeFromSuperview(); self.bannerView = nil; call.resolve() }
    }

    @objc func setBannerPosition(_ call: CAPPluginCall) {
        guard let banner = bannerView, let sv = banner.superview else { call.reject("No banner"); return }
        let position = call.getString("position") ?? "BOTTOM_CENTER"
        let margin   = call.getInt("margin") ?? 0
        DispatchQueue.main.async {
            NSLayoutConstraint.deactivate(sv.constraints.filter { $0.firstItem === banner || $0.secondItem === banner })
            self.applyBannerConstraints(banner, in: sv, position: position, margin: margin)
            call.resolve()
        }
    }

    // GADBannerViewDelegate
    public func bannerViewDidReceiveAd(_ bannerView: GADBannerView) {
        notifyListeners("bannerAdLoaded", data: ["adUnitId": bannerView.adUnitID ?? ""])
        notifyListeners("bannerAdSizeChanged", data: [
            "width":  bannerView.adSize.size.width,
            "height": bannerView.adSize.size.height
        ])
    }
    public func bannerView(_ bannerView: GADBannerView, didFailToReceiveAdWithError error: Error) {
        notifyListeners("bannerAdFailedToLoad", data: errorDict(error))
    }
    public func bannerViewWillPresentScreen(_ bannerView: GADBannerView) { notifyListeners("bannerAdOpened", data: [:]) }
    public func bannerViewDidDismissScreen(_ bannerView: GADBannerView)  { notifyListeners("bannerAdClosed", data: [:]) }
    public func bannerViewDidRecordImpression(_ bannerView: GADBannerView) { notifyListeners("bannerAdImpression", data: [:]) }

    // ── Interstitial ───────────────────────────────────────────────────────────

    @objc func prepareInterstitial(_ call: CAPPluginCall) {
        let adId = call.getString("adId") ?? ""
        GADInterstitialAd.load(withAdUnitID: adId, request: GADRequest()) { [weak self] ad, error in
            if let error = error { self?.notifyListeners("interstitialAdFailedToLoad", data: self?.errorDict(error) ?? [:]); call.reject(error.localizedDescription); return }
            self?.interstitialAd = ad
            ad?.fullScreenContentDelegate = self
            self?.notifyListeners("interstitialAdLoaded", data: ["adUnitId": adId])
            call.resolve(["adUnitId": adId])
        }
    }

    @objc func showInterstitial(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController, let ad = interstitialAd else { call.reject("Not ready"); return }
        DispatchQueue.main.async { ad.present(fromRootViewController: vc); call.resolve() }
    }

    // ── Rewarded ───────────────────────────────────────────────────────────────

    @objc func prepareRewarded(_ call: CAPPluginCall) {
        let adId = call.getString("adId") ?? ""
        GADRewardedAd.load(withAdUnitID: adId, request: GADRequest()) { [weak self] ad, error in
            if let error = error { call.reject(error.localizedDescription); return }
            self?.rewardedAd = ad
            ad?.fullScreenContentDelegate = self
            self?.notifyListeners("rewardedAdLoaded", data: ["adUnitId": adId])
            call.resolve(["adUnitId": adId])
        }
    }

    @objc func showRewarded(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController, let ad = rewardedAd else { call.reject("Not ready"); return }
        rewardedCall = call
        DispatchQueue.main.async {
            ad.present(fromRootViewController: vc) { [weak self] in
                let reward = ad.adReward
                let data: [String: Any] = ["type": reward.type, "amount": reward.amount.intValue]
                self?.notifyListeners("rewardedAdRewarded", data: data)
                call.resolve(data)
                self?.rewardedCall = nil
            }
        }
    }

    // ── Rewarded Interstitial ──────────────────────────────────────────────────

    @objc func prepareRewardedInterstitial(_ call: CAPPluginCall) {
        let adId = call.getString("adId") ?? ""
        GADRewardedInterstitialAd.load(withAdUnitID: adId, request: GADRequest()) { [weak self] ad, error in
            if let error = error { call.reject(error.localizedDescription); return }
            self?.rewardedInterstitialAd = ad
            ad?.fullScreenContentDelegate = self
            self?.notifyListeners("rewardedInterstitialAdLoaded", data: ["adUnitId": adId])
            call.resolve(["adUnitId": adId])
        }
    }

    @objc func showRewardedInterstitial(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController, let ad = rewardedInterstitialAd else { call.reject("Not ready"); return }
        rewardedInterstitialCall = call
        DispatchQueue.main.async {
            ad.present(fromRootViewController: vc) { [weak self] in
                let reward = ad.adReward
                let data: [String: Any] = ["type": reward.type, "amount": reward.amount.intValue]
                self?.notifyListeners("rewardedInterstitialAdRewarded", data: data)
                call.resolve(data)
                self?.rewardedInterstitialCall = nil
            }
        }
    }

    // ── App Open ───────────────────────────────────────────────────────────────

    @objc func prepareAppOpen(_ call: CAPPluginCall) {
        let adId = call.getString("adId") ?? ""
        GADAppOpenAd.load(withAdUnitID: adId, request: GADRequest()) { [weak self] ad, error in
            if let error = error { call.reject(error.localizedDescription); return }
            self?.appOpenAd = ad
            ad?.fullScreenContentDelegate = self
            self?.notifyListeners("appOpenAdLoaded", data: ["adUnitId": adId])
            call.resolve(["adUnitId": adId])
        }
    }

    @objc func showAppOpen(_ call: CAPPluginCall) {
        guard let vc = bridge?.viewController, let ad = appOpenAd else { call.reject("Not ready"); return }
        DispatchQueue.main.async { ad.present(fromRootViewController: vc); call.resolve() }
    }

    // ── Native Advanced ────────────────────────────────────────────────────────

    @objc func loadNativeAd(_ call: CAPPluginCall) {
        let adId = call.getString("adId") ?? ""
        let loader = GADAdLoader(adUnitID: adId, rootViewController: bridge?.viewController,
                                 adTypes: [.native], options: nil)
        let handler = NativeAdLoaderDelegate(plugin: self, call: call, adId: adId)
        loader.delegate = handler
        objc_setAssociatedObject(loader, &AssociatedKeys.delegateKey, handler, .OBJC_ASSOCIATION_RETAIN)
        DispatchQueue.main.async { loader.load(GADRequest()) }
    }

    func didLoadNativeAd(_ ad: GADNativeAd, call: CAPPluginCall, adId: String) {
        nativeAd = ad
        let assets: [String: Any?] = [
            "headline": ad.headline,
            "body": ad.body,
            "callToAction": ad.callToAction,
            "advertiser": ad.advertiser,
            "starRating": ad.starRating?.doubleValue,
        ]
        notifyListeners("nativeAdLoaded", data: ["adUnitId": adId, "assets": assets.compactMapValues { $0 }])
        call.resolve(["adUnitId": adId])
    }

    @objc func showNativeAd(_ call: CAPPluginCall) {
        let containerId = call.getString("containerId") ?? ""
        guard let vc = bridge?.viewController, let webView = bridge?.webView, let ad = nativeAd else {
            call.reject("Native ad not loaded"); return
        }
        DispatchQueue.main.async {
            self.nativeOverlay?.removeFromSuperview()
            let overlay = NativeAdOverlayView(nativeAd: ad, webView: webView, containerId: containerId)
            vc.view.addSubview(overlay)
            overlay.frame = vc.view.bounds
            overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            self.nativeOverlay = overlay
            call.resolve()
        }
    }

    @objc func hideNativeAd(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.nativeOverlay?.isHidden = true; call.resolve() }
    }

    @objc func removeNativeAd(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.nativeOverlay?.removeFromSuperview(); self.nativeOverlay = nil; call.resolve() }
    }

    @objc func updateNativeAdLayout(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.nativeOverlay?.syncPosition(); call.resolve() }
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    @objc func isGoogleMobileAdsReady(_ call: CAPPluginCall) {
        call.resolve(["ready": isInitialized])
    }

    // ── GADFullScreenContentDelegate ──────────────────────────────────────────

    public func adDidPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        let ev = eventName(for: ad, suffix: "Showed")
        notifyListeners(ev, data: [:])
    }

    public func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        let ev = eventName(for: ad, suffix: "Dismissed")
        notifyListeners(ev, data: [:])
    }

    public func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        let ev = eventName(for: ad, suffix: "FailedToShow")
        notifyListeners(ev, data: errorDict(error))
    }

    public func adDidRecordImpression(_ ad: GADFullScreenPresentingAd) {
        let ev = eventName(for: ad, suffix: "Impression")
        notifyListeners(ev, data: [:])
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private func resolveAdSize(_ name: String, vc: UIViewController) -> GADAdSize {
        switch name {
        case "LARGE_BANNER":     return GADAdSizeLargeBanner
        case "MEDIUM_RECTANGLE": return GADAdSizeMediumRectangle
        case "FULL_BANNER":      return GADAdSizeFullBanner
        case "LEADERBOARD":      return GADAdSizeLeaderboard
        case "ADAPTIVE_BANNER":  return GADCurrentOrientationAnchoredAdaptiveBannerAdSizeWithWidth(vc.view.frame.width)
        default:                 return GADAdSizeBanner
        }
    }

    private func applyBannerConstraints(_ banner: UIView, in parent: UIView, position: String, margin: Int) {
        switch position {
        case "TOP_CENTER":
            NSLayoutConstraint.activate([
                banner.topAnchor.constraint(equalTo: parent.safeAreaLayoutGuide.topAnchor, constant: CGFloat(margin)),
                banner.centerXAnchor.constraint(equalTo: parent.centerXAnchor)
            ])
        case "CENTER":
            NSLayoutConstraint.activate([
                banner.centerXAnchor.constraint(equalTo: parent.centerXAnchor),
                banner.centerYAnchor.constraint(equalTo: parent.centerYAnchor)
            ])
        default:
            NSLayoutConstraint.activate([
                banner.bottomAnchor.constraint(equalTo: parent.safeAreaLayoutGuide.bottomAnchor, constant: -CGFloat(margin)),
                banner.centerXAnchor.constraint(equalTo: parent.centerXAnchor)
            ])
        }
    }

    private func eventName(for ad: GADFullScreenPresentingAd, suffix: String) -> String {
        switch ad {
        case is GADInterstitialAd:             return "interstitialAd\(suffix)"
        case is GADRewardedAd:                 return "rewardedAd\(suffix)"
        case is GADRewardedInterstitialAd:     return "rewardedInterstitialAd\(suffix)"
        case is GADAppOpenAd:                  return "appOpenAd\(suffix)"
        default:                               return "ad\(suffix)"
        }
    }

    private func errorDict(_ error: Error) -> [String: Any] {
        let nsErr = error as NSError
        return ["code": nsErr.code, "message": nsErr.localizedDescription, "domain": nsErr.domain]
    }

    private func consentStatusString(_ status: UMPConsentStatus) -> String {
        switch status {
        case .required:    return "REQUIRED"
        case .notRequired: return "NOT_REQUIRED"
        case .obtained:    return "OBTAINED"
        default:           return "UNKNOWN"
        }
    }
}

private enum AssociatedKeys { static var delegateKey = "NativeAdLoaderDelegate" }

// ── Native Ad Loader Delegate ──────────────────────────────────────────────────

class NativeAdLoaderDelegate: NSObject, GADAdLoaderDelegate, GADNativeAdLoaderDelegate {
    weak var plugin: AdMobAdvancedPlugin?
    let call: CAPPluginCall
    let adId: String

    init(plugin: AdMobAdvancedPlugin, call: CAPPluginCall, adId: String) {
        self.plugin = plugin; self.call = call; self.adId = adId
    }

    func adLoader(_ adLoader: GADAdLoader, didReceive nativeAd: GADNativeAd) {
        plugin?.didLoadNativeAd(nativeAd, call: call, adId: adId)
    }

    func adLoader(_ adLoader: GADAdLoader, didFailToReceiveAdWithError error: Error) {
        plugin?.notifyListeners("nativeAdFailedToLoad", data: ["message": error.localizedDescription])
        call.reject(error.localizedDescription)
    }
}

// ── Native Ad Overlay UIView ───────────────────────────────────────────────────

class NativeAdOverlayView: UIView, WKScriptMessageHandler {
    private let nativeAdView: GADNativeAdView
    private weak var webView: WKWebView?
    private let containerId: String

    init(nativeAd: GADNativeAd, webView: WKWebView, containerId: String) {
        self.webView     = webView
        self.containerId = containerId
        nativeAdView     = GADNativeAdView()
        super.init(frame: .zero)
        backgroundColor  = .clear
        isUserInteractionEnabled = false
        nativeAdView.nativeAd = nativeAd
        buildLayout(nativeAd: nativeAd)
        addSubview(nativeAdView)
        injectScrollBridge(into: webView)
    }

    required init?(coder: NSCoder) { fatalError() }

    func syncPosition() {
        let escaped = containerId.replacingOccurrences(of: "'", with: "\\'")
        let js = "window.__admobNative && window.__admobNative.sync('\(escaped)');"
        webView?.evaluateJavaScript(js)
    }

    // WKScriptMessageHandler
    func userContentController(_ ucc: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "admobNativeRect",
              let body = message.body as? [String: CGFloat],
              let wv = webView else { return }
        let top = body["top"] ?? 0, left = body["left"] ?? 0
        let w   = body["width"] ?? 0, h   = body["height"] ?? 0
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let wvOrigin = wv.convert(CGPoint.zero, to: self)
            let scale    = wv.scrollView.zoomScale
            self.nativeAdView.frame = CGRect(
                x:      wvOrigin.x + left  * scale,
                y:      wvOrigin.y + top   * scale,
                width:  w * scale,
                height: h * scale
            )
        }
    }

    private func injectScrollBridge(into webView: WKWebView) {
        let escaped = containerId.replacingOccurrences(of: "'", with: "\\'")
        webView.configuration.userContentController.add(self, name: "admobNativeRect")
        let js = """
        (function(){
          if(window.__admobNative)return;
          function sync(id){
            var el=document.querySelector(id);
            if(!el)return;
            var r=el.getBoundingClientRect();
            window.webkit.messageHandlers.admobNativeRect.postMessage({top:r.top,left:r.left,width:r.width,height:r.height});
          }
          window.__admobNative={sync:sync};
          ['scroll','resize','touchmove'].forEach(function(ev){
            window.addEventListener(ev,function(){sync('\(escaped)');},{passive:true});
          });
          sync('\(escaped)');
        })();
        """
        webView.evaluateJavaScript(js)
    }

    private func buildLayout(nativeAd: GADNativeAd) {
        nativeAdView.backgroundColor = .white
        nativeAdView.layer.cornerRadius = 8
        nativeAdView.clipsToBounds = true

        let headline = UILabel()
        headline.text = nativeAd.headline; headline.font = .boldSystemFont(ofSize: 14)
        nativeAdView.headlineView = headline

        let body = UILabel()
        body.text = nativeAd.body; body.font = .systemFont(ofSize: 12); body.textColor = .darkGray
        body.numberOfLines = 2
        nativeAdView.bodyView = body

        let cta = UIButton(type: .system)
        cta.setTitle(nativeAd.callToAction, for: .normal)
        cta.backgroundColor = UIColor(red: 0.26, green: 0.52, blue: 0.96, alpha: 1)
        cta.setTitleColor(.white, for: .normal)
        cta.layer.cornerRadius = 6
        nativeAdView.callToActionView = cta

        let stack = UIStackView(arrangedSubviews: [headline, body, cta])
        stack.axis = .vertical; stack.spacing = 6
        stack.translatesAutoresizingMaskIntoConstraints = false
        nativeAdView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: nativeAdView.topAnchor, constant: 12),
            stack.leadingAnchor.constraint(equalTo: nativeAdView.leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: nativeAdView.trailingAnchor, constant: -12),
            stack.bottomAnchor.constraint(equalTo: nativeAdView.bottomAnchor, constant: -12),
        ])
    }
}
