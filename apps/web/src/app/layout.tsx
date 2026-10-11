import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PulseOS | Clinical Workspace",
  description: "A fictional-data clinical operations portfolio application"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="en"><body>{children}</body></html>;
}
