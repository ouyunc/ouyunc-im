package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;

/**
 * @author fzx
 * @description 默认持久化仓库实现,注意如果子类不进行覆盖，则使用默认的操作器来处理数据
 */
public enum DefaultRepository implements Repository{
    INSTANCE;
    @Override
    public void save(Packet packet) {

    }
}
