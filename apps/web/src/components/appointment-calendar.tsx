"use client";

import {useMemo,useState} from "react";
import {CalendarDays,ChevronLeft,ChevronRight} from "lucide-react";
import styles from "./appointment-calendar.module.css";
import {downloadCalendarIcs} from "@/lib/calendar-export";

export type CalendarStatus="SCHEDULED"|"CHECKED_IN"|"IN_CARE"|"COMPLETED"|"CANCELLED";
export type CalendarEntry={
  id:string;
  scheduledAt:string;
  label:string;
  description:string;
  status:CalendarStatus;
};
type Props={
  entries:CalendarEntry[];
  onScheduleDate?:(localDate:string)=>void;
  title?:string;
};
const weekdays=["Mon","Tue","Wed","Thu","Fri","Sat","Sun"];
const statusLabels:Record<CalendarStatus,string>={
  SCHEDULED:"Scheduled",CHECKED_IN:"Checked in",IN_CARE:"In care",
  COMPLETED:"Completed",CANCELLED:"Cancelled"
};
const statusStyles:Record<CalendarStatus,string>={
  SCHEDULED:styles.scheduled,CHECKED_IN:styles.checkedIn,IN_CARE:styles.inCare,
  COMPLETED:styles.completed,CANCELLED:styles.cancelled
};
function localDay(date:Date){
  return [date.getFullYear(),String(date.getMonth()+1).padStart(2,"0"),
    String(date.getDate()).padStart(2,"0")].join("-");
}
function atMonth(date:Date,monthOffset:number){
  return new Date(date.getFullYear(),date.getMonth()+monthOffset,1);
}
const todayDate=()=>new Date();
export default function AppointmentCalendar({entries,onScheduleDate,title="Appointment calendar"}:Props){
  const [month,setMonth]=useState(()=>atMonth(todayDate(),0));
  const [selectedDay,setSelectedDay]=useState(()=>localDay(todayDate()));
  const today=localDay(todayDate());
  const grouped=useMemo(()=>{
    const map=new Map<string,CalendarEntry[]>();
    for(const entry of entries){
      const when=new Date(entry.scheduledAt);
      if(!Number.isFinite(when.getTime()))continue;
      const key=localDay(when);
      map.set(key,[...(map.get(key)??[]),entry]);
    }
    for(const list of map.values())list.sort((a,b)=>a.scheduledAt.localeCompare(b.scheduledAt));
    return map;
  },[entries]);
  const cells=useMemo(()=>{
    const mondayOffset=(new Date(month.getFullYear(),month.getMonth(),1).getDay()+6)%7;
    return Array.from({length:42},(_,i)=>{
      const date=new Date(month.getFullYear(),month.getMonth(),i-mondayOffset+1);
      return {key:localDay(date),day:date.getDate(),current:date.getMonth()===month.getMonth()};
    });
  },[month]);
  const chosen=grouped.get(selectedDay)??[];
  const selectedDate=new Date(selectedDay+"T12:00:00");
  const selectedLabel=Number.isFinite(selectedDate.getTime())
    ?selectedDate.toLocaleDateString(undefined,{weekday:"long",month:"short",day:"numeric",year:"numeric"})
    :selectedDay;
  function moveMonth(offset:number){
    const next=atMonth(month,offset);
    setMonth(next);
    setSelectedDay(localDay(next));
  }
  function showToday(){
    const now=todayDate();setMonth(atMonth(now,0));setSelectedDay(localDay(now));
  }
  const legend=(["SCHEDULED","CHECKED_IN","IN_CARE","COMPLETED","CANCELLED"] as CalendarStatus[]);
  return <section className={styles.calendar} aria-label={title}>
    <div className={styles.heading}>
      <div className={styles.title}><CalendarDays size={19} aria-hidden="true"/>
        <div><h3>{title}</h3><p>Click a day to review visits</p></div>
      </div>
      <div className={styles.controls}>
        {entries.length>0&&<button type="button" onClick={()=>downloadCalendarIcs(entries)}>Export schedule (.ics)</button>}
        <button type="button" onClick={showToday}>Today</button>
        <button type="button" onClick={()=>moveMonth(-1)} aria-label="Previous month"><ChevronLeft size={17}/></button>
        <strong aria-live="polite">{month.toLocaleDateString(undefined,{month:"long",year:"numeric"})}</strong>
        <button type="button" onClick={()=>moveMonth(1)} aria-label="Next month"><ChevronRight size={17}/></button>
      </div>
    </div>
    <div className={styles.weekdays} aria-hidden="true">{weekdays.map(day=><span key={day}>{day}</span>)}</div>
    <div className={styles.grid} role="group" aria-label="Month days">
      {cells.map(cell=>{
        const visits=grouped.get(cell.key)??[];
        const unique=[...new Set(visits.map(visit=>visit.status))];
        return <button key={cell.key} type="button"
          className={[styles.day,!cell.current?styles.outside:"",
            cell.key===today?styles.today:"",
            cell.key===selectedDay?styles.selected:""].join(" ")}
          disabled={!cell.current}
          aria-pressed={cell.current&&cell.key===selectedDay}
          aria-label={cell.key+": "+visits.length+" appointment"+(visits.length===1?"":"s")}
          onClick={()=>setSelectedDay(cell.key)}>
          <span className={styles.dayNumber}>{cell.day}</span>
          {visits.length>0&&<><span className={styles.count}>{visits.length}</span>
            <span className={styles.marks} aria-hidden="true">
              {unique.slice(0,4).map(status=><span key={status}
                className={[styles.mark,statusStyles[status]].join(" ")}/>)}
            </span></>}
        </button>;
      })}
    </div>
    <div className={styles.legend} aria-label="Appointment statuses">
      {legend.map(status=><span key={status}><i className={[styles.mark,statusStyles[status]].join(" ")}/>{statusLabels[status]}</span>)}
    </div>
    <div className={styles.agenda}>
      <div className={styles.agendaHead}>
        <div><strong>{selectedLabel}</strong><p>{chosen.length} appointment{chosen.length===1?"":"s"}</p></div>
        {chosen.length>0&&<button type="button" onClick={()=>downloadCalendarIcs(chosen,`pulseos-${selectedDay}.ics`)}>Export day (.ics)</button>}
        {onScheduleDate&&selectedDay>=today&&
          <button type="button" onClick={()=>onScheduleDate(selectedDay)}>Schedule on this day</button>}
      </div>
      <p className={styles.empty}>Calendar downloads are manual snapshots. Export again after a visit changes.</p>
      {chosen.length===0?<p className={styles.empty}>No appointments on this date.</p>:
        <ul className={styles.agendaList}>{chosen.map(visit=>
          <li key={visit.id}>
            <time dateTime={visit.scheduledAt}>{new Date(visit.scheduledAt).toLocaleTimeString(undefined,{hour:"numeric",minute:"2-digit"})}</time>
            <div><strong>{visit.label}</strong><p>{visit.description}</p></div>
            <span className={[styles.status,statusStyles[visit.status]].join(" ")}>{statusLabels[visit.status]}</span>
          </li>
        )}</ul>}
    </div>
  </section>;
}
