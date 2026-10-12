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
    setStatus("");
    if (!/^[A-Za-z][A-Za-z0-9_.-]{2,39}$/.test(username)) {
      setStatus("Username must be 3–40 characters, start with a letter, and contain only letters, numbers, dots, underscores or hyphens.");
      return;
    }
    if (!enabled) {
      setStatus("Staff registration is currently closed. Enable authentication and invitation-only registration on both Render services.");
      return;
    }
    setPending(true);
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
        minLength={3} maxLength={40} required />
      <label htmlFor="signup-email">Work email</label>
      <input id="signup-email" type="email" autoComplete="email" value={email}
        onChange={event => setEmail(event.target.value)} maxLength={254} required />
      <label htmlFor="signup-password">Password (at least 12 characters)</label>
      <input id="signup-password" type="password" autoComplete="new-password" value={password}
        onChange={event => setPassword(event.target.value)} minLength={12} maxLength={128} required />
      <label htmlFor="signup-invite">Staff invitation code</label>
      <input id="signup-invite" type="password" autoComplete="off" value={invitationCode}
        onChange={event => setInvitationCode(event.target.value)} maxLength={256} required />
      {status && <p className="auth-error" role="alert">{status}</p>}
      <button type="submit" className="auth-submit" disabled={pending}>
        {pending ? "Creating account…" : "Create staff account"}
      </button>
    </form>
  );
}
