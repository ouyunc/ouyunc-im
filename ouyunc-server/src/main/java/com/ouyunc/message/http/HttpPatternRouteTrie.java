package com.ouyunc.message.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路径模板路由前缀树：按 HTTP 方法分根，每段字面量走子节点，{@code {name}} 走变量边。
 * 同一位置优先尝试字面量再尝试变量（更具体路径优先）。
 */
public final class HttpPatternRouteTrie {

    private static final Logger log = LoggerFactory.getLogger(HttpPatternRouteTrie.class);

    private final ConcurrentHashMap<String, PatternTrieNode> methodRoots = new ConcurrentHashMap<>();

    private final Object insertLock = new Object();

    public void add(String httpMethod, String normalizedPatternPath, HttpRegisteredRoute route) {
        String m = httpMethod.toUpperCase();
        synchronized (insertLock) {
            PatternTrieNode root = methodRoots.computeIfAbsent(m, k -> new PatternTrieNode());
            String[] ps = HttpPathTemplateMatcher.segments(normalizedPatternPath);
            insert(root, ps, 0, normalizedPatternPath, route);
        }
    }

    /**
     * 在已规范化请求路径分段上匹配；成功时写入 pathVariables 并返回路由。
     */
    public HttpRegisteredRoute match(String httpMethod, String[] pathSegments, Map<String, String> pathVariables) {
        PatternTrieNode root = methodRoots.get(httpMethod.toUpperCase());
        if (root == null) {
            return null;
        }
        pathVariables.clear();
        return matchRecursive(root, pathSegments, 0, pathVariables);
    }

    private static void insert(PatternTrieNode node, String[] patternSegs, int idx, String patternPath, HttpRegisteredRoute route) {
        if (idx == patternSegs.length) {
            if (node.leaf != null) {
                log.warn("HTTP 路径模板终端被覆盖注册: {}", patternPath);
            }
            node.leaf = route;
            return;
        }
        String seg = patternSegs[idx];
        if (HttpPathTemplateMatcher.isVariableSegment(seg)) {
            String name = HttpPathTemplateMatcher.variableName(seg);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("非法路径模板变量段: " + seg);
            }
            if (node.var == null) {
                node.var = new VariableEdge(name, new PatternTrieNode());
            } else {
                node.var.name = name;
            }
            insert(node.var.next, patternSegs, idx + 1, patternPath, route);
        } else {
            PatternTrieNode next = node.literals.computeIfAbsent(seg, k -> new PatternTrieNode());
            insert(next, patternSegs, idx + 1, patternPath, route);
        }
    }

    private static HttpRegisteredRoute matchRecursive(PatternTrieNode node, String[] segs, int idx, Map<String, String> vars) {
        if (idx == segs.length) {
            return node.leaf;
        }
        String s = segs[idx];
        PatternTrieNode literalNext = node.literals.get(s);
        if (literalNext != null) {
            HttpRegisteredRoute r = matchRecursive(literalNext, segs, idx + 1, vars);
            if (r != null) {
                return r;
            }
        }
        if (node.var != null) {
            String key = node.var.name;
            String old = vars.put(key, s);
            HttpRegisteredRoute r = matchRecursive(node.var.next, segs, idx + 1, vars);
            if (r != null) {
                return r;
            }
            if (old != null) {
                vars.put(key, old);
            } else {
                vars.remove(key);
            }
        }
        return null;
    }

    private static final class PatternTrieNode {
        final Map<String, PatternTrieNode> literals = new HashMap<>();
        VariableEdge var;
        HttpRegisteredRoute leaf;
    }

    private static final class VariableEdge {
        String name;
        final PatternTrieNode next;

        VariableEdge(String name, PatternTrieNode next) {
            this.name = name;
            this.next = next;
        }
    }
}
