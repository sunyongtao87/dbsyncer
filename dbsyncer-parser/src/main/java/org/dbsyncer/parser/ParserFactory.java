package org.dbsyncer.parser;

import org.dbsyncer.cache.CacheService;
import org.dbsyncer.common.event.FullRefreshEvent;
import org.dbsyncer.common.event.RowChangedEvent;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.model.Task;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.ConnectorFactory;
import org.dbsyncer.connector.ConnectorMapper;
import org.dbsyncer.connector.config.*;
import org.dbsyncer.connector.constant.ConnectorConstant;
import org.dbsyncer.connector.enums.ConnectorEnum;
import org.dbsyncer.connector.enums.FilterEnum;
import org.dbsyncer.connector.enums.OperationEnum;
import org.dbsyncer.listener.enums.QuartzFilterEnum;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.enums.ParserEnum;
import org.dbsyncer.parser.flush.FlushService;
import org.dbsyncer.parser.logger.LogType;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.parser.util.PickerUtil;
import org.dbsyncer.plugin.PluginFactory;
import org.dbsyncer.storage.enums.StorageDataStatusEnum;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/29 22:38
 */
@Component
public class ParserFactory implements Parser {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private PluginFactory pluginFactory;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private FlushService flushService;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public ConnectorMapper connect(ConnectorConfig config) {
        return connectorFactory.connect(config);
    }

    @Override
    public boolean refreshConnectorConfig(ConnectorConfig config) {
        return connectorFactory.refresh(config);
    }

    @Override
    public boolean isAliveConnectorConfig(ConnectorConfig config) {
        boolean alive = false;
        try {
            alive = connectorFactory.isAlive(config);
        } catch (Exception e) {
            LogType.ConnectorLog logType = LogType.ConnectorLog.FAILED;
            flushService.asyncWrite(logType.getType(), String.format("%s%s", logType.getName(), e.getMessage()));
        }
        // ????????????
        if(!alive){
            try {
                alive = connectorFactory.refresh(config);
            } catch (Exception e) {
                // nothing to do
            }
            if(alive){
                logger.info(LogType.ConnectorLog.RECONNECT_SUCCESS.getMessage());
            }
        }
        return alive;
    }

    @Override
    public List<Table> getTable(ConnectorMapper config) {
        return connectorFactory.getTable(config);
    }

    @Override
    public MetaInfo getMetaInfo(String connectorId, String tableName) {
        Connector connector = getConnector(connectorId);
        ConnectorMapper connectorMapper = connectorFactory.connect(connector.getConfig());
        MetaInfo metaInfo = connectorFactory.getMetaInfo(connectorMapper, tableName);
        if(!CollectionUtils.isEmpty(connector.getTable())){
            for(Table t :connector.getTable()){
                if(t.getName().equals(tableName)){
                    metaInfo.setTableType(t.getType());
                    break;
                }
            }
        }
        return metaInfo;
    }

    @Override
    public Map<String, String> getCommand(Mapping mapping, TableGroup tableGroup) {
        String sType = getConnectorConfig(mapping.getSourceConnectorId()).getConnectorType();
        String tType = getConnectorConfig(mapping.getTargetConnectorId()).getConnectorType();
        Table sourceTable = tableGroup.getSourceTable();
        Table targetTable = tableGroup.getTargetTable();
        Table sTable = new Table(sourceTable.getName(), sourceTable.getType(), new ArrayList<>());
        Table tTable = new Table(targetTable.getName(), targetTable.getType(), new ArrayList<>());
        List<FieldMapping> fieldMapping = tableGroup.getFieldMapping();
        if (!CollectionUtils.isEmpty(fieldMapping)) {
            fieldMapping.forEach(m -> {
                if (null != m.getSource()) {
                    sTable.getColumn().add(m.getSource());
                }
                if (null != m.getTarget()) {
                    tTable.getColumn().add(m.getTarget());
                }
            });
        }
        final CommandConfig sourceConfig = new CommandConfig(sType, sTable, sourceTable, tableGroup.getFilter());
        final CommandConfig targetConfig = new CommandConfig(tType, tTable, targetTable);
        // ???????????????????????????
        Map<String, String> command = connectorFactory.getCommand(sourceConfig, targetConfig);
        return command;
    }

    @Override
    public long getCount(String connectorId, Map<String, String> command) {
        ConnectorMapper connectorMapper = connectorFactory.connect(getConnectorConfig(connectorId));
        return connectorFactory.getCount(connectorMapper, command);
    }

