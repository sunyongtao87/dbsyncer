package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.checker.Checker;
import org.dbsyncer.biz.vo.ConnectorVo;
import org.dbsyncer.biz.vo.MappingVo;
import org.dbsyncer.biz.vo.MetaVo;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.manager.Manager;
import org.dbsyncer.monitor.Monitor;
import org.dbsyncer.parser.enums.MetaEnum;
import org.dbsyncer.parser.enums.ModelEnum;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.storage.constant.ConfigConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/17 23:20
 */
@Service
public class MappingServiceImpl implements MappingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private Manager manager;

    @Autowired
    private Monitor monitor;

    @Autowired
    private Checker mappingChecker;

    @Autowired
    private Checker metaChecker;

    // 驱动启停锁
    private final static Object LOCK = new Object();

    @Override
    public String add(Map<String, String> params) {
        ConfigModel model = mappingChecker.checkAddConfigModel(params);
        return manager.addMapping(model);
    }

    @Override
    public String edit(Map<String, String> params) {
        String mappingId = params.get(ConfigConstant.CONFIG_MODEL_ID);
        synchronized (LOCK){
            checkRunning(mappingId);

            ConfigModel model = mappingChecker.checkEditConfigModel(params);
            return manager.editMapping(model);
        }
    }

    @Override
    public String remove(String id) {
        Assert.hasText(id, "驱动ID不能为空");

        synchronized (LOCK){
            checkRunning(id);

            // 删除meta
            Mapping mapping = manager.getMapping(id);
            String metaId = mapping.getMetaId();
            if(!StringUtils.isEmpty(metaId)){
                manager.removeMeta(metaId);
            }

            // 删除tableGroup
            List<TableGroup> groupList = manager.getTableGroupAll(id);
            if (!CollectionUtils.isEmpty(groupList)) {
                groupList.forEach(t -> manager.removeTableGroup(t.getId()));
            }
            manager.removeMapping(id);
        }
        return "驱动删除成功";
    }

    @Override
    public MappingVo getMapping(String id) {
        Mapping mapping = manager.getMapping(id);
        return convertMapping2Vo(mapping);
    }

    @Override
    public List<MappingVo> getMappingAll() {
        List<MappingVo> list = manager.getMappingAll()
                .stream()
                .map(m -> convertMapping2Vo(m))
                .sorted(Comparator.comparing(MappingVo::getUpdateTime).reversed())
                .collect(Collectors.toList());
        return list;
    }

    @Override
    public String start(String id) {
        Assert.hasText(id, "驱动ID不能为空");
        Map<String, String> params = new HashMap<>();
        params.put(ConfigConstant.CONFIG_MODEL_ID, id);

        synchronized (LOCK){
            checkRunning(id);

            ConfigModel model = metaChecker.checkAddConfigModel(params);
            manager.addMeta(model);
        }
        return "驱动启动成功";
    }

    @Override
    public String stop(String id) {
        Assert.hasText(id, "驱动ID不能为空");
        Mapping mapping = manager.getMapping(id);
        Assert.notNull(mapping, "驱动不存在.");

        synchronized (LOCK){
            String metaId = mapping.getMetaId();
            if (null == manager.getMeta(metaId)) {
                throw new BizException("驱动已停止.");
            }
            manager.removeMeta(metaId);
        }
        return "驱动停止成功";
    }

    @Override
    public List<MetaVo> getMetaAll() {
        List<MetaVo> list = manager.getMetaAll()
                .stream()
                .map(m -> convertMeta2Vo(m))
                .sorted(Comparator.comparing(MetaVo::getUpdateTime).reversed())
                .collect(Collectors.toList());
        return list;
    }

    private MappingVo convertMapping2Vo(Mapping mapping) {
        Assert.notNull(mapping, "Mapping can not be null.");
        Connector s = manager.getConnector(mapping.getSourceConnectorId());
        Connector t = manager.getConnector(mapping.getTargetConnectorId());
        ConnectorVo sConn = new ConnectorVo(monitor.alive(s.getId()));
        BeanUtils.copyProperties(s, sConn);
        ConnectorVo tConn = new ConnectorVo(monitor.alive(t.getId()));
        BeanUtils.copyProperties(t, tConn);

        boolean isRunning = null != manager.getMeta(mapping.getMetaId());
        MappingVo vo = new MappingVo(isRunning, sConn, tConn);
        BeanUtils.copyProperties(mapping, vo);
        return vo;
    }

    private MetaVo convertMeta2Vo(Meta meta) {
        Mapping mapping = manager.getMapping(meta.getMappingId());
        Assert.notNull(mapping, "驱动不存在.");
        ModelEnum modelEnum = ModelEnum.getModelEnum(mapping.getModel());
        MetaEnum metaEnum = MetaEnum.getMetaEnum(meta.getState());
        MetaVo metaVo = new MetaVo(mapping.getName(), modelEnum.getMessage(), metaEnum.getMessage());
        BeanUtils.copyProperties(meta, metaVo);
        return metaVo;
    }

    /**
     * 检查是否运行中
     *
     * @param mappingId
     */
    private void checkRunning(String mappingId) {
        Mapping mapping = manager.getMapping(mappingId);
        Assert.notNull(mapping, "驱动不存在.");

        Meta meta = manager.getMeta(mapping.getMetaId());
        Assert.isNull(meta, "驱动正在运行, 请先停止.");
    }
}