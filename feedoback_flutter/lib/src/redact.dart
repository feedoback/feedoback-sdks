import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'feedoback.dart';

/// Which widgets are redacted, and where they were the last time anything
/// moved.
///
/// Batched: several widgets measuring in the same frame send one message, and
/// a frame where nothing moved sends none. A redacted widget inside a list
/// scrolls, and reporting regardless would be sixty messages a second saying
/// nothing changed.
class RedactionRegistry {
  RedactionRegistry({Future<void> Function(List<Rect>)? send})
    : _send = send ?? Feedoback.setRedactedRegions;

  final Future<void> Function(List<Rect>) _send;
  final Map<Object, Rect> _rects = <Object, Rect>{};
  List<Rect> _sent = const <Rect>[];
  bool _scheduled = false;

  /// What is marked right now, for a test to read.
  @visibleForTesting
  List<Rect> get regions => List<Rect>.unmodifiable(_rects.values);

  void put(Object id, Rect rect) {
    if (_rects[id] == rect) return;
    _rects[id] = rect;
    _schedule();
  }

  void remove(Object id) {
    if (_rects.remove(id) == null) return;
    _schedule();
  }

  /// A microtask rather than the next frame: every widget measures in the
  /// same post-frame pass, and this runs once after all of them.
  void _schedule() {
    if (_scheduled) return;
    _scheduled = true;
    scheduleMicrotask(flush);
  }

  @visibleForTesting
  Future<void> flush() async {
    _scheduled = false;
    final next = _rects.values.toList(growable: false);
    if (listEquals(next, _sent)) return;
    _sent = next;
    await _send(next);
  }
}

/// The one the widget reports to. An app that draws its own sensitive thing on
/// a canvas can call [Feedoback.setRedactedRegions] instead, but not both:
/// each replaces the other.
final RedactionRegistry redactions = RedactionRegistry();

/// Wraps anything whose contents must never leave the device — a card number,
/// an address, a medical record. It lays out exactly as its child does and
/// changes nothing on screen; what it changes is the screenshot, where its
/// frame is painted over before the bitmap exists.
///
/// ```dart
/// FeedobackRedact(child: Text(card.number))
/// ```
///
/// **Wrap obscured fields too.** On iOS, Android and React Native the SDK finds
/// a secure text field on its own, because it is a native view it can walk to.
/// Flutter draws its entire interface into one native view, including the dots
/// in a `TextField(obscureText: true)`, so there is nothing to find. Here that
/// convenience does not exist and this widget is the whole of it.
///
/// For the same reason there is no view to hand the native SDK: this reports
/// where it is instead, after every frame that moved it.
class FeedobackRedact extends StatefulWidget {
  const FeedobackRedact({super.key, required this.child, this.redacted = true});

  final Widget child;

  /// Off for a widget that is only sometimes sensitive.
  final bool redacted;

  @override
  State<FeedobackRedact> createState() => _FeedobackRedactState();
}

class _FeedobackRedactState extends State<FeedobackRedact> {
  @override
  void initState() {
    super.initState();
    if (widget.redacted) _watch();
  }

  @override
  void didUpdateWidget(FeedobackRedact oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.redacted == oldWidget.redacted) return;
    if (widget.redacted) {
      _watch();
    } else {
      redactions.remove(this);
    }
  }

  @override
  void dispose() {
    redactions.remove(this);
    super.dispose();
  }

  /// Measured after the frame, and then again after the next one that happens.
  ///
  /// This never asks for a frame of its own, so an idle app costs nothing: the
  /// callback waits for a frame something else caused, which is also the only
  /// kind of frame that can have moved this widget.
  void _watch() {
    WidgetsBinding.instance.addPostFrameCallback(_measure);
  }

  void _measure(Duration _) {
    if (!mounted || !widget.redacted) return;

    final box = context.findRenderObject();
    if (box is RenderBox && box.attached && box.hasSize) {
      redactions.put(this, box.localToGlobal(Offset.zero) & box.size);
    }
    _watch();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
