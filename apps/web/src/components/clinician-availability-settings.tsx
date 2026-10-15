"use client";

import {FormEvent,useState} from "react";
import {ClinicianAvailability,localTimeZone,WEEKDAYS,Weekday} from "@/lib/clinician-availability";
import styles from "./clinician-availability-settings.module.css";

type Props={availability:ClinicianAvailability|null;onSaved:(settings:ClinicianAvailability)=>void};
type Draft={zoneId:string;workDays:Weekday[];startTime:string;endTime:string;
  breakStartTime:string;breakEndTime:string;blockedDates:string};
const defaults=():Draft=>({zoneId:localTimeZone(),workDays:[...WEEKDAYS.slice(0,5)],
  startTime:"09:00",endTime:"18:00",breakStartTime:"13:00",breakEndTime:"14:00",
  blockedDates:""});

export default function ClinicianAvailabilitySettings({availability,onSaved}:Props){
  if(!availability)return <section className={styles.panel}><p>Loading availability settings…</p></section>;
  return <AvailabilityForm availability={availability} onSaved={onSaved}/>;
}
function AvailabilityForm({availability,onSaved}:{availability:ClinicianAvailability;onSaved:Props["onSaved"]}){
  const [draft,setDraft]=useState<Draft>(()=>availability.configured?{
      zoneId:availability.zoneId,workDays:availability.workDays,
      startTime:availability.startTime.slice(0,5),endTime:availability.endTime.slice(0,5),
      breakStartTime:availability.breakStartTime?.slice(0,5)??"",
      breakEndTime:availability.breakEndTime?.slice(0,5)??"",
      blockedDates:availability.blockedDates.join("\n")
    }:defaults());
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState("");
  const [message,setMessage]=useState("");
  function toggle(day:Weekday,checked:boolean){
    setDraft(current=>({...current,workDays:checked?
      [...new Set([...current.workDays,day])].sort((a,b)=>WEEKDAYS.indexOf(a)-WEEKDAYS.indexOf(b)):
      current.workDays.filter(other=>other!==day)}));
  }
  async function save(event:FormEvent<HTMLFormElement>){
    event.preventDefault();setError("");setMessage("");
    const blockedDates=draft.blockedDates.split(/[\s,;]+/).map(s=>s.trim()).filter(Boolean);
    if(!draft.workDays.length){setError("Choose at least one working day.");return;}
    if(blockedDates.length>90||new Set(blockedDates).size!==blockedDates.length||
       blockedDates.some(d=>!/^\d{4}-\d{2}-\d{2}$/.test(d))){
      setError("Enter up to 90 unique dates as YYYY-MM-DD.");return;
    }
    if(Boolean(draft.breakStartTime)!==Boolean(draft.breakEndTime)){
      setError("Set both break times or clear both.");return;
    }
    try{new Intl.DateTimeFormat("en-US",{timeZone:draft.zoneId});}
    catch{setError("Enter a valid time zone such as Asia/Kolkata.");return;}
    setSaving(true);
    try{
      const response=await fetch("/api/demo-appointments/availability",{
        method:"PUT",headers:{"Content-Type":"application/json"},
        body:JSON.stringify({...draft,
          breakStartTime:draft.breakStartTime||null,
          breakEndTime:draft.breakEndTime||null,blockedDates})
      });
      const result=await response.json() as ClinicianAvailability&{error?:string};
      if(!response.ok)throw new Error(result.error??"Could not save availability");
      onSaved(result);
      setMessage("Saved. Future bookings follow your schedule; existing visits are unchanged.");
    }catch(cause){setError(cause instanceof Error?cause.message:"Could not save availability");}
    finally{setSaving(false);}
  }
  return <section className={styles.panel} aria-label="Clinician availability">
    <div className={styles.head}>
      <div><h2>Working hours & time off</h2>
        <p>Set when you accept new appointments. Existing visits will not be cancelled or moved.</p></div>
      {availability&&<span className={styles.state}>
        {availability.configured?"Rules active":"Not configured · unrestricted bookings"}
      </span>}
    </div>
    <form className={styles.form} onSubmit={event=>void save(event)}>
      <label className={styles.full}>Clinician time zone (IANA)
        <input required maxLength={80} value={draft.zoneId}
          onChange={event=>setDraft(d=>({...d,zoneId:event.target.value}))}/>
        <small>Rules use this zone. The appointment pickers above use your device time zone.</small>
        <button type="button" className={styles.detectZone}
          onClick={()=>setDraft(d=>({...d,zoneId:localTimeZone()}))}>Use my device time zone</button>
      </label>
      <fieldset className={styles.full}>
        <legend>Working days</legend>
        <div className={styles.days}>{WEEKDAYS.map(day=><label key={day}>
          <input type="checkbox" checked={draft.workDays.includes(day)}
            onChange={event=>toggle(day,event.target.checked)}/>
          {day[0]+day.slice(1).toLowerCase()}
        </label>)}</div>
      </fieldset>
      <label>Start of day<input required type="time" value={draft.startTime}
        onChange={event=>setDraft(d=>({...d,startTime:event.target.value}))}/></label>
      <label>End of day<input required type="time" value={draft.endTime}
        onChange={event=>setDraft(d=>({...d,endTime:event.target.value}))}/></label>
      <label>Break starts<input type="time" value={draft.breakStartTime}
        onChange={event=>setDraft(d=>({...d,breakStartTime:event.target.value}))}/></label>
      <label>Break ends<input type="time" value={draft.breakEndTime}
        onChange={event=>setDraft(d=>({...d,breakEndTime:event.target.value}))}/></label>
      <label className={styles.full}>Blocked dates (YYYY-MM-DD, one per line)
        <textarea rows={3} value={draft.blockedDates}
          onChange={event=>setDraft(d=>({...d,blockedDates:event.target.value}))}
          placeholder={"2026-12-25\n2026-12-31"}/>
        <small>Up to 90 dates within the next year in the clinician time zone.</small>
      </label>
      <div className={styles.footer}>
        <button type="submit" disabled={!availability||saving}>{saving?"Saving…":"Save availability"}</button>
        {error&&<p className={styles.error} role="alert">{error}</p>}
        {message&&<p className={styles.success} role="status">{message}</p>}
      </div>
    </form>
  </section>;
}
