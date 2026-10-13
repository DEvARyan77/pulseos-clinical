"use client";
import Link from "next/link";
import {useState} from "react";
export default function CheckinConfirmation({appointmentId}:{appointmentId:string}){
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState("");const [error,setError]=useState("");
  async function confirm(){
    setBusy(true);setError("");setMessage("");
    try{
      const response=await fetch(`/api/patient-portal/appointments/${appointmentId}/check-in`,{
        method:"POST",headers:{"Content-Type":"application/json"},body:"{}"});
      const data=await response.json() as {status?:string;error?:string};
      if(!response.ok)throw new Error(data.error??"Unable to check in");
      setMessage(`Confirmed: ${data.status?.replaceAll("_"," ")??"CHECKED IN"}. Your demo clinician can now advance the visit. If notifications are configured, a follow-up email will be sent.`);
    }catch(cause){setError(cause instanceof Error?cause.message:"Check-in unavailable");}
    finally{setBusy(false);}
  }
  return <section style={{maxWidth:630,margin:"48px auto",padding:25,background:"var(--surface)",border:"1px solid var(--line)",borderRadius:13}}>
    <h1>Confirm appointment check-in</h1>
    <p style={{margin:"15px 0",lineHeight:1.7}}>Use this only for a fictional appointment. Your verified patient email must match the appointment record. Check-in is permitted within 24 hours before the appointment and up to 12 hours afterward.</p>
    <p style={{marginBottom:18}}>Appointment reference: {appointmentId.slice(0,8)}</p>
    {error&&<p role="alert" style={{color:"#b24b4b",marginBottom:15}}>{error}</p>}
    {message?<p role="status" style={{marginBottom:15}}>{message}</p>:
      <button type="button" onClick={()=>void confirm()} disabled={busy} style={{background:"var(--accent)",border:0,borderRadius:7,padding:"12px 18px",fontWeight:800}}>
        {busy?"Confirming…":"Confirm check-in"}
      </button>}
    <p style={{marginTop:24}}><Link href="/patient">Return to my appointments</Link></p>
  </section>;
}
