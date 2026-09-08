package com.company.itam.document.service;

import com.company.itam.common.exception.AppException;
import com.company.itam.document.entity.DocumentEntity;
import com.company.itam.workflow.core.enums.TransactionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalDocumentStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorage.class);
    private static final Map<String, String> MIME_TYPES = Map.of(
            "pdf", "application/pdf", "png", "image/png",
            "jpg", "image/jpeg", "jpeg", "image/jpeg");
    private static final byte[] PNG_HEADER = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final Path root;
    private final int maxFileSize;

    public LocalDocumentStorage(
            @Value("${itam.documents.storage-root:../storage}") String storageRoot,
            @Value("${itam.documents.max-file-size:10485760}") int maxFileSize) {
        if (maxFileSize < 1 || maxFileSize > 10485760) {
            throw new IllegalArgumentException("itam.documents.max-file-size must be between 1 and 10485760");
        }
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public StoredFile store(TransactionType type, MultipartFile file) {
        if (type == null || file == null || file.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_INVALID");
        }
        String originalName = file.getOriginalFilename();
        if (!safeOriginalName(originalName)) {
            throw error(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_INVALID");
        }
        String extension = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mimeType = MIME_TYPES.get(extension);
        if (mimeType == null || !mimeType.equalsIgnoreCase(file.getContentType())) {
            throw error(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_INVALID");
        }
        if (file.getSize() > maxFileSize) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_FILE_TOO_LARGE");
        }

        Path destination = null;
        boolean created = false;
        try {
            byte[] bytes;
            try (InputStream input = file.getInputStream()) {
                bytes = boundedRead(input);
            }
            if (!matchesSignature(extension, bytes)) {
                throw error(HttpStatus.BAD_REQUEST, "DOCUMENT_FILE_INVALID");
            }
            Path directory = root.resolve(folder(type));
            ensureDirectory(directory);
            String storedName = UUID.randomUUID() + "." + extension;
            destination = directory.resolve(storedName);
            // CREATE_NEW never replaces an existing file, including an existing link.
            try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                output.write(bytes);
            }
            StoredFile stored = new StoredFile(originalName, storedName, folder(type) + "/" + storedName,
                    mimeType, (long) bytes.length, checksum(bytes));
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                discard(stored);
                            } catch (RuntimeException cleanupFailure) {
                                log.error("Unable to clean up an uncommitted document file: {}", stored.storedFileName(),
                                        cleanupFailure);
                            }
                        }
                    }
                });
            }
            return stored;
        } catch (IOException ex) {
            if (created) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException cleanupFailure) {
                    ex.addSuppressed(cleanupFailure);
                }
            }
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_STORAGE_ERROR");
        }
    }

    public byte[] read(DocumentEntity document) {
        if (document == null || document.getTransaction() == null || document.getTransaction().getType() == null) {
            throw error(HttpStatus.CONFLICT, "DOCUMENT_PATH_INVALID");
        }
        Path path = resolveStoredPath(document.getStoragePath(), document.getStoredFileName(),
                folder(document.getTransaction().getType()));
        try {
            verifyExistingPath(path);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw error(HttpStatus.NOT_FOUND, "DOCUMENT_FILE_NOT_FOUND");
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = boundedRead(input);
            }
            if ((document.getFileSize() != null && document.getFileSize() != bytes.length)
                    || (document.getChecksum() != null && !document.getChecksum().equals(checksum(bytes)))) {
                throw error(HttpStatus.CONFLICT, "DOCUMENT_FILE_CORRUPT");
            }
            return bytes;
        } catch (java.nio.file.NoSuchFileException ex) {
            throw error(HttpStatus.NOT_FOUND, "DOCUMENT_FILE_NOT_FOUND");
        } catch (IOException ex) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_STORAGE_ERROR");
        }
    }

    /** Only for compensating an uncommitted new file, never for deleting historical documents. */
    public void discard(StoredFile file) {
        if (file == null) return;
        String relativePath = file.storagePath();
        String directory = relativePath == null || !relativePath.contains("/")
                ? "" : relativePath.substring(0, relativePath.indexOf('/'));
        Path path = resolveStoredPath(relativePath, file.storedFileName(), directory);
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
            verifyExistingPath(path);
            Files.delete(path);
        } catch (IOException ex) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_STORAGE_ERROR");
        }
    }

    private Path resolveStoredPath(String relativePath, String storedName, String directory) {
        if (!Arrays.asList("import", "handover", "recovery", "disposal").contains(directory)
                || storedName == null || !storedName.matches("[0-9a-f-]{36}\\.(pdf|png|jpg|jpeg)")
                || !(directory + "/" + storedName).equals(relativePath)) {
            throw error(HttpStatus.CONFLICT, "DOCUMENT_PATH_INVALID");
        }
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) throw error(HttpStatus.CONFLICT, "DOCUMENT_PATH_INVALID");
        return path;
    }

    private void ensureDirectory(Path directory) throws IOException {
        Path current = directory.getRoot();
        for (Path part : directory) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // A concurrent upload may have created the directory; validate it below.
                }
            }
            if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                    || !current.toRealPath().equals(current)) {
                throw error(HttpStatus.CONFLICT, "DOCUMENT_PATH_INVALID");
            }
        }
    }

    private void verifyExistingPath(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path part : path) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw error(HttpStatus.CONFLICT, "DOCUMENT_PATH_INVALID");
        }
        if (!path.toRealPath().equals(path)) throw error(HttpStatus.CONFLICT, "DOCUMENT_PATH_INVALID");
    }

    private byte[] boundedRead(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(maxFileSize + 1);
        if (bytes.length > maxFileSize) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_FILE_TOO_LARGE");
        return bytes;
    }

    private static boolean safeOriginalName(String name) {
        return name != null && !name.isBlank() && name.length() <= 500
                && name.equals(name.strip()) && !name.endsWith(".")
                && name.indexOf('.') > 0 && name.chars().noneMatch(c -> c < 32 || c == 127
                || "\\/:*?\"<>|".indexOf(c) >= 0);
    }

    private static boolean matchesSignature(String extension, byte[] bytes) {
        return switch (extension) {
            case "pdf" -> bytes.length >= 8 && bytes[0] == '%' && bytes[1] == 'P'
                    && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            case "png" -> bytes.length > PNG_HEADER.length
                    && Arrays.equals(Arrays.copyOf(bytes, PNG_HEADER.length), PNG_HEADER);
            case "jpg", "jpeg" -> bytes.length > 3 && bytes[0] == (byte) 0xff
                    && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            default -> false;
        };
    }

    private static String folder(TransactionType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static AppException error(HttpStatus status, String code) {
        return new AppException(status, code, code);
    }

    public record StoredFile(String originalFileName, String storedFileName, String storagePath,
                             String mimeType, Long fileSize, String checksum) {}
}
