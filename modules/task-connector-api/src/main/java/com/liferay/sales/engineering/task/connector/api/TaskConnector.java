package com.liferay.sales.engineering.task.connector.api;

/**
 * @author peterrichards
 */
public interface TaskConnector {
    void synchronise(long companyId);
}