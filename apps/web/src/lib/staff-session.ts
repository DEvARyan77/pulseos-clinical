import { cookies } from "next/headers";

export type StaffIdentity = { username: string; roles: string[] };

export async function currentStaff(): Promise<StaffIdentity | null> {
  const token = (await cookies()).get("pulseos-session")?.value;
  if (!token) return null;
  try {
    const response = await fetch(
      `${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/auth/me`,
      { headers: { Authorization: `Bearer ${token}` }, cache: "no-store",
        signal: AbortSignal.timeout(7000) }
    );
    if (!response.ok) return null;
    const data = await response.json() as { username?: unknown; roles?: unknown };
    if (typeof data.username !== "string" || !Array.isArray(data.roles) ||
        !data.roles.every((r: unknown) => r === "ADMIN" || r === "CLINICIAN")) return null;
    return { username: data.username, roles: data.roles as string[] };
  } catch {
    return null;
  }
}
