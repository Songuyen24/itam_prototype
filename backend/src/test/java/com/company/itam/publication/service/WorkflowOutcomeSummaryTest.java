package com.company.itam.publication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowOutcomeSummaryTest {
    @Test void distinguishesPerformedCancelledAndRejectedGroupsInFinalDocument() throws Exception {
        var snapshot=new ObjectMapper().readTree("{\"groups\":[{\"status\":\"COMPLETED\"},{\"status\":\"CANCELLED\"},{\"status\":\"REJECTED\"}]}");
        assertThat(BilingualPdfGenerator.groupOutcomeSummary(snapshot)).contains("Completed 1","Cancelled 1","Rejected 1");
        assertThat(BilingualPdfGenerator.groupOutcomeSummary(new ObjectMapper().readTree("{}"))).isEmpty();
    }
}
