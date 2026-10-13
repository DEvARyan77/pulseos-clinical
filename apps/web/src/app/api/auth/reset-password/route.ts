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
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") return jsonError("Staff login is disabled.", 503);
  if (!sameOrigin(request)) return jsonError("Invalid request origin.", 403);
  let data: unknown;
  try { data = await request.json(); } catch { return jsonError("Invalid request.", 400); }
  const details = data as { email?: unknown; code?: unknown; password?: unknown } | null;
  if (!details || typeof details.email !== "string" ||
      details.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(details.email) ||
      typeof details.code !== "string" || !/^[0-9]{6}$/.test(details.code) ||
      typeof details.password !== "string" || details.password.length < 8 ||
      details.password.length > 128) {
    return jsonError("Enter the code and a password of at least 8 characters.", 400);
  }
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/reset-password`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: details.email, code: details.code, password: details.password }),
      cache: "no-store", signal: AbortSignal.timeout(90000),
    });
    if (upstream.ok) {
      return NextResponse.json({ message: "Password updated. You can sign in now." },
        { status: 200, headers: { "Cache-Control": "no-store" } });
    }
    return jsonError(upstream.status === 400 ? "Incorrect or expired code. Request another if needed." :
      upstream.status === 429 ? "Too many attempts. Request a new code." :
      "Password reset service is unavailable.",
      [400,429].includes(upstream.status) ? upstream.status : 503);
  } catch {
    return jsonError("Password reset service is temporarily unavailable.", 503);
  }
}
