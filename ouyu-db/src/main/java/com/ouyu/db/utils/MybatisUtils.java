package com.ouyu.db.utils;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;


/**
 * @Author fangzhenxun
 * @Description mybatis 工具类
 */
public class MybatisUtils {
	
	private  static SqlSessionFactory sqlSessionFactory;

	private  MybatisUtils() {}

	// 静态方式初始化
	static{
		InputStream inputStream = null;
		try {
			inputStream = Resources.getResourceAsStream("mybatis-config.xml");
		} catch (IOException e) {
			e.printStackTrace();
		}
		sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
	}
	


	/**
	 * @Author fangzhenxun
	 * @Description 获取sqlSession
	 * @return org.apache.ibatis.session.SqlSession
	 */
	public static SqlSession sqlSession(){
		return sqlSessionFactory.openSession();
	}

}
