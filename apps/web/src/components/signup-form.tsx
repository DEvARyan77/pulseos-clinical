"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import PasswordField from "./password-field";
import { ArrowLeft, Clock3, MailCheck, RefreshCw } from "lucide-react";
import styles from "./auth-flow.module.css";
import { waitForAuthApi, wakeAuthApi } from "@/lib/auth-readiness";

export default function SignupForm({ enabled }: { enabled: boolean }) {
  const router = useRouter();
  const [step, setStep] = useState<"details" | "verify">("details");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [feedback, setFeedback] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [resendSeconds, setResendSeconds] = useState(0);
  useEffect(() => {
    if (resendSeconds <= 0) return;
    const timer = setInterval(() => setResendSeconds((remaining) => Math.max(0, remaining - 1)), 1000);
    return () => clearInterval(timer);
  }, [resendSeconds]);
  useEffect(() => { if (enabled) wakeAuthApi(); }, [enabled]);

  async function submitDetails(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFeedback("");
    setError("");
    if (!/^[A-Za-z][A-Za-z0-9_.-]{2,39}$/.test(username)) {
      setError("Username must start with a letter and contain 3–40 letters, numbers, dots, underscores or hyphens.");
      return;
    }
    if (!enabled) {
      setError("Email signup is unavailable on this deployment. Contact the project administrator.");
      return;
    }
    setPending(true);
    try {
      await waitForAuthApi(setFeedback, true);
      const response = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, email, password }),
        cache: "no-store",
      });
      const payload = await response.json() as { error?: string };
      if (!response.ok) {
        setFeedback("");
        setError(payload.error ?? "Unable to send the verification email.");
        return;
      }
      setPassword("");
      setStep("verify");
      setResendSeconds(60);
      setFeedback("The mail service accepted your request. Check your inbox, spam and Promotions. Delivery can take a little time.");
    } catch (cause) {
      setFeedback("");
      setError(cause instanceof Error ? cause.message : "Could not contact the registration server.");
    } finally {
      setPending(false);
    }
  }

  async function resendCode() {
    if (pending || resendSeconds > 0) return;
    setError("");
    setFeedback("");
    setPending(true);
    try {
      await waitForAuthApi(setFeedback, true);
      const response = await fetch("/api/auth/resend", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
        cache: "no-store",
      });
      const result = await response.json() as { error?: string; message?: string };
      if (!response.ok) throw new Error(result.error ?? "Unable to request another code.");
      setCode("");
      setResendSeconds(60);
      setFeedback(result.message ?? "Your resend request was accepted. Check your inbox and other folders.");
    } catch (cause) {
      setFeedback("");
      setError(cause instanceof Error ? cause.message : "Could not request another code.");
    } finally {
      setPending(false);
    }
  }

  async function submitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setFeedback("");
    if (!/^[0-9]{6}$/.test(code)) {
      setError("Enter the six-digit verification code from your email.");
      return;
    }
    setPending(true);
    try {
      await waitForAuthApi(setFeedback);
      const response = await fetch("/api/auth/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, code }),
        cache: "no-store",
      });
      const payload = await response.json() as { error?: string };
      if (!response.ok) {
        setFeedback("");
        setError(payload.error ?? "Could not verify the code.");
        return;
      }
      setCode("");
      router.push("/login?verified=1");
    } catch (cause) {
      setFeedback("");
      setError(cause instanceof Error ? cause.message : "Could not contact the verification server.");
    } finally {
      setPending(false);
    }
  }

  if (step === "verify") {
    return (
      <form className={`auth-form ${styles.flow}`} onSubmit={submitCode}>
        <div className={styles.stepper} aria-label="Registration step 2 of 2">
          <span className={`${styles.step} ${styles.stepDone}`}>
            <span className={styles.stepNumber}>✓</span> Account details
          </span>
          <span className={styles.stepLine} aria-hidden="true" />
          <span className={`${styles.step} ${styles.stepActive}`}>
            <span className={styles.stepNumber}>2</span> Verify email
          </span>
        </div>

        <div className={styles.inboxCard}>
          <div className={styles.inboxIcon}><MailCheck size={19} aria-hidden="true" /></div>
          <div className={styles.inboxBody}>
            <p className={styles.inboxTitle}>Check your email</p>
            <p className={styles.inboxText}>Enter the six-digit code requested for <strong>{email}</strong>.</p>
          </div>
        </div>

        <label htmlFor="email-code" className={styles.codeLabel}>
          Verification code
          <span className={styles.expiry}><Clock3 size={13} aria-hidden="true" /> Expires in 10 min</span>
        </label>
        <input id="email-code" className={styles.codeInput} type="text" inputMode="numeric"
          autoComplete="one-time-code" spellCheck={false} autoCapitalize="none"
          aria-describedby="verify-code-help" pattern="[0-9]{6}"
          value={code} onChange={event => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
          minLength={6} maxLength={6} placeholder="000000" required />
        <p id="verify-code-help" className={styles.hint}>
          Enter the most recent code. If nothing arrives, check <strong>Spam</strong> and <strong>Promotions</strong>.
        </p>
        {feedback && <p className={`${styles.status} ${styles.statusSuccess}`} role="status">{feedback}</p>}
        {error && <p className={`${styles.status} ${styles.statusError}`} role="alert">{error}</p>}
        <div className={styles.actionStack}>
          <button type="submit" className="auth-submit" disabled={pending}>
            {pending ? "Verifying email…" : "Verify email and activate account"}
          </button>
        </div>

        <div className={styles.resendPanel}>
          <div>
            <p className={styles.resendTitle}>Didn&apos;t receive the code?</p>
            <p className={styles.resendHelp}>Mail delivery may take a moment. You can request a fresh code after the cooldown.</p>
          </div>
          <button type="button" className={styles.resendButton} disabled={pending || resendSeconds > 0}
            onClick={() => void resendCode()}>
            <RefreshCw size={16} aria-hidden="true" />
            {pending ? "Sending request…" : resendSeconds > 0 ? `Resend in ${resendSeconds}s` : "Resend verification code"}
          </button>
          {resendSeconds > 0 && <span className={styles.timer} aria-live="off">Resend available after {resendSeconds} seconds</span>}
        </div>
        <button type="button" className={styles.textButton} disabled={pending}
          onClick={() => { setCode(""); setStep("details"); setFeedback(""); setError(""); }}>
          <ArrowLeft size={15} aria-hidden="true" /> Change registration details
        </button>
      </form>
    );
  }

  return (
    <form className={`auth-form ${styles.flow}`} onSubmit={submitDetails}>
      <div className={styles.stepper} aria-label="Registration step 1 of 2">
        <span className={`${styles.step} ${styles.stepActive}`}>
          <span className={styles.stepNumber}>1</span> Account details
        </span>
        <span className={styles.stepLine} aria-hidden="true" />
        <span className={styles.step}>
          <span className={styles.stepNumber}>2</span> Verify email
        </span>
      </div>
      <label htmlFor="signup-username">Username</label>
      <input id="signup-username" autoComplete="username" value={username}
        onChange={event => setUsername(event.target.value)}
        minLength={3} maxLength={40} required />
      <label htmlFor="signup-email">Email address</label>
      <input id="signup-email" type="email" autoComplete="email" value={email}
        onChange={event => setEmail(event.target.value)} maxLength={254} required />
      <label htmlFor="signup-password">Password (at least 8 characters)</label>
      <PasswordField id="signup-password" value={password} onChange={setPassword}
        autoComplete="new-password" minLength={8} maxLength={128} required />
      <p className="auth-description">We’ll email you a code to verify your address before activating your account.</p>
      {feedback && <p className="auth-success" role="status">{feedback}</p>}
      {error && <p className="auth-error" role="alert">{error}</p>}
      <button type="submit" className="auth-submit" disabled={pending}>
        {pending ? "Preparing secure signup…" : "Send verification code"}
      </button>
    </form>
  );
}
