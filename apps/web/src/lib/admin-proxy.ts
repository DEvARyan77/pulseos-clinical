import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const apiUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

/** Cookie-to-bearer server proxy. Never trust a role provided by the browser. */
export async function adminProxy(request: NextRequest, path: string, method: "GET" | "PATCH") {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") {
    return NextResponse.json({ error: "Authentication is disabled" }, { status: 503 });
  }
  if (method !== "GET") {
    const origin = request.headers.get("origin");
    try {
      if (origin && new URL(origin).host !== request.headers.get("host")) {
        return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
      }
    } catch {
      return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
    }
  }
  const token = (await cookies()).get("pulseos-session")?.value;
  if (!token) return NextResponse.json({ error: "Sign in required" }, { status: 401 });

  let body: string | undefined;
  if (method === "PATCH") {
    if (Number(request.headers.get("content-length") ?? 0) > 1024) {
      return NextResponse.json({ error: "Request too large" }, { status: 413 });
    }
    try {
      const data = await request.json() as Record<string, unknown>;
      if (!data || typeof data !== "object") throw new Error();
      const keys = Object.keys(data);
      if (path.endsWith("/role")) {
        if (keys.length !== 1 || (data.role !== "ADMIN" && data.role !== "CLINICIAN")) throw new Error();
      } else if (path.endsWith("/status")) {
        if (keys.length !== 1 || typeof data.enabled !== "boolean") throw new Error();
      } else throw new Error();
      body = JSON.stringify(data);
    } catch {
      return NextResponse.json({ error: "Invalid staff update" }, { status: 400 });
    }
  }

  try {
    const upstream = await fetch(`${apiUrl}${path}`, {
      method, headers: { Authorization: `Bearer ${token}`,
        ...(body ? { "Content-Type": "application/json" } : {}) },
      ...(body ? { body } : {}),
      cache: "no-store", signal: AbortSignal.timeout(15000),
    });
    const raw = await upstream.text();
    let data: unknown = {};
    try { data = raw ? JSON.parse(raw) : {}; } catch { data = { error: "Invalid API response" }; }
    return NextResponse.json(data, { status: upstream.status,
      headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "Admin service is unavailable" }, { status: 503 });
  }
}

export function validStaffId(id: string) {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(id);
}
