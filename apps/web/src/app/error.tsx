"use client";
import {useEffect} from "react";
export default function ErrorPage({error,reset}:{error:Error & {digest?:string};reset:()=>void}){
 useEffect(()=>{console.error("PulseOS page temporarily unavailable",error.name);},[error]);
 return <main style={{minHeight:"100vh",display:"grid",placeItems:"center",padding:"22px",background:"#f7f7f5"}}>
  <section style={{width:"100%",maxWidth:"490px",border:"1px solid #e0e1df",borderRadius:"16px",background:"#fff",padding:"30px",color:"#242832",fontFamily:"Arial,sans-serif"}}>
   <div style={{fontWeight:800,fontSize:"24px",marginBottom:"22px"}}>Pulse<span style={{color:"#bc8b12"}}>OS</span></div>
   <h1 style={{fontSize:"22px",marginBottom:"12px"}}>The application is taking longer to respond</h1>
   <p style={{fontSize:"14px",lineHeight:1.7,color:"#616774"}}>Render may be waking the API. Your sign-in cookie has not been cleared. Wait briefly, then retry instead of signing in again.</p>
   <button type="button" onClick={()=>reset()} style={{display:"block",marginTop:"24px",border:0,borderRadius:"9px",background:"#f4c64d",color:"#1a202b",fontWeight:800,padding:"12px 20px",cursor:"pointer"}}>Try again</button>
  </section>
 </main>;
}
