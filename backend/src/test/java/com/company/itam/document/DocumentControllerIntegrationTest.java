package com.company.itam.document;

import com.company.itam.auth.security.JwtTokenProvider;
import com.company.itam.document.service.LocalDocumentStorage;
import com.company.itam.document.service.LocalDocumentStorage.StoredFile;
import com.company.itam.workflow.core.enums.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentControllerIntegrationTest {

    private static final byte[] SAMPLE_PDF =
            "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void documentProperties(DynamicPropertyRegistry registry) {
        registry.add("itam.documents.storage-root", () -> storageRoot.toString());
        registry.add("itam.documents.max-file-size", () -> 10485760);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private LocalDocumentStorage storage;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<StoredFile> storedFiles = new ArrayList<>();
    private final Map<String, Long> userIds = new HashMap<>();
    private final Map<String, String> emails = new HashMap<>();
    private final Map<String, String> tokens = new HashMap<>();
    private String fixturePrefix;
    private long locationId;
    private long importId;
    private long otherImportId;
    private long handoverId;
    private long foreignHandoverId;
    private long recoveryId;
    private long disposalId;
    private long invoiceId;
    private long purchaseOrderId;
    private long otherInvoiceId;
    private long handoverReportId;
    private long foreignHandoverReportId;
    private long recoveryReportId;
    private long supplierDocumentId;
    private long wrongReportId;

    @BeforeEach
    void setUp() {
        fixturePrefix = "DOC-" + UUID.randomUUID();
        createUser("ADMIN", "ADMIN");
        createUser("IT", "IT_STAFF");
        createUser("PUR1", "PUR_STAFF");
        createUser("PUR2", "PUR_STAFF");
        createUser("USER", "USER");
        createUser("OTHER_USER", "USER");
        locationId = jdbc.queryForObject("""
                insert into locations(code, name) values (?, 'Document test location')
                returning location_id
                """, Long.class, fixturePrefix);

        importId = createTransaction("IMPORT", "PUR1", null, "IMPORT1");
        otherImportId = createTransaction("IMPORT", "PUR2", null, "IMPORT2");
        handoverId = createTransaction("HANDOVER", "IT", "USER", "HANDOVER1");
        foreignHandoverId = createTransaction("HANDOVER", "IT", "OTHER_USER", "HANDOVER2");
        recoveryId = createTransaction("RECOVERY", "IT", "USER", "RECOVERY");
        disposalId = createTransaction("DISPOSAL", "IT", null, "DISPOSAL");

        invoiceId = createDocument(importId, TransactionType.IMPORT, "INVOICE", "PUR1", false);
        purchaseOrderId = createDocument(importId, TransactionType.IMPORT, "PURCHASE_ORDER", "PUR1", false);
        otherInvoiceId = createDocument(otherImportId, TransactionType.IMPORT, "INVOICE", "PUR2", false);
        handoverReportId = createDocument(handoverId, TransactionType.HANDOVER, "HANDOVER_REPORT", "IT", true);
        foreignHandoverReportId = createDocument(foreignHandoverId, TransactionType.HANDOVER,
                "HANDOVER_REPORT", "IT", true);
        recoveryReportId = createDocument(recoveryId, TransactionType.RECOVERY, "RECOVERY_REPORT", "IT", true);
        supplierDocumentId = createDocument(handoverId, TransactionType.HANDOVER, "INVOICE", "IT", true);
        wrongReportId = createDocument(handoverId, TransactionType.HANDOVER, "RECOVERY_REPORT", "IT", true);
    }

    @AfterEach
    void cleanUpFiles() {
        storedFiles.forEach(storage::discard);
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        for (String path : List.of("/v1/transactions", "/v1/transactions/" + importId,
                "/v1/documents?transactionId=" + importId, "/v1/documents/" + invoiceId,
                "/v1/documents/" + invoiceId + "/download")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mockMvc.perform(upload(importId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/documents/{id}", invoiceId)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "IT"})
    void administratorsAndItCanReadAllTransactionTypes(String actor) throws Exception {
        mockMvc.perform(as(actor, get("/v1/transactions").param("keyword", fixturePrefix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.content[*].documentsEditable", everyItem(is(false))));
        for (long transactionId : List.of(importId, handoverId, recoveryId, disposalId)) {
            mockMvc.perform(as(actor, get("/v1/transactions/{id}", transactionId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.transactionId").value(transactionId));
        }
        for (long documentId : List.of(invoiceId, handoverReportId, recoveryReportId)) {
            mockMvc.perform(as(actor, get("/v1/documents/{id}", documentId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.documentId").value(documentId));
        }
    }

    @Test
    void secondPurchaserCanReadFirstPurchasersImportAndDocuments() throws Exception {
        mockMvc.perform(as("PUR2", get("/v1/transactions").param("keyword", fixturePrefix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].type", everyItem(is("IMPORT"))));
        mockMvc.perform(as("PUR2", get("/v1/transactions/{id}", importId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editBlockedReason").value("DOCUMENT_LOCKED"));
        mockMvc.perform(as("PUR2", get("/v1/documents").param("transactionId", Long.toString(importId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(as("PUR2", get("/v1/documents/{id}", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadedByName").value("Document test PUR1"));
        assertDownload("PUR2", invoiceId);
        assertDownload("PUR1", otherInvoiceId);
    }

    @Test
    void purchaserCannotUseForeignIdsOrAccessWarehouseAndUserLists() throws Exception {
        for (String path : List.of("/v1/transactions/" + handoverId,
                "/v1/documents?transactionId=" + handoverId,
                "/v1/documents/" + handoverReportId,
                "/v1/documents/" + handoverReportId + "/download",
                "/v1/assets", "/v1/users", "/v1/assets/1/history")) {
            mockMvc.perform(as("PUR2", get(path))).andExpect(status().isForbidden());
        }
        mockMvc.perform(as("PUR2", get("/v1/transactions").param("type", "HANDOVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void endUserCannotReadCommonListsOrMetadataEvenForOwnReport() throws Exception {
        for (String path : List.of("/v1/transactions", "/v1/transactions/" + handoverId,
                "/v1/documents?transactionId=" + handoverId, "/v1/documents/" + handoverReportId)) {
            mockMvc.perform(as("USER", get(path))).andExpect(status().isForbidden());
        }
    }

    @Test
    void endUserCanDownloadOwnHandoverAndRecoveryReports() throws Exception {
        assertDownload("USER", handoverReportId);
        assertDownload("USER", recoveryReportId);
    }

    @Test
    void endUserCannotDownloadForeignReportsSupplierFilesOrMismatchedReportType() throws Exception {
        for (long documentId : List.of(foreignHandoverReportId, invoiceId, supplierDocumentId, wrongReportId)) {
            mockMvc.perform(as("USER", get("/v1/documents/{id}/download", documentId)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as("OTHER_USER", get("/v1/documents/{id}/download", recoveryReportId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void databaseRoleOverridesClaimedAdministratorRoleInToken() throws Exception {
        String forgedRole = jwtTokenProvider.generateToken(emails.get("USER"), "ADMIN");
        mockMvc.perform(get("/v1/documents").param("transactionId", Long.toString(importId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedRole))
                .andExpect(status().isForbidden());
    }

    @Test
    void documentMetadataDoesNotExposeStorageFieldsOrUserEntities() throws Exception {
        mockMvc.perform(as("ADMIN", get("/v1/documents/{id}", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value(importId))
                .andExpect(jsonPath("$.data.originalFileName").value("sample.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSize").value(SAMPLE_PDF.length))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedFileName").doesNotExist())
                .andExpect(jsonPath("$.data.checksum").doesNotExist())
                .andExpect(jsonPath("$.data.uploadedBy").doesNotExist())
                .andExpect(jsonPath("$.data.transaction").doesNotExist());
        mockMvc.perform(as("ADMIN", get("/v1/documents").param("transactionId", Long.toString(importId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].storagePath").isEmpty())
                .andExpect(jsonPath("$.data.content[*].checksum").isEmpty());
    }

    @Test
    void downloadReturnsExactFileWithAttachmentAndSafeHeaders() throws Exception {
        mockMvc.perform(as("IT", get("/v1/documents/{id}/download", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(SAMPLE_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void documentPaginationIsStableAndConfinedToRequestedTransaction() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            String response = mockMvc.perform(as("PUR2", get("/v1/documents")
                            .param("transactionId", Long.toString(importId))
                            .param("page", Integer.toString(page)).param("size", "1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.pageNumber").value(page))
                    .andExpect(jsonPath("$.data.content[0].transactionId").value(importId))
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = objectMapper.readTree(response).get("data");
            ids.add(data.get("content").get(0).get("documentId").asLong());
        }
        assertThat(ids).containsExactlyInAnyOrder(invoiceId, purchaseOrderId);
    }

    @Test
    void transactionFiltersReturnOnlyMatchingTypeStatusAndCode() throws Exception {
        mockMvc.perform(as("ADMIN", get("/v1/transactions").param("keyword", fixturePrefix + "-HANDOVER1")
                        .param("type", "HANDOVER").param("status", "COMPLETED").param("size", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(handoverId));
        mockMvc.perform(as("ADMIN", get("/v1/transactions").param("keyword", fixturePrefix)
                        .param("type", "IMPORT").param("status", "COMPLETED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void unknownIdsReturnNotFoundForAuthorizedRoles() throws Exception {
        long missingId = Long.MAX_VALUE;
        for (String path : List.of("/v1/transactions/" + missingId,
                "/v1/documents?transactionId=" + missingId, "/v1/documents/" + missingId,
                "/v1/documents/" + missingId + "/download")) {
            mockMvc.perform(as("ADMIN", get(path))).andExpect(status().isNotFound());
        }
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101"})
    void invalidPaginationIsRejectedForBothLists(String parameter, String value) throws Exception {
        mockMvc.perform(as("ADMIN", get("/v1/transactions").param(parameter, value)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get("/v1/documents").param("transactionId", Long.toString(importId))
                        .param(parameter, value)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTransactionIdAndInvalidEnumFiltersAreRejected() throws Exception {
        mockMvc.perform(as("ADMIN", get("/v1/documents"))).andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get("/v1/documents").param("transactionId", "not-a-number")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get("/v1/transactions").param("type", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get("/v1/transactions").param("status", "APPROVED")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PUR1", "PUR2"})
    void pendingImportMutationsStayBlocked(String actor) throws Exception {
        mockMvc.perform(as(actor, upload(importId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_LOCKED"));
        mockMvc.perform(as(actor, delete("/v1/documents/{id}", invoiceId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_LOCKED"));
        assertThat(jdbc.queryForObject("select count(*) from documents where transaction_id = ?",
                Integer.class, importId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from transactions where transaction_id = ?",
                String.class, importId)).isEqualTo("PENDING");
        assertDownload(actor, invoiceId);
    }

    @Test
    void lockedDocumentCannotBeDeletedByAnotherPurchaser() throws Exception {
        jdbc.update("update documents set is_locked = true where document_id = ?", invoiceId);
        mockMvc.perform(as("PUR2", delete("/v1/documents/{id}", invoiceId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_LOCKED"));
        assertDownload("PUR2", invoiceId);
    }

    @Test
    void workflowGuardMessageFollowsRequestedLanguage() throws Exception {
        Map<String, String> messages = new HashMap<>();
        for (String language : List.of("vi", "en")) {
            String response = mockMvc.perform(as("PUR2", upload(importId))
                            .header(HttpHeaders.ACCEPT_LANGUAGE, language))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DOCUMENT_LOCKED"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            messages.put(language, objectMapper.readTree(response).get("message").asText());
        }
        assertThat(messages.get("en")).containsIgnoringCase("document")
                .isNotEqualTo("DOCUMENT_LOCKED");
        assertThat(messages.get("vi")).contains("ch\u1ee9ng t\u1eeb")
                .isNotEqualTo(messages.get("en"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IT", "USER"})
    void rolesWithoutDocumentWritePermissionCannotUploadOrDelete(String actor) throws Exception {
        mockMvc.perform(as(actor, upload(importId))).andExpect(status().isForbidden());
        mockMvc.perform(as(actor, delete("/v1/documents/{id}", invoiceId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchaserCannotMutateNonImportDocuments() throws Exception {
        mockMvc.perform(as("PUR2", upload(handoverId))).andExpect(status().isForbidden());
        mockMvc.perform(as("PUR2", delete("/v1/documents/{id}", handoverReportId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadRejectsMissingFileAndMalformedFields() throws Exception {
        mockMvc.perform(as("PUR1", multipart("/v1/documents")
                        .param("transactionId", Long.toString(importId)).param("documentType", "INVOICE")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("PUR1", multipart("/v1/documents").file(sampleFile())
                        .param("documentType", "INVOICE")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("PUR1", multipart("/v1/documents").file(sampleFile())
                        .param("transactionId", Long.toString(importId)).param("documentType", "UNKNOWN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corruptedChecksumPreventsDownloadingAlteredFile() throws Exception {
        jdbc.update("update documents set checksum = ? where document_id = ?", "0".repeat(64), invoiceId);
        mockMvc.perform(as("ADMIN", get("/v1/documents/{id}/download", invoiceId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_FILE_CORRUPT"));
    }

    @Test
    void tamperedPathCannotReadOutsideConfiguredStorage() throws Exception {
        jdbc.update("update documents set storage_path = ? where document_id = ?", "../outside.pdf", invoiceId);
        mockMvc.perform(as("ADMIN", get("/v1/documents/{id}/download", invoiceId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_PATH_INVALID"));
    }

    @Test
    void databaseRollbackRemovesNewFileWithoutRemovingExistingDocuments() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        StoredFile rolledBack = transaction.execute(status -> {
            assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
            StoredFile file = storage.store(TransactionType.IMPORT, sampleFile());
            assertThat(storageRoot.resolve(file.storagePath())).exists();
            status.setRollbackOnly();
            return file;
        });
        assertThat(rolledBack).isNotNull();
        assertThat(storageRoot.resolve(rolledBack.storagePath())).doesNotExist();
        assertDownload("PUR2", invoiceId);
    }

    private void assertDownload(String actor, long documentId) throws Exception {
        mockMvc.perform(as(actor, get("/v1/documents/{id}/download", documentId)))
                .andExpect(status().isOk()).andExpect(content().bytes(SAMPLE_PDF));
    }

    private MockHttpServletRequestBuilder as(String actor, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(actor));
    }

    private MockHttpServletRequestBuilder upload(long transactionId) {
        return multipart("/v1/documents").file(sampleFile())
                .param("transactionId", Long.toString(transactionId)).param("documentType", "INVOICE");
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "sample.pdf", "application/pdf", SAMPLE_PDF);
    }

    private void createUser(String key, String role) {
        String email = fixturePrefix + "-" + key + "@example.test";
        long id = jdbc.queryForObject("""
                insert into users(email, full_name, role_id, account_status)
                select ?, ?, role_id, 'ACTIVE' from roles where code = ? returning user_id
                """, Long.class, email, "Document test " + key, role);
        userIds.put(key, id);
        emails.put(key, email);
        tokens.put(key, jwtTokenProvider.generateToken(email, role));
    }

    private long createTransaction(String type, String requester, String relatedUser, String suffix) {
        long id;
        if ("IMPORT".equals(type)) {
            id = jdbc.queryForObject("""
                    insert into transactions(transaction_code, type, status, requester_id)
                    values (?, ?, 'PENDING', ?) returning transaction_id
                    """, Long.class, fixturePrefix + "-" + suffix, type, userIds.get(requester));
        } else {
            id = jdbc.queryForObject("""
                    insert into transactions(transaction_code, type, status, requester_id,
                        processed_by, processed_at, completed_at)
                    values (?, ?, 'COMPLETED', ?, ?, current_timestamp, current_timestamp)
                    returning transaction_id
                    """, Long.class, fixturePrefix + "-" + suffix, type,
                    userIds.get(requester), userIds.get("IT"));
        }
        if ("HANDOVER".equals(type)) {
            jdbc.update("""
                    insert into transaction_handover_details(transaction_id, recipient_user_id,
                        handover_date, destination_location_id) values (?, ?, current_date, ?)
                    """, id, userIds.get(relatedUser), locationId);
        } else if ("RECOVERY".equals(type)) {
            jdbc.update("""
                    insert into transaction_recovery_details(transaction_id, returner_user_id,
                        recovery_date, receiving_location_id, reason) values (?, ?, current_date, ?, 'Sample return')
                    """, id, userIds.get(relatedUser), locationId);
        }
        return id;
    }

    private long createDocument(long transactionId, TransactionType type, String documentType,
                                String uploadedBy, boolean locked) {
        StoredFile file = storage.store(type, sampleFile());
        storedFiles.add(file);
        return jdbc.queryForObject("""
                insert into documents(transaction_id, document_type, original_file_name, stored_file_name,
                    storage_path, mime_type, file_size, checksum, uploaded_by, is_locked)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning document_id
                """, Long.class, transactionId, documentType, file.originalFileName(), file.storedFileName(),
                file.storagePath(), file.mimeType(), file.fileSize(), file.checksum(), userIds.get(uploadedBy), locked);
    }
}
