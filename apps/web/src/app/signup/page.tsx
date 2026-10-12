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
        <div className={styles.kicker}>EMAIL-VERIFIED ACCOUNT</div>
        <h1>Create your account.</h1>
        <p>Register with your email, then enter the one-time verification code we send you.</p>
        {!enabled && <p className={styles.setupNotice} role="status">
          Registration is currently disabled on this deployment. Contact the administrator.
        </p>}
        <SignupForm enabled={enabled} />
        <p className={styles.formLink}>Already registered? <Link href="/login">Sign in</Link></p>
        <div className={styles.notice}>Portfolio environment — fictional patient records only.</div>
      </section>
    </main>
  );
}
