#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// `<FeedobackRedact>`, as a view.
///
/// A real view rather than a tag passed to the module, because the screenshot
/// pass walks the window's view tree and paints over what it finds marked
/// there. Handing it the view React drew means the mark belongs to the thing
/// on screen rather than to a node id the reconciler is free to reuse.
@interface FeedobackRedactView : RCTViewComponentView
@end

NS_ASSUME_NONNULL_END
