import { NextRequest, NextResponse } from "next/server";
import { clinicalProxy, validPatientId } from "@/lib/clinical-proxy";

type Context = { params: Promise<{ id: string }> };

export async function GET(request: NextRequest, { params }: Context) {
  const { id } = await params;
  if (!validPatientId(id)) {
    return NextResponse.json({ error: "Invalid patient ID" }, { status: 400 });
  }
  return clinicalProxy(request, `/api/v1/patients/${id}/care-team`, "GET");
}

export async function PUT(request: NextRequest, { params }: Context) {
  const { id } = await params;
  if (!validPatientId(id)) {
    return NextResponse.json({ error: "Invalid patient ID" }, { status: 400 });
  }
  return clinicalProxy(request, `/api/v1/patients/${id}/care-team`, "PUT");
}
