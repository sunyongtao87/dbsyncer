package org.dbsyncer.manager.config;

import org.dbsyncer.manager.enums.GroupStrategyEnum;
import org.dbsyncer.manager.template.Handler;
import org.dbsyncer.parser.model.ConfigModel;

public class OperationConfig {

    private String id;

    private ConfigModel model;

    private GroupStrategyEnum groupStrategyEnum;

    private Handler handler;

    public OperationConfig(String id) {
        this.id = id;
    }

    public OperationConfig(String id, GroupStrategyEnum groupStrategyEnum) {
        this.id = id;
        this.groupStrategyEnum = groupStrategyEnum;
    }

    public OperationConfig(ConfigModel model, Handler handler) {
        this.model = model;
        this.handler = handler;
    }

    public OperationConfig(ConfigModel model, GroupStrategyEnum groupStrategyEnum, Handler handler) {
        this.model = model;
        this.groupStrategyEnum = groupStrategyEnum;
        this.handler = handler;
    }

    public String getId() {
        return id;
    }

    public ConfigModel getModel() {
        return model;
    }

    public GroupStrategyEnum getGroupStrategyEnum() {
        return groupStrategyEnum;
    }

    public Handler getHandler() {
        return handler;
    }
}