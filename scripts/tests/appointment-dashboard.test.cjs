const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const ts=require("../../apps/web/node_modules/typescript");
const source=fs.readFileSync(path.join(__dirname,"../../apps/web/src/lib/appointment-dashboard.ts"),"utf8");
const js=ts.transpileModule(source,{compilerOptions:{
  module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
const ctx={module:{exports:{}},Date};ctx.exports=ctx.module.exports;
vm.runInNewContext(js,ctx);
const {isMissedVisit,reminderTotals}=ctx.module.exports;
const visit={id:"demo",patientId:"demo",scheduledAt:"2026-11-01T10:00:00Z",visitType:"FOLLOW_UP",status:"SCHEDULED"};

test("missed check-in begins exactly at the end of a 30-minute slot",()=>{
  assert.equal(isMissedVisit(visit,"2026-11-01T10:29:59.999Z"),false);
  assert.equal(isMissedVisit(visit,"2026-11-01T10:30:00Z"),true);
  assert.equal(isMissedVisit({...visit,status:"CHECKED_IN"},"2026-11-01T11:00:00Z"),false);
  for(const status of ["IN_CARE","COMPLETED","CANCELLED"])
    assert.equal(isMissedVisit({...visit,status},"2026-11-01T11:00:00Z"),false);
});
test("invalid times never flag a missed visit",()=>{
  assert.equal(isMissedVisit({...visit,scheduledAt:"invalid"},"2026-11-01T11:00:00Z"),false);
});
test("reminder totals distinguish accepted, failed, skipped and unknown outcomes",()=>{
  const totals=reminderTotals([{status:"ACCEPTED",count:125},{status:"FAILED",count:4},
    {status:"CLAIMED",count:2},{status:"SKIPPED",count:7}]);
  assert.equal(totals.ACCEPTED,125);
  assert.equal(totals.FAILED,4);
  assert.equal(totals.CLAIMED,2);
  assert.equal(totals.SKIPPED,7);
});
test("empty and invalid statistics do not fabricate deliveries",()=>{
  const totals=reminderTotals([{status:"DELIVERED",count:4},{status:"ACCEPTED",count:-1},
    {status:"FAILED",count:NaN},{status:"SKIPPED",count:1.2},{status:"__proto__",count:2}]);
  assert.equal(Object.values(totals).reduce((sum,value)=>sum+value,0),0);
});
