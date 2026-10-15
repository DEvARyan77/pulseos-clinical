const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const ts=require("../../apps/web/node_modules/typescript");
const source=fs.readFileSync(path.join(__dirname,"../../apps/web/src/lib/clinician-availability.ts"),"utf8");
const js=ts.transpileModule(source,{compilerOptions:{
  module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
const ctx={module:{exports:{}},Intl,Date};ctx.exports=ctx.module.exports;
vm.runInNewContext(js,ctx);
const {isSlotAllowed}=ctx.module.exports;
const rules={configured:true,zoneId:"Asia/Kolkata",
  workDays:["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
  startTime:"09:00",endTime:"18:00",breakStartTime:"13:00",breakEndTime:"14:00",
  blockedDates:["2026-11-16"]};
const at=(day,h,m)=>new Date(Date.UTC(2026,10,day,h-5,m-30));
test("working hours and 30-minute breaks are enforced",()=>{
  assert.equal(isSlotAllowed(at(17,9,0),rules),true);
  assert.equal(isSlotAllowed(at(17,12,30),rules),true);
  assert.equal(isSlotAllowed(at(17,12,45),rules),false);
  assert.equal(isSlotAllowed(at(17,14,0),rules),true);
  assert.equal(isSlotAllowed(at(17,17,30),rules),true);
  assert.equal(isSlotAllowed(at(17,17,45),rules),false);
  assert.equal(isSlotAllowed(at(16,10,0),rules),false);
  assert.equal(isSlotAllowed(at(15,10,0),rules),false);
});
test("unconfigured schedule preserves legacy booking behavior",()=>{
  assert.equal(isSlotAllowed(at(15,2,0),null),true);
  assert.equal(isSlotAllowed(at(15,2,0),{...rules,configured:false}),true);
});
test("invalid zones never bypass configured rules",()=>{
  assert.equal(isSlotAllowed(at(17,10,0),{...rules,zoneId:"Invalid/Zone"}),false);
});
