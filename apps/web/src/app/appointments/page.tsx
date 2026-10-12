import Link from "next/link";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import ThemeSwitcher from "@/components/theme-switcher";
import AppointmentManager from "@/components/appointment-manager";
import styles from "./appointments.module.css";

export const dynamic = "force-dynamic";

async function signedIn(): Promise<boolean> {
  const token = (await cookies()).get("pulseos-session")?.value;
  if (!token) return false;
  try {
    const upstream = await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(5000),
    });
    return upstream.ok;
  } catch {
    return false;
  }
}

export default async function AppointmentsPage() {
  const enabled = process.env.PULSEOS_AUTH_ENABLED === "true";
  if (enabled && !(await signedIn())) redirect("/login");
  return (
    <main className={styles.page}>
      <header className={styles.header}><Link href="/">← Patient workspace</Link><ThemeSwitcher /></header>
      <div className={styles.content}>
        <p className={styles.overline}>PULSEOS · DEMO OPERATIONS</p>
        <h1>Appointment scheduling</h1>
        <p className={styles.subtitle}>Create, review and cancel appointments for patients in your private test registry.</p>
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
