import { NextRequest } from "next/server";
import { adminProxy } from "@/lib/admin-proxy";
export const dynamic = "force-dynamic";
export function GET(request: NextRequest) {
  return adminProxy(request, "/api/v1/admin/staff", "GET");
}
