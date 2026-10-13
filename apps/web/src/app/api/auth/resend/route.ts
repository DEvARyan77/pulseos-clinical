import { NextRequest, NextResponse } from "next/server";
export const dynamic = "force-dynamic";

function sameOrigin(request: NextRequest): boolean {
  const origin = request.headers.get("origin");
  if (!origin) return true;
  try { return new URL(origin).host === request.headers.get("host"); }
  catch { return false; }
}

function jsonError(error: string, status: number) {
  return NextResponse.json({ error }, { status, headers: { "Cache-Control": "no-store" } });
}

export async function POST(request: NextRequest) {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true" ||
      process.env.PULSEOS_REGISTRATION_ENABLED !== "true") {
    return jsonError("Registration is closed.", 403);
  }
  if (!sameOrigin(request)) return jsonError("Invalid request origin.", 403);
  let data: unknown;
  try { data = await request.json(); } catch { return jsonError("Invalid request.", 400); }
  const details = data as { email?: unknown } | null;
  if (!details || typeof details.email !== "string" ||
      details.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(details.email)) {
    return jsonError("Enter a valid email address.", 400);
  }
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/resend`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: details.email }), cache: "no-store",
      signal: AbortSignal.timeout(90000),
    });
    if (upstream.status === 202) {
      return NextResponse.json({ message: "If signup is pending, a new code will be sent." },
        { status: 202, headers: { "Cache-Control": "no-store" } });
    }
    return jsonError(upstream.status === 403 ? "Registration is closed." :
      upstream.status === 503 ? "Verification email service is unavailable." :
      "Unable to request another code.", upstream.status === 403 ? 403 : 503);
  } catch {
    return jsonError("The verification server is temporarily unavailable.", 503);
  }
}
