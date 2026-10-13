import { NextRequest } from "next/server";
import { clinicalProxy } from "@/lib/clinical-proxy";

export const dynamic = "force-dynamic";

export function GET(request: NextRequest) {
  return clinicalProxy(request, "/api/v1/patients/care-team/contacts", "GET");
}