    @Override
    public Connector parseConnector(String json) {
        try {
            JSONObject conn = new JSONObject(json);
            JSONObject config = (JSONObject) conn.remove("config");
            JSONArray table = (JSONArray) conn.remove("table");
            Connector connector = JsonUtil.jsonToObj(conn.toString(), Connector.class);
            Assert.notNull(connector, "Connector can not be null.");
            String connectorType = config.getString("connectorType");
            Class<?> configClass = ConnectorEnum.getConfigClass(connectorType);
            ConnectorConfig obj = (ConnectorConfig) JsonUtil.jsonToObj(config.toString(), configClass);
            connector.setConfig(obj);

            List<Table> tableList = new ArrayList<>();
            boolean exist = false;
            for (int i = 0; i < table.length(); i++) {
                if(table.get(i) instanceof String){
                    tableList.add(new Table(table.getString(i)));
                    exist = true;
                }
            }
            if(!exist){
                tableList = JsonUtil.jsonToArray(table.toString(), Table.class);
            }
            connector.setTable(tableList);

            return connector;
        } catch (JSONException e) {
            logger.error(e.getMessage());
            throw new ParserException(e.getMessage());
        }
    }

    @Override
    public <T> T parseObject(String json, Class<T> clazz) {
        try {
            JSONObject obj = new JSONObject(json);
            T t = JsonUtil.jsonToObj(obj.toString(), clazz);
            String format = String.format("%s can not be null.", clazz.getSimpleName());
            Assert.notNull(t, format);
            return t;
        } catch (JSONException e) {
            logger.error(e.getMessage());
            throw new ParserException(e.getMessage());
        }
    }

    @Override
    public List<ConnectorEnum> getConnectorEnumAll() {
        return Arrays.asList(ConnectorEnum.values());
    }

    @Override
    public List<OperationEnum> getOperationEnumAll() {
        return Arrays.asList(OperationEnum.values());
    }

    @Override
    public List<QuartzFilterEnum> getQuartzFilterEnumAll() {
        return Arrays.asList(QuartzFilterEnum.values());
    }

    @Override
    public List<FilterEnum> getFilterEnumAll() {
        return Arrays.asList(FilterEnum.values());
    }

    @Override
    public List<ConvertEnum> getConvertEnumAll() {
        return Arrays.asList(ConvertEnum.values());
    }

    @Override
    public List<StorageDataStatusEnum> getStorageDataStatusEnumAll() {
        return Arrays.asList(StorageDataStatusEnum.values());
    }

    @Override
    public void execute(Task task, Mapping mapping, TableGroup tableGroup) {
        final String metaId = task.getId();
        final String sourceConnectorId = mapping.getSourceConnectorId();
        final String targetConnectorId = mapping.getTargetConnectorId();

        ConnectorConfig sConfig = getConnectorConfig(sourceConnectorId);
        Assert.notNull(sConfig, "???????????????????????????.");
        ConnectorConfig tConfig = getConnectorConfig(targetConnectorId);
        Assert.notNull(tConfig, "???????????????????????????.");
        TableGroup group = PickerUtil.mergeTableGroupConfig(mapping, tableGroup);
        Map<String, String> command = group.getCommand();
        Assert.notEmpty(command, "????????????????????????.");
        List<FieldMapping> fieldMapping = group.getFieldMapping();
        String sTableName = group.getSourceTable().getName();
        String tTableName = group.getTargetTable().getName();
        Assert.notEmpty(fieldMapping, String.format("????????????[%s]?????????????????????[%s], ????????????????????????.", sTableName, tTableName));
        // ??????????????????
        Picker picker = new Picker(fieldMapping);

        // ??????????????????
        Map<String, String> params = getMeta(metaId).getMap();
        params.putIfAbsent(ParserEnum.PAGE_INDEX.getCode(), ParserEnum.PAGE_INDEX.getDefaultValue());
        int pageSize = mapping.getReadNum();
        int batchSize = mapping.getBatchNum();
        ConnectorMapper sConnectionMapper = connectorFactory.connect(sConfig);
        ConnectorMapper tConnectionMapper = connectorFactory.connect(tConfig);

        for (; ; ) {
            if (!task.isRunning()) {
                logger.warn("???????????????:{}", metaId);
                break;
            }

            // 1????????????????????????
            int pageIndex = Integer.parseInt(params.get(ParserEnum.PAGE_INDEX.getCode()));
            Result reader = connectorFactory.reader(sConnectionMapper, new ReaderConfig(command, new ArrayList<>(), pageIndex, pageSize));
            List<Map> data = reader.getData();
            if (CollectionUtils.isEmpty(data)) {
                params.clear();
                logger.info("????????????????????????:{}, [{}] >> [{}]", metaId, sTableName, tTableName);
                break;
            }

            // 2???????????????
            List<Map> target = picker.pickData(reader.getData());

            // 3???????????????
            ConvertUtil.convert(group.getConvert(), target);

            // 4???????????????
            pluginFactory.convert(group.getPlugin(), data, target);

            // 5??????????????????
            Result writer = writeBatch(tConnectionMapper, command, picker.getTargetFields(), target, batchSize);

            // 6???????????????
            flush(task, writer, target);

            // 7??????????????????
            params.put(ParserEnum.PAGE_INDEX.getCode(), String.valueOf(++pageIndex));
        }
    }

