const {test}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const ts=require("../../apps/web/node_modules/typescript");
const source=fs.readFileSync(path.join(__dirname,"../../apps/web/src/lib/clinical-proxy.ts"),"utf8");
const js=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;

function gateway({enabled=true,token="private-demo-token",response={ok:true,status:200,text:async()=>'{"ok":true}'},timeout=false}={}) {
  const calls=[];
  const ctx={module:{exports:{}},process:{env:{PULSEOS_AUTH_ENABLED:String(enabled),API_INTERNAL_URL:"http://internal"}},
    URL,Error,AbortSignal:{timeout:ms=>ms},
    fetch:async(url,options)=>{calls.push({url,options});if(timeout){const e=new Error();e.name="TimeoutError";throw e;}return response;},
    require:name=>name==="next/headers"?{cookies:async()=>({get:()=>token?{value:token}:undefined})}:
      {NextResponse:{json:(body,init)=>({body,...init})}}};
  ctx.exports=ctx.module.exports;vm.runInNewContext(js,ctx);
  return {proxy:ctx.module.exports.clinicalProxy,calls};
}
function request(origin="https://demo.example",body={}) {
  return {headers:new Map([["origin",origin],["host","demo.example"]]),json:async()=>body};
}
test("dashboard requests keep bearer credentials on the server and disable caching",async()=>{
  const {proxy,calls}=gateway();
  const result=await proxy(request(),"/api/v1/demo-appointments/dashboard","GET");
  assert.equal(calls[0].url,"http://internal/api/v1/demo-appointments/dashboard");
  assert.equal(calls[0].options.headers.Authorization,"Bearer private-demo-token");
  assert.equal(calls[0].options.cache,"no-store");
  assert.equal(result.headers["Cache-Control"],"no-store");
  assert.equal(JSON.stringify(result).includes("private-demo-token"),false);
});
test("disabled auth, missing sessions and cross-origin writes fail without calling backend",async()=>{
  for(const [options,origin,status] of [[{enabled:false},"https://demo.example",503],
    [{token:null},"https://demo.example",401],[{},"https://foreign.example",403]]) {
    const {proxy,calls}=gateway(options);
    const result=await proxy(request(origin),"/api/v1/demo-appointments","POST");
    assert.equal(result.status,status);
    assert.equal(result.headers["Cache-Control"],"no-store");
    assert.equal(calls.length,0);
  }
});
test("mail-producing appointment operations allow provider time and warn against blind retries",async()=>{
  for(const [method,route] of [["POST","/api/v1/demo-appointments"],
    ["PATCH","/api/v1/demo-appointments/12345678-1234-1234-1234-123456789abc/advance"],
    ["POST","/api/v1/patient-portal/appointments/12345678-1234-1234-1234-123456789abc/check-in"]]) {
    const {proxy,calls}=gateway({timeout:true});
    const result=await proxy(request(),route,method);
    assert.equal(calls[0].options.signal,65000);
    assert.match(result.body.error,/Check the appointment list before retrying/);
    assert.equal(result.headers["Cache-Control"],"no-store");
  }
});
test("malformed successful upstream responses become gateway errors",async()=>{
  const {proxy}=gateway({response:{ok:true,status:200,text:async()=>"<html>bad gateway</html>"}});
  const result=await proxy(request(),"/api/v1/demo-appointments/dashboard","GET");
  assert.equal(result.status,502);
  assert.equal(result.headers["Cache-Control"],"no-store");
});


test("non-object JSON is rejected before contacting the backend",async()=>{
  for(const body of [null,[],"text",42]) {
    const {proxy,calls}=gateway();
    const result=await proxy(request("https://demo.example",body),"/api/v1/demo-appointments","POST");
    assert.equal(result.status,400);
    assert.equal(calls.length,0);
  }
});
