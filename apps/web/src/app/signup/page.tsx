import Link from "next/link";
import SignupForm from "@/components/signup-form";
import ThemeSwitcher from "@/components/theme-switcher";
import styles from "../login/login.module.css";

export const dynamic = "force-dynamic";

export default function SignupPage() {
  const enabled = process.env.PULSEOS_AUTH_ENABLED === "true" &&
                  process.env.PULSEOS_REGISTRATION_ENABLED === "true";
  return (
    <main className={styles.screen}>
      <div className={styles.toolbar}><ThemeSwitcher /></div>
      <section className={styles.card}>
        <div className={styles.wordmark}>Pulse<span>OS</span></div>
        <div className={styles.kicker}>STAFF ENROLLMENT</div>
        <h1>Create your account.</h1>
        <p>Register as an invited demo clinician to access the medical workspace.</p>
        {!enabled && <p className={styles.notice} role="status">
          Registration is currently disabled on this deployment. Ask the project administrator for an invitation.
        </p>}
        <SignupForm enabled={enabled} />
        <p className={styles.formLink}>Already have an account? <Link href="/login">Sign in</Link></p>
        <div className={styles.notice}>Demo application only · Do not enter real patient information.</div>
      </section>
    </main>
  );
}
