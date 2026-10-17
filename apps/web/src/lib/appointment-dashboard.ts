export type DashboardVisit = {
  id: string;
  patientId: string;
  scheduledAt: string;
  visitType: string;
  status: string;
};
export type AppointmentDashboardData = {
  generatedAt: string;
  date: string;
  zoneId: string;
  todayVisits: number;
  pendingCheckins: number;
  missedAppointments: number;
  visits: DashboardVisit[];
  remindersEnabled: boolean;
  reminderWindowStart: string;
  reminderCounts: { status: string; count: number }[];
};

export function isMissedVisit(visit: DashboardVisit, at: string): boolean {
  return visit.status === "SCHEDULED" &&
    new Date(visit.scheduledAt).getTime() + 30 * 60 * 1000 <= new Date(at).getTime();
}

export function reminderTotals(counts: AppointmentDashboardData["reminderCounts"]) {
  const totals = { CLAIMED: 0, ACCEPTED: 0, FAILED: 0, SKIPPED: 0 };
  for (const item of counts) {
    if (Object.hasOwn(totals, item.status) && Number.isSafeInteger(item.count) && item.count >= 0) {
      totals[item.status as keyof typeof totals] += item.count;
    }
  }
  return totals;
}
