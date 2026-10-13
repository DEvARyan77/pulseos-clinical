"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import PasswordField from "./password-field";
import { ArrowLeft, Clock3, MailCheck, RefreshCw } from "lucide-react";
import styles from "./auth-flow.module.css";
import { waitForAuthApi, wakeAuthApi } from "@/lib/auth-readiness";

export default function ForgotPasswordForm({ enabled }: { enabled: boolean }) {
  const [step, setStep] = useState<"email" | "code" | "complete">("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => { if (enabled) wakeAuthApi(); }, [enabled]);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => setCooldown((current) => Math.max(0, current - 1)), 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  async function requestCode(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setError("");
    setMessage("");
    if (!enabled) { setError("Staff authentication is not enabled."); return; }
    setBusy(true);
    try {
      await waitForAuthApi(setMessage);
      const response = await fetch("/api/auth/forgot-password", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }), cache: "no-store",
      });
      const result = await response.json() as { error?: string; message?: string };
      if (!response.ok) throw new Error(result.error ?? "Could not request a reset code.");
      setStep("code");
      setCode("");
      setMessage(result.message ?? "If this account exists, check your email for a code.");
      setCooldown(60);
    } catch (cause) {
      setMessage("");
      setError(cause instanceof Error ? cause.message : "Unable to reach password reset service.");
    } finally {
      setBusy(false);
    }
  }

  async function resetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (password !== confirmation) {
      setError("Your passwords do not match.");
      return;
    }
    setBusy(true);
    try {
      await waitForAuthApi(setMessage);
      const response = await fetch("/api/auth/reset-password", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, code, password }), cache: "no-store",
      });
      const result = await response.json() as { error?: string; message?: string };
      if (!response.ok) throw new Error(result.error ?? "Could not reset your password.");
      setPassword("");
      setConfirmation("");
      setCode("");
      setStep("complete");
      setMessage("Your password has been updated.");
    } catch (cause) {
      setMessage("");
      setError(cause instanceof Error ? cause.message : "Unable to reach password reset service.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.flow}>
      {step === "email" && (
        <form className="auth-form" onSubmit={requestCode}>
          <label htmlFor="reset-email">Account email address</label>
          <input id="reset-email" type="email" autoComplete="email" value={email}
            onChange={(event) => setEmail(event.target.value)} maxLength={254} required />
          <p className="auth-description">If this email belongs to an account, you can recover it with a six-digit code.</p>
          {message && <p className={`${styles.status} ${styles.statusSuccess}`} role="status">{message}</p>}
          {error && <p className={`${styles.status} ${styles.statusError}`} role="alert">{error}</p>}
          <button className="auth-submit" type="submit" disabled={busy}>
            {busy ? "Connecting to account service…" : "Email me a reset code"}
          </button>
        </form>
      )}

      {step === "code" && (
        <form className="auth-form" onSubmit={resetPassword}>
          <div className={styles.inboxCard}>
            <div className={styles.inboxIcon}><MailCheck size={19} aria-hidden="true" /></div>
            <div className={styles.inboxBody}>
              <p className={styles.inboxTitle}>Check your email</p>
              <p className={styles.inboxText}>If an account exists, your reset code was requested for <strong>{email}</strong>.</p>
            </div>
          </div>

          <label htmlFor="reset-code" className={styles.codeLabel}>
            Six-digit reset code
            <span className={styles.expiry}><Clock3 size={13} aria-hidden="true" /> Expires in 10 min</span>
          </label>
          <input id="reset-code" className={styles.codeInput} type="text" inputMode="numeric"
            autoComplete="one-time-code" pattern="[0-9]{6}" spellCheck={false}
            value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            minLength={6} maxLength={6} required placeholder="000000" />
          <p className={styles.hint}>Use the most recent code and check Spam or Promotions if your message is delayed.</p>

          <div className={styles.passwordGroup}>
            <label htmlFor="reset-password">New password (8 characters minimum)</label>
            <PasswordField id="reset-password" value={password} onChange={setPassword}
              autoComplete="new-password" minLength={8} maxLength={128} required />
            <label htmlFor="reset-confirm">Confirm new password</label>
            <PasswordField id="reset-confirm" value={confirmation} onChange={setConfirmation}
              autoComplete="new-password" minLength={8} maxLength={128} required />
          </div>

          {message && <p className={`${styles.status} ${styles.statusSuccess}`} role="status">{message}</p>}
          {error && <p className={`${styles.status} ${styles.statusError}`} role="alert">{error}</p>}

          <button type="submit" className="auth-submit" disabled={busy}>
            {busy ? "Updating password…" : "Reset password"}
          </button>
          <div className={styles.resendPanel}>
            <div>
              <p className={styles.resendTitle}>Code hasn&apos;t arrived?</p>
              <p className={styles.resendHelp}>Request a new code once the one-minute cooldown finishes.</p>
            </div>
            <button type="button" className={styles.resendButton}
              disabled={busy || cooldown > 0} onClick={() => void requestCode()}>
              <RefreshCw size={16} aria-hidden="true" />
              {busy ? "Sending request…" : cooldown > 0 ? `Resend in ${cooldown}s` : "Resend reset code"}
            </button>
          </div>
          <button type="button" className={styles.textButton} disabled={busy}
            onClick={() => { setStep("email"); setCode(""); setError(""); setMessage(""); }}>
            <ArrowLeft size={15} aria-hidden="true" /> Change email address
          </button>
        </form>
      )}

      {step === "complete" && (
        <div className={styles.successPanel} role="status">
          <h2>Password updated</h2>
          <p>You can now sign in using your new password.</p>
          <Link href="/login">Continue to sign in</Link>
        </div>
      )}
    </div>
  );
}
