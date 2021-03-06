package org.dbsyncer.parser.model;

import org.dbsyncer.connector.config.Table;

import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/15 23:56
 */
public class TableGroup extends AbstractConfigModel {

    // 驱动映射关系ID
    private String mappingId;

    // 数据源表
    private Table sourceTable;

    // 目标源表
    private Table targetTable;

    // 字段映射关系
    private List<FieldMapping> fieldMapping;

    // 执行命令，例SQL等
    private Map<String, String> command;

    public String getMappingId() {
        return mappingId;
    }

    public TableGroup setMappingId(String mappingId) {
        this.mappingId = mappingId;
        return this;
    }

    public Table getSourceTable() {
        return sourceTable;
    }

    public TableGroup setSourceTable(Table sourceTable) {
        this.sourceTable = sourceTable;
        return this;
    }

    public Table getTargetTable() {
        return targetTable;
    }

    public TableGroup setTargetTable(Table targetTable) {
        this.targetTable = targetTable;
        return this;
    }

    public List<FieldMapping> getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(List<FieldMapping> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public Map<String, String> getCommand() {
        return command;
    }

    public TableGroup setCommand(Map<String, String> command) {
        this.command = command;
        return this;
    }

}