require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

# The React Native side of the iOS SDK. Thin on purpose: the Feedoback pod is
# the SDK, and everything here is the bridge between an NSDictionary and it.
Pod::Spec.new do |s|
  s.name         = "FeedobackReactNative"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = { :type => "MIT", :file => "LICENSE" }
  s.author       = { "Masoud" => "dev3mike@gmail.com" }
  s.platforms    = { :ios => "15.1" }
  s.swift_version = "5.9"

  # Never fetched: autolinking resolves this pod by path out of node_modules.
  # It is what `pod lib lint` reads and what a reader believes, so it names the
  # public mirror at the tag the release puts there.
  s.source       = { :git => "https://github.com/feedoback/feedoback-sdks.git", :tag => "feedoback-react-native-v#{s.version}" }
  s.source_files = "ios/**/*.{h,m,mm,swift}"
  # Nothing here is public. The pod has Swift in it, so CocoaPods gives it a
  # Clang module, and every public header has to compile as plain
  # Objective-C — which neither of these does: one inherits RCTTurboModule and
  # the other RCTViewComponentView, and both reach the C++ renderer, where
  # <atomic> does not exist in C mode. React Native finds the module through
  # RCT_EXPORT_MODULE and the view through the codegen component provider, so
  # no host app ever imports either by name.
  s.private_header_files = "ios/FeedobackRedactView.h"

  s.dependency "Feedoback", "~> 0.1"

  # Brings in React-Core and, on the new architecture, the generated spec this
  # module conforms to. It comes from the app's Podfile, which is the only
  # place that knows which React Native this is.
  install_modules_dependencies(s)
end
