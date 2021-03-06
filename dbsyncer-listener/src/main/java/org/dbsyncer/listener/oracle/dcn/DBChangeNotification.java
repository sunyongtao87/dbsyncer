/**
 * DBSyncer Copyright 2019-2024 All Rights Reserved.
 */
package org.dbsyncer.listener.oracle.dcn;

import oracle.jdbc.OracleDriver;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.*;
import oracle.jdbc.driver.OracleConnection;
import org.dbsyncer.common.event.RowChangedEvent;
import org.dbsyncer.connector.constant.ConnectorConstant;
import org.dbsyncer.listener.oracle.event.DCNEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 授予登录账号监听事件权限
 * <p>sqlplus/as sysdba
 * <p>
 * <p>grant change notification to AE86
 *
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-06-08 21:53
 */
public class DBChangeNotification {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String QUERY_ROW_DATA_SQL  = "SELECT * FROM \"%s\" WHERE ROWID = '%s'";
    private static final String QUERY_TABLE_ALL_SQL = "SELECT OBJECT_ID, OBJECT_NAME FROM DBA_OBJECTS WHERE OWNER='%S' AND OBJECT_TYPE = 'TABLE'";
    private static final String QUERY_TABLE_SQL     = "SELECT 1 FROM \"%s\" WHERE 1=2";
    private static final String QUERY_CALLBACK_SQL  = "SELECT REGID,CALLBACK FROM USER_CHANGE_NOTIFICATION_REGS";
    private static final String CALLBACK            = "net8://(ADDRESS=(PROTOCOL=tcp)(HOST=%s)(PORT=%s))?PR=0";

    private String                     username;
    private String                     password;
    private String                     url;
    private OracleConnection           conn;
    private OracleStatement            statement;
    private DatabaseChangeRegistration dcr;
    private Map<Integer, String>       tables;
    private Worker                     worker;
    private Set<String>                filterTable;
    private List<RowEventListener>     listeners = new ArrayList<>();
    private BlockingQueue<DCNEvent>    queue = new LinkedBlockingQueue<> (100);

    public DBChangeNotification(String username, String password, String url) {
        this.username = username;
        this.password = password;
        this.url = url;
    }

    public void addRowEventListener(RowEventListener rowEventListener) {
        this.listeners.add(rowEventListener);
    }

    public void start() throws SQLException {
        try {
            conn = connect();
            statement = (OracleStatement) conn.createStatement();
            readTables();

            Properties prop = new Properties();
            prop.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
            prop.setProperty(OracleConnection.DCN_IGNORE_UPDATEOP, "false");
            prop.setProperty(OracleConnection.DCN_IGNORE_INSERTOP, "false");
            prop.setProperty(OracleConnection.DCN_IGNORE_INSERTOP, "false");

            // add the listener:NTFDCNRegistration
            dcr = conn.registerDatabaseChangeNotification(prop);
            dcr.addListener(new DCNListener());

            final long regId = dcr.getRegId();
            final String host = getHost();
            final int port = getPort(dcr);
            final String callback = String.format(CALLBACK, host, port);
            logger.info("regId:{}, callback:{}", regId, callback);
            // clean the registrations
            clean(statement, regId, callback);
            statement.setDatabaseChangeRegistration(dcr);

            // 启动消费线程
            worker = new Worker();
            worker.setName(new StringBuilder("dcn-parser-").append(host).append(":").append(port).append("_").append(regId).toString());
            worker.setDaemon(false);
            worker.start();

            // 配置监听表
            for (Map.Entry<Integer, String> m : tables.entrySet()) {
                String sql = String.format(QUERY_TABLE_SQL, m.getValue());
                try {
                    statement.executeQuery(sql);
                } catch (SQLException e) {
                    logger.debug("配置监听表异常:{}, {}", sql, e.getMessage());
                }
            }
        } catch (SQLException ex) {
            // if an exception occurs, we need to close the registration in order
            // to interrupt the thread otherwise it will be hanging around.
            close();
            throw ex;
        }
    }

