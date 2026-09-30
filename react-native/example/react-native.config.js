const path = require('path');

/**
 * Where the library is, said rather than installed.
 *
 * A `file:..` dependency makes npm symlink node_modules/feedoback-react-native
 * back to the package above this directory — which contains this directory,
 * which contains that link. Anything that walks the repository follows it
 * round forever, and one of the things that walks the repository is the main
 * product's own build.
 *
 * Autolinking takes a path instead, so the pods and the Gradle project are
 * found with no link to follow. Metro finds the JavaScript the same way, from
 * the alias in metro.config.js.
 */
module.exports = {
  dependencies: {
    'feedoback-react-native': {
      root: path.resolve(__dirname, '..'),
    },
  },
};
