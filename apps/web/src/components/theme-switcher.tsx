"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useEffect, useSyncExternalStore } from "react";

type Preference = "system" | "light" | "dark";
const options = [
  { id: "light" as const, title: "Light", Icon: Sun },
  { id: "dark" as const, title: "Dark", Icon: Moon },
  { id: "system" as const, title: "System", Icon: Monitor },
];

const eventName = "pulseos-theme-change";
function readPreference(): Preference {
  try {
    const saved = localStorage.getItem("pulseos-theme");
    if (saved === "system" || saved === "dark" || saved === "light") return saved;
  } catch { /* Browser storage is optional. */ }
  return "system";
}
function subscribe(listener: () => void) {
  window.addEventListener("storage", listener);
  window.addEventListener(eventName, listener);
  return () => {
    window.removeEventListener("storage", listener);
    window.removeEventListener(eventName, listener);
  };
}

export default function ThemeSwitcher() {
  const preference = useSyncExternalStore(subscribe, readPreference, () => "system");

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const apply = () => {
      document.documentElement.dataset.theme = preference === "system"
        ? (media.matches ? "dark" : "light") : preference;
    };
    apply();
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, [preference]);

  function choose(next: Preference) {
    try { localStorage.setItem("pulseos-theme", next); } catch { /* Optional storage. */ }
    window.dispatchEvent(new Event(eventName));
  }

  return <div className="themes" role="group" aria-label="Color scheme">
    {options.map(({ id, title, Icon }) =>
      <button key={id} type="button" title={title} aria-label={title + " theme"}
        aria-pressed={preference === id} className={preference === id ? "chosen" : ""}
        onClick={() => choose(id)}>
        <Icon size={15} /><span>{title}</span>
      </button>)}
  </div>;
}
