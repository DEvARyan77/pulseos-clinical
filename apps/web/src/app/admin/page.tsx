import { redirect } from "next/navigation";
import { currentStaff } from "@/lib/staff-session";
import AdminDashboard from "@/components/admin-dashboard";

export const dynamic = "force-dynamic";

export default async function AdminPage() {
  if (process.env.PULSEOS_AUTH_ENABLED !== "true") redirect("/login");
  const staff = await currentStaff();
  if (!staff) redirect("/login");
  if (!staff.roles.includes("ADMIN")) redirect("/");
  return <AdminDashboard username={staff.username} />;
}
