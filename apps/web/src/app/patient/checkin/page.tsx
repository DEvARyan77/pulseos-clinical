import Link from "next/link";
import {currentStaff} from "@/lib/staff-session";
import StaffNavbar from "@/components/staff-navbar";
import CheckinConfirmation from "@/components/checkin-confirmation";
import {redirect} from "next/navigation";
export const dynamic="force-dynamic";
export default async function CheckInPage({searchParams}:{searchParams:Promise<{appointment?:string}>}){
  const params=await searchParams;
  const id=typeof params.appointment==="string"&&/^[0-9a-f-]{36}$/.test(params.appointment)?params.appointment:null;
  const staff=await currentStaff();
  if(staff&&!staff.roles.includes("PATIENT"))redirect("/");
  if(!staff)return <main style={{maxWidth:640,margin:"60px auto",padding:22}}>
    <h1>PulseOS demo patient check-in</h1>
    <p>For security, opening or scanning a QR never changes status automatically. Sign in with the matching verified patient account, then confirm your check-in.</p>
    <p style={{marginTop:20}}><Link href={`/login?next=${encodeURIComponent(id?`/patient/checkin?appointment=${id}`:"/patient")}`}>Sign in to continue</Link></p>
  </main>;
  return <main style={{minHeight:"100vh"}}><StaffNavbar role="PATIENT" active="patients"/>
    {id?<CheckinConfirmation appointmentId={id}/>:<div style={{maxWidth:680,margin:"50px auto",padding:20}}>Invalid appointment QR. <Link href="/patient">Open patient portal</Link>.</div>}
  </main>;
}
