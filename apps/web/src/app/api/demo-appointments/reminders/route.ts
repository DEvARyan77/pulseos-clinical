import {NextRequest} from "next/server";
import {clinicalProxy} from "@/lib/clinical-proxy";
export const dynamic="force-dynamic";
export async function GET(request:NextRequest){
  return clinicalProxy(request,"/api/v1/demo-appointments/reminders","GET");
}
