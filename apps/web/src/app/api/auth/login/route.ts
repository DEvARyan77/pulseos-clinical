import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const authEnabled = process.env.PULSEOS_AUTH_ENABLED === "true";

export async function POST(request: NextRequest) {
  if (!authEnabled) return NextResponse.json({ error: "Staff sign-in is not enabled" }, { status: 503 });

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

  let credentials: unknown;
  try { credentials = await request.json(); } catch {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }
  const body = credentials as Record<string, unknown> | null;
  if (!body || typeof body.username !== "string" || typeof body.password !== "string" ||
      body.username.length > 254 || body.password.length > 256 ||
      !body.username || !body.password) {
    return NextResponse.json({ error: "Invalid request" }, { status: 400 });
  }

  let apiResponse: Response;
  try {
    apiResponse = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: body.username, password: body.password }),
      cache: "no-store",
      // Render free instances may take substantially longer than 8 seconds to wake.
      signal: AbortSignal.timeout(90000),
    });
  } catch (error) {
    console.error("PulseOS authentication upstream error:", error instanceof Error ? error.name : "unknown");
    return NextResponse.json({
      error: "Authentication server is unreachable or waking up. Check the API health endpoint and retry.",
    }, { status: 503, headers: { "Cache-Control": "no-store" } });
  }

  if (!apiResponse.ok) {
    return NextResponse.json(
      { error: apiResponse.status === 401
          ? "Invalid username or password"
          : apiResponse.status === 429
            ? "Too many sign-in attempts. Try again later."
            : "Authentication API returned an error. Verify the backend is healthy and retry." },
      { status: apiResponse.status === 401 ? 401 : apiResponse.status === 429 ? 429 : 503,
        headers: { "Cache-Control": "no-store" } }
    );
  }
  let payload: { accessToken?: string; expiresIn?: number };
  try { payload = await apiResponse.json(); } catch {
    return NextResponse.json({ error: "Invalid authentication response" }, { status: 502 });
  }
  if (typeof payload.accessToken !== "string" || payload.accessToken.length < 24 ||
      typeof payload.expiresIn !== "number" || !Number.isFinite(payload.expiresIn)) {
    return NextResponse.json({ error: "Invalid authentication response" }, { status: 502 });
  }

  const expires = Math.max(1, Math.min(Math.trunc(payload.expiresIn), 28800));
  const response = NextResponse.json({ ok: true });
  response.cookies.set("pulseos-session", payload.accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: expires,
  });
  response.headers.set("Cache-Control", "no-store");
  return response;
}
