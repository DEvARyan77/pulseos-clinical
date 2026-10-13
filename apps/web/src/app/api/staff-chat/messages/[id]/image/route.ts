import { cookies } from "next/headers";
import { NextRequest,NextResponse } from "next/server";
import { validPatientId } from "@/lib/clinical-proxy";
type Context={params:Promise<{id:string}>};
export async function GET(_request:NextRequest,{params}:Context){
  const {id}=await params;
  if(!validPatientId(id))return NextResponse.json({error:"Invalid attachment ID"},{status:400});
  if(process.env.PULSEOS_AUTH_ENABLED!=="true")return NextResponse.json({error:"Not available"},{status:503});
  const token=(await cookies()).get("pulseos-session")?.value;
  if(!token)return NextResponse.json({error:"Sign in required"},{status:401});
  try{
    const upstream=await fetch(`${process.env.API_INTERNAL_URL??"http://localhost:8080"}/api/v1/staff-chat/messages/${id}/image`,{
      headers:{Authorization:`Bearer ${token}`},cache:"no-store",signal:AbortSignal.timeout(9000)});
    if(!upstream.ok)return NextResponse.json({error:"Attachment unavailable"},{status:upstream.status});
    const mime=upstream.headers.get("content-type")??"";
    if(!["image/png","image/jpeg","image/webp"].includes(mime))
      return NextResponse.json({error:"Invalid attachment format"},{status:502});
    const bytes=await upstream.arrayBuffer();
    if(bytes.byteLength>262144)return NextResponse.json({error:"Attachment too large"},{status:502});
    return new NextResponse(bytes,{status:200,headers:{"Content-Type":mime,"Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff"}});
  }catch{return NextResponse.json({error:"Attachment service unavailable"},{status:503});}
}
