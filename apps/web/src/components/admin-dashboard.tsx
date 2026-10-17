"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ShieldCheck, Users, UserCheck, UserX, RefreshCw } from "lucide-react";
import StaffNavbar from "./staff-navbar";
import styles from "./admin-dashboard.module.css";

type Role = "ADMIN" | "CLINICIAN";
type PatientSummary = {
  id: string; fullName: string; age: number; gender: string; reason: string;
  room: string; status: "WAITING" | "IN_CARE" | "COMPLETED";
  owner: string; updatedAt: string;
};
type Staff = {
  id: string;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
};

async function requestJson<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...options, cache: "no-store" });
  const result = await response.json() as T & { error?: string };
  if (!response.ok) throw new Error(result.error ?? "Unable to complete request");
  return result;
}

export default function AdminDashboard({ username }: { username: string }) {
  const [members, setMembers] = useState<Staff[]>([]);
  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [patientSearch, setPatientSearch] = useState("");
  const [patientError, setPatientError] = useState("");
  const [patientsLoading, setPatientsLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(() => {
    return requestJson<Staff[]>("/api/admin/staff")
      .then((records) => {
        setMembers(records);
        setError("");
      })
      .catch((cause: unknown) => {
        setError(cause instanceof Error ? cause.message : "Unable to load staff accounts");
      })
      .finally(() => setLoading(false));
  }, []);

  const loadPatients = useCallback(() => {
    return requestJson<PatientSummary[]>("/api/admin/patients")
      .then(records => { setPatients(records); setPatientError(""); })
      .catch((cause: unknown) =>
        setPatientError(cause instanceof Error ? cause.message : "Unable to load patient records"))
      .finally(() => setPatientsLoading(false));
  }, []);

  useEffect(() => { void load(); void loadPatients(); }, [load, loadPatients]);

  const counts = useMemo(() => ({
    all: members.length,
    admins: members.filter((m) => m.role === "ADMIN").length,
    clinicians: members.filter((m) => m.role === "CLINICIAN").length,
    active: members.filter((m) => m.enabled).length,
  }), [members]);

  const filtered = members.filter((member) =>
    `${member.username} ${member.email} ${member.role}`
      .toLowerCase().includes(search.trim().toLowerCase()));

  const matchingPatients = patients.filter(patient =>
    `${patient.fullName} ${patient.owner} ${patient.reason} ${patient.room} ${patient.status}`
      .toLowerCase().includes(patientSearch.trim().toLowerCase()));

  async function change(member: Staff, field: "role" | "status", value: Role | boolean) {
    setBusy(member.id);
    setError("");
    setMessage("");
    try {
      const updated = await requestJson<Staff>(
        `/api/admin/staff/${member.id}/${field}`,
        { method: "PATCH", headers: { "Content-Type": "application/json" },
          body: JSON.stringify(field === "role" ? { role: value } : { enabled: value }) });
      setMembers((current) => current.map((record) => record.id === member.id ? updated : record));
      setMessage(`Updated permissions for ${member.username}.`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Permission update failed");
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className={styles.page}>
      <StaffNavbar role="ADMIN" active="admin" />

      <div className={styles.container}>
        <div className={styles.intro}>
          <p className={styles.kicker}>WORKFORCE &amp; PERMISSIONS</p>
          <h1>Administration overview</h1>
          <p>Welcome, <strong>{username}</strong>. Manage access to this fictional-data demonstration workspace.</p>
        </div>

        <div className={styles.metrics}>
          <article><Users size={19}/><strong>{counts.all}</strong><span>Staff accounts</span></article>
          <article><ShieldCheck size={19}/><strong>{counts.admins}</strong><span>Administrators</span></article>
          <article><UserCheck size={19}/><strong>{counts.clinicians}</strong><span>Clinicians</span></article>
          <article><UserX size={19}/><strong>{counts.active}</strong><span>Enabled accounts</span></article>
        </div>

        <section className={styles.panel} aria-label="Staff access management">
          <div className={styles.panelTop}>
            <div><h2>Staff access</h2><p>New email-verified accounts start as Clinicians.</p></div>
            <button type="button" onClick={() => { setLoading(true); void load(); }}
              className={styles.refresh}><RefreshCw size={16} /> Refresh</button>
          </div>
          <label className={styles.searchLabel}>Search staff
            <input type="search" placeholder="Search name, email, or role"
              value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
          {error && <div className={styles.error} role="alert">{error}</div>}
          {message && <div className={styles.success} role="status">{message}</div>}
          <div className={styles.scroll}>
            <table className={styles.table}>
              <thead><tr><th>Staff member</th><th>Role</th><th>Account</th><th>Joined</th></tr></thead>
              <tbody>
                {filtered.map((member) => {
                  const self = member.username === username;
                  return (
                    <tr key={member.id}>
                      <td><strong>{member.username}</strong><span>{member.email}</span></td>
                      <td>
                        <select aria-label={`Role for ${member.username}`}
                          value={member.role} disabled={busy === member.id || self}
                          onChange={(event) => void change(member, "role", event.target.value as Role)}>
                          <option value="CLINICIAN">Clinician</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                      </td>
                      <td><button type="button"
                        className={member.enabled ? styles.enabled : styles.disabled}
                        disabled={busy === member.id || self}
                        onClick={() => void change(member, "status", !member.enabled)}>
                        {member.enabled ? "Enabled" : "Disabled"}
                      </button></td>
                      <td>{new Date(member.createdAt).toLocaleDateString()}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {loading && <p className={styles.empty}>Loading staff accounts…</p>}
            {!loading && filtered.length === 0 && <p className={styles.empty}>No matching staff accounts.</p>}
          </div>
          <p className={styles.note}>Changes are enforced by the API. Staff members should sign in again after role changes. Existing JWTs expire within eight hours; do not use this portfolio for real patient information.</p>
        </section>

        <section className={styles.panel} aria-label="Fictional patient directory" style={{marginTop:24}}>
          <div className={styles.panelTop}>
            <div><h2>Patient directory</h2><p>Read-only overview of the 100 most recently updated fictional records across staff accounts.</p></div>
            <button type="button" className={styles.refresh}
              onClick={() => { setPatientsLoading(true); void loadPatients(); }}>
              <RefreshCw size={16}/> Refresh patients
            </button>
          </div>
          <label className={styles.searchLabel}>Search patients
            <input type="search" placeholder="Search patient, clinician, status or reason"
              value={patientSearch} onChange={event => setPatientSearch(event.target.value)}/>
          </label>
          {patientError && <div className={styles.error} role="alert">{patientError}</div>}
          <div className={styles.scroll}>
            <table className={styles.table}>
              <thead><tr><th>Fictional patient</th><th>Registry owner</th><th>Status</th><th>Room</th><th>Last updated</th></tr></thead>
              <tbody>{matchingPatients.map(patient => <tr key={patient.id}>
                <td><strong>{patient.fullName}</strong><span>{patient.age} years · {patient.gender} · {patient.reason}</span></td>
                <td>{patient.owner}</td>
                <td>{patient.status.replaceAll("_"," ")}</td>
                <td>{patient.room}</td>
                <td>{new Date(patient.updatedAt).toLocaleString()}</td>
              </tr>)}</tbody>
            </table>
            {patientsLoading && <p className={styles.empty}>Loading fictional patients…</p>}
            {!patientsLoading && matchingPatients.length === 0 && <p className={styles.empty}>No matching fictional patient records.</p>}
          </div>
          <p className={styles.note}>Admin access here is read-only; patient charts and visit notes remain clinician-restricted.</p>
        </section>
      </div>
    </main>
  );
}
