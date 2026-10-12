import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

export async function PATCH(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") {
    return NextResponse.json({ error: "Staff authentication required" }, { status: 503 });
  }
  const origin = request.headers.get("origin");
  try {
    if (origin && new URL(origin).host !== request.headers.get("host")) {
      return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
    }
  } catch {
    return NextResponse.json({ error: "Invalid request origin" }, { status: 403 });
  }
  const { id } = await params;
  if (!/^[a-fA-F0-9-]{36}$/.test(id)) {
    return NextResponse.json({ error: "Invalid appointment ID" }, { status: 400 });
  }
  const token = (await cookies()).get("pulseos-session")?.value;
  if (!token) return NextResponse.json({ error: "Sign in required" }, { status: 401 });
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/demo-appointments/${encodeURIComponent(id)}/cancel`, {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
    const data = await upstream.json() as unknown;
    return NextResponse.json(data, { status: upstream.status, headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "Appointment service unavailable" }, { status: 503 });
  }
}
