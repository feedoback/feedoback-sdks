#import <RNFeedobackSpec/RNFeedobackSpec.h>

// Static libraries and frameworks name the generated Swift header
// differently, and an app may build this pod either way.
#if __has_include(<FeedobackReactNative/FeedobackReactNative-Swift.h>)
#import <FeedobackReactNative/FeedobackReactNative-Swift.h>
#else
#import "FeedobackReactNative-Swift.h"
#endif

/// The module's own interface lives here rather than in a header, because a
/// header is a public header, and a public header has to compile as plain
/// Objective-C. `NativeFeedobackSpec` does not: it inherits RCTTurboModule,
/// which is C++. Nothing outside this pod needs the class by name — React
/// Native finds it through RCT_EXPORT_MODULE.
@interface FeedobackModule : NSObject <NativeFeedobackSpec>
@end

/// Seven forwarding calls and nothing else.
///
/// Everything worth deciding was decided twice already — in `options.ts`,
/// which shapes what may cross the bridge, and in `FeedobackBridge.swift`,
/// which turns what crossed into the SDK's own types. This file exists because
/// a Turbo Module has to be Objective-C++ to conform to the generated
/// protocol, and Swift cannot do that.
@implementation FeedobackModule

RCT_EXPORT_MODULE(Feedoback)

- (void)start:(NSDictionary *)options
{
  [FeedobackBridge start:options];
}

- (void)present:(NSString *)category
{
  [FeedobackBridge present:category];
}

- (void)identify:(NSDictionary *)visitor
{
  [FeedobackBridge identify:visitor];
}

- (void)setContext:(NSDictionary *)context
{
  [FeedobackBridge setContext:context];
}

- (void)setScreen:(NSString *)route title:(NSString *)title
{
  [FeedobackBridge setScreen:route title:title];
}

- (void)setLauncherHidden:(BOOL)hidden
{
  [FeedobackBridge setLauncherHidden:hidden];
}

- (void)reset
{
  [FeedobackBridge reset];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeFeedobackSpecJSI>(params);
}

@end
