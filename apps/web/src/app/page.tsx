import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import Workspace from "@/components/workspace";

export const dynamic = "force-dynamic";

const apiUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
const authEnabled = process.env.PULSEOS_AUTH_ENABLED === "true";

async function apiStatus(): Promise<"connected" | "offline"> {
  try {
    const response = await fetch(`${apiUrl}/api/v1/system/status`, {
      cache: "no-store",
      signal: AbortSignal.timeout(2000),
    });
    return response.ok ? "connected" : "offline";
  } catch {
    return "offline";
  }
}

async function authorized(): Promise<boolean> {
  const token = (await cookies()).get("pulseos-session")?.value;
  if (!token) return false;
  try {
    const response = await fetch(`${apiUrl}/api/v1/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

export default async function Home() {
  if (authEnabled && !(await authorized())) redirect("/login");
  return <Workspace apiStatus={await apiStatus()} authEnabled={authEnabled} />;
}
