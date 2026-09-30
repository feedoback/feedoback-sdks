const path = require('path');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const pkg = require('../package.json');

// The library is not installed — see react-native.config.js for why — so Metro
// is told where it is, and then told to ignore the copies of React and React
// Native sitting in its own node_modules. Two copies of React in one bundle is
// a blank screen and an error about hooks that names neither cause.
const root = path.resolve(__dirname, '..');
const shared = Object.keys(pkg.peerDependencies);
const escape = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const defaults = getDefaultConfig(__dirname);

module.exports = mergeConfig(defaults, {
  watchFolders: [root],
  resolver: {
    blockList: [
      ...[].concat(defaults.resolver.blockList ?? []),
      ...shared.map(
        (name) => new RegExp(`^${escape(path.join(root, 'node_modules', name))}\\/.*$`),
      ),
    ],
    extraNodeModules: {
      ...Object.fromEntries(
        shared.map((name) => [name, path.join(__dirname, 'node_modules', name)]),
      ),
      [pkg.name]: root,
    },
  },
});
