package org.dbsyncer.connector.sql;

import org.dbsyncer.connector.config.*;
import org.dbsyncer.connector.constant.DatabaseConstant;
import org.dbsyncer.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.connector.database.DatabaseConnectorMapper;

import java.util.List;
import java.util.Map;

public final class DQLMysqlConnector extends AbstractDatabaseConnector {

    @Override
    protected String getTableSql() {
        return "show tables";
    }

    @Override
    public String getPageSql(PageSqlConfig config) {
        return config.getQuerySql() + DatabaseConstant.MYSQL_PAGE_SQL;
    }

    @Override
    public Object[] getPageArgs(int pageIndex, int pageSize) {
        return new Object[]{(pageIndex - 1) * pageSize, pageSize};
    }

    @Override
    public List<Table> getTable(DatabaseConnectorMapper config) {
        return super.getDqlTable(config);
    }

    @Override
    public MetaInfo getMetaInfo(DatabaseConnectorMapper connectorMapper, String tableName) {
        return super.getDqlMetaInfo(connectorMapper);
    }

    @Override
    public Map<String, String> getSourceCommand(CommandConfig commandConfig) {
        return super.getDqlSourceCommand(commandConfig, true);
    }
}