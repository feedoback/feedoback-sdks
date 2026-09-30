# The Flutter side of the iOS SDK. Thin on purpose: the Feedoback pod is the
# SDK, and everything here is the bridge between a FlutterMethodCall and it.
#
# CocoaPods only, for now. Flutter integrates a plugin with no Package.swift
# through CocoaPods even in an app that has Swift Package Manager turned on,
# and the SDK this depends on cannot be an SPM dependency until it is mirrored
# to its own repository — Swift Package Manager will not resolve a package from
# a subdirectory, which is the whole reason that mirror exists.
Pod::Spec.new do |s|
  s.name             = 'feedoback_flutter'
  s.version          = '0.1.0'
  s.summary          = 'Native feedback for Flutter apps.'
  s.description      = <<-DESC
A feedback sheet from a control your app already owns, or a floating launcher
over it. The Flutter side of the Feedoback iOS SDK.
                       DESC
  s.homepage         = 'https://feedoback.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Masoud' => 'dev3mike@gmail.com' }
  s.source           = { :path => '.' }
  s.source_files = 'feedoback_flutter/Sources/feedoback_flutter/**/*'
  s.dependency 'Flutter'
  s.dependency 'Feedoback', '~> 0.1'
  s.platform = :ios, '15.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # The SDK it wraps ships its own manifest, declaring the one required-reason
  # API either of them touches: UserDefaults, for the install id. This plugin
  # adds nothing of its own to declare.
end
