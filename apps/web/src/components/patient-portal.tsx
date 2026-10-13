"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import styles from "./patient-portal.module.css";
type Visit={id:string;scheduledAt:string;visitType:string;status:string};
type RecordMatch={id:string;fullName:string;clinicianUsername:string;status:string;appointments:Visit[]};
type Summary={username:string;email:string;records:RecordMatch[]};
export default function PatientPortal(){
  const [data,setData]=useState<Summary|null>(null);const [error,setError]=useState("");
  useEffect(()=>{let alive=true;void fetch("/api/patient-portal/summary",{cache:"no-store"})
    .then(async response=>{const result=await response.json() as Summary&{error?:string};
      if(!response.ok)throw new Error(result.error??"Unable to load portal");
      if(alive)setData(result);
    }).catch(cause=>{if(alive)setError(cause instanceof Error?cause.message:"Unable to load portal");});
    return()=>{alive=false;};
  },[]);
  return <section className={styles.container}>
    <p className={styles.eyebrow}>PATIENT SELF-SERVICE</p>
    <h1>My appointments</h1>
    <p className={styles.subtitle}>Your appointment updates are linked only to your verified email. No clinical charts or visit notes are exposed.</p>
    {error&&<p className={styles.error} role="alert">{error}</p>}
    {!data&&!error&&<p>Loading your appointments…</p>}
    {data&&<><p className={styles.account}>Signed in as {data.username} · {data.email}</p>
      {!data.records.length&&<div className={styles.card}>
        <h2>No linked appointments yet</h2><p>Ask your demo clinician to create a patient record with your verified test email and schedule an appointment.</p>
      </div>}
      {data.records.map(record=><article className={styles.card} key={record.id}>
        <h2>{record.fullName}</h2>
        <p>Assigned registry clinician: <strong>{record.clinicianUsername}</strong></p>
        <p>Check-in status: <strong>{record.status.replaceAll("_"," ")}</strong></p>
        {!record.appointments.length&&<p>No appointments scheduled yet.</p>}
        {record.appointments.map(visit=><div className={styles.visit} key={visit.id}>
          <strong>{visit.visitType.replaceAll("_"," ")}</strong>
          <span>{new Date(visit.scheduledAt).toLocaleString()}</span>
          <span>{visit.status.replaceAll("_"," ")}</span>
          {visit.status==="SCHEDULED"&&<Link href={`/patient/checkin?appointment=${visit.id}`}>Check in using QR</Link>}
        </div>)}
      </article>)}
    </>}
  </section>;
}
