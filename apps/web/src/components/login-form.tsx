"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

export default function LoginForm({ enabled }: { enabled: boolean }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const router = useRouter();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!enabled) {
      setError("Staff login is not configured on this deployment. The administrator must enable authentication on both Render services.");
      return;
    }
    setPending(true);
    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username.trim(), password }),
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
      <label htmlFor="staff-user">Staff username or email</label>
      <input id="staff-user" name="username" value={username}
        onChange={(e) => setUsername(e.target.value)} minLength={1} maxLength={254}
        autoComplete="username" required />
      <label htmlFor="staff-password">Password</label>
      <input id="staff-password" name="password" type="password" value={password}
        onChange={(e) => setPassword(e.target.value)}
        minLength={1} maxLength={256} autoComplete="current-password" required />
      {error && <p className="auth-error" role="alert">{error}</p>}
      <button type="submit" disabled={pending} className="auth-submit">
        {pending ? "Signing in…" : "Sign in"}
      </button>
    </form>
  );
}
