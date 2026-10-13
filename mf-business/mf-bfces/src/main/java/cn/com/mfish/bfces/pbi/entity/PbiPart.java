package cn.com.mfish.bfces.pbi.entity;

import java.util.Map;

/**
 * @description: PBI产品型号
 * @author: mfish
 * @date: 2026-10-12
 * @version: V1.0.0
 */
public class PbiPart {
    private String wtPartNumber;
    private String name;
    private String image;
    private Map<String, Object> attrs;

    public String getWtPartNumber() {
        return wtPartNumber;
    }

    public void setWtPartNumber(String wtPartNumber) {
        this.wtPartNumber = wtPartNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, Object> attrs) {
        this.attrs = attrs;
    }
}
