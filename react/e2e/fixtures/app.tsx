import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";

// Imported from the built artifact on purpose: the e2e exercises what npm ships.
import { FeedbackWidget, useFeedback, type FeedobackVisitor } from "../../dist/index.js";

function Demo() {
  const feedback = useFeedback();
  // Set from the first render, so identify is queued before the async widget
  // script boots — the e2e checks the widget replays it.
  const [visitor] = useState<FeedobackVisitor>({ id: "usr_42", email: "e2e@test.dev" });

  return (
    <div style={{ padding: 24 }}>
      <h1>feedoback-react e2e</h1>
      <FeedbackWidget
        projectKey="pk_e2e"
        host={window.location.origin}
        version="1.0.0"
        visitor={visitor}
        context={{ plan: "pro" }}
      />
      <button id="btn-open" onClick={() => feedback.open()}>
        Open
      </button>
      <button id="btn-feedback" onClick={() => feedback.startFeedback()}>
        Report a bug
      </button>
      <button id="btn-record" onClick={() => feedback.startRecording()}>
        Record
      </button>
      <button id="btn-destroy" onClick={() => feedback.destroy()}>
        Destroy
      </button>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Demo />
  </StrictMode>,
);
