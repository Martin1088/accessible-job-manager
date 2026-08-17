// Karma configuration.
//
// Supplying this file stops @angular/build:karma from injecting its own defaults, so
// the frameworks, plugins and reporters it would normally add are restated here. The
// bundling itself still comes from the builder.
//
// The reason the file exists at all is the launcher: Chromium refuses to start as root
// unless --no-sandbox is passed, which is exactly the case inside the devcontainer
// (devcontainer.json sets "remoteUser": "root"). On a normal desktop or on GitHub's
// runners the process is not root, and there the sandbox stays on - dropping it weakens
// the browser's isolation, so it is applied only where Chromium would otherwise refuse
// to run at all.
//
// CI passes --browsers=ChromeHeadless explicitly, which overrides the default below
// and keeps the stock, sandboxed launcher.

const runningAsRoot = typeof process.getuid === 'function' && process.getuid() === 0;

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
    ],
    reporters: ['progress', 'kjhtml'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        // --disable-dev-shm-usage: containers often cap /dev/shm at 64 MB, which
        // Chromium exhausts and then crashes mid-run.
        flags: ['--no-sandbox', '--disable-dev-shm-usage'],
      },
    },
    browsers: [runningAsRoot ? 'ChromeHeadlessNoSandbox' : 'ChromeHeadless'],
    restartOnFileChange: true,
  });
};
