package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 地图/位置消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#LOCATION_CONTENT}）。
 */
public class LocationContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 纬度（WGS84 / 业务约定坐标系） */
    private double latitude;
    /** 经度（WGS84 / 业务约定坐标系） */
    private double longitude;
    /** 地点名称，如门店名、仓库名 */
    private String name;
    /** 详细地址文案，用于列表与气泡展示 */
    private String address;
    /** 静态地图缩略图 URL，客户端可直接展示；为空则本地根据经纬度绘制 */
    private String thumbUrl;
    /** 地图服务商标识，如 amap / tencent / google，便于客户端调起对应 App */
    private String mapProvider;
    /** 地图缩放级别（可选），数值越大越近 */
    private Integer zoom;

    public LocationContent() {
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getMapProvider() {
        return mapProvider;
    }

    public void setMapProvider(String mapProvider) {
        this.mapProvider = mapProvider;
    }

    public Integer getZoom() {
        return zoom;
    }

    public void setZoom(Integer zoom) {
        this.zoom = zoom;
    }
}
