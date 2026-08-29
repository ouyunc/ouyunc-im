package com.ouyunc.base.constant;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 根据 JDBC 配置选择 MySQL / PostgreSQL / Oracle 内联 SQL（与 {@link JdbcSqlConstant} 对应）。
 */
public final class JdbcSqlDialectHolder {

    private enum SqlDialect {
        MYSQL,
        POSTGRESQL,
        ORACLE
    }

    private static volatile SqlDialect current = SqlDialect.MYSQL;

    private JdbcSqlDialectHolder() {
    }

    /**
     * 在创建数据源时调用一次。{@code dialect} 可为空，此时根据 {@code jdbcUrl} 自动判断。
     */
    public static void configure(String dialect, String jdbcUrl) {
        String d = StringUtils.trimToEmpty(dialect).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(d) && StringUtils.isNotBlank(jdbcUrl)) {
            String u = jdbcUrl.toLowerCase(Locale.ROOT);
            if (u.contains("postgresql") || u.contains("jdbc:postgres")) {
                d = "postgresql";
            } else if (u.contains("jdbc:oracle")) {
                d = "oracle";
            }
        }
        if ("postgresql".equals(d) || "pgsql".equals(d) || "postgres".equals(d)) {
            current = SqlDialect.POSTGRESQL;
        } else if ("oracle".equals(d) || "ora".equals(d)) {
            current = SqlDialect.ORACLE;
        } else {
            current = SqlDialect.MYSQL;
        }
    }

    public static boolean isPostgreSql() {
        return current == SqlDialect.POSTGRESQL;
    }

    public static boolean isOracle() {
        return current == SqlDialect.ORACLE;
    }

    public static String selectMessage() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_MESSAGE, JdbcSqlConstant.ORACLE.SELECT_MESSAGE, JdbcSqlConstant.MYSQL.SELECT_MESSAGE);
    }

    public static String selectSessionMessageOffset() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_SESSION_MESSAGE_OFFSET, JdbcSqlConstant.ORACLE.SELECT_SESSION_MESSAGE_OFFSET, JdbcSqlConstant.MYSQL.SELECT_SESSION_MESSAGE_OFFSET);
    }

    public static String selectFriend() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_FRIEND, JdbcSqlConstant.ORACLE.SELECT_FRIEND, JdbcSqlConstant.MYSQL.SELECT_FRIEND);
    }

    public static String selectGroup() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_GROUP, JdbcSqlConstant.ORACLE.SELECT_GROUP, JdbcSqlConstant.MYSQL.SELECT_GROUP);
    }

    public static String selectGroupUser() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_GROUP_USER, JdbcSqlConstant.ORACLE.SELECT_GROUP_USER, JdbcSqlConstant.MYSQL.SELECT_GROUP_USER);
    }

    public static String selectGroupUserBatch() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_GROUP_USER_BATCH, JdbcSqlConstant.ORACLE.SELECT_GROUP_USER_BATCH, JdbcSqlConstant.MYSQL.SELECT_GROUP_USER_BATCH);
    }

    public static String selectAllGroupUser() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_ALL_GROUP_USER, JdbcSqlConstant.ORACLE.SELECT_ALL_GROUP_USER, JdbcSqlConstant.MYSQL.SELECT_ALL_GROUP_USER);
    }

    public static String selectUser() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_USER, JdbcSqlConstant.ORACLE.SELECT_USER, JdbcSqlConstant.MYSQL.SELECT_USER);
    }

    public static String selectBlacklist() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_BLACKLIST, JdbcSqlConstant.ORACLE.SELECT_BLACKLIST, JdbcSqlConstant.MYSQL.SELECT_BLACKLIST);
    }

    public static String selectApp() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_APP, JdbcSqlConstant.ORACLE.SELECT_APP, JdbcSqlConstant.MYSQL.SELECT_APP);
    }

    public static String selectAllApps() {
        return pick(JdbcSqlConstant.POSTGRESQL.SELECT_ALL_APPS, JdbcSqlConstant.ORACLE.SELECT_ALL_APPS, JdbcSqlConstant.MYSQL.SELECT_ALL_APPS);
    }

    private static String pick(JdbcSqlConstant.POSTGRESQL pg, JdbcSqlConstant.ORACLE ora, JdbcSqlConstant.MYSQL my) {
        return switch (current) {
            case POSTGRESQL -> pg.sql();
            case ORACLE -> ora.sql();
            case MYSQL -> my.sql();
        };
    }

}
