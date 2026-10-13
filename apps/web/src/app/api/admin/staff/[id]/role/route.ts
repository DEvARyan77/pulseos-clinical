import { NextRequest, NextResponse } from "next/server";
import { adminProxy, validStaffId } from "@/lib/admin-proxy";
export const dynamic = "force-dynamic";
export async function PATCH(request: NextRequest, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  if (!validStaffId(id)) return NextResponse.json({ error: "Invalid staff ID" }, { status: 400 });
  return adminProxy(request, `/api/v1/admin/staff/${id}/role`, "PATCH");
}
