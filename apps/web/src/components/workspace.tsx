"use client";
import {useMemo,useState} from "react";
import {Activity,CalendarDays,CheckCircle2,ClipboardList,Clock3,FileText,FlaskConical,HeartPulse,Menu,Pill,Search,ShieldCheck} from "lucide-react";
import ThemeSwitcher from "./theme-switcher";
import SignOutButton from "./sign-out-button";
import {patients,events,earlierVisits,initialTasks} from "@/lib/demo-data";
type Tab="Overview"|"Notes"|"Orders"|"Results";
const tabs:Tab[]=["Overview","Notes","Orders","Results"];
export default function Workspace({apiStatus,authEnabled}:{apiStatus:"connected"|"offline";authEnabled:boolean}){
 const [patientId,setPatientId]=useState(patients[0].id);
 const [search,setSearch]=useState("");
 const [filter,setFilter]=useState("All");
 const [tab,setTab]=useState<Tab>("Overview");
 const [tasks,setTasks]=useState(initialTasks);
 const [queueOpen,setQueueOpen]=useState(false);
 const patient=patients.find(p=>p.id===patientId)??patients[0];
 const visible=useMemo(()=>patients.filter(p=>
   (p.name+" "+p.id+" "+p.reason).toLowerCase().includes(search.trim().toLowerCase()) &&
   (filter==="All"||p.status===filter)),[search,filter]);
 const pending=tasks.filter(t=>!t.done).length;
 return <main className="app">
  <header className="topbar">
   <div className="logo"><HeartPulse size={25}/><span>Pulse<b>OS</b></span></div>
   <nav className="topnav" aria-label="Main navigation"><span className="active">Workspace</span><span>Patients</span><span>Appointments</span><span>Care teams</span><span>Reports</span></nav>
   <div className="topactions"><span className="demo-label"><ShieldCheck size={13}/> SYNTHETIC DEMO</span><ThemeSwitcher/><span className="api-state"><i className={apiStatus==="connected"?"connected":""}/> API {apiStatus}</span><div className="avatar-small" title="Demo clinician">RC</div>{authEnabled && <SignOutButton/>}</div>
  </header>
  <div className="mobile-queue"><button type="button" onClick={()=>setQueueOpen(!queueOpen)} aria-expanded={queueOpen} aria-controls="patient-queue"><Menu size={17}/> Patient queue</button><span>Clinical workspace</span></div>
  <div className="columns">
   <aside className={queueOpen?"queue open":"queue"} id="patient-queue" aria-label="Patient queue">
    <div className="queue-heading"><div><h2>Patient queue <small>{patients.length}</small></h2><p>Today’s appointments</p></div><CalendarDays size={18}/></div>
    <label className="search-field"><Search size={16}/><span className="sr-only">Search demo patients</span><input type="search" placeholder="Search patients..." value={search} onChange={e=>setSearch(e.target.value)}/></label>
    <div className="filter-row">{["All","Checked in","In room","Scheduled"].map(status=><button key={status} type="button" aria-pressed={status===filter} className={status===filter?"selected":""} onClick={()=>setFilter(status)}>{status}</button>)}</div>
    <div className="queue-rows">{visible.length===0?<p className="empty">No matches</p>:visible.map(p=><button key={p.id} type="button" className={patient.id===p.id?"patient-row picked":"patient-row"} aria-pressed={patient.id===p.id} onClick={()=>{setPatientId(p.id);setTab("Overview");setQueueOpen(false);}}><span className="patient-time">{p.time}</span><span className="patient-list-detail"><strong>{p.name}</strong><small>{p.age} · {p.reason}</small><small>{p.room}</small></span><span className={"status-pill "+p.status.toLowerCase().replaceAll(" ","-")}>{p.status}</span></button>)}</div>
    <div className="queue-bottom"><ShieldCheck size={15}/> Fictional records only</div>
   </aside>
   <section className="chart" aria-label="Selected patient chart">
    <div className="chart-heading"><div className="patient-portrait">{patient.initials}</div><div className="patient-info"><span className="eyebrow">DEMO PATIENT CHART</span><h1>{patient.name}</h1><p>{patient.age} years · {patient.gender} · {patient.id}</p><div className="patient-chips"><span>Follow-up</span><span>{patient.reason}</span></div></div><div className="room-indicator"><span>● {patient.room}</span><small>{patient.status}</small></div></div>
    <nav className="chart-tabs" aria-label="Patient record tabs">{tabs.map(t=><button key={t} type="button" className={t===tab?"current":""} aria-current={t===tab?"page":undefined} onClick={()=>setTab(t)}>{t}</button>)}</nav>
    {tab==="Overview"?<>
     <div className="section-title"><h2>Clinical overview</h2><span>Sample values · not verified</span></div>
     <div className="overview-grid">
      <article className="info-card"><div className="card-title"><Activity size={17}/><h3>Vitals</h3></div><dl className="vitals"><div><dt>Blood pressure</dt><dd>{patient.bp}</dd></div><div><dt>Heart rate</dt><dd>{patient.heartRate} bpm</dd></div><div><dt>Temperature</dt><dd>{patient.temperature}</dd></div><div><dt>SpO₂</dt><dd>{patient.oxygen}</dd></div></dl><small className="card-caption">Illustrative observation</small></article>
      <article className="info-card"><div className="card-title warning"><ShieldCheck size={17}/><h3>Allergies</h3></div><ul className="facts">{patient.allergies.map(a=><li key={a}>{a}</li>)}</ul><small className="card-caption">Example data</small></article>
      <article className="info-card"><div className="card-title"><ClipboardList size={17}/><h3>Diagnoses</h3></div><ul className="facts">{patient.diagnoses.map(d=><li key={d}>{d}</li>)}</ul><small className="card-caption">Not clinically verified</small></article>
      <article className="info-card"><div className="card-title"><Pill size={17}/><h3>Medications</h3></div><ul className="facts">{patient.medications.map(m=><li key={m}>{m}</li>)}</ul><small className="card-caption">Demo chart information</small></article>
     </div>
     <div className="section-title"><h2>Visit workspace</h2><span>Read-only note preview</span></div>
     <article className="visit-note"><div className="note-title"><span><FileText size={16}/> Visit note</span><small>Sample record</small></div><div className="note-body"><h3>Reason for visit</h3><p>{patient.reason}</p><h3>Subjective</h3><p>Fictional encounter created exclusively for demonstrating the workspace. This content does not describe a real person.</p><h3>Assessment &amp; plan</h3><p>Clinician review is required. No care recommendation is provided by this demo.</p></div></article>
    </>:<article className="tab-detail">
      {tab==="Notes"?<FileText size={26}/>:tab==="Orders"?<ClipboardList size={26}/>:<FlaskConical size={26}/>}
      <h2>{tab==="Notes"?"Visit notes":tab==="Orders"?"Orders workspace":"Clinical results"}</h2>
      <p>{tab==="Notes"?"A preview of structured clinical documentation. Editing and saving require staff authentication and audited backend APIs.":tab==="Orders"?"No orders are available in the demo. Order placement is disabled until authorization and validation are implemented.":"No test results are stored. Laboratory and imaging records will require authenticated, controlled access."}</p>
      <div className="placeholder"><CheckCircle2 size={16}/> Prototype view · No clinical action available</div>
    </article>}
   </section>
   <aside className="sidebar" aria-label="Activity and tasks">
    <div className="sidebar-tabs"><span className="active"><Clock3 size={15}/> Timeline</span><span>Tasks <b>{pending}</b></span></div>
    <section className="side-section"><div className="side-title"><h2>Today’s activity</h2><span>Demo events</span></div><ol className="timeline">{events.map(event=><li key={event.title}><time>{event.time}</time><strong>{event.title}</strong><p>{event.description}</p></li>)}</ol></section>
    <section className="side-section"><div className="side-title"><h2>Previous visits</h2><span>Examples</span></div><div className="past-visits">{earlierVisits.map(v=><div key={v.date}><span>{v.date}</span><div><strong>{v.name}</strong><small>{v.detail}</small></div></div>)}</div></section>
    <section className="side-section"><div className="side-title"><h2>Visit tasks</h2><span>{tasks.length-pending}/{tasks.length} done</span></div><div className="task-list">{tasks.map(t=><label key={t.id}><input type="checkbox" checked={t.done} onChange={()=>setTasks(old=>old.map(x=>x.id===t.id?{...x,done:!x.done}:x))}/><span className={t.done?"done":""}>{t.name}</span></label>)}</div><p className="footnote">Tasks reset on page reload.</p></section>
    <section className="side-section"><div className="side-title"><h2>Upcoming appointment</h2><CalendarDays size={16}/></div><div className="appointment"><span>06<strong>NOV</strong></span><div><strong>Follow-up consultation</strong><small>9:00 AM · Demo schedule</small></div></div></section>
    <div className="privacy-note"><ShieldCheck size={16}/> Prototype records only. No clinical data persistence or audited access yet.</div>
   </aside>
  </div>
 </main>;
}
