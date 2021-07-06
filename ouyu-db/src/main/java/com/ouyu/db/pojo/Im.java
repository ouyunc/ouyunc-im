package com.ouyu.db.pojo;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public class Im {
    private int id;
    private String name;
    private String maxMoney;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMaxMoney() {
        return maxMoney;
    }

    public void setMaxMoney(String maxMoney) {
        this.maxMoney = maxMoney;
    }

    @Override
    public String toString() {
        return "Im{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", maxMoney='" + maxMoney + '\'' +
                '}';
    }
}
