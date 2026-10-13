import { redirect } from "next/navigation";
import { currentStaff } from "@/lib/staff-session";
import StaffNavbar from "@/components/staff-navbar";
import ProfileEditor from "@/components/profile-editor";
export const dynamic="force-dynamic";
export default async function ProfilePage(){
  if(process.env.PULSEOS_AUTH_ENABLED!=="true")redirect("/login");
  const staff=await currentStaff();if(!staff)redirect("/login");
  const role=staff.roles.includes("ADMIN")?"ADMIN":"CLINICIAN";
  return <main style={{minHeight:"100vh"}}><StaffNavbar role={role} active="profile"/>
    <ProfileEditor/></main>;
}
