package org.dbsyncer.connector.sqlserver;

import org.dbsyncer.connector.ConnectorException;
import org.dbsyncer.connector.ConnectorMapper;
import org.dbsyncer.connector.HandleCallback;
import org.dbsyncer.connector.config.ConnectorConfig;
import org.dbsyncer.connector.database.DatabaseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class SqlServerConnectorMapper extends ConnectorMapper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Lock lock = new ReentrantLock(true);

    public SqlServerConnectorMapper(ConnectorConfig config, Connection connection) {
        super(config, connection);
    }

    /**
     * 使用连接时加锁(SqlServer 2008以下版本连接未释放问题)
     * TODO 判断版本，是否加锁
     *
     * @param callback
     * @return
     */
    @Override
    public <T> T execute(HandleCallback callback) {
        final Lock connectionLock = lock;
        boolean locked = false;
        Object apply = null;
        try {
            locked = connectionLock.tryLock();
            if (locked) {
                apply = callback.apply(new DatabaseTemplate(connection));
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ConnectorException(e.getMessage());
        } finally {
            if (locked) {
                connectionLock.unlock();
            }
        }
        return (T) apply;
    }
}
