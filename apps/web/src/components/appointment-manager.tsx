"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
type PatientOption = { id: string; fullName: string };
import styles from "./appointment-manager.module.css";

type VisitType = "FOLLOW_UP" | "ANNUAL_EXAM" | "CONSULTATION";
type Appointment = {
  id: string;
  patientId: string;
  scheduledAt: string;
  visitType: VisitType;
  status: "SCHEDULED" | "CANCELLED";
};
const visitNames: Record<VisitType,string> = {
  FOLLOW_UP: "Follow-up",
  ANNUAL_EXAM: "Annual examination",
  CONSULTATION: "Consultation",
};

export default function AppointmentManager() {
  const [items, setItems] = useState<Appointment[]>([]);
  const [patients, setPatients] = useState<PatientOption[]>([]);
  const [patientId, setPatientId] = useState("");
  const [datetime, setDatetime] = useState("");
  const [visitType, setVisitType] = useState<VisitType>("FOLLOW_UP");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [response, patientResponse] = await Promise.all([
        fetch("/api/demo-appointments", { cache: "no-store" }),
        fetch("/api/patients", { cache: "no-store" }),
      ]);
      const result = await response.json() as Appointment[] | { error: string };
      const patientResult = await patientResponse.json() as PatientOption[] | { error: string };
      if (!response.ok || !Array.isArray(result)) {
        throw new Error(Array.isArray(result) ? "Failed to load appointments" : result.error);
      }
      if (!patientResponse.ok || !Array.isArray(patientResult)) {
        throw new Error(Array.isArray(patientResult) ? "Failed to load patients" : patientResult.error);
      }
      setItems(result);
      setPatients(patientResult);
      setPatientId(current => patientResult.some(p => p.id === current) ? current : patientResult[0]?.id ?? "");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load appointments");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  const total = useMemo(() => items.filter(item => item.status === "SCHEDULED").length, [items]);

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
      setSuccess("Demo appointment saved to PostgreSQL.");
      await reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create appointment");
    } finally {
      setSaving(false);
    }
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
          <button type="submit" disabled={saving || patients.length===0}>{saving ? "Saving…" : "Schedule appointment"}</button>
        </form>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {success && <p className={styles.success} role="status">{success}</p>}
      </section>
      <section className={styles.listCard}>
        <div className={styles.listHead}><div><h2>Appointments</h2><p>{total} upcoming scheduled visit(s)</p></div><button type="button" onClick={()=>void reload()} disabled={loading}>Refresh</button></div>
        {loading ? <p className={styles.empty}>Loading appointments…</p> : items.length === 0 ?
          <p className={styles.empty}>No appointments yet. Schedule a fictional patient to see a saved visit here.</p> :
          <ul className={styles.list}>{items.map(item => {
            const patient = patients.find(entry => entry.id === item.patientId);
            return <li key={item.id}>
              <div>
                <strong>{patient?.fullName ?? item.patientId}</strong>
                <p>{visitNames[item.visitType]} · {new Date(item.scheduledAt).toLocaleString()}</p>
                <span className={item.status === "SCHEDULED" ? styles.active : styles.cancelled}>{item.status === "SCHEDULED" ? "Scheduled" : "Cancelled"}</span>
              </div>
              {item.status === "SCHEDULED" && <button type="button" disabled={saving} onClick={()=>void cancel(item.id)}>Cancel</button>}
            </li>;
          })}</ul>}
      </section>
    </div>
  );
}
