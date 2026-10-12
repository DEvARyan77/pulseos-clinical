"use client";
import Link from "next/link";
import {FormEvent,useCallback,useEffect,useState} from "react";
import {Activity,CalendarDays,ClipboardList,FileText,HeartPulse,History,Plus,Search,ShieldCheck,UserRound} from "lucide-react";
import ThemeSwitcher from "./theme-switcher";
import SignOutButton from "./sign-out-button";
import styles from "./registry-workspace.module.css";

type Status="WAITING"|"IN_CARE"|"COMPLETED";
type PatientInput={
 fullName:string;age:number;gender:string;reason:string;room:string;status:Status;
 bp:string;heartRate:number|null;oxygen:number|null;temperature:string;
 allergies:string;diagnoses:string;medications:string;
};
type Patient=PatientInput&{id:string;createdAt:string;updatedAt:string;version:number};
type Note={id:string;content:string;createdAt:string;owner:string};
type Audit={id:string;action:string;actor:string;happenedAt:string};
type Tab="Overview"|"Notes"|"History";
const blank:PatientInput={fullName:"",age:35,gender:"Unspecified",reason:"General consultation",room:"Unassigned",status:"WAITING",bp:"—",heartRate:null,oxygen:null,temperature:"—",allergies:"",diagnoses:"",medications:""};
const fields:[keyof PatientInput,string,number][]=[
 ["fullName","Name (fictional only)",120],["gender","Gender",32],["reason","Visit reason",240],
 ["room","Room / location",32],["bp","Blood pressure",16],["temperature","Temperature",16],
 ["allergies","Allergies",500],["diagnoses","Diagnoses",500],["medications","Medications",500]
];
const statuses:Record<Status,string>={WAITING:"Waiting",IN_CARE:"In care",COMPLETED:"Completed"};
function readable(iso:string){return new Date(iso).toLocaleString();}
async function jsonRequest<T>(url:string,init?:RequestInit):Promise<T>{
 const response=await fetch(url,{...init,cache:"no-store"});
 const body=await response.json() as T&{error?:string;message?:string};
 if(!response.ok)throw new Error(body.error??body.message??"Request failed");
 return body;
}
export default function RegistryWorkspace({apiStatus}:{apiStatus:"connected"|"offline"}){
 const [records,setRecords]=useState<Patient[]>([]);
 const [selected,setSelected]=useState<string|null>(null);
 const [search,setSearch]=useState("");
 const [tab,setTab]=useState<Tab>("Overview");
 const [mode,setMode]=useState<"view"|"new"|"edit">("view");
 const [form,setForm]=useState<PatientInput>({...blank});
 const [notes,setNotes]=useState<Note[]>([]);
 const [history,setHistory]=useState<Audit[]>([]);
 const [noteText,setNoteText]=useState("");
 const [busy,setBusy]=useState(false);
 const [loading,setLoading]=useState(true);
 const [message,setMessage]=useState("");
 const [error,setError]=useState("");
 const [mobileQueue,setMobileQueue]=useState(false);

 const active=records.find(p=>p.id===selected)??null;
 const reload=useCallback(async()=>{
  try{
   const data=await jsonRequest<Patient[]>("/api/patients");
   setRecords(data);
   setSelected(current=>current&&data.some(p=>p.id===current)?current:data[0]?.id??null);
  }catch(cause){setError(cause instanceof Error?cause.message:"Cannot load patient registry");}
  finally{setLoading(false);}
 },[]);
 useEffect(()=>{void reload();},[reload]);
 useEffect(()=>{
  if(!selected){setNotes([]);setHistory([]);return;}
  let alive=true;
  Promise.all([
   jsonRequest<Note[]>(`/api/patients/${selected}/notes`),
   jsonRequest<Audit[]>(`/api/patients/${selected}/history`)
  ]).then(([n,h])=>{if(alive){setNotes(n);setHistory(h);}})
   .catch(cause=>{if(alive)setError(cause instanceof Error?cause.message:"Cannot load visit timeline");});
  return()=>{alive=false;};
 },[selected]);

 function startNew(){setForm({...blank});setMode("new");setError("");setMessage("");setMobileQueue(false);}
 function startEdit(){if(!active)return;const {id,createdAt,updatedAt,version,...data}=active;void id;void createdAt;void updatedAt;void version;setForm({...data});setMode("edit");setError("");setMessage("");}
 function editValue(key:keyof PatientInput,value:string){
  setForm(old=>({...old,[key]:key==="age"?Number(value):key==="heartRate"||key==="oxygen"?(value===""?null:Number(value)):value}));
 }
 async function savePatient(event:FormEvent<HTMLFormElement>){
  event.preventDefault();setError("");setMessage("");setBusy(true);
  try{
   const url=mode==="new"?"/api/patients":`/api/patients/${selected}`;
   const saved=await jsonRequest<Patient>(url,{method:mode==="new"?"POST":"PUT",
    headers:{"Content-Type":"application/json"},body:JSON.stringify(form)});
   await reload();setSelected(saved.id);setMode("view");setMessage(mode==="new"?"Patient created and saved.":"Patient record updated.");
   const h=await jsonRequest<Audit[]>(`/api/patients/${saved.id}/history`);
   setHistory(h);
  }catch(cause){setError(cause instanceof Error?cause.message:"Cannot save patient");}
  finally{setBusy(false);}
 }
 async function addNote(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(!active||!noteText.trim())return;
  setBusy(true);setError("");setMessage("");
  try{
   await jsonRequest<Note>(`/api/patients/${active.id}/notes`,{
    method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({content:noteText.trim()})
   });
   setNoteText("");
   const [n,h]=await Promise.all([
    jsonRequest<Note[]>(`/api/patients/${active.id}/notes`),
    jsonRequest<Audit[]>(`/api/patients/${active.id}/history`)
   ]);setNotes(n);setHistory(h);setMessage("Visit note saved and recorded in history.");
  }catch(cause){setError(cause instanceof Error?cause.message:"Cannot save note");}
  finally{setBusy(false);}
 }
 const shown=records.filter(p=>(p.fullName+" "+p.reason+" "+p.room).toLowerCase().includes(search.trim().toLowerCase()));
 return <main className="app">
   <header className="topbar">
    <div className="logo"><HeartPulse size={25}/><span>Pulse<b>OS</b></span></div>
    <nav className="topnav" aria-label="Navigation"><Link href="/">Patient registry</Link><Link href="/appointments">Appointments</Link></nav>
    <div className="topactions"><span className="demo-label"><ShieldCheck size={13}/> TEST RECORDS ONLY</span><ThemeSwitcher/>
      <span className="api-state"><i className={apiStatus==="connected"?"connected":""}/> API {apiStatus}</span><SignOutButton/></div>
   </header>
   <div className="mobile-queue"><button type="button" onClick={()=>setMobileQueue(!mobileQueue)}>Patients</button><span>Clinical workspace</span></div>
   <div className="columns">
    <aside className={mobileQueue?"queue open":"queue"} aria-label="Patient registry">
     <div className="queue-heading"><div><h2>Patient registry <small>{records.length}</small></h2><p>Account-scoped records</p></div><UserRound size={18}/></div>
     <label className="search-field"><Search size={16}/><input type="search" aria-label="Search patients" placeholder="Search patients…" value={search} onChange={e=>setSearch(e.target.value)}/></label>
     <div className={styles.queueActions}><button type="button" onClick={startNew}><Plus size={16}/> Add patient</button><Link href="/appointments"><CalendarDays size={15}/> Appointments</Link></div>
     <div className="queue-rows">
      {loading?<p className="empty">Loading records…</p>:shown.length===0?<p className="empty">{records.length?"No matching records.":"No patients yet. Add a fictional patient to begin."}</p>:
      shown.map(p=><button type="button" key={p.id} className={active?.id===p.id?"patient-row picked":"patient-row"} aria-pressed={active?.id===p.id} onClick={()=>{setSelected(p.id);setMode("view");setTab("Overview");setError("");setMessage("");setMobileQueue(false);}}>
       <span className="patient-time">{statuses[p.status]}</span><span className="patient-list-detail"><strong>{p.fullName}</strong><small>{p.age} years · {p.reason}</small><small>{p.room}</small></span>
      </button>)}
     </div>
     <div className="queue-bottom"><ShieldCheck size={15}/> Portfolio environment · Fictional data only</div>
    </aside>
    <section className="chart">
     <div className={styles.alerts}>{error&&<div className={styles.error} role="alert">{error}</div>}{message&&<div className={styles.success} role="status">{message}</div>}</div>
     {mode!=="view"?<>
       <div className="section-title"><h2>{mode==="new"?"Register fictional patient":"Edit patient record"}</h2><button type="button" className={styles.textButton} onClick={()=>setMode("view")}>Cancel</button></div>
       <form onSubmit={savePatient} className={styles.form}>
        <p className={styles.helper}>Use invented names and clinical details only. Saved values are persisted to PostgreSQL.</p>
        <div className={styles.formGrid}>
         {fields.map(([key,label,max])=><label key={key}>{label}
           <input type="text" value={String(form[key]??"")} required={["fullName","gender","reason","room"].includes(key)}
            maxLength={max} onChange={e=>editValue(key,e.target.value)}/>
         </label>)}
         <label>Age <input type="number" min="0" max="120" value={form.age} onChange={e=>editValue("age",e.target.value)} required/></label>
         <label>Heart rate (bpm)<input type="number" min="0" max="250" value={form.heartRate??""} onChange={e=>editValue("heartRate",e.target.value)}/></label>
         <label>Oxygen saturation (%)<input type="number" min="0" max="100" value={form.oxygen??""} onChange={e=>editValue("oxygen",e.target.value)}/></label>
         <label>Status<select value={form.status} onChange={e=>editValue("status",e.target.value)}>{Object.entries(statuses).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>
        </div>
        <button disabled={busy} className={styles.primary} type="submit">{busy?"Saving…":mode==="new"?"Create patient":"Save patient changes"}</button>
       </form>
      </>:active?<>
       <div className="chart-heading">
        <div className="patient-portrait">{active.fullName.split(" ").map(x=>x[0]).slice(0,2).join("").toUpperCase()}</div>
        <div className="patient-info"><span className="eyebrow">ACCOUNT PATIENT RECORD</span><h1>{active.fullName}</h1><p>{active.age} years · {active.gender}</p><div className="patient-chips"><span>{statuses[active.status]}</span><span>{active.reason}</span></div></div>
        <button type="button" className={styles.primarySmall} onClick={startEdit}>Edit patient</button>
       </div>
       <nav className="chart-tabs" aria-label="Patient sections">{(["Overview","Notes","History"] as const).map(t=><button type="button" key={t} className={tab===t?"current":""} onClick={()=>setTab(t)}>{t}</button>)}</nav>
       {tab==="Overview"&&<>
        <div className="section-title"><h2>Clinical overview</h2><span>Last edited {readable(active.updatedAt)}</span></div>
        <div className="overview-grid">
         <article className="info-card"><div className="card-title"><Activity size={17}/><h3>Vitals</h3></div><dl className="vitals"><div><dt>Blood pressure</dt><dd>{active.bp||"—"}</dd></div><div><dt>Heart rate</dt><dd>{active.heartRate??"—"}</dd></div><div><dt>SpO₂</dt><dd>{active.oxygen===null?"—":active.oxygen+"%"}</dd></div><div><dt>Temperature</dt><dd>{active.temperature||"—"}</dd></div></dl></article>
         <article className="info-card"><div className="card-title"><ShieldCheck size={17}/><h3>Allergies</h3></div><p className={styles.cardText}>{active.allergies||"No entries"}</p></article>
         <article className="info-card"><div className="card-title"><ClipboardList size={17}/><h3>Diagnoses</h3></div><p className={styles.cardText}>{active.diagnoses||"No entries"}</p></article>
         <article className="info-card"><div className="card-title"><FileText size={17}/><h3>Medications</h3></div><p className={styles.cardText}>{active.medications||"No entries"}</p></article>
        </div>
        <div className="section-title"><h2>Patient activity</h2><span>{history.length} recorded changes</span></div>
        <div className={styles.panel}>{history.length?history.slice(0,5).map(h=><div className={styles.event} key={h.id}><strong>{h.action.replaceAll("_"," ")}</strong><small>{readable(h.happenedAt)} · {h.actor}</small></div>):<p className={styles.helper}>No activity yet.</p>}</div>
       </>}
       {tab==="Notes"&&<section className={styles.panel}>
        <h2>Visit notes</h2><p className={styles.helper}>Append-only notes. Updates are recorded in the patient timeline.</p>
        <form onSubmit={addNote} className={styles.noteForm}>
         <label htmlFor="new-note">New visit note</label><textarea id="new-note" minLength={1} maxLength={4000} value={noteText} onChange={e=>setNoteText(e.target.value)} rows={5} required placeholder="Enter fictional encounter notes only…"/>
         <button disabled={busy||!noteText.trim()} type="submit" className={styles.primary}>{busy?"Saving…":"Add visit note"}</button>
        </form>
        <div className={styles.notes}>{notes.map(n=><article key={n.id} className={styles.note}><small>{readable(n.createdAt)} · {n.owner}</small><p>{n.content}</p></article>)}{!notes.length&&<p className={styles.helper}>No visit notes yet.</p>}</div>
       </section>}
       {tab==="History"&&<section className={styles.panel}><h2>Audit history</h2><p className={styles.helper}>Actions are timestamped and attributed to the signed-in staff account.</p>
        {history.map(h=><div className={styles.event} key={h.id}><strong>{h.action.replaceAll("_"," ")}</strong><small>{readable(h.happenedAt)} · {h.actor}</small></div>)}
       </section>}
      </>:<div className={styles.emptyState}><UserRound size={32}/><h2>No patient selected</h2><p>Add a fictional patient to start creating records and tracking visits.</p><button type="button" className={styles.primary} onClick={startNew}>Add first patient</button></div>}
    </section>
    <aside className="sidebar"><div className="sidebar-tabs"><span className="active"><History size={15}/> Activity</span></div>
     <div className="side-section"><div className="side-title"><h2>Record tracking</h2><span>Live data</span></div>
       {active?<div className={styles.metric}><strong>{active.fullName}</strong><small>{statuses[active.status]} · {active.room}</small><small>Created {readable(active.createdAt)}</small></div>:<p className={styles.helper}>Select a record to see its activity.</p>}
     </div>
     <div className="side-section"><div className="side-title"><h2>Quick navigation</h2></div><div className={styles.links}>
       <Link href="/appointments"><CalendarDays size={16}/> Book an appointment</Link>
       <button type="button" onClick={()=>{if(active){setMode("view");setTab("Notes");}}} disabled={!active}><FileText size={16}/> Visit notes</button>
       <button type="button" onClick={()=>{if(active){setMode("view");setTab("History");}}} disabled={!active}><History size={16}/> View audit trail</button>
     </div></div>
     <div className="privacy-note"><ShieldCheck size={16}/> Portfolio test environment. Do not store identifiable patients or real medical records.</div>
    </aside>
   </div>
  </main>;
}
