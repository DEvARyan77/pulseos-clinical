/**
 * Render Free can suspend a backend between visits. A Java cold start can exceed
 * the POST proxy's timeout, so warm up and confirm readiness before sending
 * a login or registration request.
 */
class AuthConfigurationError extends Error {}

/**
 * When checking registration, also verify that the server has email delivery
 * credentials. The check is non-sensitive and never sends an OTP itself.
 */
export async function waitForAuthApi(
  onStatus: (text: string) => void,
  requireEmail = false,
): Promise<void> {
  const deadline = Date.now() + 210_000;
  let attempt = 0;
  while (Date.now() < deadline) {
    attempt += 1;
    onStatus(attempt === 1
      ? "Connecting to the secure server…"
      : "Starting the server (free hosting can take a few minutes)…");
    try {
      const response = await fetch(requireEmail ? "/api/auth/readiness?email=1" : "/api/auth/readiness", {
        cache: "no-store",
        signal: AbortSignal.timeout(12_000),
      });
      const data = await response.json() as { ready?: boolean; reason?: string };
      if (data.reason === "auth_disabled") {
        throw new AuthConfigurationError("Staff authentication is not enabled on the server.");
      }
      if (data.reason === "registration_closed") {
        throw new AuthConfigurationError("New account registration is currently disabled.");
      }
      if (data.reason === "mail_not_configured") {
        throw new AuthConfigurationError(
          "Signup cannot send verification codes yet. Configure an email provider in the PulseOS API Render environment."
        );
      }
      if (response.ok && data.ready) {
        onStatus("Server connected. Processing your request…");
        return;
      }
    } catch (error) {
      if (error instanceof AuthConfigurationError) {
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
