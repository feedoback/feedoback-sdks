/// Shaping what crosses the channel.
///
/// The web SDK shapes `identify()` and `setContext()` twice, for two reasons:
/// one place only makes the payload safe to send across a boundary, and the
/// other does the validation that counts. The split here is the same one. This
/// file makes a value safe to hand a platform channel — a value the codec
/// cannot encode would throw out of a `build`, and an infinity arrives as
/// something unusable — and the native SDKs bound what is left against the
/// server's own limits. Neither half is the other's backstop.
///
/// Nothing here fills in a default. Both native SDKs already have defaults and
/// they are the ones a plain-Swift or plain-Kotlin app gets; a second set here
/// would be a second thing to keep in step. An option this drops is an option
/// native never hears about, which is exactly how it falls back.
library;

import 'types.dart';

/// Everything [FeedobackOptions] carried, minus what the channel cannot take.
Map<String, Object?> encodeOptions(FeedobackOptions options) {
  final encoded = <String, Object?>{'projectKey': options.projectKey.trim()};

  // A trailing slash would make every URL the transport builds a double one.
  final host = options.host?.trim();
  if (host != null && host.isNotEmpty) {
    encoded['host'] = host.replaceAll(RegExp(r'/+$'), '');
  }

  if (options.theme != null) encoded['theme'] = options.theme!.name;
  if (options.screenshots != null) {
    encoded['screenshots'] = options.screenshots!.name;
  }
  if (options.logLevel != null) encoded['logLevel'] = options.logLevel!.name;

  final categories = options.categories;
  if (categories != null && categories.isNotEmpty) {
    encoded['categories'] = <String>[
      for (final category in categories.toSet()) category.wire,
    ];
  }

  final launcher = options.launcher;
  if (launcher != null) encoded['launcher'] = encodeLauncher(launcher);

  return encoded;
}

Map<String, Object?> encodeLauncher(FeedobackLauncher launcher) {
  final encoded = <String, Object?>{'enabled': launcher.enabled};

  if (launcher.corner != null) encoded['corner'] = launcher.corner!.wire;
  if (launcher.style != null) encoded['style'] = launcher.style!.name;
  if (launcher.draggable != null) encoded['draggable'] = launcher.draggable;
  if (launcher.hidesWithKeyboard != null) {
    encoded['hidesWithKeyboard'] = launcher.hidesWithKeyboard;
  }

  // Both numbers or neither: an offset with one axis missing would put the
  // button somewhere nobody asked for on the other.
  final offset = launcher.offset;
  if (offset != null && offset.x.isFinite && offset.y.isFinite) {
    encoded['offset'] = {'x': offset.x, 'y': offset.y};
  }

  return encoded;
}

/// The four fields an identity can have, and nothing else.
///
/// An empty string is dropped rather than sent: it is what a signed-out app
/// passes without meaning to, and `''` as an id would name somebody.
Map<String, Object?> encodeVisitor(FeedobackVisitor visitor) {
  final encoded = <String, Object?>{};
  void put(String key, String? value) {
    final text = value?.trim();
    if (text != null && text.isNotEmpty) encoded[key] = text;
  }

  put('id', visitor.id);
  put('email', visitor.email);
  put('name', visitor.name);
  put('userHash', visitor.userHash);
  return encoded;
}

/// A flat map of scalars, which is what the server stores and all the channel
/// can carry. A list or a nested map is dropped rather than stringified:
/// filing `Instance of 'Order'` under somebody's feedback helps nobody.
Map<String, Object?> encodeContext(Map<String, Object?> context) {
  final encoded = <String, Object?>{};
  for (final entry in context.entries) {
    if (entry.key.isEmpty) continue;
    final value = entry.value;
    if (value == null || value is String || value is bool) {
      encoded[entry.key] = value;
    } else if (value is num && value.isFinite) {
      encoded[entry.key] = value;
    }
  }
  return encoded;
}
