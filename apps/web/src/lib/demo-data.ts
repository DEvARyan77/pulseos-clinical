/** Entirely fictional sample records. Never replace with real PHI. */
export type Patient={
 id:string;name:string;initials:string;age:number;gender:string;time:string;reason:string;room:string;
 status:"In room"|"Checked in"|"Scheduled"|"Arrived";
 bp:string;heartRate:number;temperature:string;oxygen:string;
 allergies:string[];diagnoses:string[];medications:string[];
};
export const patients:Patient[]=[
 {id:"DEMO-1001",name:"Marisol Vega",initials:"MV",age:34,gender:"Female",time:"8:30 AM",reason:"Hypertension follow-up",room:"Room 3",status:"In room",bp:"128/82",heartRate:76,temperature:"98.4 °F",oxygen:"99%",allergies:["Penicillin · rash","Shellfish · hives"],diagnoses:["Essential hypertension","Generalized anxiety","Seasonal allergic rhinitis"],medications:["Lisinopril 10 mg","Sertraline 50 mg","Cetirizine 10 mg"]},
 {id:"DEMO-1002",name:"Daniel Kim",initials:"DK",age:67,gender:"Male",time:"9:00 AM",reason:"Blood pressure review",room:"Room 5",status:"Checked in",bp:"136/84",heartRate:71,temperature:"98.1 °F",oxygen:"97%",allergies:["None documented in demo"],diagnoses:["Hypertension","Hyperlipidemia"],medications:["Amlodipine 5 mg","Atorvastatin 20 mg"]},
 {id:"DEMO-1003",name:"Aisha Patel",initials:"AP",age:28,gender:"Female",time:"9:30 AM",reason:"Annual wellness",room:"Room 2",status:"Checked in",bp:"114/72",heartRate:68,temperature:"98.6 °F",oxygen:"100%",allergies:["None documented in demo"],diagnoses:["Routine wellness exam"],medications:["None documented in demo"]},
 {id:"DEMO-1004",name:"Robert Chen",initials:"RC",age:52,gender:"Male",time:"10:00 AM",reason:"Diabetes review",room:"Room 4",status:"Arrived",bp:"126/79",heartRate:73,temperature:"98.5 °F",oxygen:"98%",allergies:["Sulfonamides · rash"],diagnoses:["Type 2 diabetes","Hypertension"],medications:["Metformin 500 mg","Losartan 25 mg"]},
 {id:"DEMO-1005",name:"Elena Cruz",initials:"EC",age:46,gender:"Female",time:"10:30 AM",reason:"Back pain consultation",room:"Room 6",status:"Scheduled",bp:"122/76",heartRate:78,temperature:"98.2 °F",oxygen:"99%",allergies:["None documented in demo"],diagnoses:["Back pain"],medications:["None documented in demo"]},
 {id:"DEMO-1006",name:"Marcus Bell",initials:"MB",age:39,gender:"Male",time:"11:00 AM",reason:"Respiratory symptoms",room:"Room 1",status:"Scheduled",bp:"120/78",heartRate:82,temperature:"99.0 °F",oxygen:"98%",allergies:["None documented in demo"],diagnoses:["Upper respiratory symptoms"],medications:["None documented in demo"]},
 {id:"DEMO-1007",name:"Priya Shah",initials:"PS",age:31,gender:"Female",time:"11:30 AM",reason:"Post-op follow-up",room:"Room 7",status:"Scheduled",bp:"118/76",heartRate:70,temperature:"98.3 °F",oxygen:"99%",allergies:["Latex · reaction"],diagnoses:["Postoperative follow-up"],medications:["None documented in demo"]}
];
export const events=[
 {time:"8:42 AM",title:"Vitals recorded",description:"Sample observations added by demo nurse"},
 {time:"8:38 AM",title:"Patient roomed",description:"Room assigned for fictional visit"},
 {time:"8:30 AM",title:"Checked in",description:"Illustrative front desk event"}
];
export const earlierVisits=[
 {date:"Mar 12",name:"Follow-up",detail:"Blood pressure review"},
 {date:"Jan 10",name:"Annual exam",detail:"Wellness consultation"},
 {date:"Aug 18",name:"Consultation",detail:"Medication discussion"}
];
export const initialTasks=[
 {id:1,name:"Review home BP log",done:false},
 {id:2,name:"Review sample lab results",done:false},
 {id:3,name:"Prepare follow-up summary",done:true}
];
