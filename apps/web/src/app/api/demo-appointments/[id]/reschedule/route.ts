import {NextRequest,NextResponse} from "next/server";
import {clinicalProxy,validPatientId} from "@/lib/clinical-proxy";
type Context={params:Promise<{id:string}>};
export async function PATCH(request:NextRequest,{params}:Context){
  const {id}=await params;
  if(!validPatientId(id))return NextResponse.json({error:"Invalid appointment ID"},{status:400});
  return clinicalProxy(request,`/api/v1/demo-appointments/${id}/reschedule`,"PATCH");
}
