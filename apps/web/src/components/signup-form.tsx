"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

export default function SignupForm({ enabled }: { enabled: boolean }) {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [invitationCode, setInvitationCode] = useState("");
  const [status, setStatus] = useState("");
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!enabled) return;
    setPending(true);
    setStatus("");
    try {
      const response = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, email, password, invitationCode }),
        cache: "no-store",
      });
      const result = await response.json() as { error?: string };
      if (!response.ok) {
        setStatus(result.error ?? "Registration failed");
        return;
      }
      setPassword("");
      setInvitationCode("");
      router.push("/login?registered=1");
    } catch {
      setStatus("Registration service unavailable");
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <label htmlFor="signup-username">Staff username</label>
      <input id="signup-username" autoComplete="username" value={username}
        onChange={event => setUsername(event.target.value)}
        minLength={3} maxLength={40} pattern="[a-zA-Z][a-zA-Z0-9_.-]{2,39}" required disabled={!enabled} />
      <label htmlFor="signup-email">Work email</label>
      <input id="signup-email" type="email" autoComplete="email" value={email}
        onChange={event => setEmail(event.target.value)} maxLength={254} required disabled={!enabled} />
      <label htmlFor="signup-password">Password (at least 12 characters)</label>
      <input id="signup-password" type="password" autoComplete="new-password" value={password}
        onChange={event => setPassword(event.target.value)} minLength={12} maxLength={128}
        required disabled={!enabled} />
      <label htmlFor="signup-invite">Staff invitation code</label>
      <input id="signup-invite" type="password" autoComplete="off" value={invitationCode}
        onChange={event => setInvitationCode(event.target.value)} maxLength={256}
        required disabled={!enabled} />
      {status && <p className="auth-error" role="alert">{status}</p>}
      <button type="submit" className="auth-submit" disabled={!enabled || pending}>
        {pending ? "Creating account…" : "Create staff account"}
      </button>
    </form>
  );
}
