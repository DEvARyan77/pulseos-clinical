"use client";

import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { useState } from "react";

export default function SignOutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  async function logout() {
    setPending(true);
    try {
      const response = await fetch("/api/auth/logout", { method: "POST" });
      if (!response.ok) throw new Error("Sign out failed");
      router.replace("/login");
      router.refresh();
    } catch {
      setPending(false);
    }
  }
  return <button className="signout" type="button" disabled={pending} onClick={logout} title="Sign out of PulseOS">
    <LogOut size={16} /><span>{pending ? "Signing out…" : "Sign out"}</span>
  </button>;
}
