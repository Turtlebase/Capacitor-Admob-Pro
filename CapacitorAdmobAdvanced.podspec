Pod::Spec.new do |s|
  s.name             = 'CapacitorAdmobAdvanced'
  s.version          = '1.0.0'
  s.summary          = 'Advanced AdMob plugin for Capacitor — all ad formats.'
  s.description      = <<-DESC
    Complete AdMob Capacitor plugin supporting Banner, Interstitial, Rewarded,
    Rewarded Interstitial, App Open, and Native Advanced ads with full scroll
    synchronisation for native overlay over WebView.
  DESC
  s.homepage         = 'https://github.com/turtlebase/capacitor-admob-advanced'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'Umesh Dafda' => 'umeshdafda52@gmail.com' }
  s.source           = { :git => 'https://github.com/turtlebase/capacitor-admob-advanced.git', :tag => s.version.to_s }
  s.source_files     = 'ios/Plugin/**/*.{swift,h,m}'
  s.ios.deployment_target = '14.0'
  s.dependency 'Capacitor'
  s.dependency 'Google-Mobile-Ads-SDK', '~> 11.0'
  s.swift_version    = '5.1'
end
