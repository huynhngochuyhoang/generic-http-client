package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;

/**
 * Convenience record for {@link FormFile} parameters: carries raw bytes plus the
 * filename and content-type that should appear in the multipart part headers.
 *
 * <p>When a {@link FormFile}-annotated parameter is a {@code FileAttachment}, the
 * values on this record take precedence over the annotation defaults. If
 * {@link #filename()} or {@link #contentType()} is {@code null} or blank, the
 * annotation default is used.
 */
public record FileAttachment(byte[] content, String filename, String contentType) {

    public static FileAttachment of(byte[] content, String filename, String contentType) {
        return new FileAttachment(content, filename, contentType);
    }

    public static FileAttachment of(byte[] content, String filename) {
        return new FileAttachment(content, filename, null);
    }
}
