package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class ClinicianAvailabilityTest {
    private static final ZoneId ZONE=ZoneId.of("Asia/Kolkata");
    private static ClinicianAvailability rules(){
        return new ClinicianAvailability("clinician",ZONE.getId(),
            List.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),
            LocalTime.of(9,0),LocalTime.of(18,0),
            LocalTime.of(13,0),LocalTime.of(14,0),
            List.of(LocalDate.of(2026,11,16)));
    }
    private static Instant at(int year,int month,int day,int hour,int minute){
        return ZonedDateTime.of(year,month,day,hour,minute,0,0,ZONE).toInstant();
    }

    @Test void admitsHalfHourVisitsOnWorkingDaysAndEdges(){
        var schedule=rules();
        assertThat(schedule.allows(at(2026,11,17,9,0))).isTrue();
        assertThat(schedule.allows(at(2026,11,17,12,30))).isTrue();
        assertThat(schedule.allows(at(2026,11,17,14,0))).isTrue();
        assertThat(schedule.allows(at(2026,11,17,17,30))).isTrue();
    }

    @Test void rejectsBreakOverlapsWeekendsBlackoutsAndOutsideHours(){
        var schedule=rules();
        assertThat(schedule.allows(at(2026,11,17,12,45))).isFalse();
        assertThat(schedule.allows(at(2026,11,17,13,30))).isFalse();
        assertThat(schedule.allows(at(2026,11,17,8,45))).isFalse();
        assertThat(schedule.allows(at(2026,11,17,17,45))).isFalse();
        assertThat(schedule.allows(at(2026,11,15,10,0))).isFalse();
        assertThat(schedule.allows(at(2026,11,16,10,0))).isFalse();
    }

    @Test void availabilityUpdatesAreScopedToSignedInClinician(){
        var storage=mock(ClinicianAvailabilityRepository.class);
        var appointments=mock(DemoAppointmentRepository.class);
        var controller=new ClinicianAvailabilityController(storage,appointments);
        var jwt=mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("clinician");
        var body=new ClinicianAvailabilityController.SaveAvailabilityRequest(
            ZONE.getId(),List.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY),
            LocalTime.of(9,0),LocalTime.of(18,0),
            LocalTime.of(13,0),LocalTime.of(14,0),
            List.of(LocalDate.now(ZONE).plusDays(1)));
        assertThat(controller.save(jwt,body).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(appointments).lockSchedule("clinician");
        verify(storage).saveAndFlush(argThat(row->
            row.getClinicianUsername().equals("clinician") && row.getBlockedDates().size()==1));
    }

    @Test void invalidWorkHoursAndDuplicateDaysDoNotPersist(){
        var storage=mock(ClinicianAvailabilityRepository.class);
        var controller=new ClinicianAvailabilityController(storage,mock(DemoAppointmentRepository.class));
        var jwt=mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("clinician");
        var badHours=new ClinicianAvailabilityController.SaveAvailabilityRequest(
            ZONE.getId(),List.of(DayOfWeek.MONDAY),LocalTime.of(18,0),LocalTime.of(9,0),
            null,null,List.of());
        assertThat(controller.save(jwt,badHours).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var duplicateDays=new ClinicianAvailabilityController.SaveAvailabilityRequest(
            ZONE.getId(),List.of(DayOfWeek.MONDAY,DayOfWeek.MONDAY),
            LocalTime.of(9,0),LocalTime.of(18,0),null,null,List.of());
        assertThat(controller.save(jwt,duplicateDays).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(storage);
    }
}
