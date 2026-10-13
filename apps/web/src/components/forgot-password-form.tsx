"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import PasswordField from "./password-field";
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
    <div>
      {step === "email" && (
        <form className="auth-form" onSubmit={requestCode}>
          <label htmlFor="reset-email">Account email address</label>
          <input id="reset-email" type="email" autoComplete="email" value={email}
            onChange={(event) => setEmail(event.target.value)} maxLength={254} required />
          <p className="auth-description">We&apos;ll send a six-digit code if an account exists for this email.</p>
          <button className="auth-submit" type="submit" disabled={busy}>
            {busy ? "Connecting to account service…" : "Email me a reset code"}
          </button>
        </form>
      )}
      {step === "code" && (
        <form className="auth-form" onSubmit={resetPassword}>
          <p className="auth-description">Check <strong>{email}</strong>. Codes expire after 10 minutes.</p>
          <label htmlFor="reset-code">Six-digit email code</label>
          <input id="reset-code" type="text" inputMode="numeric" autoComplete="one-time-code"
            value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            minLength={6} maxLength={6} required placeholder="000000" />
          <label htmlFor="reset-password">New password (8 characters minimum)</label>
          <PasswordField id="reset-password" value={password} onChange={setPassword}
            autoComplete="new-password" minLength={8} maxLength={128} required />
          <label htmlFor="reset-confirm">Confirm new password</label>
          <PasswordField id="reset-confirm" value={confirmation} onChange={setConfirmation}
            autoComplete="new-password" minLength={8} maxLength={128} required />
          <button type="submit" className="auth-submit" disabled={busy}>
            {busy ? "Updating password…" : "Reset password"}
          </button>
          <button type="button" className="auth-secondary"
            disabled={busy || cooldown > 0} onClick={() => void requestCode()}>
            {cooldown > 0 ? `Request another code in ${cooldown}s` : "Email another code"}
          </button>
        </form>
      )}
      {step === "complete" && (
        <p className="auth-success" role="status">Password changed successfully. <Link href="/login">Sign in with your new password.</Link></p>
      )}
      {message && step !== "complete" && <p className="auth-success" role="status">{message}</p>}
      {error && <p className="auth-error" role="alert">{error}</p>}
    </div>
  );
}
