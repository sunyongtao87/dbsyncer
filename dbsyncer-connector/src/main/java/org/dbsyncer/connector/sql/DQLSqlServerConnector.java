package org.dbsyncer.connector.sql;

import org.apache.commons.lang.StringUtils;
import org.dbsyncer.connector.ConnectorException;
import org.dbsyncer.connector.config.*;
import org.dbsyncer.connector.constant.DatabaseConstant;
import org.dbsyncer.connector.database.AbstractDatabaseConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class DQLSqlServerConnector extends AbstractDatabaseConnector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected String getTablesSql(DatabaseConfig config) {
        return "SELECT NAME FROM SYS.TABLES WHERE SCHEMA_ID = SCHEMA_ID('DBO')";
    }

    @Override
    public String getPageSql(PageSqlBuilderConfig config) {
        String pk = config.getConfig().getPk();
        String tableName = config.getConfig().getTableName();
        if (StringUtils.isBlank(pk) || StringUtils.isBlank(tableName)) {
            logger.error("Table primary key and name can not be empty.");
            throw new ConnectorException("Table primary key and name can not be empty.");
        }
        return String.format(DatabaseConstant.SQLSERVER_PAGE_SQL, tableName, tableName, pk, pk, pk, pk);
    }

    @Override
    public PageArgConfig prepareSetArgs(String sql, int pageIndex, int pageSize) {
        sql = sql.replaceFirst("\\?", String.valueOf(pageSize));
        sql = sql.replaceFirst("\\?", String.valueOf((pageIndex - 1) * pageSize + 1));
        return new PageArgConfig(sql, new Object[] {});
    }

    @Override
    public List<String> getTable(ConnectorConfig config) {
        return super.getDqlTable(config);
    }

    @Override
    public MetaInfo getMetaInfo(ConnectorConfig config, String tableName) {
        return super.getDqlMetaInfo(config);
    }

    @Override
    public Map<String, String> getSourceCommand(CommandConfig commandConfig) {
        return super.getDqlSourceCommand(commandConfig, false);
    }

}