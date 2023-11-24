package com.ouyunc.im.db.operator;

import com.ouyunc.im.db.config.JdbcPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.util.List;

/**
 * mysql 数据库操作
 */
public class MysqlDbOperator extends AbstractDbOperator{
    private static Logger log = LoggerFactory.getLogger(MysqlDbOperator.class);


    /**
     * 单个查询
     * @param sql
     * @param tClass
     * @param args
     * @return
     */
    @Override
    public <T> T selectOne(String sql, Class<T> tClass, Object... args) {
        try{
            return JdbcPool.INSTANCE.jdbcTemplate().queryForObject(sql, new BeanPropertyRowMapper<>(tClass), args);
        }catch (EmptyResultDataAccessException e ){
            log.warn("未查询到数据，返回null; sql: {} , args：{}", sql, args);
            return null;
        }
    }

    /**
     * 批量查询
     * @param sql
     * @param tClass
     * @param args
     * @return
     */
    @Override
    public <T> List<T> batchSelect(String sql, Class<T> tClass, Object... args) {
        try {
            return JdbcPool.INSTANCE.jdbcTemplate().query(sql, new BeanPropertyRowMapper<>(tClass), args);
        }catch (IllegalStateException e) {
            log.warn("未查询到数据，返回null; sql: {} , args：{}", sql, args);
            return null;
        }
    }

    /**
     * 单个插入
     * @param sql
     * @param args
     * @return
     */
    @Override
    public int insert(String sql, Object... args) {
        return JdbcPool.INSTANCE.jdbcTemplate().update(sql, args);
    }

    /**
     * 批量插入
     * @param sql
     * @param batchArgs
     * @return
     */
    @Override
    public int[] batchInsert(String sql, List<Object[]> batchArgs) {
        return JdbcPool.INSTANCE.jdbcTemplate().batchUpdate(sql, batchArgs);
    }

    /**
     * 更新
     * @param sql
     * @param args
     * @return
     */
    @Override
    public int update(String sql, Object... args) {
        return JdbcPool.INSTANCE.jdbcTemplate().update(sql, args);
    }

    /**
     * 批量更新
     * @param sql
     * @param batchArgs
     * @return
     */
    @Override
    public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
        return JdbcPool.INSTANCE.jdbcTemplate().batchUpdate(sql, batchArgs);
    }

    /**
     * 单个删除
     * @param sql
     * @param args
     * @return
     */
    @Override
    public int delete(String sql, Object... args) {
        return JdbcPool.INSTANCE.jdbcTemplate().update(sql, args);
    }

    /**
     * 批量删除
     * @param sql
     * @param batchArgs
     * @return
     */
    @Override
    public int[] batchDelete(String sql, List<Object[]> batchArgs) {
        return JdbcPool.INSTANCE.jdbcTemplate().batchUpdate(sql, batchArgs);
    }
}
