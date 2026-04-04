package com.ouyunc.message.processor.http.demo;

import com.ouyunc.base.model.HttpFileResponse;
import com.ouyunc.base.model.HttpRawResponse;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.annotation.GetHttpRequest;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.IgnoreAuth;
import com.ouyunc.message.http.annotation.PathVariable;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestBody;
import com.ouyunc.message.http.annotation.RequestHeader;
import com.ouyunc.message.http.annotation.RequestParam;
import com.ouyunc.message.http.annotation.RequestPart;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 能力演示：GET/POST、Query、Path、Header、JSON、form-urlencoded、multipart 上传、原始响应与文件下载；
 * 以及 {@code @RequestBody} 与 {@code @RequestParam}/{@code @PathVariable}/{@code @RequestHeader} 的混合用法
 *（JSON 占满 body 时 {@code @RequestParam} 只从 URI query 取）。
 * <p>
 * 基路径 {@code /api/demo}；类上 {@link IgnoreAuth} 便于本地联调，生产环境请去掉或改为显式鉴权。
 */
@IgnoreAuth
@HttpRestController
@HttpRequestMapping("/api/demo")
public class HttpDemoController {

    private static final Logger log = LoggerFactory.getLogger(HttpDemoController.class);

    private static volatile Path cachedDownloadFile;

    /**
     * GET + Query：{@code /api/demo/echo?msg=hi}
     */
    @GetHttpRequest("/echo")
    public Map<String, Object> echo(@RequestParam(value = "msg", defaultValue = "hello") String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "get-query");
        m.put("msg", msg);
        return m;
    }

    /**
     * GET + Path：{@code /api/demo/user/123}
     */
    @GetHttpRequest("/user/{id}")
    public Map<String, Object> userById(@PathVariable("id") String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "get-path");
        m.put("id", id);
        return m;
    }

    /**
     * GET + Header：传入任意请求头名，例如 {@code X-Demo: abc}
     */
    @GetHttpRequest("/header")
    public Map<String, Object> headerSample(@RequestHeader("User-Agent") String ua,
                                            @RequestHeader("X-Demo") String xDemo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "get-header");
        m.put("userAgent", ua);
        m.put("xDemo", xDemo);
        return m;
    }

    /**
     * GET + {@link HttpContext}（可读取 appKey、path 变量等）
     */
    @GetHttpRequest("/context")
    public Map<String, Object> contextSample(HttpContext ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "get-context");
        m.put("appKey", ctx.getAppKey());
        m.put("pathVariables", ctx.getPathVariables());
        return m;
    }

    /**
     * POST + JSON：{@code Content-Type: application/json}，body 映射 {@link HttpDemoJsonRequest}
     */
    @PostHttpRequest("/json")
    public Map<String, Object> postJson(@RequestBody HttpDemoJsonRequest body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "post-json");
        m.put("message", body.getMessage());
        m.put("repeat", body.getRepeat());
        return m;
    }

    /**
     * 混合：{@code @RequestBody}（JSON）+ {@code @RequestParam}（仅来自 query，因 body 已被 JSON 占用）。
     * 例：{@code POST /api/demo/mix/json-param?traceId=t1&dryRun=true}
     */
    @PostHttpRequest("/mix/json-param")
    public Map<String, Object> mixJsonAndRequestParam(@RequestBody HttpDemoJsonRequest body,
                                                      @RequestParam("traceId") String traceId,
                                                      @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "mix-json-requestparam");
        m.put("bodyMessage", body.getMessage());
        m.put("bodyRepeat", body.getRepeat());
        m.put("traceId", traceId);
        m.put("dryRun", dryRun);
        return m;
    }

    /**
     * 混合：JSON body + path + query + 请求头。
     * 例：{@code POST /api/demo/mix/full/tenant-A/save?source=web}，Header {@code X-Request-Id: rid-1}
     */
    @PostHttpRequest("/mix/full/{tenant}/save")
    public Map<String, Object> mixJsonPathParamHeader(@PathVariable("tenant") String tenant,
                                                       @RequestParam(value = "source", defaultValue = "unknown") String source,
                                                       @RequestHeader(value = "X-Request-Id") String requestId,
                                                       @RequestBody HttpDemoJsonRequest body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "mix-json-path-param-header");
        m.put("tenant", tenant);
        m.put("source", source);
        m.put("requestId", requestId);
        m.put("bodyMessage", body.getMessage());
        m.put("bodyRepeat", body.getRepeat());
        return m;
    }

    /**
     * POST + {@code application/x-www-form-urlencoded}：字段通过 {@link RequestParam} 绑定（可与 query 合并，query 优先）。
     */
    @PostHttpRequest("/form")
    public Map<String, Object> postForm(@RequestParam("title") String title,
                                        @RequestParam(value = "count", defaultValue = "1") int count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "post-form-urlencoded");
        m.put("title", title);
        m.put("count", count);
        return m;
    }

    /**
     * POST + {@code multipart/form-data}：文件域 {@code file}，可选文本域 {@code note}。
     */
    @PostHttpRequest("/upload")
    public Map<String, Object> upload(@RequestPart("file") FileUpload file,
                                      @RequestPart(value = "note", required = false) String note) throws IOException {
        byte[] bytes = file.get();
        log.info("demo upload: name={}, len={}, note={}", file.getFilename(), bytes.length, note);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "post-multipart");
        m.put("filename", file.getFilename());
        m.put("contentType", file.getContentType());
        m.put("size", bytes.length);
        m.put("note", note);
        return m;
    }

    /**
     * GET：小体积二进制/文本走 {@link HttpRawResponse}（不包统一 JSON 信封）。
     */
    @GetHttpRequest("/download/raw")
    public HttpRawResponse downloadRaw() {
        byte[] body = "plain text from HttpRawResponse\n".getBytes(StandardCharsets.UTF_8);
        return new HttpRawResponse(HttpResponseStatus.OK, Unpooled.wrappedBuffer(body),
                "text/plain; charset=UTF-8",
                "inline; filename=\"demo.txt\"",
                true);
    }

    /**
     * GET：磁盘文件走 {@link HttpFileResponse}（{@link io.netty.channel.DefaultFileRegion}，适合较大文件）。
     */
    @GetHttpRequest("/download/file")
    public HttpFileResponse downloadFile() throws IOException {
        Path path = ensureDemoDownloadFile();
        return HttpFileResponse.attachment(path, "sample-download.txt");
    }

    private static Path ensureDemoDownloadFile() throws IOException {
        if (cachedDownloadFile != null && Files.isRegularFile(cachedDownloadFile)) {
            return cachedDownloadFile;
        }
        synchronized (HttpDemoController.class) {
            if (cachedDownloadFile != null && Files.isRegularFile(cachedDownloadFile)) {
                return cachedDownloadFile;
            }
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "ouyunc-http-demo");
            Files.createDirectories(dir);
            Path f = dir.resolve("sample-download.txt");
            String text = "OUYUNC HTTP demo — file download\n生成时间(ms): " + System.currentTimeMillis() + "\n";
            Files.writeString(f, text, StandardCharsets.UTF_8);
            cachedDownloadFile = f;
            return f;
        }
    }
}
