import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

/**
 * Checks readiness without forwarding credentials. Always returns HTTP 200 so a
 * sleeping Render Free backend does not generate noisy 503 console errors.
 */
export async function GET() {
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
    return NextResponse.json({ ready: health.status === "UP", reason: health.status === "UP" ? "ready" : "starting" }, {
      headers: { "Cache-Control": "no-store" },
    });
  } catch {
    return NextResponse.json({ ready: false, reason: "starting" }, {
      headers: { "Cache-Control": "no-store" },
    });
  }
}
