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
    return NextResponse.json({ error: "Invalid signup request" }, { status: 400 });
  }
  if (!value || typeof value !== "object") {
    return NextResponse.json({ error: "Invalid signup request" }, { status: 400 });
  }
  const data = value as Record<string, unknown>;
  if (typeof data.username !== "string" || !/^[a-zA-Z][a-zA-Z0-9_.-]{2,39}$/.test(data.username) ||
      typeof data.email !== "string" || data.email.length > 254 ||
      typeof data.password !== "string" || data.password.length < 12 || data.password.length > 128) {
    return NextResponse.json({ error: "Check the required fields" }, { status: 400 });
  }
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: data.username, email: data.email, password: data.password }),
      cache: "no-store",
      signal: AbortSignal.timeout(90000),
    });
    const body = await upstream.json().catch(() => ({})) as { error?: string };
    if (upstream.status === 202) {
      return NextResponse.json({ message: "Verification code sent to your email." }, {
        status: 202, headers: { "Cache-Control": "no-store" }
      });
    }
    const error = upstream.status === 409 ? "Username or email already registered or unavailable" :
                  upstream.status === 429 ? "Please wait before requesting another code." :
                  upstream.status === 403 ? "Registration is closed." :
                  upstream.status === 400 ? "Check the required fields." :
                  upstream.status === 503 ? "Email delivery is not configured or temporarily unavailable." :
                  "Registration failed. Please try again.";
    return NextResponse.json({ error },
      { status: [400,403,409,429].includes(upstream.status) ? upstream.status : 503,
        headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    console.error("Registration service error:", error instanceof Error ? error.name : "unknown");
    return NextResponse.json({ error: "The registration server is unavailable. Please try again." },
      { status: 503, headers: { "Cache-Control": "no-store" } });
  }
}
