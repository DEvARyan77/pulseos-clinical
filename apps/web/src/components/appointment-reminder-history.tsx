"use client";

import {useCallback,useEffect,useState} from "react";
import styles from "./appointment-reminder-history.module.css";

type Attempt={
  appointmentId:string;
  scheduledAt:string;
  status:"CLAIMED"|"ACCEPTED"|"FAILED"|"SKIPPED";
  attemptedAt:string;
  finishedAt:string|null;
};
type History={enabled:boolean;attempts:Attempt[]};
const labels:Record<Attempt["status"],string>={
  CLAIMED:"Outcome unknown",
  ACCEPTED:"Provider accepted",
  FAILED:"Provider failed",
  SKIPPED:"Not eligible"
};

export default function AppointmentReminderHistory(){
  const [history,setHistory]=useState<History|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState("");
  const reload=useCallback(async()=>{
    try{
      const response=await fetch("/api/demo-appointments/reminders",{cache:"no-store"});
      const data=await response.json() as History&{error?:string};
      if(!response.ok||!Array.isArray(data.attempts))
        throw new Error(data.error??"Unable to load reminder history");
      setHistory(data);setError("");
    }catch(cause){
      setError(cause instanceof Error?cause.message:"Unable to load reminders");
    }finally{setLoading(false);}
  },[]);
  useEffect(()=>{void reload();},[reload]);
  return <section className={styles.panel} aria-label="Appointment reminder history">
    <div className={styles.heading}>
      <div><h2>Automatic appointment reminders</h2>
        <p>One QR appointment email is attempted 1–24 hours before each eligible demo visit.</p></div>
      <button type="button" onClick={()=>{setLoading(true);void reload();}} disabled={loading}>
        {loading?"Loading…":"Refresh history"}
      </button>
    </div>
    {history&&<p className={styles.info}>
      {history.enabled?
        "Reminders enabled. A running API checks every 5 minutes.":
        "Reminders are off. To enable them, set PULSEOS_APPOINTMENT_REMINDERS_ENABLED=true on the API service."}
    </p>}
    <p className={styles.note}>Only approved test recipients are eligible. Provider acceptance does not confirm inbox delivery. Unknown results are not automatically retried to prevent duplicate emails.</p>
    {error&&<p className={styles.error} role="alert">{error}</p>}
    {!history&&loading&&<p className={styles.note}>Loading reminder history…</p>}
    {history?.attempts.length===0&&<p className={styles.empty}>No reminder attempts recorded yet.</p>}
    {history&&history.attempts.length>0&&<div className={styles.scroll}>
      <table className={styles.table}>
        <thead><tr><th>Visit time</th><th>Attempted</th><th>Appointment</th><th>Result</th></tr></thead>
        <tbody>{history.attempts.map((attempt,index)=><tr
          key={attempt.appointmentId+"-"+attempt.scheduledAt+"-"+index}>
          <td>{new Date(attempt.scheduledAt).toLocaleString()}</td>
          <td>{new Date(attempt.attemptedAt).toLocaleString()}</td>
          <td><code>{attempt.appointmentId.slice(0,8)}</code></td>
          <td><span className={styles.status} data-status={attempt.status}>
            {labels[attempt.status]??attempt.status}
          </span></td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>;
}
