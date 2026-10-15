package com.pulseos.appointments;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Clinician-owned wall-clock availability. Existing appointments are never moved. */
@Entity
@Table(name = "clinician_availability")
public class ClinicianAvailability {
    @Id
    @Column(name = "clinician_username", nullable = false, length = 80)
    private String clinicianUsername;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId;

    @Column(name = "working_days", nullable = false, length = 90)
    private String workingDays;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "break_start_at")
    private LocalTime breakStartAt;

    @Column(name = "break_end_at")
    private LocalTime breakEndAt;

    @Column(name = "blocked_dates", nullable = false, columnDefinition = "text")
    private String blockedDates;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ClinicianAvailability() {}

    public ClinicianAvailability(String clinicianUsername, String zoneId,
        Collection<DayOfWeek> days, LocalTime opensAt, LocalTime closesAt,
        LocalTime breakStartAt, LocalTime breakEndAt, Collection<LocalDate> blockedDates) {
        this.clinicianUsername = Objects.requireNonNull(clinicianUsername);
        update(zoneId,days,opensAt,closesAt,breakStartAt,breakEndAt,blockedDates);
    }

    /** Retain the existing entity version on repeated updates. */
    public void update(String zoneId,Collection<DayOfWeek> days,LocalTime opensAt,
        LocalTime closesAt,LocalTime breakStartAt,LocalTime breakEndAt,
        Collection<LocalDate> blockedDates){
        this.zoneId = Objects.requireNonNull(zoneId);
        this.workingDays = days.stream().sorted().map(DayOfWeek::name)
            .collect(Collectors.joining(","));
        this.opensAt = Objects.requireNonNull(opensAt);
        this.closesAt = Objects.requireNonNull(closesAt);
        this.breakStartAt = breakStartAt;
        this.breakEndAt = breakEndAt;
        this.blockedDates = blockedDates.stream().sorted().map(LocalDate::toString)
            .collect(Collectors.joining(","));
    }

    public String getClinicianUsername() { return clinicianUsername; }
    public String getZoneId() { return zoneId; }
    public List<DayOfWeek> getWorkDays() {
        return Arrays.stream(workingDays.split(",")).map(DayOfWeek::valueOf).toList();
    }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public LocalTime getBreakStartAt() { return breakStartAt; }
    public LocalTime getBreakEndAt() { return breakEndAt; }
    public List<LocalDate> getBlockedDates() {
        if (blockedDates.isEmpty()) return List.of();
        return Arrays.stream(blockedDates.split(",")).map(LocalDate::parse).toList();
    }

    /** A visit consumes 30 minutes; both ends must fit the same open day. */
    public boolean allows(Instant start) {
        if (start == null) return false;
        ZonedDateTime from = start.atZone(ZoneId.of(zoneId));
        ZonedDateTime to = start.plus(Duration.ofMinutes(30)).atZone(ZoneId.of(zoneId));
        if (!from.toLocalDate().equals(to.toLocalDate())
            || !getWorkDays().contains(from.getDayOfWeek())
            || getBlockedDates().contains(from.toLocalDate())) return false;
        LocalTime begin = from.toLocalTime();
        LocalTime end = to.toLocalTime();
        if (begin.isBefore(opensAt) || end.isAfter(closesAt) || !end.isAfter(begin))
            return false;
        return breakStartAt == null ||
            !begin.isBefore(breakEndAt) || !end.isAfter(breakStartAt);
    }
}
