"use client";

import { useEffect, useState } from "react";
import { AppointmentDashboardData, isMissedVisit, reminderTotals } from "@/lib/appointment-dashboard";
import styles from "./appointment-dashboard.module.css";

export default function AppointmentDashboard({
  patients, revision,
}: { patients: { id: string; fullName: string }[]; revision: number }) {
  const [data, setData] = useState<AppointmentDashboardData | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const response = await fetch("/api/demo-appointments/dashboard", {
          cache: "no-store", signal: controller.signal,
        });
        const result = await response.json();
        if (!response.ok || !Array.isArray(result.visits) || !Array.isArray(result.reminderCounts)) {
          throw new Error(result.error ?? "Unable to load appointment dashboard");
        }
        if (!controller.signal.aborted) { setData(result); setError(""); }
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : "Unable to load appointment dashboard");
        }
      } finally { if (!controller.signal.aborted) setLoading(false); }
    }
    void load();
    // Keep clock-based missed flags current, including across local midnight.
    const timer = window.setInterval(() => { setLoading(true); setRefresh(value => value + 1); }, 60000);
    return () => { controller.abort(); window.clearInterval(timer); };
  }, [revision, refresh]);

  const totals = reminderTotals(data?.reminderCounts ?? []);
  return <section className={styles.panel} aria-label="Appointment dashboard" aria-busy={loading}>
    <div className={styles.heading}>
      <div><h2>Today’s appointment dashboard</h2>
        <p>Fictional visits only. Your account’s schedule and automatic reminder activity.</p></div>
      <button type="button" disabled={loading} onClick={() => {
        setLoading(true); setRefresh(value => value + 1);
      }}>{loading ? "Refreshing…" : "Refresh dashboard"}</button>
    </div>
    {error && <p className={styles.error} role="alert">{error}{data ? " Showing the last successful snapshot." : ""}</p>}
    {!data && !error && <p role="status">Loading today’s visits…</p>}
    {data && <>
      <p>{data.date} · {data.zoneId} · Updated {new Date(data.generatedAt).toLocaleTimeString([], { timeZone: data.zoneId })}</p>
      <dl className={styles.metrics}>
        <div><dt>Today’s visits (excluding cancelled)</dt><dd>{data.todayVisits}</dd></div>
        <div><dt>Pending check-ins today</dt><dd>{data.pendingCheckins}</dd></div>
        <div><dt>Missed check-ins today</dt><dd>{data.missedAppointments}</dd></div>
      </dl>
      <p>Pending includes scheduled visits until their 30-minute slot ends. Missed means the slot ended without check-in; it is a flag, not a stored status. Late check-in updates the next snapshot. Times below use {data.zoneId}; UTC is used until availability is configured.</p>
      {data.visits.length === 0 ? <p>No visits scheduled for today.</p> :
        <div className={styles.scroll}><table className={styles.table}>
          <thead><tr><th scope="col">Time</th><th scope="col">Fictional patient</th><th scope="col">Visit</th><th scope="col">Status</th></tr></thead>
          <tbody>{data.visits.map(visit => <tr key={visit.id}>
            <td>{new Date(visit.scheduledAt).toLocaleTimeString([], { timeZone: data.zoneId, hour: "2-digit", minute: "2-digit" })}</td>
            <td>{patients.find(patient => patient.id === visit.patientId)?.fullName ?? "Demo patient"}</td>
            <td>{visit.visitType.replaceAll("_", " ")}</td>
            <td>{isMissedVisit(visit, data.generatedAt) ? "Missed check-in" : visit.status.replaceAll("_", " ")}</td>
          </tr>)}</tbody>
        </table></div>}
      <h3>Automatic reminder statistics · last 24 hours</h3>
      <p>{data.remindersEnabled ? "Automatic reminders enabled." : "Automatic reminders disabled."} Window begins {new Date(data.reminderWindowStart).toLocaleString([], { timeZone: data.zoneId })} ({data.zoneId}).</p>
      <dl className={styles.reminders}>
        <div><dt>Provider accepted</dt><dd>{totals.ACCEPTED}</dd></div>
        <div><dt>Failed</dt><dd>{totals.FAILED}</dd></div>
        <div><dt>Skipped / ineligible</dt><dd>{totals.SKIPPED}</dd></div>
        <div><dt>Claimed / outcome unknown</dt><dd>{totals.CLAIMED}</dd></div>
      </dl>
      <p>Counts cover all attempts in this window, including earlier appointment times after rescheduling. They exclude booking and stage-change notices. Provider acceptance does not confirm inbox delivery. Only allowlisted test recipients are eligible; unknown attempts are not retried automatically.</p>
    </>}
  </section>;
}
