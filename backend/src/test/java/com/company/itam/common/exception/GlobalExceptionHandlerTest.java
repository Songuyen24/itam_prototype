package com.company.itam.common.exception;

import com.company.itam.common.util.MessageHelper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new MessageHelper(new StaticMessageSource()));

    @Test void missingReferencedActorIsNotReportedAsDeletingAnInUseCatalog() {
        var response = handler.handleDataIntegrityViolationException(new DataIntegrityViolationException(
                "insert or update on table audit_logs violates foreign key constraint audit_logs_actor_user_id_fkey; Key (actor_user_id) is not present in table users"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("DATA_INTEGRITY_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("actor_user_id", "audit_logs");
    }

    @Test void deletingAReferencedCatalogKeepsExistingConflictCode() {
        var response = handler.handleDataIntegrityViolationException(new DataIntegrityViolationException(
                "update or delete on table locations violates foreign key constraint; Key (location_id) is still referenced from table assets"));
        assertThat(response.getBody().getCode()).isEqualTo("CATALOG_IN_USE");
    }
}