    @Override
    public void execute(Mapping mapping, TableGroup tableGroup, RowChangedEvent rowChangedEvent) {
        logger.info("Table[{}] {}, before:{}, after:{}", rowChangedEvent.getTableName(), rowChangedEvent.getEvent(),
                rowChangedEvent.getBefore(), rowChangedEvent.getAfter());
        final String metaId = mapping.getMetaId();

        ConnectorMapper tConnectorMapper = connectorFactory.connect(getConnectorConfig(mapping.getTargetConnectorId()));
        // 1?????????????????????
        final String event = rowChangedEvent.getEvent();
        Map<String, Object> data = StringUtil.equals(ConnectorConstant.OPERTION_DELETE, event) ? rowChangedEvent.getBefore() : rowChangedEvent.getAfter();
        Picker picker = new Picker(tableGroup.getFieldMapping(), data);
        Map target = picker.getTargetMap();

        // 2???????????????
        ConvertUtil.convert(tableGroup.getConvert(), target);

        // 3???????????????
        pluginFactory.convert(tableGroup.getPlugin(), event, data, target);

        // 4??????????????????
        Result writer = connectorFactory.writer(tConnectorMapper, new WriterSingleConfig(picker.getTargetFields(), tableGroup.getCommand(), event, target, rowChangedEvent.getTableName(), rowChangedEvent.isForceUpdate()));

        // 5???????????????
        flush(metaId, writer, event, picker.getTargetMapList());
    }

    /**
     * ????????????
     *
     * @param task
     * @param writer
     * @param data
     */
    private void flush(Task task, Result writer, List<Map> data) {
        flush(task.getId(), writer, ConnectorConstant.OPERTION_INSERT, data);

        // ?????????????????????FullExtractor
        task.setEndTime(Instant.now().toEpochMilli());
        applicationContext.publishEvent(new FullRefreshEvent(applicationContext, task));
    }

    private void flush(String metaId, Result writer, String event, List<Map> data) {
        // ????????????
        long total = data.size();
        long fail = writer.getFail().get();
        Meta meta = getMeta(metaId);
        meta.getFail().getAndAdd(fail);
        meta.getSuccess().getAndAdd(total - fail);

        // ??????????????????
        Queue<Map> failData = writer.getFailData();
        boolean success = CollectionUtils.isEmpty(failData);
        if (!success) {
            data.clear();
            data.addAll(failData);
        }
        String error = writer.getError().toString();
        flushService.asyncWrite(metaId, event, success, data, error);
    }

    /**
     * ??????Meta(???: ??????bean??????, ????????????????????????)
     *
     * @param metaId
     * @return
     */
    private Meta getMeta(String metaId) {
        Assert.hasText(metaId, "Meta id can not be empty.");
        Meta meta = cacheService.get(metaId, Meta.class);
        Assert.notNull(meta, "Meta can not be null.");
        return meta;
    }

    /**
     * ???????????????
     *
     * @param connectorId
     * @return
     */
    private Connector getConnector(String connectorId) {
        Assert.hasText(connectorId, "Connector id can not be empty.");
        Connector conn = cacheService.get(connectorId, Connector.class);
        Assert.notNull(conn, "Connector can not be null.");
        Connector connector = new Connector();
        BeanUtils.copyProperties(conn, connector);
        return connector;
    }

    /**
     * ??????????????????
     *
     * @param connectorId
     * @return
     */
    private ConnectorConfig getConnectorConfig(String connectorId) {
        Connector connector = getConnector(connectorId);
        return connector.getConfig();
    }

    /**
     * ????????????
     *
     * @param connectorMapper
     * @param command
     * @param fields
     * @param target
     * @param batchSize
     * @return
     */
    private Result writeBatch(ConnectorMapper connectorMapper, Map<String, String> command, List<Field> fields, List<Map> target, int batchSize) {
        // ??????
        int total = target.size();
        // ????????????
        if (total <= batchSize) {
            return connectorFactory.writer(connectorMapper, new WriterBatchConfig(command, fields, target));
        }

        // ????????????, ??????
        int taskSize = total % batchSize == 0 ? total / batchSize : total / batchSize + 1;

        // ????????????????????????????????????
        Queue<Map> queue = new ConcurrentLinkedQueue<>(target);

        final Result result = new Result();
        final CountDownLatch latch = new CountDownLatch(taskSize);
        for (int i = 0; i < taskSize; i++) {
            taskExecutor.execute(() -> {
                try {
                    Result w = parallelTask(batchSize, queue, connectorMapper, command, fields);
                    // CAS
                    result.getFailData().addAll(w.getFailData());
                    result.getFail().getAndAdd(w.getFail().get());
                    result.getError().append(w.getError());
                } catch (Exception e) {
                    result.getError().append(e.getMessage()).append(System.lineSeparator());
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
        return result;
    }

    private Result parallelTask(int batchSize, Queue<Map> queue, ConnectorMapper connectorMapper, Map<String, String> command,
                                List<Field> fields) {
        List<Map> data = new ArrayList<>();
        for (int j = 0; j < batchSize; j++) {
            Map poll = queue.poll();
            if (null == poll) {
                break;
            }
            data.add(poll);
        }
        return connectorFactory.writer(connectorMapper, new WriterBatchConfig(command, fields, data));
    }

}