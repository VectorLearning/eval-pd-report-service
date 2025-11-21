package com.evplus.report.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routing DataSource implementation.
 *
 * Routes database operations to read or write datasource based on
 * the @Transactional(readOnly) flag.
 *
 * - readOnly=true → routes to READ datasource (replica)
 * - readOnly=false or no transaction → routes to WRITE datasource (primary)
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger logger = LoggerFactory.getLogger(RoutingDataSource.class);

    /**
     * Determine which datasource to use for current operation.
     *
     * @return DatabaseType.READ if transaction is read-only, DatabaseType.WRITE otherwise
     */
    @Override
    protected Object determineCurrentLookupKey() {
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

        DatabaseRoutingConfig.DatabaseType databaseType = isReadOnly
            ? DatabaseRoutingConfig.DatabaseType.READ
            : DatabaseRoutingConfig.DatabaseType.WRITE;

        logger.debug("Routing to {} database (readOnly={})", databaseType, isReadOnly);

        return databaseType;
    }
}
