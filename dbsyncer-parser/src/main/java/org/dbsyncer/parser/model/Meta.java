package org.dbsyncer.parser.model;

import org.dbsyncer.parser.enums.MetaEnum;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>驱动同步元信息</p>
 * <pre>
 *     全量同步: 存放分页数
 *     增量同步:定时>时间戳; 日志>binlogFileName/binlogPosition/主从节点信息等
 * </pre>
 *
 * @author AE86
 * @version 1.0.0
 * @date 2020/04/21 16:19
 */
public class Meta extends ConfigModel {

    private String mappingId;
    /**
     * {@link MetaEnum}
     */
    private int state;
    private AtomicLong total;
    private AtomicLong success;
    private AtomicLong fail;
    private Map<String, String> map;
    private long beginTime;
    private long endTime;

    public Meta() {
        init();
    }

    /**
     * 还原状态
     */
    public void clear() {
        init();
    }

    private void init(){
        this.state = MetaEnum.READY.getCode();
        this.total = new AtomicLong(0);
        this.success = new AtomicLong(0);
        this.fail = new AtomicLong(0);
        this.map = new LinkedHashMap<>();
        this.beginTime = 0L;
        this.endTime = 0L;
    }

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public AtomicLong getTotal() {
        return total;
    }

    public void setTotal(AtomicLong total) {
        this.total = total;
    }

    public AtomicLong getSuccess() {
        return success;
    }

    public void setSuccess(AtomicLong success) {
        this.success = success;
    }

    public AtomicLong getFail() {
        return fail;
    }

    public void setFail(AtomicLong fail) {
        this.fail = fail;
    }

    public Map<String, String> getMap() {
        return map;
    }

    public void setMap(Map<String, String> map) {
        this.map = map;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
}