#import "FeedobackRedactView.h"

#import <react/renderer/components/RNFeedobackSpec/ComponentDescriptors.h>
#import <react/renderer/components/RNFeedobackSpec/EventEmitters.h>
#import <react/renderer/components/RNFeedobackSpec/Props.h>
#import <react/renderer/components/RNFeedobackSpec/RCTComponentViewHelpers.h>

// Static libraries and frameworks name the generated Swift header
// differently, and an app may build this pod either way.
#if __has_include(<FeedobackReactNative/FeedobackReactNative-Swift.h>)
#import <FeedobackReactNative/FeedobackReactNative-Swift.h>
#else
#import "FeedobackReactNative-Swift.h"
#endif

using namespace facebook::react;

@implementation FeedobackRedactView

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<FeedobackRedactViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const FeedobackRedactViewProps>();
    _props = defaultProps;
    // Marked on the way in, before any prop arrives. A view that is created
    // and drawn in the same frame as the sheet opening is still covered.
    [FeedobackBridge redact:self redacted:YES];
  }
  return self;
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
  const auto &next = *std::static_pointer_cast<FeedobackRedactViewProps const>(props);
  [FeedobackBridge redact:self redacted:next.redacted];
  [super updateProps:props oldProps:oldProps];
}

// No prepareForRecycle. Fabric only ever reuses a view as its own component
// type, so a pooled one is always another <FeedobackRedact>; unmarking it on
// the way into the pool would leave a window where a remount that skipped
// updateProps came back unredacted. For a privacy mark, staying on is the
// side to fail towards.

@end

Class<RCTComponentViewProtocol> FeedobackRedactViewCls(void)
{
  return FeedobackRedactView.class;
}
