package org.dbsyncer.connector.enums;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.connector.CompareFilter;
import org.dbsyncer.connector.ConnectorException;

/**
 * 运算符表达式类型
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/26 23:21
 */
public enum FilterEnum {

    /**
     * 等于
     */
    EQUAL("=", (value, filterValue) -> StringUtil.equals(value, filterValue)),
    /**
     * 不等于
     */
    NOT_EQUAL("!=", (value, filterValue) -> !StringUtil.equals(value, filterValue)),
    /**
     * 大于
     */
    GT(">", (value, filterValue) -> NumberUtil.toInt(value) > NumberUtil.toInt(filterValue)),
    /**
     * 小于
     */
    LT("<", (value, filterValue) -> NumberUtil.toInt(value) < NumberUtil.toInt(filterValue)),
    /**
     * 大于等于
     */
    GT_AND_EQUAL(">=", (value, filterValue) -> NumberUtil.toInt(value) >= NumberUtil.toInt(filterValue)),
    /**
     * 小于等于
     */
    LT_AND_EQUAL("<=", (value, filterValue) -> NumberUtil.toInt(value) <= NumberUtil.toInt(filterValue));

    // 运算符名称
    private String name;
    // 比较器
    private CompareFilter compareFilter;

    FilterEnum(String name, CompareFilter compareFilter) {
        this.name = name;
        this.compareFilter = compareFilter;
    }

    /**
     * 获取比较器
     *
     * @param filterName
     * @return
     * @throws ConnectorException
     */
    public static CompareFilter getCompareFilter(String filterName) throws ConnectorException {
        for (FilterEnum e : FilterEnum.values()) {
            if (StringUtil.equals(filterName, e.getName())) {
                return e.getCompareFilter();
            }
        }
        throw new ConnectorException(String.format("FilterEnum name \"%s\" does not exist.", filterName));
    }

    public String getName() {
        return name;
    }

    public CompareFilter getCompareFilter() {
        return compareFilter;
    }

}