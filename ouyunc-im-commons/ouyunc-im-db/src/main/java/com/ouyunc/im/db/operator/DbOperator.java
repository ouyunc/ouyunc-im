package com.ouyunc.im.db.operator;

import java.util.List;

/**
 * 数据库操作层
 */
public interface DbOperator {

    /**
     * 返回单个实体对象
     * @param sql
     * @param tClass
     * @param args
     * @return
     */
    <T> T selectOne(String sql, Class<T> tClass, Object... args);

    /**
     * 返回多个实体对象
     * @param sql
     * @param tClass
     * @param args
     * @return
     */
    <T> List<T> batchSelect(String sql,Class<T> tClass, Object... args);

    /**
     * 单个插入
     * @param sql
     * @param args
     * @return
     */
    int insert(String sql, Object... args);

    /**
     * 批量插入
     * @param sql
     * @param batchArgs
     * @return
     */
    int[] batchInsert(String sql, List<Object[]> batchArgs);

    /**
     * 单个修改
     * @param sql
     * @param args
     * @return
     */
    int update(String sql, Object... args);

    /**
     * 批量修改
     * @param sql
     * @param batchArgs
     * @return
     */
    int[] batchUpdate(String sql, List<Object[]> batchArgs);

    /**
     * 单个删除
     * @param sql
     * @param args
     * @return
     */
    int delete(String sql, Object... args);

    /**
     * 批量删除
     * @param sql
     * @param batchArgs
     * @return
     */
    int[] batchDelete(String sql, List<Object[]> batchArgs);
}
