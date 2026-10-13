"use client";
import Link from "next/link";
import {FormEvent,useCallback,useEffect,useMemo,useState} from "react";
import {HeartPulse,MessageCircle,RefreshCw,Send,ShieldCheck,UserRound} from "lucide-react";
import ThemeSwitcher from "./theme-switcher";
import SignOutButton from "./sign-out-button";
import styles from "./staff-chat.module.css";

type Contact={username:string;role:string};
type Message={id:string;sender:string;recipient:string;content:string;createdAt:string;readAt:string|null};
async function json<T>(url:string,options?:RequestInit):Promise<T>{
 const response=await fetch(url,{...options,cache:"no-store"});
 const data=await response.json() as T&{error?:string};
 if(!response.ok)throw new Error(data.error??"Message service unavailable");
 return data;
}
export default function StaffChat({username,role,initialTarget}:{username:string;role:"ADMIN"|"CLINICIAN";initialTarget:string|null}){
 const [contacts,setContacts]=useState<Contact[]>([]);
 const [messages,setMessages]=useState<Message[]>([]);
 const [target,setTarget]=useState<string|null>(null);
 const [draft,setDraft]=useState("");
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState("");
 const reload=useCallback(()=>{
   return Promise.all([
     json<Contact[]>("/api/staff-chat/contacts"),
     json<Message[]>("/api/staff-chat/messages")
   ]).then(([people,history])=>{
     setContacts(people);setMessages(history);
     setTarget(previous=>previous&&people.some(p=>p.username===previous)?previous:
       people.find(p=>p.username===initialTarget)?.username??people[0]?.username??null);
     setError("");
   }).catch((cause:unknown)=>setError(cause instanceof Error?cause.message:"Cannot load messages"));
 },[initialTarget]);
 useEffect(()=>{
   void reload();
   const timer=setInterval(()=>{void reload();},15000);
   return ()=>clearInterval(timer);
 },[reload]);
 const active=contacts.find(p=>p.username===target);
 const visible=useMemo(()=>messages
   .filter(m=>(m.sender===username&&m.recipient===target)||(m.sender===target&&m.recipient===username))
   .slice().reverse(),[messages,username,target]);
 const unread=messages.filter(m=>m.recipient===username&&!m.readAt).length;
 async function send(event:FormEvent<HTMLFormElement>){
   event.preventDefault();
   if(!target||!draft.trim()||busy)return;
   setBusy(true);setError("");
   try {
     await json<Message>("/api/staff-chat/messages",{
       method:"POST",headers:{"Content-Type":"application/json"},
       body:JSON.stringify({to:target,content:draft.trim()})
     });
     setDraft("");await reload();
   }catch(cause){setError(cause instanceof Error?cause.message:"Message failed");}
   finally{setBusy(false);}
 }
 async function markRead(message:Message){
   if(message.recipient!==username||message.readAt)return;
   try{
     await json<Message>(`/api/staff-chat/messages/${message.id}/read`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:"{}"});
     await reload();
   }catch(cause){setError(cause instanceof Error?cause.message:"Unable to mark read");}
 }
 return <main className={styles.page}>
   <header className={styles.header}>
     <div className={styles.brand}><HeartPulse size={24}/>Pulse<b>OS</b></div>
     <nav><Link href={role==="ADMIN"?"/admin":"/"}>Dashboard</Link><span>Staff chat</span></nav>
     <div className={styles.tools}><ThemeSwitcher/><SignOutButton/></div>
   </header>
   <div className={styles.container}>
     <div className={styles.title}><div><p>STAFF COMMUNICATION</p><h1>Team messages</h1>
       <span>Direct conversations with enabled clinicians and supervisors</span></div>
       <button type="button" onClick={()=>void reload()}><RefreshCw size={16}/> Refresh</button>
     </div>
     <p className={styles.safety}><ShieldCheck size={17}/> Demo messages only. Do not send identifiable patient information or real clinical advice.</p>
     {error&&<p className={styles.error} role="alert">{error}</p>}
     <div className={styles.layout}>
       <aside className={styles.directory}><h2><UserRound size={16}/> Team directory <span>{unread? `· ${unread} unread`:""}</span></h2>
         {contacts.map(p=><button key={p.username} type="button" className={target===p.username?styles.chosen:""}
           aria-pressed={target===p.username} onClick={()=>setTarget(p.username)}>
           <strong>{p.username}</strong><small>{p.role==="ADMIN"?"Supervisor / Admin":"Clinician / Doctor"}</small>
           {messages.some(m=>m.sender===p.username&&m.recipient===username&&!m.readAt)&&<i aria-label="Unread messages"/>}
         </button>)}
         {!contacts.length&&<p className={styles.empty}>No other enabled staff accounts are available.</p>}
       </aside>
       <section className={styles.conversation}>
         {active?<><div className={styles.recipient}><MessageCircle size={20}/><div><strong>{active.username}</strong><small>{active.role==="ADMIN"?"Supervisor / Admin":"Clinician / Doctor"}</small></div></div>
           <div className={styles.history} aria-label="Message history" aria-live="polite">
             {!visible.length&&<p className={styles.empty}>No messages yet. Start a fictional-data conversation.</p>}
             {visible.map(m=><div key={m.id} className={m.sender===username?styles.outgoing:styles.incoming}>
                <p>{m.content}</p>
                <div><time>{new Date(m.createdAt).toLocaleString()}</time>
                  {m.recipient===username&&!m.readAt&&<button type="button" onClick={()=>void markRead(m)}>Mark read</button>}
                </div>
             </div>)}
           </div>
           <form className={styles.compose} onSubmit={send}>
             <label htmlFor="chat-message">New message (demo only)</label>
             <textarea id="chat-message" maxLength={1200} rows={3} placeholder="Write a short message to your colleague…"
               value={draft} onChange={e=>setDraft(e.target.value)} required/>
             <div><small>{draft.length}/1200 · Never share real patient data</small>
               <button type="submit" disabled={busy||!draft.trim()}><Send size={16}/>{busy?"Sending…":"Send message"}</button></div>
           </form></>:<div className={styles.empty}>Select an available staff member to begin.</div>}
       </section>
     </div>
   </div>
 </main>;
}
