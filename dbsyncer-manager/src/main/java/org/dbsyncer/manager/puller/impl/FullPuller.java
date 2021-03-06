package org.dbsyncer.manager.puller.impl;

import org.dbsyncer.common.event.FullRefreshEvent;
import org.dbsyncer.common.model.Task;
import org.dbsyncer.manager.Manager;
import org.dbsyncer.manager.puller.AbstractPuller;
import org.dbsyncer.parser.Parser;
import org.dbsyncer.parser.logger.LogService;
import org.dbsyncer.parser.logger.LogType;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全量同步
 *
 * @author AE86
 * @version 1.0.0
 * @date 2020/04/26 15:28
 */
@Component
public class FullPuller extends AbstractPuller implements ApplicationListener<FullRefreshEvent> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private Parser parser;

    @Autowired
    private Manager manager;

    @Autowired
    private LogService logService;

    private Map<String, Task> map = new ConcurrentHashMap<>();

    @Override
    public void asyncStart(Mapping mapping) {
        final String mappingId = mapping.getId();
        final String metaId = mapping.getMetaId();
        map.putIfAbsent(metaId, new Task(metaId));

        try {
            List<TableGroup> list = manager.getTableGroupAll(mappingId);
            Assert.notEmpty(list, "映射关系不能为空");

            // 执行任务
            logger.info("启动任务:{}", metaId);
            Task task = map.get(metaId);
            doTask(task, mapping, list);

        } catch (Exception e) {
            logger.error(e.getMessage());
            logService.log(LogType.SystemLog.ERROR, e.getMessage());
        } finally {
            map.remove(metaId);
            publishClosedEvent(metaId);
            logger.info("结束任务:{}", metaId);
        }
    }

    @Override
    public void close(String metaId) {
        Task task = map.get(metaId);
        if (null != task) {
            task.stop();
        }
    }

    @Override
    public void onApplicationEvent(FullRefreshEvent event) {
        // 异步监听任务刷新事件
        flush(event.getTask());
    }

    private void doTask(Task task, Mapping mapping, List<TableGroup> list) {
        // 记录开始时间
        long now = Instant.now().toEpochMilli();
        task.setBeginTime(now);
        task.setEndTime(now);
        flush(task);

        for (TableGroup t : list) {
            if (!task.isRunning()) {
                break;
            }
            parser.execute(task, mapping, t);
        }

        // 记录结束时间
        task.setEndTime(Instant.now().toEpochMilli());
        flush(task);
    }

    private void flush(Task task) {
        Meta meta = manager.getMeta(task.getId());
        Assert.notNull(meta, "检查meta为空.");

        meta.setBeginTime(task.getBeginTime());
        meta.setEndTime(task.getEndTime());
        manager.editMeta(meta);
    }

}