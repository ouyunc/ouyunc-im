package com.ouyunc.message.processor.http.demo;

import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 将 multipart {@link FileUpload} 落盘到本地目录（可配置，默认 {@code ${user.dir}/http-uploads}）。
 */
public final class LocalHttpUploadSaver {

    private LocalHttpUploadSaver() {
    }

    /**
     * 保存上传文件并返回用于 JSON 响应的字段（含绝对路径 {@code savedPath}、{@code size} 等）。
     */
    public static Map<String, Object> save(FileUpload file, String note) throws IOException {
        Path dir = resolveUploadDirectory();
        Files.createDirectories(dir);

        String safeName = sanitizeFilename(file.getFilename());
        String unique = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
        Path dest = dir.resolve(unique).normalize();
        if (!dest.startsWith(dir.normalize())) {
            throw new IOException("非法保存路径");
        }

        if (!file.isInMemory()) {
            File src = file.getFile();
            Files.copy(src.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.write(dest, file.get());
        }

        long size = Files.size(dest);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filename", file.getFilename());
        m.put("savedFilename", unique);
        m.put("savedPath", dest.toAbsolutePath().toString());
        m.put("contentType", file.getContentType());
        m.put("size", size);
        m.put("note", note);
        return m;
    }

    public static Path resolveUploadDirectory() {
        MessageServerProperties p = MessageServerContext.serverProperties();
        String raw = p != null ? p.getHttpUploadLocalDirectory() : null;
        if (raw == null || raw.isBlank()) {
            return Path.of(System.getProperty("user.dir"), "http-uploads").normalize().toAbsolutePath();
        }
        return Path.of(raw).normalize().toAbsolutePath();
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        String base = Path.of(filename).getFileName().toString();
        if (base.isBlank() || ".".equals(base) || "..".equals(base)) {
            return "upload.bin";
        }
        return base;
    }
}
