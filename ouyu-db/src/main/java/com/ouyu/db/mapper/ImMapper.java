package com.ouyu.db.mapper;

import com.ouyu.db.pojo.Im;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
@Mapper
public interface ImMapper {

    // 根据id查找im
    @Select("select * from im where id  = #{id}")
    Im selectById(@Param("id") int id);
}
