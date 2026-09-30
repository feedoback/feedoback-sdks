import 'package:feedoback_flutter/feedoback_flutter.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'feedoback_test.dart' show RecordingChannel;

/// What the last setRedactedRegions call carried, as rectangles.
List<Rect> regionsIn(RecordingChannel platform) {
  final calls = platform.calls.where((c) => c.method == 'setRedactedRegions');
  if (calls.isEmpty) return const [];
  final regions =
      (calls.last.arguments! as Map<String, Object?>)['regions']! as List;
  return [
    for (final region in regions.cast<Map<String, Object?>>())
      Rect.fromLTWH(
        region['left']! as double,
        region['top']! as double,
        region['width']! as double,
        region['height']! as double,
      ),
  ];
}

Widget wrap(Widget child) => Directionality(
  textDirection: TextDirection.ltr,
  child: Align(alignment: Alignment.topLeft, child: child),
);

void main() {
  late RecordingChannel platform;

  setUp(() {
    platform = RecordingChannel();
    Feedoback.channel = platform;
    Feedoback.resetForTesting();
  });

  testWidgets('reports where it is once the frame is laid out', (tester) async {
    await tester.pumpWidget(
      wrap(const FeedobackRedact(child: SizedBox(width: 120, height: 40))),
    );
    await tester.pump();

    expect(regionsIn(platform), [const Rect.fromLTWH(0, 0, 120, 40)]);
  });

  testWidgets('follows the widget when something moves it', (tester) async {
    Future<void> build(double left) => tester.pumpWidget(
      wrap(
        Padding(
          padding: EdgeInsets.only(left: left),
          child: const FeedobackRedact(child: SizedBox(width: 120, height: 40)),
        ),
      ),
    );

    await build(0);
    await tester.pump();
    expect(regionsIn(platform), [const Rect.fromLTWH(0, 0, 120, 40)]);

    await build(30);
    await tester.pump();
    expect(regionsIn(platform), [const Rect.fromLTWH(30, 0, 120, 40)]);
  });

  /// One message for a frame, not one per widget in it.
  testWidgets('sends every region in one message', (tester) async {
    await tester.pumpWidget(
      wrap(
        const Column(
          children: [
            FeedobackRedact(child: SizedBox(width: 120, height: 40)),
            FeedobackRedact(child: SizedBox(width: 60, height: 20)),
          ],
        ),
      ),
    );
    await tester.pump();

    final sends = platform.calls.where((c) => c.method == 'setRedactedRegions');
    expect(sends, hasLength(1));
    expect(regionsIn(platform), hasLength(2));
  });

  /// A frame where nothing moved is a frame with nothing to say.
  testWidgets('says nothing when nothing moved', (tester) async {
    await tester.pumpWidget(
      wrap(const FeedobackRedact(child: SizedBox(width: 120, height: 40))),
    );
    await tester.pump();
    final before = platform.calls
        .where((c) => c.method == 'setRedactedRegions')
        .length;

    await tester.pump();
    await tester.pump();

    expect(
      platform.calls.where((c) => c.method == 'setRedactedRegions').length,
      before,
    );
  });

  testWidgets('stops marking a widget that is no longer sensitive', (
    tester,
  ) async {
    Future<void> build(bool redacted) => tester.pumpWidget(
      wrap(
        FeedobackRedact(
          redacted: redacted,
          child: const SizedBox(width: 120, height: 40),
        ),
      ),
    );

    await build(true);
    await tester.pump();
    expect(regionsIn(platform), hasLength(1));

    await build(false);
    await tester.pump();
    expect(regionsIn(platform), isEmpty);
  });

  testWidgets('forgets a widget that has gone', (tester) async {
    await tester.pumpWidget(
      wrap(const FeedobackRedact(child: SizedBox(width: 120, height: 40))),
    );
    await tester.pump();
    expect(regionsIn(platform), hasLength(1));

    await tester.pumpWidget(wrap(const SizedBox(width: 120, height: 40)));
    await tester.pump();

    expect(regionsIn(platform), isEmpty);
  });

  /// It lays out exactly as its child does: a wrapper that changed the layout
  /// would be one nobody could put around anything.
  testWidgets('changes nothing on screen', (tester) async {
    await tester.pumpWidget(
      wrap(const FeedobackRedact(child: SizedBox(width: 120, height: 40))),
    );

    expect(tester.getSize(find.byType(SizedBox)), const Size(120, 40));
    expect(tester.getTopLeft(find.byType(SizedBox)), Offset.zero);
  });
}
