package com.ouyunc.message.safety;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双数组 Aho-Corasick 多模式敏感词匹配器。
 * <p>
 * 词库可以到几十万。转移表压成 base/check 两张连续数组，扫描时按字符跳下标，
 * 不再给每个节点挂一个 HashMap。输出链只串真正的词尾，没命中时每个字符只做常数次判断。
 * 构建发生在词库加载或刷新，不在发消息的热路径上。构建完成后只读，可多线程共享。
 * 拉丁字母按小写匹配，中文原样。词库变更应新建实例。
 * </p>
 */
public final class SensitiveWordAcAutomaton {

    /** check 空位。不能用 0，因为根节点编号就是 0。 */
    private static final int EMPTY_CHECK = -1;

    /** 没有输出链，或该状态不是词尾。 */
    private static final int NONE = -1;

    /** 字符不在词表字母表里。 */
    private static final int NO_CODE = -1;

    /** 双数组初始槽位。构建期按需翻倍。 */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * 单个自动机状态上限。按 base/check/fail/word/out 五张 int 数组估算，
     * 约 320MB，挡住异常稀疏或坏数据把进程打满。
     */
    private static final int MAX_STATES = 16_000_000;

    /** 高字节页数。字符编码按页懒分配，小词库不会占一整张 65536 的表。 */
    private static final int PAGE_COUNT = 256;

    /** 每页低 8 位。 */
    private static final int PAGE_SIZE = 256;

    /** 页内掩码。 */
    private static final int PAGE_MASK = 0xFF;

    /** base[state] + 字符编码 = 子状态候选下标。 */
    private final int[] base;

    /** check[候选] == 父状态时，候选才是合法子状态。 */
    private final int[] check;

    /** 失配后改走的状态。根的 fail 是自己。 */
    private final int[] fail;

    /** 该状态作为词尾时的词编号，否则 NONE。 */
    private final int[] wordOf;

    /** 下一个仍可能出词的失配状态，跳过中间的非词节点。 */
    private final int[] outLink;

    /** 归一化后的词，下标是词编号。 */
    private final String[] dict;

    /**
     * 字符到稠密编码。只分配出现过的高字节页，避免每个租户固定占 256KB。
     * 页内未出现的字符为 NO_CODE。
     */
    private final int[][] charPages;

    /** 词表为空时扫描直接返回。 */
    private final boolean empty;

    /**
     * 用词集合构建自动机（含 fail 与双数组）。
     *
     * @param words 敏感词，空、null、纯空白会被跳过
     */
    public SensitiveWordAcAutomaton(Iterable<String> words) {
        Compiled compiled = Compiler.compile(words);
        this.base = compiled.base;
        this.check = compiled.check;
        this.fail = compiled.fail;
        this.wordOf = compiled.wordOf;
        this.outLink = compiled.outLink;
        this.dict = compiled.dict;
        this.charPages = compiled.charPages;
        this.empty = compiled.dict.length == 0;
    }

