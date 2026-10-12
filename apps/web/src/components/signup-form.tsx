"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import PasswordField from "./password-field";
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
      await waitForAuthApi(setFeedback);
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
      setFeedback("We sent a six-digit code to your email. Check your inbox and spam folder.");
    } catch (cause) {
      setFeedback("");
      setError(cause instanceof Error ? cause.message : "Could not contact the registration server.");
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
      <form className="auth-form" onSubmit={submitCode}>
        <p>Verify <strong>{email}</strong>. The code expires in 10 minutes.</p>
        <label htmlFor="email-code">Email verification code</label>
        <input id="email-code" type="text" inputMode="numeric" autoComplete="one-time-code"
          value={code} onChange={event => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
          minLength={6} maxLength={6} placeholder="6-digit code" required />
        {feedback && <p className="auth-success" role="status">{feedback}</p>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button type="submit" className="auth-submit" disabled={pending}>
          {pending ? "Verifying…" : "Verify email and activate account"}
        </button>
        <button type="button" className="auth-secondary" disabled={pending}
          onClick={() => { setCode(""); setStep("details"); setFeedback(""); setError(""); }}>
          Start over / request a new code
        </button>
      </form>
    );
  }

  return (
    <form className="auth-form" onSubmit={submitDetails}>
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
