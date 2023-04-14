package com.liferay.sales.engineering.task.connector;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peterrichards
 */
public final class SalesforceTaskConnectorConstants {
    public static final String AUTH_HEADER_KEY = "Authorization";
    public static final String AUTH_HEADER_VALUE_PREFIX = "Bearer ";
    public static final int DEFAULT_INTEGER_PROPERTY_RADIX = 10;
    public static final String DEFAULT_LANGUAGE_ID = "en_US";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_START_PAGE = "1";
    public static final Map<String, String> FIELD_MAPPING = new HashMap<>() {
        {
            put(SALESFORCE_FIELDS.TASK_DESCRIPTION.fieldName, OBJECT_FIELDS.TASK_DESCRIPTION.fieldName);
            put(SALESFORCE_FIELDS.TASK_DUE_DATE.fieldName, OBJECT_FIELDS.TASK_DUE_DATE.fieldName);
            put(SALESFORCE_FIELDS.TASK_STATUS.fieldName, OBJECT_FIELDS.TASK_STATUS.fieldName);
        }
    };
    public static final String LANGUAGE_ID_PROPERTY_KEY = "language-id";
    public static final String PAGE_SIZE_PROPERTY_KEY = "page-size";
    public static final String SF_ACCESS_TOKEN = "access_token";
    public static final String SF_API_VERSION = "v57.0";
    public static final String SF_AUTH_PATH = "/services/oauth2/token";
    public static final String SF_CLIENT_ID_KEY = "client_id";
    public static final String SF_CLIENT_SECRET_KEY = "client_secret";
    public static final String SF_DATA_PATH = "/services/data";
    public static final String SF_GRANT_TYPE_KEY = "grant_type";
    public static final String SF_GRANT_TYPE_VALUE = "password";
    public static final String SF_INSTANCE_URL = "instance_url";
    public static final String SF_OBJECT_NAME = "LiferayTask__c";
    public static final String SF_PASSWORD_KEY = "password";
    public static final String SF_QUERY_KEY = "q";
    public static final String SF_QUERY_PATH = SF_DATA_PATH + "/" + SF_API_VERSION + "/query";
    public static final String SF_USERNAME_KEY = "username";
    public static final String START_PAGE_PROPERTY_KEY = "start-page";

    public enum OBJECT_FIELDS {
        TASK_DESCRIPTION("description"),
        TASK_DUE_DATE("dueDate"),
        TASK_STATUS("taskStatus");

        public final String fieldName;

        private OBJECT_FIELDS(String fieldName) {
            this.fieldName = fieldName;
        }
    }

    ;

    public enum SALESFORCE_FIELDS {
        TASK_DESCRIPTION("Name"),
        TASK_DUE_DATE("Due_Date__c"),
        TASK_STATUS("Task_Status__c"),
        ID("Id"),
        USER("OwnerId"),
        CREATED("CreatedDate"),
        LAST_MODIFIED("LastModifiedDate"),
        TOTAL_SIZE("totalSize"),
        RECORDS("records"),
        FIRST_EXPRESSION("expr0");

        public final String fieldName;

        private SALESFORCE_FIELDS(String fieldName) {
            this.fieldName = fieldName;
        }
    }
}
