"use client";
import {Monitor,Moon,Sun} from "lucide-react";
import {useEffect,useState} from "react";
type Preference="system"|"light"|"dark";
const options=[{id:"light" as const,title:"Light",Icon:Sun},{id:"dark" as const,title:"Dark",Icon:Moon},{id:"system" as const,title:"System",Icon:Monitor}];
export default function ThemeSwitcher(){
  const [preference,setPreference]=useState<Preference>("system");
  const [ready,setReady]=useState(false);
  useEffect(()=>{
    try{const saved=localStorage.getItem("pulseos-theme");if(saved==="system"||saved==="dark"||saved==="light")setPreference(saved);}catch{}
    setReady(true);
  },[]);
  useEffect(()=>{
    if(!ready)return;
    const media=matchMedia("(prefers-color-scheme: dark)");
    const apply=()=>{document.documentElement.dataset.theme=preference==="system"?(media.matches?"dark":"light"):preference;};
    apply();media.addEventListener("change",apply);
    try{localStorage.setItem("pulseos-theme",preference);}catch{}
    return()=>media.removeEventListener("change",apply);
  },[preference,ready]);
  return <div className="themes" role="group" aria-label="Color scheme">{options.map(({id,title,Icon})=><button key={id} type="button" title={title} aria-label={title+" theme"} aria-pressed={ready&&preference===id} className={ready&&preference===id?"chosen":""} onClick={()=>setPreference(id)}><Icon size={15}/><span>{title}</span></button>)}</div>;
}
