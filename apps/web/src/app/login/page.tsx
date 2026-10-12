import Link from "next/link";
import LoginForm from "@/components/login-form";
import ThemeSwitcher from "@/components/theme-switcher";
import styles from "./login.module.css";

export const dynamic = "force-dynamic";

export default function LoginPage() {
  const enabled = process.env.PULSEOS_AUTH_ENABLED === "true";
  return (
    <main className={styles.screen}>
      <div className={styles.toolbar}><ThemeSwitcher /></div>
      <section className={styles.card}>
        <div className={styles.wordmark}>Pulse<span>OS</span></div>
        <div className={styles.kicker}>CLINICIAN PORTAL</div>
        <h1>Welcome back.</h1>
        <p>Enter your staff username or email and your password. Usernames are case-insensitive; passwords are case-sensitive.</p>
        {!enabled && <div className={styles.setupNotice} role="status">
          Authentication is not enabled on this deployment. You can try the form,
          but sign-in will not work until the Render environment is configured.
        </div>}
        <LoginForm enabled={enabled} />
        <p className={styles.formLink}>New here? <Link href="/signup">Create an account</Link></p>
        {!enabled && <p className={styles.formLink}><Link href="/">Explore the synthetic dashboard →</Link></p>}
        <div className={styles.notice}>Only fictional records. Not authorized for real patient data.</div>
      </section>
    </main>
  );
}
