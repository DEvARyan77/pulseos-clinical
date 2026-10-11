import { Activity, ArrowUpRight, CalendarClock, Database, HeartPulse, LockKeyhole, ShieldCheck, UsersRound } from "lucide-react";

export const dynamic = "force-dynamic";

async function getApiStatus(): Promise<"connected" | "offline"> {
  const origin = process.env.API_INTERNAL_URL || "http://localhost:8080";
  try {
    const response = await fetch(`${origin}/api/v1/system/status`, {
      cache: "no-store",
      signal: AbortSignal.timeout(2000)
    });
    return response.ok ? "connected" : "offline";
  } catch {
    return "offline";
  }
}

const roadmap = [
  { title: "Patient workspace", description: "Structured charts, search and clinical context", icon: UsersRound, label: "Stage 03" },
  { title: "Appointment flow", description: "Scheduling and visit-status management", icon: CalendarClock, label: "Stage 03" },
  { title: "Security & identity", description: "Access controls and auditable actions", icon: LockKeyhole, label: "Stage 02" }
];

export default async function Home() {
  const api = await getApiStatus();
  return (
    <main className="shell">
      <aside className="rail">
        <div className="brand"><HeartPulse size={29} strokeWidth={2.5} /><span>Pulse<span className="accent">OS</span></span></div>
        <div className="eyebrow">CLINICAL OPERATIONS</div>
        <div className="rail-link selected"><Activity size={18} /> Overview</div>
        <div className="rail-link muted"><UsersRound size={18} /> Patients <span>SOON</span></div>
        <div className="rail-link muted"><CalendarClock size={18} /> Appointments <span>SOON</span></div>
        <div className="rail-link muted"><ShieldCheck size={18} /> Audit center <span>SOON</span></div>
        <div className="rail-footer">Portfolio environment<br/><strong>Synthetic data only</strong></div>
      </aside>

      <section className="main">
        <header className="topbar"><span>Workspace <span className="slash">/</span> Overview</span><span className="topright"><span className={api === "connected" ? "dot connected" : "dot"} /> API {api}</span></header>
        <div className="content">
          <div className="hero-label">MONITORING THE FUTURE OF CARE</div>
          <div className="hero"><div><h1>Clarity for every<br/><em>clinical moment.</em></h1><p>A modern foundation for secure care operations, designed around teams, not spreadsheets.</p><div className="hero-actions"><span className="pill"><HeartPulse size={17}/> Infrastructure ready</span><span className="tiny">Stage 01 / 05</span></div></div><div className="hero-art"><div className="pulse-halo"><HeartPulse size={90} strokeWidth={1}/></div></div></div>
          <div className="section-title"><h2>Workspace modules</h2><p>Features are introduced in separate commits.</p></div>
          <div className="cards">{roadmap.map(({title,description,icon:Icon,label})=><article className="card" key={title}><div className="card-head"><span className="icon-box"><Icon size={22}/></span><span className="tag">{label}</span></div><h3>{title}</h3><p>{description}</p><div className="card-bottom">Planned <ArrowUpRight size={16}/></div></article>)}</div>
          <div className="section-title"><h2>Platform foundations</h2><p>Verified infrastructure, not fictional clinical metrics.</p></div>
          <div className="platform"><div className="platform-line"><Database size={21}/><span><strong>PostgreSQL 17</strong><small>Internal database container</small></span></div><div className="platform-line"><Activity size={21}/><span><strong>Spring Boot API</strong><small>Live status: {api}</small></span></div><div className="platform-line"><ShieldCheck size={21}/><span><strong>Developer safety</strong><small>Local secrets and CI checks</small></span></div></div>
          <footer>PulseOS / A portfolio software project — not approved for real patient information.</footer>
        </div>
      </section>
    </main>
  );
}
