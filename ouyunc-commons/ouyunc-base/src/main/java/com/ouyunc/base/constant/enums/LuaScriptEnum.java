package com.ouyunc.base.constant.enums;

/**
 * lua 脚本枚举
 */
public enum LuaScriptEnum {

    BATCH_SCORE_LUA_SCRIPT("1","local scores = {}\n" +
            "for i = 1, #ARGV do\n" +
            "    local member = ARGV[i]\n" +
            "    local score = redis.call('ZSCORE', KEYS[1], member)\n" +
            "    scores[i] = score\n" +
            "end\n" +
            "return scores", "zset批量获取分数lua脚本"),

    ;

    private String version;

    private String script;

    private String description;


    LuaScriptEnum(String version, String script, String description) {
        this.script = script;
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
