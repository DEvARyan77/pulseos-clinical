/** The client previews a clinician's availability; the API enforces it. */
export const WEEKDAYS=["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"] as const;
export type Weekday=(typeof WEEKDAYS)[number];
export type ClinicianAvailability={
  configured:boolean;zoneId:string;workDays:Weekday[];
  startTime:string;endTime:string;breakStartTime:string|null;
  breakEndTime:string|null;blockedDates:string[];
};
export function localTimeZone():string{
  try{return Intl.DateTimeFormat().resolvedOptions().timeZone||"Etc/UTC";}
  catch{return "Etc/UTC";}
}
type Clock={day:Weekday;date:string;minute:number};
function clock(instant:Date,zone:string):Clock{
  const parts=new Intl.DateTimeFormat("en-US",{
    timeZone:zone,weekday:"long",year:"numeric",month:"2-digit",
    day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"
  }).formatToParts(instant);
  const get=(part:string)=>parts.find(item=>item.type===part)?.value??"";
  return {day:get("weekday").toUpperCase() as Weekday,
    date:[get("year"),get("month"),get("day")].join("-"),
    minute:Number(get("hour"))*60+Number(get("minute"))};
}
const minutes=(time:string)=>Number(time.slice(0,2))*60+Number(time.slice(3,5));
/** HTML datetime-local is in device time, while availability is clinician-zone time. */
export function isSlotAllowed(start:Date,rule:ClinicianAvailability|null):boolean{
  if(!rule?.configured)return true;
  if(!Number.isFinite(start.getTime()))return false;
  try{
    const a=clock(start,rule.zoneId);
    const b=clock(new Date(start.getTime()+30*60000),rule.zoneId);
    if(a.date!==b.date||!rule.workDays.includes(a.day)||
      rule.blockedDates.includes(a.date))return false;
    if(a.minute<minutes(rule.startTime)||b.minute>minutes(rule.endTime)||
      b.minute<=a.minute)return false;
    if(rule.breakStartTime&&rule.breakEndTime&&
      a.minute<minutes(rule.breakEndTime)&&b.minute>minutes(rule.breakStartTime))
      return false;
    return true;
  }catch{return false;}
}
