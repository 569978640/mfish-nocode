package cn.com.mfish.bfces.pbi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.commons.lang3.StringUtils;

@TableName("PBI_FeatureParameterDisplayConfig")
public class PbiParamConfig {
    private String firstCategory;
    private String paramGroup;
    private String param;
    private String displayName;
    private String internalName;
    private String columnName;
    private String groupColor;
    private String paramColor;

    public PbiParamConfig() {
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getFirstCategory() {
        return firstCategory;
    }

    public void setFirstCategory(String firstCategory) {
        this.firstCategory = firstCategory;
    }

    public String getParamGroup() {
        return paramGroup;
    }

    public void setParamGroup(String paramGroup) {
        this.paramGroup = paramGroup;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public String getGroupColor() {
        return groupColor;
    }

    public void setGroupColor(String groupColor) {
        this.groupColor = groupColor;
    }

    public String getParamColor() {
        return paramColor;
    }

    public void setParamColor(String paramColor) {
        this.paramColor = paramColor;
    }

    @Override
    public String toString() {
        return "PbiParamConfig{" +
                "firstCategory='" + firstCategory + '\'' +
                ", paramGroup='" + paramGroup + '\'' +
                ", param='" + param + '\'' +
                ", displayName='" + displayName + '\'' +
                ", internalName='" + internalName + '\'' +
                ", groupColor='" + groupColor + '\'' +
                ", paramColor='" + paramColor + '\'' +
                '}';
    }
}