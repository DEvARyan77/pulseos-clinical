/**
 * Render Free can suspend a backend between visits. A Java cold start can exceed
 * the POST proxy's timeout, so warm up and confirm readiness before sending
 * a login or registration request.
 */
export async function waitForAuthApi(onStatus: (text: string) => void): Promise<void> {
  const deadline = Date.now() + 210_000;
  let attempt = 0;
  while (Date.now() < deadline) {
    attempt += 1;
    onStatus(attempt === 1
      ? "Connecting to the secure server…"
      : "Starting the server (free hosting can take a few minutes)…");
    try {
      const response = await fetch("/api/auth/readiness", {
        cache: "no-store",
        signal: AbortSignal.timeout(12_000),
      });
      const data = await response.json() as { ready?: boolean; reason?: string };
      if (data.reason === "auth_disabled") {
        throw new Error("Staff authentication is not enabled on the server.");
      }
      if (response.ok && data.ready) {
        onStatus("Server connected. Processing your request…");
        return;
      }
    } catch (error) {
      if (error instanceof Error && error.message.includes("not enabled on the server")) {
        throw error;
      }
    }
    if (Date.now() >= deadline) break;
    await new Promise<void>((resolve) => setTimeout(resolve, 5_000));
  }
  throw new Error(
    "The API did not finish starting. Open the API health link, check that it says UP, then try again."
  );
}

/** Start Render's backend wake-up on opening the form. */
export function wakeAuthApi(): void {
  void fetch("/api/auth/readiness", { cache: "no-store" }).catch(() => undefined);
}
