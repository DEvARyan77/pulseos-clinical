import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

/** Server-only API gateway: bearer token never reaches client JavaScript. */
function json(body: unknown, init: {status: number; headers?: Record<string,string>}) {
  return NextResponse.json(body, {...init, headers: {"Cache-Control":"no-store", ...init.headers}});
}
export async function clinicalProxy(request: NextRequest, path: string, method: "GET"|"POST"|"PUT"|"PATCH") {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true")
    return json({error:"Enable staff authentication to manage records"},{status:503,headers:{"Cache-Control":"no-store"}});
  if (method !== "GET") {
    const origin = request.headers.get("origin");
    try {
      if (origin && new URL(origin).host !== request.headers.get("host"))
        return json({error:"Invalid request origin"},{status:403});
    } catch { return json({error:"Invalid request origin"},{status:403}); }
  }
  const token=(await cookies()).get("pulseos-session")?.value;
  if(!token)return json({error:"Sign in required"},{status:401});
  let body: string|undefined;
  if(method !== "GET"){
    const len=Number(request.headers.get("content-length")??0);
    const limit=path==="/api/v1/staff-chat/messages"&&method==="POST"?400000:12000;
    if(len>limit)return json({error:"Request too large"},{status:413});
    try {
      const parsed=await request.json();
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
        return json({error:"Expected a JSON object"},{status:400});
      body=JSON.stringify(parsed);
      if(body.length>limit)return json({error:"Request too large"},{status:413});
    }catch{return json({error:"Invalid JSON"},{status:400});}
  }
  // Google Apps Script can take up to 45 seconds for an approved demo email.
  // Keep a longer timeout ONLY for that mail-producing endpoint.
  const patientNotificationRequest=method==="POST"&&/^\/api\/v1\/patients\/[0-9a-fA-F-]{36}\/notifications$/.test(path);
  // Ordinary in-app notifications do not need the longer mail timeout.
  const patientEmailRequest=patientNotificationRequest&&body!==undefined&&
    (JSON.parse(body) as {channel?: unknown}).channel==="EMAIL";
  // Rescheduling also attempts an optional Google Script QR email.
  const appointmentReschedule=method==="PATCH"&&
    /^\/api\/v1\/demo-appointments\/[0-9a-fA-F-]{36}\/reschedule$/.test(path);
  const appointmentNotice = appointmentReschedule ||
    (method==="POST" && path==="/api/v1/demo-appointments") ||
    (method==="PATCH" && /^\/api\/v1\/demo-appointments\/[0-9a-fA-F-]{36}\/advance$/.test(path)) ||
    (method==="POST" && /^\/api\/v1\/patient-portal\/appointments\/[0-9a-fA-F-]{36}\/check-in$/.test(path));
  try {
    const response=await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}${path}`,{
      method,
      headers:{Authorization:`Bearer ${token}`,...(body?{"Content-Type":"application/json"}:{})},
      ...(body?{body}:{}),
      cache:"no-store",
      signal:AbortSignal.timeout(patientEmailRequest||appointmentNotice?65000:9000)
    });
    const raw=await response.text();
    let content:unknown={};
    try {content=raw?JSON.parse(raw):{};}catch{return json({error:"Invalid backend response"},{status:502,headers:{"Cache-Control":"no-store"}});}
    // Spring can return problem details without an "error" field.
    // Show only safe, actionable messages for this test-only email operation.
    if(patientEmailRequest&&!response.ok){
      let message: string|undefined;
      if(response.status===403){
        message="Demo email blocked. Verify that this fictional patient's contact email appears in PULSEOS_DEMO_EMAIL_ALLOWLIST on pulseos-api.";
      }else if(response.status===503){
        message="Demo email could not be sent. Check the backend email provider settings and publish the latest Google Apps Script relay version under Manage deployments. Review Apps Script Executions.";
      }else if(response.status===504){
        message="Email delivery timed out. Check notification history before retrying to avoid duplicate messages.";
      }
      if(message)return json({error:message},{status:response.status,
        headers:{"Cache-Control":"no-store"}});
    }
    return json(content,{status:response.status,headers:{"Cache-Control":"no-store"}});
  }catch(error){
    const timedOut=error instanceof Error && (error.name==="TimeoutError" || error.name==="AbortError");
    return json({error:timedOut&&appointmentNotice
      ?"Appointment update timed out. Check the appointment list before retrying; the operation may still complete."
      :patientEmailRequest&&timedOut
      ?"Patient notification request timed out. Delivery may still be processing. Check notification history before retrying."
      :"Clinical API unavailable"},{status:503,headers:{"Cache-Control":"no-store"}});
  }
}
export function validPatientId(id:string):boolean {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(id);
}
