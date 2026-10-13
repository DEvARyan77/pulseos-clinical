"use client";
import {FormEvent,useEffect,useState} from "react";
import styles from "./profile-editor.module.css";
type Profile={username:string;email:string;displayName:string;role:string};
export default function ProfileEditor(){
  const [profile,setProfile]=useState<Profile|null>(null);const [name,setName]=useState("");
  const [busy,setBusy]=useState(false);const [error,setError]=useState("");const [success,setSuccess]=useState("");
  useEffect(()=>{
    let alive=true;
    void fetch("/api/auth/profile",{cache:"no-store"}).then(async response=>{
      const data=await response.json() as Profile&{error?:string};
      if(!response.ok)throw new Error(data.error??"Profile unavailable");
      if(alive){setProfile(data);setName(data.displayName);}
    }).catch(cause=>{if(alive)setError(cause instanceof Error?cause.message:"Profile unavailable");});
    return()=>{alive=false;};
  },[]);
  async function save(event:FormEvent<HTMLFormElement>){
    event.preventDefault();setBusy(true);setError("");setSuccess("");
    try{
      const response=await fetch("/api/auth/profile",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({displayName:name.trim()})});
      const data=await response.json() as Profile&{error?:string};
      if(!response.ok)throw new Error(data.error??"Could not update profile");
      setProfile(data);setName(data.displayName);setSuccess("Profile saved.");
    }catch(cause){setError(cause instanceof Error?cause.message:"Could not update profile");}finally{setBusy(false);}
  }
  return <section className={styles.card}>
    <p className={styles.eyebrow}>ACCOUNT SETTINGS</p><h1>My profile</h1>
    <p className={styles.helper}>Manage your display name. Username and verified email remain read-only until an email re-verification flow is available.</p>
    {error&&<p role="alert" className={styles.error}>{error}</p>}
    {success&&<p role="status" className={styles.success}>{success}</p>}
    {profile?<form onSubmit={save} className={styles.form}>
      <label>Display name<input value={name} onChange={event=>setName(event.target.value)} maxLength={80} minLength={2} required/></label>
      <label>Username<input value={profile.username} readOnly/></label>
      <label>Verified email<input value={profile.email} readOnly/></label>
      <label>Account role<input value={profile.role} readOnly/></label>
      <button type="submit" disabled={busy||name.trim().length<2}>{busy?"Saving…":"Save profile"}</button>
    </form>:!error&&<p className={styles.helper}>Loading your profile…</p>}
  </section>;
}
