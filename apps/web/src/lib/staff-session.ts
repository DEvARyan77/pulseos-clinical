import { cookies } from "next/headers";

export type StaffIdentity = { username: string; roles: string[] };

/**
 * A slow, cold-started backend must not be mistaken for an expired cookie.
 * Retry transient timeouts/server failures; never retry a 401/403 identity failure.
 */
export async function currentStaff(): Promise<StaffIdentity | null> {
  const token = (await cookies()).get("pulseos-session")?.value;
  if (!token) return null;
  const url = `${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/me`;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const response = await fetch(url, {
        headers: { Authorization: `Bearer ${token}` },
        cache: "no-store",
        signal: AbortSignal.timeout(30000),
      });
      if (response.status === 401 || response.status === 403) return null;
      if (!response.ok) continue;
      const data: unknown = await response.json();
      if (!data || typeof data !== "object") return null;
      const identity = data as { username?: unknown; roles?: unknown };
      if (typeof identity.username !== "string" || !Array.isArray(identity.roles) ||
          !identity.roles.every((role: unknown) =>
            role === "ADMIN" || role === "CLINICIAN" || role === "PATIENT")) return null;
      return { username: identity.username, roles: identity.roles as string[] };
    } catch {
      // Render free instances may be waking; retry transient network failures.
    }
  }
  return null;
}
