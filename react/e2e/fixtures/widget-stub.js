/**
 * A faithful stand-in for the hosted widget.js, for end-to-end tests.
 *
 * It implements the same contract the real loader does — reads its own script
 * tag's data-project / data-version, drains the pre-boot queue, then installs
 * the full window.Feedoback API — but instead of a shadow-DOM widget it renders
 * one button and mirrors its state onto data-* attributes the test can read.
 * This lets the SDK be exercised in a real browser without the SaaS backend.
 */
(function () {
  var script =
    document.currentScript || document.querySelector('script[data-project^="pk_"]');
  var projectKey = script ? script.getAttribute("data-project") : null;
  var version = script ? script.getAttribute("data-version") : null;

  var existing = window.Feedoback;
  var queue = existing && Array.isArray(existing.q) ? existing.q : [];

  var launcher = document.createElement("button");
  launcher.id = "feedoback-stub-launcher";
  launcher.type = "button";
  launcher.textContent = "Feedback";
  launcher.setAttribute("data-project", projectKey || "");
  if (version) launcher.setAttribute("data-version", version);
  document.body.appendChild(launcher);

  var state = { open: false, mode: "menu", visitor: "", context: "" };

  function paint() {
    launcher.setAttribute("data-open", String(state.open));
    launcher.setAttribute("data-mode", state.mode);
    launcher.setAttribute("data-visitor", state.visitor);
    launcher.setAttribute("data-context", state.context);
  }

  function who(visitor) {
    if (!visitor || typeof visitor !== "object") return "";
    return visitor.id || visitor.email || visitor.name || "";
  }

  var api = {
    init: function () {},
    open: function () {
      state.open = true;
      paint();
    },
    close: function () {
      state.open = false;
      paint();
    },
    toggle: function () {
      state.open = !state.open;
      paint();
    },
    startFeedback: function () {
      state.open = true;
      state.mode = "picker";
      paint();
    },
    startRecording: function () {
      state.open = true;
      state.mode = "recording";
      paint();
    },
    identify: function (visitor) {
      state.visitor = who(visitor);
      paint();
    },
    setContext: function (context) {
      state.context = context ? JSON.stringify(context) : "";
      paint();
    },
    destroy: function () {
      launcher.remove();
      delete window.Feedoback;
    },
  };

  window.Feedoback = api;

  for (var i = 0; i < queue.length; i++) {
    var method = queue[i][0];
    var arg = queue[i][1];
    if (typeof api[method] === "function") api[method](arg);
  }

  paint();
})();
