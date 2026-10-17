package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AppointmentConflictHandlerTest {
  @Test void concurrentUpdatesReturnSafeConflict() {
    var response=new AppointmentConflictHandler().conflict(
      new ObjectOptimisticLockingFailureException("private-entity","private-id"));
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody().get("error")).contains("Refresh").doesNotContain("private");
  }
}