    public void close() {
        if(null != worker && !worker.isInterrupted()){
            worker.interrupt();
            worker = null;
        }
        try {
            if (null != conn) {
                conn.unregisterDatabaseChangeNotification(dcr);
            }
            close(statement);
            close(conn);
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
    }

    public void close(AutoCloseable rs) {
        if (null != rs) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    public void read(String tableName, String rowId, List<Object> data) {
        OracleStatement os = null;
        ResultSet rs = null;
        try {
            os = (OracleStatement) conn.createStatement();
            rs = os.executeQuery(String.format(QUERY_ROW_DATA_SQL, tableName, rowId));
            if (rs.next()) {
                final int size = rs.getMetaData().getColumnCount();
                do {
                    data.add(rowId);
                    for (int i = 1; i <= size; i++) {
                        data.add(rs.getObject(i));
                    }
                } while (rs.next());
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        } finally {
            close(rs);
            close(os);
        }
    }

    private void readTables() {
        tables = new LinkedHashMap<>();
        ResultSet rs = null;
        try {
            String sql = String.format(QUERY_TABLE_ALL_SQL, username);
            rs = statement.executeQuery(sql);
            while (rs.next()) {
                tables.put(rs.getInt(1), rs.getString(2));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        } finally {
            close(rs);
        }
    }

    private String getHost() {
        if (url != null) {
            String host = url.substring(url.indexOf("@") + 1);
            host = host.substring(0, host.indexOf(":"));
            return host;
        }
        return "127.0.0.1";
    }

    private int getPort(DatabaseChangeRegistration dcr) {
        Object obj = null;
        try {
            // 反射获取抽象属性 NTFRegistration
            Class clazz = dcr.getClass().getSuperclass();
            Method method = clazz.getDeclaredMethod("getClientTCPPort");
            method.setAccessible(true);
            obj = method.invoke(dcr, new Object[] {});
        } catch (NoSuchMethodException e) {
            logger.error(e.getMessage());
        } catch (IllegalAccessException e) {
            logger.error(e.getMessage());
        } catch (InvocationTargetException e) {
            logger.error(e.getMessage());
        }
        return null == obj ? 0 : Integer.parseInt(String.valueOf(obj));
    }

    private void clean(OracleStatement statement, long excludeRegId, String excludeCallback) {
        ResultSet rs = null;
        try {
            rs = statement.executeQuery(QUERY_CALLBACK_SQL);
            while (rs.next()) {
                long regId = rs.getLong(1);
                String callback = rs.getString(2);

                if (regId != excludeRegId && callback.equals(excludeCallback)) {
                    logger.info("Clean regid:{}, callback:{}", regId, callback);
                    conn.unregisterDatabaseChangeNotification(regId, callback);
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        } finally {
            close(rs);
        }
    }

    private OracleConnection connect() throws SQLException {
        OracleDriver dr = new OracleDriver();
        Properties prop = new Properties();
        prop.setProperty(OracleConnection.CONNECTION_PROPERTY_USER_NAME, username);
        prop.setProperty(OracleConnection.CONNECTION_PROPERTY_PASSWORD, password);
        return (OracleConnection) dr.connect(url, prop);
    }

    public OracleConnection getOracleConnection() {
        return conn;
    }

    public void setFilterTable(Set<String> filterTable) {
        this.filterTable = filterTable;
    }

    final class DCNListener implements DatabaseChangeListener {

        @Override
        public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
            for (TableChangeDescription td : event.getTableChangeDescription()) {
                RowChangeDescription[] rds = td.getRowChangeDescription();
                for (RowChangeDescription rd : rds) {
                    String tableName = tables.get(td.getObjectNumber());
                    if(!filterTable.contains(tableName)){
                        logger.info("Table[{}] {}", tableName, rd.getRowOperation().name());
                        continue;
                    }
                    try {
                        // 如果BlockQueue没有空间,则调用此方法的线程被阻断直到BlockingQueue里面有空间再继续
                        queue.put(new DCNEvent(tableName, rd.getRowid().stringValue(),
                                rd.getRowOperation().getCode()));
                    } catch (InterruptedException ex) {
                        logger.error("Table[{}], RowId:{}, Code:{}, Error:{}", tableName,
                                rd.getRowid().stringValue(), rd.getRowOperation().getCode(), ex.getMessage());
                    }
                }
            }
        }

    }

    final class Worker extends Thread {

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    // 取走BlockingQueue里排在首位的对象,若BlockingQueue为空,阻断进入等待状态直到Blocking有新的对象被加入为止
                    DCNEvent event = queue.take();
                    if(null != event){
                        parseEvent(event);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        private void parseEvent(DCNEvent event) {
            List<Object> data = new ArrayList<>();
            if (event.getCode() == TableChangeDescription.TableOperation.UPDATE.getCode()) {
                read(event.getTableName(), event.getRowId(), data);
                listeners.forEach(listener -> listener.onEvents(new RowChangedEvent(event.getTableName(), ConnectorConstant.OPERTION_UPDATE, Collections.EMPTY_LIST, data)));

            } else if (event.getCode() == TableChangeDescription.TableOperation.INSERT.getCode()) {
                read(event.getTableName(), event.getRowId(), data);
                listeners.forEach(listener -> listener.onEvents(new RowChangedEvent(event.getTableName(), ConnectorConstant.OPERTION_INSERT, Collections.EMPTY_LIST, data)));

            } else {
                data.add(event.getRowId());
                listeners.forEach(listener -> listener.onEvents(new RowChangedEvent(event.getTableName(), ConnectorConstant.OPERTION_DELETE, data, Collections.EMPTY_LIST)));
            }
        }
    }
}