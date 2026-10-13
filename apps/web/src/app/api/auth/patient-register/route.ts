import { NextRequest,NextResponse } from "next/server";
export const dynamic="force-dynamic";
export async function POST(request:NextRequest){
  if(process.env.PULSEOS_AUTH_ENABLED!=="true"||process.env.PULSEOS_REGISTRATION_ENABLED!=="true")
    return NextResponse.json({error:"Registration is closed"},{status:403});
  const origin=request.headers.get("origin");
  try{if(origin&&new URL(origin).host!==request.headers.get("host"))
    return NextResponse.json({error:"Invalid request origin"},{status:403});}
  catch{return NextResponse.json({error:"Invalid request origin"},{status:403});}
  let data:unknown;
  try{data=await request.json();}catch{return NextResponse.json({error:"Invalid request"},{status:400});}
  if(!data||typeof data!=="object")return NextResponse.json({error:"Invalid request"},{status:400});
  const fields=data as Record<string,unknown>;
  if(typeof fields.username!=="string"||!/^[a-zA-Z][a-zA-Z0-9_.-]{2,39}$/.test(fields.username)||
    typeof fields.email!=="string"||fields.email.length>254||
    typeof fields.password!=="string"||fields.password.length<8||fields.password.length>128)
    return NextResponse.json({error:"Check username, email and password"},{status:400});
  try{
    const response=await fetch(`${process.env.API_INTERNAL_URL??"http://localhost:8080"}/api/v1/auth/patient-register`,{
      method:"POST",headers:{"Content-Type":"application/json"},cache:"no-store",
      body:JSON.stringify({username:fields.username,email:fields.email,password:fields.password}),
      signal:AbortSignal.timeout(90000)});
    const result=await response.json().catch(()=>({})) as {error?:string};
    return NextResponse.json(response.ok?{message:"Check your inbox for a six-digit code."}:
      {error:result.error??"Patient registration could not be completed"},
      {status:response.status,headers:{"Cache-Control":"no-store"}});
  }catch{return NextResponse.json({error:"Patient registration server unavailable"},{status:503});}
}
