import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {title:"PulseOS | Clinical Workspace",description:"Fictional-data clinical workspace portfolio app"};
const init = '(function(){try{var x=localStorage.getItem("pulseos-theme");var m=matchMedia("(prefers-color-scheme: dark)").matches;document.documentElement.dataset.theme=x==="light"||x==="dark"?x:m?"dark":"light"}catch(e){}})()';
export default function RootLayout({children}:{children:React.ReactNode}) {
  return <html lang="en" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{__html:init}}/></head><body>{children}</body></html>;
}
