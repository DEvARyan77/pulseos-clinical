import {NextRequest} from "next/server";
import {clinicalProxy} from "@/lib/clinical-proxy";
export const dynamic="force-dynamic";
const endpoint="/api/v1/demo-appointments/availability";
export async function GET(request:NextRequest){return clinicalProxy(request,endpoint,"GET");}
export async function PUT(request:NextRequest){return clinicalProxy(request,endpoint,"PUT");}
