import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

async function requestApi(method: "GET" | "POST", request: NextRequest) {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") {
    return NextResponse.json({ error: "Enable staff authentication to use scheduling" }, { status: 503 });
  }
  if (method === "POST") {
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
  if (method === "POST") {
    let data: unknown;
    try { data = await request.json(); } catch {
      return NextResponse.json({ error: "Invalid JSON" }, { status: 400 });
    }
    if (!data || typeof data !== "object") return NextResponse.json({ error: "Invalid data" }, { status: 400 });
    const record = data as Record<string, unknown>;
    if (typeof record.patientId !== "string" || typeof record.scheduledAt !== "string" ||
        typeof record.visitType !== "string" ||
        !["FOLLOW_UP", "ANNUAL_EXAM", "CONSULTATION"].includes(record.visitType)) {
      return NextResponse.json({ error: "Invalid appointment request" }, { status: 400 });
    }
    body = JSON.stringify({ patientId: record.patientId, scheduledAt: record.scheduledAt, visitType: record.visitType });
  }
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/demo-appointments`, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      ...(body ? { body } : {}),
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
    const responseText = await upstream.text();
    const parsed = responseText ? JSON.parse(responseText) as unknown : {};
    return NextResponse.json(parsed, { status: upstream.status, headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "Appointment service unavailable" }, { status: 503 });
  }
}

export async function GET(request: NextRequest) { return requestApi("GET", request); }
export async function POST(request: NextRequest) { return requestApi("POST", request); }
