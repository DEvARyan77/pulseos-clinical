import Link from "next/link";
import { currentStaff } from "@/lib/staff-session";
import { redirect } from "next/navigation";
import ThemeSwitcher from "@/components/theme-switcher";
import StaffNavbar from "@/components/staff-navbar";
import AppointmentManager from "@/components/appointment-manager";
import styles from "./appointments.module.css";

export const dynamic = "force-dynamic";

export default async function AppointmentsPage() {
  const enabled = process.env.PULSEOS_AUTH_ENABLED === "true";
  if (enabled) {
    const staff = await currentStaff();
    if (!staff) redirect("/login");
    if (staff.roles.includes("ADMIN")) redirect("/admin");
    if (!staff.roles.includes("CLINICIAN")) redirect("/login");
  }
  return (
    <main className={styles.page}>
      {enabled ? <StaffNavbar role="CLINICIAN" active="appointments" /> : <header className={styles.header}><Link href="/">← Patient workspace</Link><ThemeSwitcher /></header>}
      <div className={styles.content}>
        <p className={styles.overline}>PULSEOS · DEMO OPERATIONS</p>
        <h1>Appointment operations</h1>
        <p className={styles.subtitle}>Schedule and reschedule with working hours, breaks and blocked dates. Each visit lasts 30 minutes; clinician rules use their chosen time zone.</p>
        {enabled ? <AppointmentManager/> : (
          <div className={styles.disabled}>
            <h2>Staff authentication is not enabled</h2>
            <p>Scheduling saves to PostgreSQL only when staff login is configured. The patient dashboard remains available in demo mode.</p>
            <Link href="/login">Open staff login</Link>
          </div>
        )}
      </div>
    </main>
  );
}
