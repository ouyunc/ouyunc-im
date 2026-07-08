package com.ouyunc.base.http.client;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** multipart/form-data 报文构建。 */
final class MultipartBodyBuilder {

    private final String boundary;
    private final List<byte[]> chunks = new ArrayList<>();

    MultipartBodyBuilder() {
        this.boundary = "----OuyuncBoundary" + UUID.randomUUID();
    }

    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    void addPart(HttpMultipartPart part) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(part.name()).append('"');
        if (part.isFile()) {
            sb.append("; filename=\"").append(part.filename()).append('"');
        }
        sb.append("\r\n");
        if (StringUtils.isNotBlank(part.contentType())) {
            sb.append("Content-Type: ").append(part.contentType()).append("\r\n");
        } else if (part.isFile()) {
            sb.append("Content-Type: application/octet-stream\r\n");
        }
        sb.append("\r\n");
        chunks.add(sb.toString().getBytes(StandardCharsets.UTF_8));
        chunks.add(part.content());
        chunks.add("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    byte[] build() {
        byte[] closing = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        int total = closing.length;
        for (byte[] chunk : chunks) {
            total += chunk.length;
        }
        byte[] body = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, body, offset, chunk.length);
            offset += chunk.length;
        }
        System.arraycopy(closing, 0, body, offset, closing.length);
        return body;
    }
}
