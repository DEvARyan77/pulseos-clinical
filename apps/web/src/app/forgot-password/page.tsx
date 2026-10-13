import Link from "next/link";
import ThemeSwitcher from "@/components/theme-switcher";
import ForgotPasswordForm from "@/components/forgot-password-form";
import styles from "../login/login.module.css";

export const dynamic = "force-dynamic";

export default function ForgotPasswordPage() {
  const enabled = process.env.PULSEOS_AUTH_ENABLED === "true";
  return (
    <main className={styles.screen}>
      <div className={styles.toolbar}><ThemeSwitcher /></div>
      <section className={styles.card}>
        <div className={styles.wordmark}>Pulse<span>OS</span></div>
        <div className={styles.kicker}>SECURE ACCOUNT RECOVERY</div>
        <h1>Reset your password.</h1>
        <p>Receive a code by email to securely set a new password.</p>
        <ForgotPasswordForm enabled={enabled} />
        <p className={styles.formLink}><Link href="/login">Back to sign in</Link></p>
        <div className={styles.notice}>Portfolio environment — fictional patient records only.</div>
      </section>
    </main>
  );
}
