"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import PasswordField from "./password-field";
import { waitForAuthApi, wakeAuthApi } from "@/lib/auth-readiness";

export default function LoginForm({ enabled, redirectTo }: { enabled: boolean; redirectTo?:string }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState("");
  useEffect(() => { if (enabled) wakeAuthApi(); }, [enabled]);
  const router = useRouter();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setConnectionStatus("");
    if (!enabled) {
      setError("Login is not configured on this deployment. The administrator must enable authentication on both Render services.");
      return;
    }
    setPending(true);
    try {
      await waitForAuthApi(setConnectionStatus);
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username.trim(), password }),
        cache: "no-store",
      });
      if (!response.ok) {
        const data = await response.json() as { error?: string };
        setConnectionStatus("");
        setError(data.error ?? "Sign-in failed");
        return;
      }
      setPassword("");
      router.replace(redirectTo??"/");
      router.refresh();
    } catch (cause) {
      setConnectionStatus("");
      setError(cause instanceof Error ? cause.message : "Cannot reach the sign-in service");
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} className="auth-form">
      <label htmlFor="staff-user">Username or verified email</label>
      <input id="staff-user" name="username" value={username}
        onChange={(e) => setUsername(e.target.value)} minLength={1} maxLength={254}
        autoComplete="username" required />
      <label htmlFor="staff-password">Password</label>
      <PasswordField id="staff-password" value={password} onChange={setPassword}
        minLength={1} maxLength={256} autoComplete="current-password" required />
      {connectionStatus && <p className="auth-success" role="status">{connectionStatus}</p>}
      {error && <p className="auth-error" role="alert">{error}</p>}
      <button type="submit" disabled={pending} className="auth-submit">
        {pending ? "Connecting and signing in…" : "Sign in"}
      </button>
    </form>
  );
}
