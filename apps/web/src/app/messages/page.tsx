import {redirect} from "next/navigation";
import {currentStaff} from "@/lib/staff-session";
import StaffChat from "@/components/staff-chat";
export const dynamic="force-dynamic";
export default async function MessagesPage(){
 if(process.env.PULSEOS_AUTH_ENABLED!=="true")redirect("/login");
 const staff=await currentStaff();
 if(!staff)redirect("/login");
 return <StaffChat username={staff.username} role={staff.roles.includes("ADMIN")?"ADMIN":"CLINICIAN"}/>;
}
