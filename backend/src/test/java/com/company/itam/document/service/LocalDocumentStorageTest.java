package com.company.itam.document.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.workflow.core.entity.TransactionEntity;
import com.company.itam.workflow.core.enums.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDocumentStorageTest {
    @TempDir Path directory;
    private static final byte[] SAMPLE = "%PDF-1.4\n% ITAM sample invoice - fictional data\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @ValueSource(strings = {"IMPORT", "HANDOVER", "RECOVERY", "DISPOSAL"})
    void keepsExactBytesAndUsesUniqueNamesInTransactionFolder(String type) throws Exception {
        LocalDocumentStorage storage = storage(1024);
        TransactionType transactionType = TransactionType.valueOf(type);
        var first = storage.store(transactionType, pdf("invoice.pdf", SAMPLE));
        var second = storage.store(transactionType, pdf("invoice.pdf", SAMPLE));
        assertThat(first.storagePath()).startsWith(type.toLowerCase() + "/");
        assertThat(first.storedFileName()).isNotEqualTo(second.storedFileName());
        assertThat(first.originalFileName()).isEqualTo("invoice.pdf");
        assertThat(first.checksum()).hasSize(64);
        assertThat(storage.read(document(transactionType, first))).isEqualTo(SAMPLE);
        storage.discard(second);
        assertThat(Files.exists(directory.resolve(second.storagePath()))).isFalse();
        assertThat(storage.read(document(transactionType, first))).isEqualTo(SAMPLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../invoice.pdf", "..\\invoice.pdf", "C:\\invoice.pdf", "invoice.pdf:stream",
            "bad\r\nname.pdf", "invoice.pdf ", "invoice.pdf.", ".pdf", "invoice.exe"})
    void rejectsUnsafeOrUnsupportedOriginalName(String name) {
        assertCode(() -> storage(1024).store(TransactionType.IMPORT, pdf(name, SAMPLE)), "DOCUMENT_FILE_INVALID");
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test
    void rejectsEmptyMimeMismatchAndSpoofedContent() {
        var storage = storage(1024);
        assertCode(() -> storage.store(TransactionType.IMPORT, pdf("invoice.pdf", new byte[0])), "DOCUMENT_FILE_INVALID");
        assertCode(() -> storage.store(TransactionType.IMPORT,
                new MockMultipartFile("file", "invoice.pdf", "image/png", SAMPLE)), "DOCUMENT_FILE_INVALID");
        assertCode(() -> storage.store(TransactionType.IMPORT, pdf("invoice.pdf", "not a PDF".getBytes())), "DOCUMENT_FILE_INVALID");
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test
    void acceptsPngSample() {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jA1sAAAAASUVORK5CYII=");
        var storage = storage(1024);
        var saved = storage.store(TransactionType.IMPORT, new MockMultipartFile("file", "sample.png", "image/png", png));
        assertThat(storage.read(document(TransactionType.IMPORT, saved))).isEqualTo(png);
    }

    @Test
    void enforcesLimitOnBothDeclaredAndActualStreamLength() {
        var storage = storage(SAMPLE.length - 1);
        assertCode(() -> storage.store(TransactionType.IMPORT, pdf("invoice.pdf", SAMPLE)), "DOCUMENT_FILE_TOO_LARGE");
        MockMultipartFile underReported = new MockMultipartFile("file", "invoice.pdf", "application/pdf", SAMPLE) {
            @Override public long getSize() { return 1; }
        };
        assertCode(() -> storage.store(TransactionType.IMPORT, underReported), "DOCUMENT_FILE_TOO_LARGE");
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test
    void acceptsExactLimit() {
        var storage = storage(SAMPLE.length);
        var saved = storage.store(TransactionType.IMPORT, pdf("invoice.pdf", SAMPLE));
        assertThat(storage.read(document(TransactionType.IMPORT, saved))).isEqualTo(SAMPLE);
    }

    @Test
    void refusesTamperedBytesAndMissingFiles() throws Exception {
        var storage = storage(1024);
        var saved = storage.store(TransactionType.IMPORT, pdf("invoice.pdf", SAMPLE));
        var document = document(TransactionType.IMPORT, saved);
        byte[] changed = SAMPLE.clone();
        changed[12] = '!';
        Files.write(directory.resolve(saved.storagePath()), changed);
        assertCode(() -> storage.read(document), "DOCUMENT_FILE_CORRUPT");
        storage.discard(saved);
        assertCode(() -> storage.read(document), "DOCUMENT_FILE_NOT_FOUND");
    }

    @Test
    void rejectsMetadataPointingOutsideStorageOrDifferentTransactionFolder() {
        var storage = storage(1024);
        var saved = storage.store(TransactionType.IMPORT, pdf("invoice.pdf", SAMPLE));
        var document = document(TransactionType.IMPORT, saved);
        document.setStoragePath("../" + saved.storedFileName());
        assertCode(() -> storage.read(document), "DOCUMENT_PATH_INVALID");
        document.setStoragePath(directory.resolve(saved.storagePath()).toString());
        assertCode(() -> storage.read(document), "DOCUMENT_PATH_INVALID");
        document.setStoragePath(saved.storagePath());
        document.getTransaction().setType(TransactionType.HANDOVER);
        assertCode(() -> storage.read(document), "DOCUMENT_PATH_INVALID");
    }

    @Test
    void compensatesOnlyRolledBackNewFiles() {
        var storage = storage(1024);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var saved = storage.store(TransactionType.IMPORT, pdf("invoice.pdf", SAMPLE));
            assertThat(Files.exists(directory.resolve(saved.storagePath()))).isTrue();
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertThat(Files.exists(directory.resolve(saved.storagePath()))).isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private LocalDocumentStorage storage(int limit) {
        return new LocalDocumentStorage(directory.toString(), limit);
    }

    private static MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }

    private static DocumentEntity document(TransactionType type, LocalDocumentStorage.StoredFile file) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setType(type);
        DocumentEntity document = new DocumentEntity();
        document.setTransaction(transaction);
        document.setOriginalFileName(file.originalFileName());
        document.setStoredFileName(file.storedFileName());
        document.setStoragePath(file.storagePath());
        document.setFileSize(file.fileSize());
        document.setChecksum(file.checksum());
        return document;
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
