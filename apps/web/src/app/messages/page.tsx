import {redirect} from "next/navigation";
import {currentStaff} from "@/lib/staff-session";
import StaffChat from "@/components/staff-chat";

export const dynamic="force-dynamic";
type Props={searchParams:Promise<{to?:string|string[]}>};
export default async function MessagesPage({searchParams}:Props){
 if(process.env.PULSEOS_AUTH_ENABLED!=="true")redirect("/login");
 const staff=await currentStaff();
 if(!staff)redirect("/login");
 if(staff.roles.includes("PATIENT"))redirect("/patient");
 const params=await searchParams;
 const to=typeof params.to==="string"&&/^[a-zA-Z0-9_.-]{1,80}$/.test(params.to)
   ?params.to:null;
 return <StaffChat username={staff.username} role={staff.roles.includes("ADMIN")?"ADMIN":"CLINICIAN"} initialTarget={to}/>;
}
