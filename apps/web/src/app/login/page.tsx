import { redirect } from "next/navigation";
import LoginForm from "@/components/login-form";
import ThemeSwitcher from "@/components/theme-switcher";
import styles from "./login.module.css";

export const dynamic = "force-dynamic";

export default function LoginPage() {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") redirect("/");
  return (
    <main className={styles.screen}>
      <div className={styles.toolbar}><ThemeSwitcher /></div>
      <section className={styles.card}>
        <div className={styles.wordmark}><span className={styles.pulse}>♥</span> Pulse<span>OS</span></div>
        <div className={styles.kicker}>STAFF ACCESS</div>
        <h1>Welcome back.</h1>
        <p>Sign in to the clinical workspace with your configured staff account.</p>
        <LoginForm />
        <div className={styles.notice}>Portfolio environment · Synthetic records only. Never enter real patient data.</div>
      </section>
    </main>
  );
}