    /**
     * 扫描文本，返回去重且保序的命中词（归一化后的词形）。
     *
     * @param text 原文
     * @return 命中列表，未命中为空列表
     */
    public List<String> findAll(String text) {
        if (empty || text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = normalize(text);
        List<String> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int state = 0;
        for (int i = 0; i < normalized.length(); i++) {
            state = move(state, normalized.charAt(i));
            collect(state, hits, seen);
        }
        if (hits.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(hits);
    }

    /**
     * 按命中词从长到短、等长掩码替换。同一长度保持原顺序，短词不会改写已经盖住的长词。
     * 正文很短，这里按区间填掩码字符，不为每个命中词编译正则。
     *
     * @param text     原文
     * @param hits     命中词
     * @param maskChar 掩码字符，取首字符，空则 *
     * @return 脱敏文本
     */
    public String mask(String text, List<String> hits, String maskChar) {
        if (text == null || hits == null || hits.isEmpty()) {
            return text;
        }
        String token = (maskChar == null || maskChar.isBlank()) ? "*" : maskChar.substring(0, 1);
        String normalized = normalize(text);
        if (normalized.length() != text.length()) {
            return maskByRegex(text, hits, token);
        }
        char star = token.charAt(0);
        char[] out = text.toCharArray();
        char[] norm = normalized.toCharArray();
        List<String> sorted = new ArrayList<>(hits);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String hit : sorted) {
            fillHit(out, norm, hit, star);
        }
        return new String(out);
    }

    /**
     * 沿输出链收集以当前状态结尾的词。链上只有词尾，长度与命中次数成正比。
     *
     * @param state 当前状态
     * @param hits  保序结果
     * @param seen  去重
     */
    private void collect(int state, List<String> hits, Set<String> seen) {
        int guard = 0;
        int stateCursor = state;
        while (stateCursor > 0 && guard++ < dict.length) {
            int wordId = wordOf[stateCursor];
            if (wordId >= 0 && seen.add(dict[wordId])) {
                hits.add(dict[wordId]);
            }
            int next = outLink[stateCursor];
            if (next <= 0 || next == stateCursor) {
                return;
            }
            stateCursor = next;
        }
    }

    /**
     * 读一个字符并转移到下一个状态。词表里没有的字符直接回根。
     *
     * @param state 当前状态
     * @param ch    归一化后的字符
     * @return 下一个状态，根为 0
     */
    private int move(int state, char ch) {
        int code = codeOf(ch);
        if (code < 0) {
            return 0;
        }
        int cursor = state;
        while (true) {
            int next = child(cursor, code);
            if (next >= 0) {
                return next;
            }
            if (cursor == 0) {
                return 0;
            }
            int fallback = fail[cursor];
            if (fallback == cursor) {
                return 0;
            }
            cursor = fallback;
        }
    }

    /**
     * base/check 转移。候选下标没有落在当前状态名下时，视为没有这条边。
     *
     * @param state 父状态
     * @param code  稠密字符编码
     * @return 子状态下标，没有则 -1
     */
    private int child(int state, int code) {
        long pos = (long) base[state] + code;
        if (pos <= 0L || pos >= check.length) {
            return -1;
        }
        int index = (int) pos;
        return check[index] == state ? index : -1;
    }

    /**
     * 字符编码。未出现在词表中的字符返回 NO_CODE。
     *
     * @param ch 字符
     * @return 稠密编码
     */
    private int codeOf(char ch) {
        int[] page = charPages[ch >>> 8];
        if (page == null) {
            return NO_CODE;
        }
        return page[ch & PAGE_MASK];
    }

    /**
     * 在归一化正文上找词的不重叠出现，并在原文同一下标填掩码。
     * 已经填过的位置会写成掩码字符，后面的短词不会再匹配到原字。
     *
     * @param out  输出缓冲，初始是原文
     * @param norm 归一化正文，命中处同步改成掩码，避免短词回头匹配
     * @param hit  调用方传入的命中词
     * @param star 掩码字符
     */
    private static void fillHit(char[] out, char[] norm, String hit, char star) {
        if (hit == null || hit.isEmpty()) {
            return;
        }
        String key = normalize(hit);
        int keyLen = key.length();
        if (keyLen == 0 || keyLen > norm.length) {
            return;
        }
        int from = 0;
        while (from <= norm.length - keyLen) {
            int at = indexOf(norm, key, from);
            if (at < 0) {
                return;
            }
            for (int i = 0; i < keyLen; i++) {
                out[at + i] = star;
                norm[at + i] = star;
            }
            from = at + keyLen;
        }
    }

    /**
     * 在字符数组里做字面量查找。正文是聊天长度，不做额外预处理。
     *
     * @param text 被查找的字符
     * @param key  要找的词
     * @param from 起始下标
     * @return 命中下标，没有则 -1
     */
    private static int indexOf(char[] text, String key, int from) {
        int limit = text.length - key.length();
        char first = key.charAt(0);
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (text[i] != first) {
                continue;
            }
            int matched = 1;
            while (matched < key.length() && text[i + matched] == key.charAt(matched)) {
                matched++;
            }
            if (matched == key.length()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 大小写折叠会改变长度时（极少数字母）退回正则，保证和原下标语义一致。
     *
     * @param text  原文
     * @param hits  命中词
     * @param token 单字符掩码
     * @return 脱敏文本
     */
    private static String maskByRegex(String text, List<String> hits, String token) {
        String out = text;
        List<String> sorted = new ArrayList<>(hits);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String hit : sorted) {
            if (hit == null || hit.isEmpty()) {
                continue;
            }
            String replacement = Matcher.quoteReplacement(token.repeat(Math.max(1, hit.length())));
            out = out.replaceAll("(?i)" + Pattern.quote(hit), replacement);
        }
        return out;
    }

    /**
     * 拉丁字母转小写，中文不变。
     *
     * @param value 原文
     * @return 归一化结果
     */
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * 构建结果。数组在返回后不再改写。
     *
     * @param base      双数组 base
     * @param check     双数组 check
     * @param fail      失配指针
     * @param wordOf    词尾编号
     * @param outLink   输出链
     * @param dict      词表
     * @param charPages 字符编码页
     */
    private record Compiled(int[] base, int[] check, int[] fail, int[] wordOf, int[] outLink,
                            String[] dict, int[][] charPages) {
    }

    /**
     * 先用临时 Trie 算 fail，再把节点铺进双数组。临时节点构建结束即可回收。
     */
    private static final class Compiler {

        /** 下一个词编号。 */
        private int wordSeq;

        /** 下一个稠密字符编码。 */
        private int codeSeq;

        /** 下一处优先尝试的空槽，避免每次从数组头重扫。 */
        private int nextCheckPos = 1;

        /** 构建期 check，空位是 EMPTY_CHECK。 */
        private int[] check = emptyCheck(INITIAL_CAPACITY);

        /** 构建期 base。 */
        private int[] base = new int[INITIAL_CAPACITY];

        /** 字符编码页，构建完成后交给自动机只读使用。 */
        private final int[][] charPages = new int[PAGE_COUNT][];

        /** 归一化词表。 */
        private final List<String> dict = new ArrayList<>();

        private Compiler() {
            this.check[0] = 0;
        }

        /**
         * 编译词库。无有效词时返回空自动机，扫描走快速返回。
         *
         * @param words 原始词
         * @return 只读数组
         */
        static Compiled compile(Iterable<String> words) {
            Compiler compiler = new Compiler();
            TNode root = new TNode();
            if (words != null) {
                for (String word : words) {
                    compiler.insert(root, word);
                }
            }
            if (compiler.dict.isEmpty()) {
                return new Compiled(new int[0], new int[0], new int[0], new int[0], new int[0],
                        new String[0], compiler.charPages);
            }
            List<TNode> bfs = compiler.buildFail(root);
            compiler.place(root);
            return compiler.freeze(root, bfs);
        }

        /**
         * 插入一个词。重复词只保留第一次。
         *
         * @param root 根
         * @param raw  原始词
         */
        private void insert(TNode root, String raw) {
            if (raw == null || raw.isBlank()) {
                return;
            }
            String word = normalize(raw);
            TNode cursor = root;
            for (int i = 0; i < word.length(); i++) {
                char ch = word.charAt(i);
                cursor = cursor.next.computeIfAbsent(ch, ignored -> new TNode());
                codeOf(ch);
            }
            if (cursor.word >= 0) {
                return;
            }
            cursor.word = wordSeq++;
            dict.add(word);
        }

        /**
         * BFS 构建 fail。根的失败指针指向自己，根的子节点失败后回到根。
         *
         * @param root 根
         * @return 广度优先节点序，后面写输出链时用
         */
        private List<TNode> buildFail(TNode root) {
            List<TNode> order = new ArrayList<>();
            Queue<TNode> queue = new ArrayDeque<>();
            root.fail = root;
            order.add(root);
            for (TNode child : root.next.values()) {
                child.fail = root;
                queue.offer(child);
                order.add(child);
            }
            while (!queue.isEmpty()) {
                TNode parent = queue.poll();
                linkChildren(parent, root, queue, order);
            }
            return order;
        }

        /**
         * 给一个节点的全部子节点接上 fail。
         *
         * @param parent 父节点
         * @param root   根
         * @param queue  BFS 队列
         * @param order  访问序
         */
        private void linkChildren(TNode parent, TNode root, Queue<TNode> queue, List<TNode> order) {
            for (Map.Entry<Character, TNode> entry : parent.next.entrySet()) {
                char ch = entry.getKey();
                TNode child = entry.getValue();
                TNode failCursor = parent.fail;
                while (failCursor != root && !failCursor.next.containsKey(ch)) {
                    failCursor = failCursor.fail;
                }
                child.fail = failCursor.next.getOrDefault(ch, root);
                if (child.fail == child) {
                    child.fail = root;
                }
                queue.offer(child);
                order.add(child);
            }
        }

        /**
         * 按广度优先把 Trie 铺进双数组。父状态先落位，子状态编号就是 base[父] + 字符编码。
         *
         * @param root 根，固定占用 0 号状态
         */
        private void place(TNode root) {
            root.state = 0;
            Queue<TNode> queue = new ArrayDeque<>();
            queue.offer(root);
            while (!queue.isEmpty()) {
                TNode node = queue.poll();
                if (node.next.isEmpty()) {
                    continue;
                }
                placeChildren(node, queue);
            }
        }

        /**
         * 为同一个父节点的所有出边找一段不冲突的 base。
         *
         * @param node  父节点
         * @param queue 待铺开的子节点
         */
        private void placeChildren(TNode node, Queue<TNode> queue) {
            int size = node.next.size();
            int[] codes = new int[size];
            TNode[] children = new TNode[size];
            int index = 0;
            for (Map.Entry<Character, TNode> entry : node.next.entrySet()) {
                codes[index] = codeOf(entry.getKey());
                children[index] = entry.getValue();
                index++;
            }
            sortByCode(codes, children);
            int begin = findBase(codes);
            ensure(node.state);
            base[node.state] = begin;
            for (int i = 0; i < size; i++) {
                int pos = begin + codes[i];
                occupy(node.state, pos);
                children[i].state = pos;
                queue.offer(children[i]);
            }
            advanceNextCheck();
        }

        /**
         * 按字符编码升序排列，方便用最小编码对齐空槽。
         *
         * @param codes    编码
         * @param children 与编码平行的子节点
         */
        private static void sortByCode(int[] codes, TNode[] children) {
            for (int i = 1; i < codes.length; i++) {
                int code = codes[i];
                TNode child = children[i];
                int slot = i - 1;
                while (slot >= 0 && codes[slot] > code) {
                    codes[slot + 1] = codes[slot];
                    children[slot + 1] = children[slot];
                    slot--;
                }
                codes[slot + 1] = code;
                children[slot + 1] = child;
            }
        }

        /**
         * 找到一个 begin，使 begin + 每个编码都落在空位上，且不占用根的 0 号槽。
         * 数组尾部以外视为空，因此一定能在有限步内找到。
         *
         * @param codes 已按升序排列的字符编码
         * @return base 值
         */
        private int findBase(int[] codes) {
            int lowest = codes[0];
            int highest = codes[codes.length - 1];
            for (int pos = Math.max(nextCheckPos, 1); ; pos++) {
                int begin = pos - lowest;
                if (begin < 0) {
                    continue;
                }
                long end = (long) begin + highest;
                if (end >= MAX_STATES) {
                    throw new IllegalStateException("敏感词双数组超过上限，拒绝构建以免拖垮进程");
                }
                if (fits(begin, codes)) {
                    if (pos == nextCheckPos) {
                        nextCheckPos = pos + 1;
                    }
                    return begin;
                }
            }
        }

        /**
         * 这段位移上的每个子状态是否都还空着。
         *
         * @param begin 候选 base
         * @param codes 字符编码
         * @return 可以放置
         */
        private boolean fits(int begin, int[] codes) {
            for (int code : codes) {
                long pos = (long) begin + code;
                if (pos <= 0L || pos >= MAX_STATES) {
                    return false;
                }
                if (pos < check.length && check[(int) pos] != EMPTY_CHECK) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 占用一个状态槽，check 记下父状态。
         *
         * @param parent 父状态
         * @param pos    子状态
         */
        private void occupy(int parent, int pos) {
            ensure(pos);
            check[pos] = parent;
        }

        /**
         * 把探测指针推到下一个空位，减少后续父节点的无效尝试。
         */
        private void advanceNextCheck() {
            while (nextCheckPos < check.length && check[nextCheckPos] != EMPTY_CHECK) {
                nextCheckPos++;
            }
        }

        /**
         * 保证下标可写，新槽位标成空。0 号根槽在扩容时保持已占用。
         *
         * @param pos 需要可写的下标
         */
        private void ensure(int pos) {
            if (pos < check.length) {
                return;
            }
            if (pos >= MAX_STATES) {
                throw new IllegalStateException("敏感词双数组超过上限，拒绝构建以免拖垮进程");
            }
            int size = check.length;
            int next = size;
            while (next <= pos) {
                next <<= 1;
                if (next <= 0 || next > MAX_STATES) {
                    next = MAX_STATES;
                    break;
                }
            }
            check = Arrays.copyOf(check, next);
            Arrays.fill(check, size, next, EMPTY_CHECK);
            base = Arrays.copyOf(base, next);
        }

        /**
         * 裁掉尾部空槽，写下 fail、词尾和输出链。
         *
         * @param root 根
         * @param bfs  广度优先序，fail 深度更浅的节点一定排在前面
         * @return 可交给自动机的只读数据
         */
        private Compiled freeze(TNode root, List<TNode> bfs) {
            int states = 1;
            for (TNode node : bfs) {
                states = Math.max(states, node.state + 1);
            }
            int[] failOut = new int[states];
            int[] words = new int[states];
            int[] links = new int[states];
            Arrays.fill(words, NONE);
            Arrays.fill(links, NONE);
            for (TNode node : bfs) {
                failOut[node.state] = node.fail.state;
                if (node.word >= 0) {
                    words[node.state] = node.word;
                }
            }
            for (TNode node : bfs) {
                if (node == root) {
                    continue;
                }
                links[node.state] = outputLink(node, words, links);
            }
            return new Compiled(Arrays.copyOf(base, states), Arrays.copyOf(check, states),
                    failOut, words, links, dict.toArray(String[]::new), charPages);
        }

        /**
         * 输出链指向最近一个仍会出词的失败状态。调用时失败节点已经写好自己的链。
         *
         * @param node  当前节点
         * @param words 词尾表
         * @param links 已经写好的输出链
         * @return 下一个状态，没有则 NONE
         */
        private static int outputLink(TNode node, int[] words, int[] links) {
            TNode failNode = node.fail;
            if (failNode == null || failNode.state <= 0) {
                return NONE;
            }
            if (words[failNode.state] >= 0) {
                return failNode.state;
            }
            return links[failNode.state];
        }

        /**
         * 取字符的稠密编码，没有则分配。页在第一次见到该高字节时才创建。
         *
         * @param ch 字符
         * @return 编码，从 0 连续递增
         */
        private int codeOf(char ch) {
            int pageIndex = ch >>> 8;
            int[] page = charPages[pageIndex];
            if (page == null) {
                page = new int[PAGE_SIZE];
                Arrays.fill(page, NO_CODE);
                charPages[pageIndex] = page;
            }
            int offset = ch & PAGE_MASK;
            int code = page[offset];
            if (code >= 0) {
                return code;
            }
            code = codeSeq++;
            page[offset] = code;
            return code;
        }

        /**
         * 创建一页 check，全部标空。
         *
         * @param size 长度
         * @return check 数组
         */
        private static int[] emptyCheck(int size) {
            int[] slots = new int[size];
            Arrays.fill(slots, EMPTY_CHECK);
            return slots;
        }
    }

    /**
     * 构建期 Trie 节点。只在编译时存活，不进入扫描路径。
     */
    private static final class TNode {

        /** 子边。构建期使用，扫描不再经过这里。 */
        private final Map<Character, TNode> next = new HashMap<>();

        /** 失败指针。 */
        private TNode fail;

        /** 词编号，不是词尾时为 NONE。 */
        private int word = NONE;

        /** 双数组状态号，根为 0。 */
        private int state = NONE;
    }
}
