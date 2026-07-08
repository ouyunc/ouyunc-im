package com.ouyunc.base.http.client;

/** multipart/form-data 单字段。 */
public final class HttpMultipartPart {

    private final String name;
    private final String filename;
    private final String contentType;
    private final byte[] content;

    private HttpMultipartPart(String name, String filename, String contentType, byte[] content) {
        this.name = name;
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    public static HttpMultipartPart formField(String name, String value) {
        return new HttpMultipartPart(name, null, null,
                value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static HttpMultipartPart file(String name, String filename, String contentType, byte[] content) {
        return new HttpMultipartPart(name, filename, contentType, content == null ? new byte[0] : content);
    }

    public String name() {
        return name;
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] content() {
        return content;
    }

    public boolean isFile() {
        return filename != null;
    }
}
