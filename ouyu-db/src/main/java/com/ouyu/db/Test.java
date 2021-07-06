package com.ouyu.db;

import com.ouyu.db.pojo.Im;
import com.ouyu.db.mapper.ImMapper;
import com.ouyu.db.utils.MybatisUtils;
import org.apache.ibatis.session.SqlSession;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public class Test {

    public static void main(String[] args) {
        SqlSession sqlSession = MybatisUtils.sqlSession();
        ImMapper mapper = sqlSession.getMapper(ImMapper.class);
        Im im = mapper.selectById(1);
        System.out.println(im);
    }

}
