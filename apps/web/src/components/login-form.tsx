"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

export default function LoginForm({ enabled }: { enabled: boolean }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const router = useRouter();
  // Server-rendered login page remains usable without client-only query state.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!enabled) return;
    setPending(true);
    setError("");
    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
        cache: "no-store",
      });
      if (!response.ok) {
        const data = await response.json() as { error?: string };
        setError(data.error ?? "Sign-in failed");
        return;
      }
      setPassword("");
      router.replace("/");
      router.refresh();
    } catch {
      setError("Cannot reach the sign-in service");
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} className="auth-form">
      <label htmlFor="staff-user">Staff username</label>
      <input id="staff-user" name="username" value={username}
        onChange={(e) => setUsername(e.target.value)} minLength={1} maxLength={80}
        autoComplete="username" required disabled={!enabled} />
      <label htmlFor="staff-password">Password</label>
      <input id="staff-password" name="password" type="password" value={password}
        onChange={(e) => setPassword(e.target.value)}
        minLength={1} maxLength={256} autoComplete="current-password" required disabled={!enabled} />
      {error && <p className="auth-error" role="alert">{error}</p>}
      <button type="submit" disabled={!enabled || pending} className="auth-submit">
        {pending ? "Signing in…" : "Sign in"}
      </button>
    </form>
  );
}
