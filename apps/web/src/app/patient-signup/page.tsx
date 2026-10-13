import Link from "next/link";
import SignupForm from "@/components/signup-form";
import ThemeSwitcher from "@/components/theme-switcher";
import styles from "../login/login.module.css";
export const dynamic="force-dynamic";
export default function PatientSignupPage(){
  const enabled=process.env.PULSEOS_AUTH_ENABLED==="true"&&process.env.PULSEOS_REGISTRATION_ENABLED==="true";
  return <main className={styles.screen}>
    <div className={styles.toolbar}><ThemeSwitcher/></div>
    <section className={styles.card}>
      <div className={styles.wordmark}>Pulse<span>OS</span></div>
      <div className={styles.kicker}>PATIENT ACCOUNT REGISTRATION</div>
      <h1>Register as a patient</h1>
      <p>Use a fictional identity in this demo. Verify your email to create a patient-only account with no clinical staff permissions.</p>
      {!enabled&&<p className={styles.setupNotice}>Registration is disabled on this deployment.</p>}
      <SignupForm enabled={enabled} accountType="patient"/>
      <p className={styles.formLink}>Already registered? <Link href="/login">Sign in</Link></p>
      <div className={styles.notice}>For synthetic data only. A clinician must link your verified email to an appointment.</div>
    </section></main>;
}
