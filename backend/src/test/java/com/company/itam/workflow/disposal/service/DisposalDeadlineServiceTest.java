package com.company.itam.workflow.disposal.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class DisposalDeadlineServiceTest {
    @Test void sevenWorkingDaysExcludeWeekendsAndApprovalDay() {
        assertThat(DisposalDeadlineService.addWorkingDays(LocalDate.of(2026,11,3),7)).isEqualTo(LocalDate.of(2026,11,12));
        assertThat(DisposalDeadlineService.addWorkingDays(LocalDate.of(2026,11,6),7)).isEqualTo(LocalDate.of(2026,11,17));
    }
}
