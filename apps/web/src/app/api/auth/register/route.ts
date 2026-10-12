import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true" || process.env.PULSEOS_REGISTRATION_ENABLED !== "true") {
    return NextResponse.json({ error: "Registration is closed" }, { status: 403 });
  }
  const origin = request.headers.get("origin");
  if (origin) {
    try {
      if (new URL(origin).host !== request.headers.get("host")) {
        return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
      }
    } catch {
      return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
    }
  }

  let payload: unknown;
  try { payload = await request.json(); } catch {
    return NextResponse.json({ error: "Invalid signup request" }, { status: 400 });
  }
  if (!payload || typeof payload !== "object") {
    return NextResponse.json({ error: "Invalid signup request" }, { status: 400 });
  }
  const data = payload as Record<string, unknown>;
  if (typeof data.username !== "string" || !/^[a-zA-Z][a-zA-Z0-9_.-]{2,39}$/.test(data.username) ||
      typeof data.email !== "string" || data.email.length > 254 ||
      typeof data.password !== "string" || data.password.length < 12 || data.password.length > 128 ||
      typeof data.invitationCode !== "string" || data.invitationCode.length < 1 || data.invitationCode.length > 256) {
    return NextResponse.json({ error: "Check the required fields" }, { status: 400 });
  }
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: data.username,
        email: data.email,
        password: data.password,
        invitationCode: data.invitationCode,
      }),
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
    if (upstream.status === 201) {
      return NextResponse.json({ message: "Account created. Sign in to continue." }, {
        status: 201,
        headers: { "Cache-Control": "no-store" }
      });
    }
    const error = upstream.status === 403 ? "Registration unavailable or invitation invalid" :
                  upstream.status === 409 ? "Username or email already in use" :
                  upstream.status === 400 ? "Check the required fields" : "Registration failed";
    return NextResponse.json({ error }, { status: [400,403,409].includes(upstream.status) ? upstream.status : 503 });
  } catch {
    return NextResponse.json({ error: "Registration service unavailable" }, { status: 503 });
  }
}
