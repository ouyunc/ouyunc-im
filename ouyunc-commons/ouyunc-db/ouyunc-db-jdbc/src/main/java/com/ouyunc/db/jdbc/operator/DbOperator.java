package com.ouyunc.db.jdbc.operator;

import java.util.List;

/**
 * 数据库操作层
 */
public interface DbOperator {

    /**
     * 执行任何sql,一般执行DDL语句 ,如存储过程，函数等
     *
     * @param tClass
     * @param sql
     * @return
     */
    void execute(String sql);


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
    <T> List<T> selectList(String sql, Class<T> tClass, Object... args);

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
    default int[] batchInsert(String sql, List<Object[]> batchArgs){
        throw new UnsupportedOperationException("不支持批量插入");
    }

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
    default int[] batchUpdate(String sql, List<Object[]> batchArgs){
        throw new UnsupportedOperationException("不支持批量更新");
    }

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
    default int[] batchDelete(String sql, List<Object[]> batchArgs) {
        throw new UnsupportedOperationException("不支持批量删除");
    }
}
