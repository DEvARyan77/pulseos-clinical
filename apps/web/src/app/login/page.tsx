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
        <p>Sign in to your staff account to open the clinical workspace.</p>
        {!enabled && <div className={styles.setupNotice} role="status">
          Staff login is not activated on this deployment. The demo dashboard is available without an account.
        </div>}
        <LoginForm enabled={enabled} />
        <p className={styles.formLink}>New here? <Link href="/signup">Create an account</Link></p>
        {!enabled && <p className={styles.formLink}><Link href="/">Explore the demo dashboard →</Link></p>}
        <div className={styles.notice}>Portfolio demo with synthetic records · Not for real patient information.</div>
      </section>
    </main>
  );
}
