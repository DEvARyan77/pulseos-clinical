const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("../../apps/web/node_modules/typescript");

const source = fs.readFileSync(
  path.join(__dirname, "../../apps/web/src/lib/calendar-export.ts"), "utf8"
);
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
}).outputText;
const mod = {exports: {}};
vm.runInNewContext(compiled, {
  module: mod, exports: mod.exports, TextEncoder, Blob, URL, setTimeout
}, {filename: "calendar-export.js"});
const {buildCalendarIcs} = mod.exports;

const visit = {
  id: "e8b7f690-7e76-4ad9-a31f-d3f78c993b9a",
  scheduledAt: "2026-11-15T10:00:00Z",
  label: "Demo patient",
  description: "FOLLOW_UP",
  status: "SCHEDULED"
};
const generate = entries => buildCalendarIcs(entries, "PulseOS demo", new Date("2026-10-15T00:00:00Z"));

test("creates calendar with UTC start/end, private events and CRLF separators", () => {
  const ics = generate([visit]);
  assert.match(ics, /^BEGIN:VCALENDAR\r\nVERSION:2.0\r\n/);
  assert.match(ics, /\r\nDTSTART:20261115T100000Z\r\n/);
  assert.match(ics, /\r\nDTEND:20261115T103000Z\r\n/);
  assert.match(ics, /\r\nDTSTAMP:20261015T000000Z\r\n/);
  assert.match(ics, /\r\nCLASS:PRIVATE\r\n/);
  assert.match(ics, /\r\nSTATUS:CONFIRMED\r\n/);
  assert.match(ics, /\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n$/);
  assert.equal(ics.split("BEGIN:VEVENT").length - 1, 1);
});

test("cancellation uses standard cancelled status with a stable event UID", () => {
  const ics = generate([{...visit, status: "CANCELLED"}]);
  assert.match(ics, /STATUS:CANCELLED\r\n/);
  assert.match(ics, /UID:e8b7f690-7e76-4ad9-a31f-d3f78c993b9a@pulseos\.local/);
});

test("escapes calendar text to prevent injected properties and folds UTF-8 octets", () => {
  const ics = generate([{
    ...visit,
    label: "Test,\nBEGIN:VTODO;💚" + "💚".repeat(45),
    description: "Consultation\\follow-up"
  }]);
  assert.match(ics, /Test\\,\\nBEGIN:VTODO\\;/);
  assert.equal((ics.match(/\r\nBEGIN:VTODO/g)||[]).length, 0);
  assert.match(ics, /Consultation\\\\follow-up/);
  for (const line of ics.split("\r\n")) {
    assert.ok(Buffer.byteLength(line, "utf8") <= 75, "overlong ICS line");
  }
  assert.ok(ics.includes("\r\n "), "long content lines should be folded");
});

test("skips invalid dates and duplicate identifiers", () => {
  const ics = generate([visit, visit, {...visit, id: "bad", scheduledAt: "invalid"}, {...visit, id: "\n"}]);
  assert.equal((ics.match(/BEGIN:VEVENT/g)||[]).length, 1);
});
