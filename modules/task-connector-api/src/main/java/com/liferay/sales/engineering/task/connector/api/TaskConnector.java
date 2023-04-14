package com.liferay.sales.engineering.task.connector.api;

import java.util.Map;

/**
 * @author peterrichards
 */
public interface TaskConnector {
    void synchronise(long companyId, Map<String, String> properties);
}