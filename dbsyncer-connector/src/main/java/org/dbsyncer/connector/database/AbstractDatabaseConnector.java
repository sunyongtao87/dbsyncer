package org.dbsyncer.connector.database;

import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.AbstractConnector;
import org.dbsyncer.connector.Connector;
import org.dbsyncer.connector.ConnectorException;
import org.dbsyncer.connector.ConnectorMapper;
import org.dbsyncer.connector.config.*;
import org.dbsyncer.connector.constant.ConnectorConstant;
import org.dbsyncer.connector.enums.OperationEnum;
import org.dbsyncer.connector.enums.SetterEnum;
import org.dbsyncer.connector.enums.SqlBuilderEnum;
import org.dbsyncer.connector.util.DatabaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.util.Assert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractDatabaseConnector extends AbstractConnector implements Connector<DatabaseConnectorMapper, DatabaseConfig>, Database {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected abstract String getTableSql();

    @Override
    public ConnectorMapper connect(DatabaseConfig config) {
        try {
            return new DatabaseConnectorMapper(config, DatabaseUtil.getConnection(config));
        } catch (Exception e) {
            logger.error("Failed to connect:{}, message:{}", config.getUrl(), e.getMessage());
        }
        throw new ConnectorException(String.format("Failed to connect:%s", config.getUrl()));
    }

    @Override
    public void disconnect(DatabaseConnectorMapper connectorMapper) {
        DatabaseUtil.close(connectorMapper.getConnection());
    }

    @Override
    public boolean isAlive(DatabaseConnectorMapper connectorMapper) {
        Integer count = connectorMapper.execute(databaseTemplate -> databaseTemplate.queryForObject(getValidationQuery(), Integer.class));
        return null != count && count > 0;
    }

    @Override
    public String getConnectorMapperCacheKey(DatabaseConfig config) {
        return String.format("%s-%s", config.getUrl(), config.getUsername());
    }

    @Override
    public List<Table> getTable(DatabaseConnectorMapper connectorMapper) {
        String sql = getTableSql();
        List<String> tableNames = connectorMapper.execute(databaseTemplate -> databaseTemplate.queryForList(sql, String.class));
        if(!CollectionUtils.isEmpty(tableNames)){
            return tableNames.stream().map(name -> new Table(name)).collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
    }

    @Override
    public MetaInfo getMetaInfo(DatabaseConnectorMapper connectorMapper, String tableName) {
        String quotation = buildSqlWithQuotation();
        StringBuilder queryMetaSql = new StringBuilder("SELECT * FROM ").append(quotation).append(tableName).append(quotation).append(" WHERE 1 != 1");
        return connectorMapper.execute(databaseTemplate -> DatabaseUtil.getMetaInfo(databaseTemplate, queryMetaSql.toString(), tableName));
    }

    @Override
    public long getCount(DatabaseConnectorMapper connectorMapper, Map<String, String> command) {
        // 1?????????select SQL
        String queryCountSql = command.get(ConnectorConstant.OPERTION_QUERY_COUNT);
        Assert.hasText(queryCountSql, "??????????????????????????????.");

        // 2??????????????????
        return connectorMapper.execute(databaseTemplate -> databaseTemplate.queryForObject(queryCountSql, Long.class));
    }

    @Override
    public Result reader(DatabaseConnectorMapper connectorMapper, ReaderConfig config) {
        // 1?????????select SQL
        String querySql = config.getCommand().get(SqlBuilderEnum.QUERY.getName());
        Assert.hasText(querySql, "????????????????????????.");

        // 2???????????????
        Collections.addAll(config.getArgs(), getPageArgs(config.getPageIndex(), config.getPageSize()));

        // 3?????????SQL
        List<Map<String, Object>> list = connectorMapper.execute(databaseTemplate -> databaseTemplate.queryForList(querySql, config.getArgs().toArray()));

        // 4??????????????????
        return new Result(new ArrayList<>(list));
    }

    @Override
    public Result writer(DatabaseConnectorMapper connectorMapper, WriterBatchConfig config) {
        List<Field> fields = config.getFields();
        List<Map> data = config.getData();

        // 1?????????select SQL
        String insertSql = config.getCommand().get(SqlBuilderEnum.INSERT.getName());
        Assert.hasText(insertSql, "????????????????????????.");
        if (CollectionUtils.isEmpty(fields)) {
            logger.error("writer fields can not be empty.");
            throw new ConnectorException("writer fields can not be empty.");
        }
        if (CollectionUtils.isEmpty(data)) {
            logger.error("writer data can not be empty.");
            throw new ConnectorException("writer data can not be empty.");
        }
        final int size = data.size();
        final int fSize = fields.size();

        Result result = new Result();
        try {
            // 2???????????????
            connectorMapper.execute(databaseTemplate -> {
                databaseTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement preparedStatement, int i) {
                        batchRowsSetter(databaseTemplate.getConnection(), preparedStatement, fields, fSize, data.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return size;
                    }
                });
                return true;
            });
        } catch (Exception e) {
            // ??????????????????
            result.getFailData().addAll(data);
            result.getFail().set(size);
            result.getError().append(e.getMessage()).append(System.lineSeparator());
            logger.error(e.getMessage());
        }
        return result;
    }

    @Override
    public Result writer(DatabaseConnectorMapper connectorMapper, WriterSingleConfig config) {
        String event = config.getEvent();
        List<Field> fields = config.getFields();
        Map<String, Object> data = config.getData();
        // 1????????? SQL
        String sql = config.getCommand().get(event);
        Assert.hasText(sql, "????????????????????????.");
        if (CollectionUtils.isEmpty(data) || CollectionUtils.isEmpty(fields)) {
            logger.error("writer data can not be empty.");
            throw new ConnectorException("writer data can not be empty.");
        }

        Field pkField = getPrimaryKeyField(fields);
        // Update / Delete
        if (isUpdate(event) || isDelete(event)) {
            if (isDelete(event)) {
                fields.clear();
            }
            fields.add(pkField);
        }

        int size = fields.size();
        Result result = new Result();
        int execute = 0;
        try {
            // 2???????????????
            execute = connectorMapper.execute(databaseTemplate ->
                    databaseTemplate.update(sql, (ps) -> {
                        Field f = null;
                        for (int i = 0; i < size; i++) {
                            f = fields.get(i);
                            SetterEnum.getSetter(f.getType()).set(databaseTemplate.getConnection(), ps, i + 1, f.getType(), data.get(f.getName()));
                        }
                    })
            );
        } catch (Exception e) {
            // ??????????????????
            if(!config.isForceUpdate()){
                result.getFailData().add(data);
                result.getFail().set(1);
                result.getError().append("SQL:").append(sql).append(System.lineSeparator())
                        .append("DATA:").append(data).append(System.lineSeparator())
                        .append("ERROR:").append(e.getMessage()).append(System.lineSeparator());
                logger.error("SQL:{}, DATA:{}, ERROR:{}", sql, data, e.getMessage());
            }
        }

        if (0 == execute && !config.isRetry() && null != pkField) {
            // ????????????insert
            if(isUpdate(event)){
                String queryCount = config.getCommand().get(ConnectorConstant.OPERTION_QUERY_COUNT_EXIST);
                if(!existRow(connectorMapper, queryCount, data.get(pkField.getName()))){
                    fields.remove(fields.size() - 1);
                    config.setEvent(ConnectorConstant.OPERTION_INSERT);
                    config.setRetry(true);
                    logger.warn("{}?????????{}??????, ????????????{}, {}", config.getTable(), event, config.getEvent(), data);
                    return writer(connectorMapper, config);
                }
                return result;
            }
            // ?????????update
            if(isInsert(event)){
                config.setEvent(ConnectorConstant.OPERTION_UPDATE);
                config.setRetry(true);
                return writer(connectorMapper, config);
            }

        }
        return result;
    }

    @Override
    public Map<String, String> getSourceCommand(CommandConfig commandConfig) {
        // ????????????SQL
        List<Filter> filter = commandConfig.getFilter();
        String queryFilterSql = getQueryFilterSql(filter);

        // ????????????SQL
        Table table = commandConfig.getTable();
        Map<String, String> map = new HashMap<>();

        String query = ConnectorConstant.OPERTION_QUERY;
        map.put(query, buildSql(query, table, commandConfig.getOriginalTable(), queryFilterSql));

        // ??????????????????SQL
        String quotation = buildSqlWithQuotation();
        String pk = DatabaseUtil.findTablePrimaryKey(commandConfig.getOriginalTable(), quotation);
        StringBuilder queryCount = new StringBuilder();
        queryCount.append("SELECT COUNT(1) FROM (SELECT 1 FROM ").append(quotation).append(table.getName()).append(quotation);
        if (StringUtil.isNotBlank(queryFilterSql)) {
            queryCount.append(queryFilterSql);
        }
        if(!StringUtil.isBlank(pk)){
            queryCount.append(" GROUP BY ").append(pk);
        }
        queryCount.append(") DBSYNCER_T");
        map.put(ConnectorConstant.OPERTION_QUERY_COUNT, queryCount.toString());
        return map;
    }

    @Override
    public Map<String, String> getTargetCommand(CommandConfig commandConfig) {
        // ???????????????SQL
        Map<String, String> map = new HashMap<>();
        Table table = commandConfig.getTable();
        Table originalTable = commandConfig.getOriginalTable();

        String insert = SqlBuilderEnum.INSERT.getName();
        map.put(insert, buildSql(insert, table, originalTable, null));

        String update = SqlBuilderEnum.UPDATE.getName();
        map.put(update, buildSql(update, table, originalTable, null));

        String delete = SqlBuilderEnum.DELETE.getName();
        map.put(delete, buildSql(delete, table, originalTable, null));

        // ?????????????????????????????????
        String quotation = buildSqlWithQuotation();
        String pk = DatabaseUtil.findTablePrimaryKey(commandConfig.getOriginalTable(), quotation);
        StringBuilder queryCount = new StringBuilder().append("SELECT COUNT(1) FROM ").append(quotation).append(table.getName()).append(
                quotation).append(" WHERE ").append(pk).append(" = ?");
        String queryCountExist = ConnectorConstant.OPERTION_QUERY_COUNT_EXIST;
        map.put(queryCountExist, queryCount.toString());
        return map;
    }

    /**
     * ??????DQL?????????
     *
     * @param config
     * @return
     */
    protected List<Table> getDqlTable(DatabaseConnectorMapper config) {
        MetaInfo metaInfo = getDqlMetaInfo(config);
        Assert.notNull(metaInfo, "SQL????????????.");
        DatabaseConfig cfg = config.getConfig();
        List<Table> tables = new ArrayList<>();
        tables.add(new Table(cfg.getSql()));
        return tables;
    }

    /**
     * ??????DQL?????????
     *
     * @param config
     * @return
     */
    protected MetaInfo getDqlMetaInfo(DatabaseConnectorMapper config) {
        DatabaseConfig cfg = config.getConfig();
        String sql = cfg.getSql().toUpperCase();
        String queryMetaSql = StringUtil.contains(sql, " WHERE ") ? sql + " AND 1!=1 " : sql + " WHERE 1!=1 ";
        return config.execute(databaseTemplate -> DatabaseUtil.getMetaInfo(databaseTemplate, queryMetaSql, cfg.getTable()));
    }

    /**
     * ??????DQL?????????
     *
     * @param commandConfig
     * @param appendGroupByPK
     * @return
     */
    protected Map<String, String> getDqlSourceCommand(CommandConfig commandConfig, boolean appendGroupByPK) {
        // ????????????SQL
        List<Filter> filter = commandConfig.getFilter();
        String queryFilterSql = getQueryFilterSql(filter);

        // ????????????SQL
        Table table = commandConfig.getTable();
        Map<String, String> map = new HashMap<>();
        String querySql = table.getName();

        // ????????????
        if (StringUtil.isNotBlank(queryFilterSql)) {
            querySql += queryFilterSql;
        }
        String quotation = buildSqlWithQuotation();
        String pk = DatabaseUtil.findTablePrimaryKey(commandConfig.getOriginalTable(), quotation);
        map.put(SqlBuilderEnum.QUERY.getName(), getPageSql(new PageSqlConfig(querySql, pk)));

        // ??????????????????SQL
        StringBuilder queryCount = new StringBuilder();
        queryCount.append("SELECT COUNT(1) FROM (").append(table.getName());
        if (StringUtil.isNotBlank(queryFilterSql)) {
            queryCount.append(queryFilterSql);
        }
        // Mysql
        if (appendGroupByPK) {
            queryCount.append(" GROUP BY ").append(pk);
        }
        queryCount.append(") DBSYNCER_T");
        map.put(ConnectorConstant.OPERTION_QUERY_COUNT, queryCount.toString());
        return map;
    }

    /**
     * ?????????????????????????????????????????????????????????
     *
     * @return
     */
    protected String buildSqlWithQuotation() {
        return "";
    }

    /**
     * ??????????????????SQL
     *
     * @param filter
     * @return
     */
    protected String getQueryFilterSql(List<Filter> filter) {
        if (CollectionUtils.isEmpty(filter)) {
            return "";
        }
        // ????????????SQL
        StringBuilder sql = new StringBuilder();

        // ????????????SQL
        String addSql = getFilterSql(OperationEnum.AND.getName(), filter);
        // ??????Add????????????
        if (StringUtil.isNotBlank(addSql)) {
            sql.append(addSql);
        }

        // ????????????SQL
        String orSql = getFilterSql(OperationEnum.OR.getName(), filter);
        // ??????Or?????????Add???????????????
        if (StringUtil.isNotBlank(orSql) && StringUtil.isNotBlank(addSql)) {
            sql.append(" OR ");
        }
        sql.append(orSql);

        // ????????????????????? WHERE
        if (StringUtil.isNotBlank(sql.toString())) {
            // WHERE (USER.USERNAME = 'zhangsan' AND USER.AGE='20') OR (USER.TEL='18299996666')
            sql.insert(0, " WHERE ");
        }
        return sql.toString();
    }

    /**
     * ????????????SQL
     *
     * @param type           {@link SqlBuilderEnum}
     * @param table
     * @param originalTable
     * @param queryFilterSQL
     * @return
     */
    protected String buildSql(String type, Table table, Table originalTable, String queryFilterSQL) {
        if (null == table) {
            logger.error("Table can not be null.");
            throw new ConnectorException("Table can not be null.");
        }
        List<Field> column = table.getColumn();
        if (CollectionUtils.isEmpty(column)) {
            logger.warn("Table column is null.");
            return null;
        }
        String pk = null;
        Set<String> mark = new HashSet<>();
        List<Field> fields = new ArrayList<>();
        for (Field c : column) {
            String name = c.getName();
            if (StringUtil.isBlank(name)) {
                throw new ConnectorException("The field name can not be empty.");
            }
            if (c.isPk()) {
                pk = name;
            }
            if (!mark.contains(name)) {
                fields.add(c);
                mark.add(name);
            }
        }
        if (CollectionUtils.isEmpty(fields)) {
            logger.error("The fields can not be empty.");
            throw new ConnectorException("The fields can not be empty.");
        }
        String tableName = table.getName();
        if (StringUtil.isBlank(tableName)) {
            logger.error("Table name can not be empty.");
            throw new ConnectorException("Table name can not be empty.");
        }
        if (StringUtil.isBlank(pk)) {
            pk = DatabaseUtil.findTablePrimaryKey(originalTable, "");
        }

        SqlBuilderConfig config = new SqlBuilderConfig(this, tableName, pk, fields, queryFilterSQL, buildSqlWithQuotation());
        return SqlBuilderEnum.getSqlBuilder(type).buildSql(config);
    }

    /**
     * ????????????
     *
     * @return
     */
    protected String getValidationQuery() {
        return "select 1";
    }

    /**
     * ??????????????????????????????SQL
     *
     * @param queryOperator and/or
     * @param filter
     * @return
     */
    private String getFilterSql(String queryOperator, List<Filter> filter) {
        List<Filter> list = filter.stream().filter(f -> StringUtil.equals(f.getOperation(), queryOperator)).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }

        int size = list.size();
        int end = size - 1;
        StringBuilder sql = new StringBuilder();
        sql.append("(");
        Filter c = null;
        String quotation = buildSqlWithQuotation();
        for (int i = 0; i < size; i++) {
            c = list.get(i);
            // "USER" = 'zhangsan'
            sql.append(quotation).append(c.getName()).append(quotation).append(c.getFilter()).append("'").append(c.getValue()).append("'");
            if (i < end) {
                sql.append(" ").append(queryOperator).append(" ");
            }
        }
        sql.append(")");
        return sql.toString();
    }

    /**
     * @param connection ??????
     * @param ps         ???????????????
     * @param fields     ?????????????????????[{name=ID, type=4}, {name=NAME, type=12}]
     * @param fSize      ??????????????????
     * @param row        ?????????????????????????????????{ID=123, NAME=??????11}
     */
    private void batchRowsSetter(Connection connection, PreparedStatement ps, List<Field> fields, int fSize, Map row) {
        Field f = null;
        int type;
        Object val = null;
        for (int i = 0; i < fSize; i++) {
            // ????????????????????????
            f = fields.get(i);
            type = f.getType();
            val = row.get(f.getName());
            SetterEnum.getSetter(type).set(connection, ps, i + 1, type, val);
        }
    }

    private boolean existRow(DatabaseConnectorMapper connectorMapper, String sql, Object value) {
        int rowNum = 0;
        try {
            rowNum = connectorMapper.execute(databaseTemplate -> databaseTemplate.queryForObject(sql, new Object[] {value}, Integer.class));
        } catch (Exception e) {
            logger.error("???????????????????????????:{}???SQL:{},??????:{}", e.getMessage(), sql, value);
        }
        return rowNum > 0;
    }

}