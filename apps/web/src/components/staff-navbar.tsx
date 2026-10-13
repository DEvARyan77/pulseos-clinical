"use client";
import Link from "next/link";
import {HeartPulse,UserRound} from "lucide-react";
import ThemeSwitcher from "./theme-switcher";
import SignOutButton from "./sign-out-button";
import styles from "./staff-navbar.module.css";
type Active="patients"|"appointments"|"admin"|"messages"|"profile";
export default function StaffNavbar({role,active,apiStatus}:{role:"ADMIN"|"CLINICIAN";active:Active;apiStatus?:"connected"|"offline"}){
  const links=role==="ADMIN"?[{href:"/admin",title:"Administration",id:"admin"},{href:"/messages",title:"Staff chat",id:"messages"}]:
    [{href:"/",title:"Patients",id:"patients"},{href:"/appointments",title:"Appointments",id:"appointments"},{href:"/messages",title:"Staff chat",id:"messages"}];
  return <header className={styles.bar}>
    <Link href={role==="ADMIN"?"/admin":"/"} className={styles.brand}><HeartPulse size={23}/>Pulse<b>OS</b></Link>
    <nav aria-label="Main navigation" className={styles.links}>
      {links.map(item=><Link key={item.id} href={item.href} aria-current={active===item.id?"page":undefined}
        className={active===item.id?styles.selected:""}>{item.title}</Link>)}
      <Link href="/profile" aria-current={active==="profile"?"page":undefined}
        className={active==="profile"?styles.selected:""}><UserRound size={15}/> My profile</Link>
    </nav>
    <div className={styles.actions}>
      {apiStatus&&<span className={styles.api}>API {apiStatus}</span>}
      <ThemeSwitcher/><SignOutButton/>
    </div>
  </header>;
}
