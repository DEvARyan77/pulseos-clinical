"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
type PatientOption = { id: string; fullName: string };
import styles from "./appointment-manager.module.css";
import AppointmentDashboard from "./appointment-dashboard";
import AppointmentCalendar from "./appointment-calendar";
import AppointmentSlotPicker from "./appointment-slot-picker";
import {downloadCalendarIcs} from "@/lib/calendar-export";
import ClinicianAvailabilitySettings from "./clinician-availability-settings";
import AppointmentReminderHistory from "./appointment-reminder-history";
import {ClinicianAvailability,isSlotAllowed} from "@/lib/clinician-availability";

type VisitType = "FOLLOW_UP" | "ANNUAL_EXAM" | "CONSULTATION";
type Appointment = {
  id: string;
  patientId: string;
  scheduledAt: string;
  visitType: VisitType;
  status: "SCHEDULED" | "CHECKED_IN" | "IN_CARE" | "COMPLETED" | "CANCELLED";
};
const visitNames: Record<VisitType,string> = {
  FOLLOW_UP: "Follow-up",
  ANNUAL_EXAM: "Annual examination",
  CONSULTATION: "Consultation",
};

export default function AppointmentManager() {
  const [loadedAt, setLoadedAt] = useState(0);
  const [revision, setRevision] = useState(0);
  const [items, setItems] = useState<Appointment[]>([]);
  const [patients, setPatients] = useState<PatientOption[]>([]);
  const [availability, setAvailability] = useState<ClinicianAvailability|null>(null);
  const [patientId, setPatientId] = useState("");
  const [datetime, setDatetime] = useState("");
  const [visitType, setVisitType] = useState<VisitType>("FOLLOW_UP");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<"ALL" | Appointment["status"]>("ALL");
  const [rescheduleId, setRescheduleId] = useState<string|null>(null);
  const [newDatetime, setNewDatetime] = useState("");

  const reload = useCallback(() => {
    return Promise.all([
      fetch("/api/demo-appointments", { cache: "no-store" }),
      fetch("/api/patients", { cache: "no-store" }),
      fetch("/api/demo-appointments/availability", { cache: "no-store" }),
    ]).then(async ([response, patientResponse, availabilityResponse]) => {
      const result = await response.json() as Appointment[] | { error: string };
      const patientResult = await patientResponse.json() as PatientOption[] | { error: string };
      const availabilityResult = await availabilityResponse.json() as ClinicianAvailability & {error?:string};
      if (!response.ok || !Array.isArray(result)) {
        throw new Error(Array.isArray(result) ? "Failed to load appointments" : result.error);
      }
      if (!patientResponse.ok || !Array.isArray(patientResult)) {
        throw new Error(Array.isArray(patientResult) ? "Failed to load patients" : patientResult.error);
      }
      if(!availabilityResponse.ok || typeof availabilityResult.configured!=="boolean")
        throw new Error(availabilityResult.error??"Failed to load availability");
      return { result, patientResult, availabilityResult };
    }).then(({ result, patientResult, availabilityResult }) => {
      setAvailability(availabilityResult);
      setItems(result);
      setLoadedAt(Date.now());
      setRevision(value => value + 1);
      setPatients(patientResult);
      setPatientId(current => patientResult.some(p => p.id === current) ? current : patientResult[0]?.id ?? "");
      setError("");
    }).catch((cause: unknown) => {
      setError(cause instanceof Error ? cause.message : "Failed to load appointments");
    }).finally(() => setLoading(false));
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  const total = useMemo(() => items.filter(item => ["SCHEDULED", "CHECKED_IN", "IN_CARE"].includes(item.status) && new Date(item.scheduledAt).getTime() >= loadedAt).length, [items, loadedAt]);

  const shown=useMemo(()=>items.filter(item=>{
    if(filter!=="ALL"&&item.status!==filter)return false;
    const patient=patients.find(p=>p.id===item.patientId);
    return [patient?.fullName??"",item.patientId,visitNames[item.visitType],item.status]
      .join(" ").toLowerCase().includes(query.trim().toLowerCase());
  }),[items,patients,filter,query]);

  function localInput(iso:string){
    const d=new Date(iso), pad=(value:number)=>String(value).padStart(2,"0");
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  function startReschedule(item:Appointment){
    setRescheduleId(item.id);setNewDatetime(localInput(item.scheduledAt));setError("");setSuccess("");
  }
  async function reschedule(event:FormEvent<HTMLFormElement>,item:Appointment){
    event.preventDefault();setError("");setSuccess("");
    const time=new Date(newDatetime);
    if(!newDatetime||!Number.isFinite(time.getTime())||time.getTime()<=Date.now()){
      setError("Select a future appointment date and time.");return;
    }
    if(time.getTime()>Date.now()+365*86400000){
      setError("Choose a date within the next year.");return;
    }
    if(time.getTime()===new Date(item.scheduledAt).getTime()){
      setError("Choose a different appointment time.");return;
    }
    const slotBusy=items.some(visit=>visit.id!==item.id&&
      ["SCHEDULED","CHECKED_IN","IN_CARE"].includes(visit.status)&&
      Math.abs(new Date(visit.scheduledAt).getTime()-time.getTime())<30*60*1000);
    if(slotBusy){setError("This time overlaps another active appointment. Choose an available slot.");return;}
    if(!isSlotAllowed(time,availability)){setError("Outside working hours, break, or blocked dates.");return;}
    setSaving(true);
    try{
      const response=await fetch(`/api/demo-appointments/${encodeURIComponent(item.id)}/reschedule`,{
        method:"PATCH",headers:{"Content-Type":"application/json"},
        body:JSON.stringify({scheduledAt:time.toISOString()})
      });
      const result=await response.json() as {error?:string};
      if(!response.ok)throw new Error(result.error??"Could not reschedule appointment");
      setRescheduleId(null);setNewDatetime("");
      setSuccess("Appointment rescheduled. The patient portal shows the new time; a refreshed QR email is attempted for approved demo mailboxes.");
      await reload();
    }catch(cause){setError(cause instanceof Error?cause.message:"Could not reschedule appointment");}
    finally{setSaving(false);}
  }

  function pickBookingDate(day:string){
    const now=new Date();
    const localDay=[now.getFullYear(),String(now.getMonth()+1).padStart(2,"0"),
      String(now.getDate()).padStart(2,"0")].join("-");
    if(day===localDay){
      // Choosing today late at night must roll into tomorrow rather than suggest a past time.
      const next=new Date(now.getTime()+60*60*1000);
      next.setMinutes(0,0,0);
      setDatetime(localInput(next.toISOString()));
    }else{
      const time=/^([01]\\d|2[0-3]):[0-5]\\d$/.test(datetime.split("T")[1]??"")?
        datetime.split("T")[1]:"10:00";
      setDatetime(day+"T"+time);
    }
    document.getElementById("booking-time")?.focus();
  }

  async function schedule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    const date = new Date(datetime);
    if (!patientId || !patients.some(p => p.id === patientId)) {
      setError("Create a patient in your registry first");
      return;
    }
    if (!datetime || !Number.isFinite(date.getTime()) || date.getTime() <= Date.now()) {
      setError("Choose a future date and time");
      return;
    }
    const slotBusy=items.some(item=>["SCHEDULED","CHECKED_IN","IN_CARE"].includes(item.status)&&
      Math.abs(new Date(item.scheduledAt).getTime()-date.getTime())<30*60*1000);
    if(slotBusy){setError("This clinician has a booking within 30 minutes. Select an available slot.");return;}
    if(!isSlotAllowed(date,availability)){setError("Outside working hours, break, or blocked dates.");return;}
    setSaving(true);
    try {
      const response = await fetch("/api/demo-appointments", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ patientId, scheduledAt: date.toISOString(), visitType }),
      });
      const result = await response.json() as Appointment | { error: string };
      if (!response.ok) {
        throw new Error("error" in result ? result.error : "Could not create appointment");
      }
      setDatetime("");
      setSuccess("Appointment saved without a scheduling conflict. A QR email is attempted if the test address is allowlisted and mail is configured.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create appointment");
    } finally {
      setSaving(false);
    }
  }

  async function advance(id:string){
    setSaving(true);setError("");setSuccess("");
    try{
      const response=await fetch(`/api/demo-appointments/${encodeURIComponent(id)}/advance`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:"{}"});
      const result=await response.json() as {error?:string};
      if(!response.ok)throw new Error(result.error??"Unable to advance visit");
      setSuccess("Visit status advanced. A test email is sent only to an approved, configured demo address.");
      await reload();
    }catch(cause){setError(cause instanceof Error?cause.message:"Unable to advance visit");}
    finally{setSaving(false);}
  }
  async function cancel(id: string) {
    setError("");
    setSuccess("");
    setSaving(true);
    try {
      const response = await fetch(`/api/demo-appointments/${encodeURIComponent(id)}/cancel`, {
        method: "PATCH",
      });
      const result = await response.json() as { error?: string };
      if (!response.ok) throw new Error(result.error ?? "Could not cancel appointment");
      setSuccess("Appointment cancelled.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not cancel appointment");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className={styles.layout}>
      <AppointmentDashboard patients={patients} revision={revision}/>
      <section className={styles.formCard}>
        <h2>Schedule a visit</h2>
        <p>Select a record from your account’s patient registry (fictional test data only).</p>
        <form onSubmit={schedule} className={styles.form}>
          <label htmlFor="booking-patient">Patient record</label>
          <select id="booking-patient" value={patientId} onChange={event => setPatientId(event.target.value)}>
            {patients.length === 0 && <option value="">Create a patient first</option>}
            {patients.map(patient => <option key={patient.id} value={patient.id}>{patient.fullName} · {patient.id.slice(0,8)}</option>)}
          </select>
          <label htmlFor="booking-type">Visit type</label>
          <select id="booking-type" value={visitType} onChange={event => setVisitType(event.target.value as VisitType)}>
            {Object.entries(visitNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <label htmlFor="booking-time">Date and time</label>
          <input id="booking-time" type="datetime-local" value={datetime}
            onChange={event => setDatetime(event.target.value)} required />
          <AppointmentSlotPicker value={datetime} onSelect={setDatetime} visits={items} availability={availability}/>
          <button type="submit" disabled={saving || loading || patients.length===0}>{saving ? "Saving…" : "Schedule appointment"}</button>
        </form>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {success && <p className={styles.success} role="status">{success}</p>}
      </section>
      <section className={styles.listCard}>
        <div className={styles.listHead}><div><h2>Appointments</h2><p>{total} upcoming scheduled visit(s)</p></div><button type="button" onClick={()=>{setLoading(true);void reload();}} disabled={loading}>Refresh</button></div>
        <AppointmentCalendar title="Clinician appointment calendar"
          entries={shown.map(item=>({
            id:item.id,scheduledAt:item.scheduledAt,status:item.status,
            label:patients.find(patient=>patient.id===item.patientId)?.fullName??"Demo patient",
            description:visitNames[item.visitType]
          }))}
          onScheduleDate={pickBookingDate}/>
        <div className={styles.filters}>
          <label htmlFor="appointment-search">Search
            <input id="appointment-search" type="search" value={query} onChange={event=>setQuery(event.target.value)}
              placeholder="Patient name, ID or visit type"/>
          </label>
          <label htmlFor="appointment-filter">Status
            <select id="appointment-filter" value={filter}
              onChange={event=>setFilter(event.target.value as typeof filter)}>
              <option value="ALL">All statuses</option>
              <option value="SCHEDULED">Scheduled</option>
              <option value="CHECKED_IN">Checked in</option>
              <option value="IN_CARE">In care</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </label>
        </div>
        {!loading&&items.length>0&&<p className={styles.filterCount}>{shown.length} of {items.length} appointments shown</p>}
        {loading ? <p className={styles.empty}>Loading appointments…</p> : items.length === 0 ?
          <p className={styles.empty}>No appointments yet. Schedule a fictional patient to see a saved visit here.</p> :
          shown.length===0 ? <p className={styles.empty}>No appointments match these filters.</p> :
          <ul className={styles.list}>{shown.map(item => {
            const patient = patients.find(entry => entry.id === item.patientId);
            return <li key={item.id}>
              <div>
                <strong>{patient?.fullName ?? item.patientId}</strong>
                <p>{visitNames[item.visitType]} · {new Date(item.scheduledAt).toLocaleString()}</p>
                <span className={item.status === "CANCELLED" ? styles.cancelled : styles.active}>{item.status.replaceAll("_"," ")}</span>
              </div>
              <div className={styles.actions}>
                <button type="button" onClick={()=>downloadCalendarIcs([{
                  id:item.id,scheduledAt:item.scheduledAt,status:item.status,
                  label:patient?.fullName??"Demo patient",description:visitNames[item.visitType]
                }],`pulseos-visit-${item.id}.ics`,"PulseOS demo visit")}>Add to calendar (.ics)</button>
                {(item.status === "CHECKED_IN" || item.status === "IN_CARE") && <button type="button" disabled={saving} onClick={()=>void advance(item.id)}>Advance stage</button>}
                {item.status === "SCHEDULED" && <>
                  <button type="button" disabled={saving} onClick={()=>startReschedule(item)}>Reschedule</button>
                  <button type="button" disabled={saving} onClick={()=>void cancel(item.id)}>Cancel</button>
                </>}
              </div>
              {rescheduleId===item.id&&<form className={styles.rescheduleForm} onSubmit={event=>void reschedule(event,item)}>
                <label htmlFor={`move-${item.id}`}>New date and time
                  <input id={`move-${item.id}`} type="datetime-local" required value={newDatetime}
                    onChange={event=>setNewDatetime(event.target.value)}/>
                </label>
                <AppointmentSlotPicker value={newDatetime} onSelect={setNewDatetime}
                  visits={items} excludeId={item.id} availability={availability}/>
                <button type="submit" disabled={saving}>{saving?"Saving…":"Confirm new time"}</button>
                <button type="button" disabled={saving} onClick={()=>setRescheduleId(null)}>Cancel changes</button>
                <p>Only scheduled future visits can be moved. QR email delivery requires an allowlisted test address.</p>
              </form>}
            </li>;
          })}</ul>}
      </section>
      <ClinicianAvailabilitySettings availability={availability} onSaved={value => { setAvailability(value); setRevision(current => current + 1); }}/>
      <AppointmentReminderHistory key={revision}/>
    </div>
  );
}
