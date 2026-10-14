"use client";

import styles from "./appointment-slot-picker.module.css";

export type SlotVisit={id:string;scheduledAt:string;status:string};
type Props={
  value:string;
  onSelect:(localDatetime:string)=>void;
  visits:SlotVisit[];
  excludeId?:string|null;
};
const busyStatuses=new Set(["SCHEDULED","CHECKED_IN","IN_CARE"]);
const pad=(value:number)=>String(value).padStart(2,"0");

/** Suggestions are local-time conveniences; the server enforces the booking rule. */
export default function AppointmentSlotPicker({value,onSelect,visits,excludeId}:Props){
  const day=value.slice(0,10);
  const selectedDay=/^\d{4}-\d{2}-\d{2}$/.test(day)?day:"";
  if(!selectedDay)return <p className={styles.hint}>Choose a date to see available time slots.</p>;
  const now=Date.now();
  const slots=Array.from({length:18},(_,index)=>{
    const hour=9+Math.floor(index/2),minute=index%2?30:0;
    const label=`${pad(hour)}:${pad(minute)}`;
    const local=`${selectedDay}T${label}`;
    const candidate=new Date(local).getTime();
    const occupied=visits.some(item=>item.id!==excludeId&&busyStatuses.has(item.status)&&
      Math.abs(new Date(item.scheduledAt).getTime()-candidate)<30*60*1000);
    return {label,local,disabled:!Number.isFinite(candidate)||candidate<=now||
      candidate>now+365*86400000||occupied,occupied};
  });
  const open=slots.filter(slot=>!slot.disabled).length;
  return <div className={styles.picker}>
    <div className={styles.heading}><strong>Suggested time slots</strong>
      <span>{open} available · 9 AM–6 PM</span></div>
    <div className={styles.slots} role="group" aria-label="Available appointment times">
      {slots.map(slot=><button key={slot.label} type="button"
        disabled={slot.disabled}
        aria-pressed={value===slot.local}
        title={slot.occupied?"Another active appointment is within 30 minutes":slot.disabled?"Unavailable":undefined}
        onClick={()=>onSelect(slot.local)}>
        {new Date(slot.local).toLocaleTimeString(undefined,{hour:"numeric",minute:"2-digit"})}
      </button>)}
    </div>
    <p className={styles.hint}>Unavailable slots are disabled. You can also enter a different time above; the API checks for conflicts before saving.</p>
  </div>;
}
