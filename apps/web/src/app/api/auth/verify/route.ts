import { NextRequest, NextResponse } from "next/server";
export const dynamic = "force-dynamic";
export async function POST(request: NextRequest) {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true" ||
      process.env.PULSEOS_REGISTRATION_ENABLED !== "true") {
    return NextResponse.json({ error: "Registration is closed" }, { status: 403 });
  }
  const origin = request.headers.get("origin");
  try {
    if (origin && new URL(origin).host !== request.headers.get("host")) {
      return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
    }
  } catch {
    return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
  }
  let value: unknown;
  try { value = await request.json(); } catch {
    return NextResponse.json({ error: "Invalid verification request" }, { status: 400 });
  }
  if (!value || typeof value !== "object") {
    return NextResponse.json({ error: "Invalid verification request" }, { status: 400 });
  }
  const data = value as Record<string, unknown>;
  if (typeof data.email !== "string" || data.email.length > 254 ||
      typeof data.code !== "string" || !/^[0-9]{6}$/.test(data.code)) {
    return NextResponse.json({ error: "Enter a valid six-digit verification code" }, { status: 400 });
  }
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/verify`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: data.email, code: data.code }), cache: "no-store",
      signal: AbortSignal.timeout(90000),
    });
    if (upstream.ok) {
      return NextResponse.json({ message: "Email verified. You may sign in." },
        { status: 200, headers: { "Cache-Control": "no-store" } });
    }
    const error = upstream.status === 429 ? "Too many incorrect attempts. Request a new code." :
                  upstream.status === 400 ? "Incorrect or expired code. Please check and try again." :
                  upstream.status === 403 ? "Registration is closed." :
                  upstream.status === 409 ? "Account already registered." :
                  "Could not verify the code. Try again later.";
    return NextResponse.json({ error },
      { status: [400,403,409,429].includes(upstream.status) ? upstream.status : 503,
        headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "Email verification service unavailable." },
      { status: 503, headers: { "Cache-Control": "no-store" } });
  }
}
