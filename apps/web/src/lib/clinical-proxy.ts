import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

/** Server-only API gateway: bearer token never reaches client JavaScript. */
export async function clinicalProxy(request: NextRequest, path: string, method: "GET"|"POST"|"PUT"|"PATCH") {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true")
    return NextResponse.json({error:"Enable staff authentication to manage records"},{status:503});
  if (method !== "GET") {
    const origin = request.headers.get("origin");
    try {
      if (origin && new URL(origin).host !== request.headers.get("host"))
        return NextResponse.json({error:"Invalid request origin"},{status:403});
    } catch { return NextResponse.json({error:"Invalid request origin"},{status:403}); }
  }
  const token=(await cookies()).get("pulseos-session")?.value;
  if(!token)return NextResponse.json({error:"Sign in required"},{status:401});
  let body: string|undefined;
  if(method !== "GET"){
    const len=Number(request.headers.get("content-length")??0);
    const limit=path==="/api/v1/staff-chat/messages"&&method==="POST"?400000:12000;
    if(len>limit)return NextResponse.json({error:"Request too large"},{status:413});
    try {
      const parsed=await request.json();
      body=JSON.stringify(parsed);
      if(body.length>limit)return NextResponse.json({error:"Request too large"},{status:413});
    }catch{return NextResponse.json({error:"Invalid JSON"},{status:400});}
  }
  // Google Apps Script can take up to 45 seconds for an approved demo email.
  // Keep a longer timeout ONLY for that mail-producing endpoint.
  const patientEmailRequest=method==="POST"&&/^\/api\/v1\/patients\/[0-9a-fA-F-]{36}\/notifications$/.test(path);
  try {
    const response=await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}${path}`,{
      method,
      headers:{Authorization:`Bearer ${token}`,...(body?{"Content-Type":"application/json"}:{})},
      ...(body?{body}:{}),
      cache:"no-store",
      signal:AbortSignal.timeout(patientEmailRequest?65000:9000)
    });
    const raw=await response.text();
    let content:unknown={};
    try {content=raw?JSON.parse(raw):{};}catch{content={error:"Invalid backend response"};}
    return NextResponse.json(content,{status:response.status,headers:{"Cache-Control":"no-store"}});
  }catch(error){
    const timedOut=error instanceof Error && (error.name==="TimeoutError" || error.name==="AbortError");
    return NextResponse.json({error:patientEmailRequest&&timedOut
      ?"Patient notification request timed out. Delivery may still be processing. Check notification history before retrying."
      :"Clinical API unavailable"},{status:503});
  }
}
export function validPatientId(id:string):boolean {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(id);
}
