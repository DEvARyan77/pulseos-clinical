"use client";

import {useState} from "react";
import styles from "./appointment-slot-picker.module.css";
import {ClinicianAvailability,isSlotAllowed} from "@/lib/clinician-availability";

export type SlotVisit={id:string;scheduledAt:string;status:string};
type Props={
  value:string;
  onSelect:(localDatetime:string)=>void;
  visits:SlotVisit[];
  excludeId?:string|null;
  availability?:ClinicianAvailability|null;
};
const busyStatuses=new Set(["SCHEDULED","CHECKED_IN","IN_CARE"]);
const pad=(value:number)=>String(value).padStart(2,"0");

/** Suggestions are local-time conveniences; the server enforces the booking rule. */
export default function AppointmentSlotPicker({value,onSelect,visits,excludeId,availability}:Props){
  // Hooks must run before any conditional rendering path.
  const [now]=useState(()=>Date.now());
  const day=value.slice(0,10);
  const selectedDay=/^\d{4}-\d{2}-\d{2}$/.test(day)?day:"";
  if(!selectedDay)return <p className={styles.hint}>Choose a date to see available time slots.</p>;
  const slots=Array.from({length:18},(_,index)=>{
    const hour=9+Math.floor(index/2),minute=index%2?30:0;
    const label=`${pad(hour)}:${pad(minute)}`;
    const local=`${selectedDay}T${label}`;
    const candidate=new Date(local).getTime();
    const occupied=visits.some(item=>item.id!==excludeId&&busyStatuses.has(item.status)&&
      Math.abs(new Date(item.scheduledAt).getTime()-candidate)<30*60*1000);
    const outsideHours=!isSlotAllowed(new Date(candidate),availability??null);
    return {label,local,disabled:!Number.isFinite(candidate)||candidate<=now||
      candidate>now+365*86400000||occupied||outsideHours,occupied,outsideHours};
  });
  const open=slots.filter(slot=>!slot.disabled).length;
  return <div className={styles.picker}>
    <div className={styles.heading}><strong>Suggested time slots</strong>
      <span>{open} available · {availability?.configured?`Rules: ${availability.zoneId}`:"Suggested 9 AM–6 PM"}</span></div>
    <div className={styles.slots} role="group" aria-label="Available appointment times">
      {slots.map(slot=><button key={slot.label} type="button"
        disabled={slot.disabled}
        aria-pressed={value===slot.local}
        title={slot.occupied?"Another active appointment is within 30 minutes":slot.outsideHours?"Outside working hours or blocked":slot.disabled?"Unavailable":undefined}
        onClick={()=>onSelect(slot.local)}>
        {new Date(slot.local).toLocaleTimeString(undefined,{hour:"numeric",minute:"2-digit"})}
      </button>)}
    </div>
    <p className={styles.hint}>Times use your device time zone. The API checks working hours, breaks, blocked dates and 30-minute spacing before saving.</p>
  </div>;
}
