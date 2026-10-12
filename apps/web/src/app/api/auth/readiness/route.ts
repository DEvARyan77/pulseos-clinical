import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

/**
 * Checks readiness without forwarding credentials. Always returns HTTP 200 so a
 * sleeping Render Free backend does not generate noisy 503 console errors.
 */
export async function GET(request: NextRequest) {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") {
    return NextResponse.json({ ready: false, reason: "auth_disabled" }, {
      headers: { "Cache-Control": "no-store" },
    });
  }
  try {
    const response = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/actuator/health`, {
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) {
      return NextResponse.json({ ready: false, reason: "starting" }, {
        headers: { "Cache-Control": "no-store" },
      });
    }
    const health = await response.json() as { status?: string };
    if (health.status !== "UP") {
      return NextResponse.json({ ready: false, reason: "starting" }, {
        headers: { "Cache-Control": "no-store" },
      });
    }
    if (request.nextUrl.searchParams.get("email") === "1") {
      const status = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/registration-status`, {
        cache: "no-store",
        signal: AbortSignal.timeout(8000),
      });
      if (!status.ok) {
        return NextResponse.json({ ready: false, reason: "configuration_unavailable" }, {
          headers: { "Cache-Control": "no-store" },
        });
      }
      const config = await status.json() as { registrationEnabled?: boolean; emailConfigured?: boolean };
      const reason = !config.registrationEnabled ? "registration_closed"
        : !config.emailConfigured ? "mail_not_configured" : "ready";
      return NextResponse.json({ ready: reason === "ready", reason }, {
        headers: { "Cache-Control": "no-store" },
      });
    }
    return NextResponse.json({ ready: true, reason: "ready" }, {
      headers: { "Cache-Control": "no-store" },
    });
  } catch {
    return NextResponse.json({ ready: false, reason: "starting" }, {
      headers: { "Cache-Control": "no-store" },
    });
  }
}
