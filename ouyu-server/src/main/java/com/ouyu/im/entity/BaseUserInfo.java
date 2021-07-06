package com.ouyu.im.entity;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 用户基础信息
 * @Version V1.0
 **/
public class BaseUserInfo implements Serializable {

    /**
     * 用户唯一标识，不为空
     */
    private String identity;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
