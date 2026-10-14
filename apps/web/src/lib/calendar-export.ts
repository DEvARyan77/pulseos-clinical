/**
 * Private, one-time iCalendar exports for fictional PulseOS appointments.
 * The file is generated locally from the signed-in user's displayed visits.
 * It is not a live calendar subscription or a public appointment feed.
 */
export type CalendarExportEntry = {
  id: string;
  scheduledAt: string;
  label: string;
  description: string;
  status: string;
};

const encoder = new TextEncoder();
const escapeText = (value: string) =>
  value.replace(/\\/g, "\\\\").replace(/\r\n|\r|\n/g, "\\n")
    .replace(/;/g, "\\;").replace(/,/g, "\\,");

/** RFC 5545 content lines must be at most 75 UTF-8 octets (not characters). */
function foldLine(line: string): string {
  let segment = "";
  let bytes = 0;
  const parts: string[] = [];
  for (const character of line) {
    const width = encoder.encode(character).length;
    if (bytes + width > 75) {
      parts.push(segment);
      segment = " ";
      bytes = 1;
    }
    segment += character;
    bytes += width;
  }
  parts.push(segment);
  return parts.join("\r\n");
}

const timestamp = (date: Date) =>
  date.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");

/** Pure generator: caller supplies only its own authorized, visible entries. */
export function buildCalendarIcs(
  entries: readonly CalendarExportEntry[],
  name = "PulseOS demo appointments",
  generatedAt = new Date()
): string {
  const stamp = timestamp(generatedAt);
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//PulseOS//Fictional Appointment Calendar//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    "X-WR-CALNAME:" + escapeText(name),
  ];
  const seen = new Set<string>();
  for (const entry of entries) {
    const start = new Date(entry.scheduledAt);
    const id = entry.id.replace(/[^a-z0-9-]/gi, "");
    if (!Number.isFinite(start.getTime()) || !id || seen.has(id)) continue;
    seen.add(id);
    const end = new Date(start.getTime() + 30 * 60 * 1000);
    const status = entry.status === "CANCELLED" ? "CANCELLED" : "CONFIRMED";
    lines.push(
      "BEGIN:VEVENT",
      "UID:" + id + "@pulseos.local",
      "DTSTAMP:" + stamp,
      "DTSTART:" + timestamp(start),
      "DTEND:" + timestamp(end),
      "SUMMARY:" + escapeText("Demo appointment · " + entry.label),
      "DESCRIPTION:" + escapeText(
        entry.description + "\nVisit status: " + entry.status.replace(/_/g, " ") +
        "\nFictional PulseOS test appointment only. Not for real clinical care."
      ),
      "STATUS:" + status,
      "CLASS:PRIVATE",
      "TRANSP:OPAQUE",
      "END:VEVENT"
    );
  }
  lines.push("END:VCALENDAR");
  return lines.map(foldLine).join("\r\n") + "\r\n";
}

export function downloadCalendarIcs(
  entries: readonly CalendarExportEntry[],
  filename = "pulseos-appointments.ics",
  name = "PulseOS demo appointments"
): void {
  if (!entries.length) return;
  const ics = buildCalendarIcs(entries, name);
  const blob = new Blob([ics], { type: "text/calendar;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename.replace(/[^a-z0-9_.-]/gi, "-").slice(0, 100);
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
