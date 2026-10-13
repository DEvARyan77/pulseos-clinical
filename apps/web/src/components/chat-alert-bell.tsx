"use client";
import Link from "next/link";
import {Bell} from "lucide-react";
import {useEffect,useRef,useState} from "react";
import styles from "./chat-alert-bell.module.css";
type Message={id:string;sender:string;recipient:string;readAt:string|null};
export default function ChatAlertBell(){
  const [count,setCount]=useState(0);
  const [newSender,setNewSender]=useState("");
  const known=useRef(new Set<string>());
  const primed=useRef(false);
  useEffect(()=>{
    let active=true;
    async function poll(){
      if(document.visibilityState!=="visible")return;
      try{
        const response=await fetch("/api/staff-chat/messages",{cache:"no-store"});
        if(!response.ok)return;
        const data=await response.json() as Message[];
        if(!active||!Array.isArray(data))return;
        const unread=data.filter(item=>!item.readAt&&item.sender!==item.recipient);
        setCount(unread.length);
        if(primed.current){
          const recent=unread.find(item=>!known.current.has(item.id));
          if(recent)setNewSender(recent.sender);
        }
        unread.forEach(item=>known.current.add(item.id));
        primed.current=true;
      }catch{ /* Background polling is best-effort. */ }
    }
    void poll();
    const timer=window.setInterval(()=>{void poll();},20000);
    const onVisibility=()=>{if(document.visibilityState==="visible")void poll();};
    document.addEventListener("visibilitychange",onVisibility);
    return()=>{active=false;window.clearInterval(timer);document.removeEventListener("visibilitychange",onVisibility);};
  },[]);
  return <div className={styles.wrap}>
    <Link href="/messages" className={styles.bell} aria-label={`Team messages: ${count} unread`}>
      <Bell size={16}/>{count>0&&<span className={styles.count}>{count>99?"99+":count}</span>}
    </Link>
    {newSender&&<div className={styles.toast} role="status">
      <span>New team message from <strong>{newSender}</strong></span>
      <Link href={`/messages?to=${encodeURIComponent(newSender)}`} onClick={()=>setNewSender("")}>Open</Link>
      <button type="button" aria-label="Dismiss message alert" onClick={()=>setNewSender("")}>×</button>
    </div>}
  </div>;
}
