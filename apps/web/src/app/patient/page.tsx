import {redirect} from "next/navigation";
import {currentStaff} from "@/lib/staff-session";
import StaffNavbar from "@/components/staff-navbar";
import PatientPortal from "@/components/patient-portal";
export const dynamic="force-dynamic";
export default async function PatientPage(){
  if(process.env.PULSEOS_AUTH_ENABLED!=="true")redirect("/login");
  const user=await currentStaff();
  if(!user)redirect("/login");
  if(!user.roles.includes("PATIENT"))redirect("/");
  return <main style={{minHeight:"100vh"}}><StaffNavbar role="PATIENT" active="patients"/><PatientPortal/></main>;
}
