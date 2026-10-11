import Workspace from "@/components/workspace";
export const dynamic = "force-dynamic";
async function status(): Promise<"connected"|"offline"> {
  try {
    const r=await fetch(`${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/v1/system/status`,{cache:"no-store",signal:AbortSignal.timeout(2000)});
    return r.ok?"connected":"offline";
  } catch {return "offline";}
}
export default async function Home(){return <Workspace apiStatus={await status()}/>;}
