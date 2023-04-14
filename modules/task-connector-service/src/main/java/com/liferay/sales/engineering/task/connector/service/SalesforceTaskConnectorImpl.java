package com.liferay.sales.engineering.task.connector.service;

import com.liferay.headless.delivery.dto.v1_0.util.CreatorUtil;
import com.liferay.list.type.model.ListTypeEntry;
import com.liferay.list.type.service.ListTypeEntryLocalService;
import com.liferay.object.constants.ObjectFieldConstants;
import com.liferay.object.exception.NoSuchObjectEntryException;
import com.liferay.object.field.business.type.ObjectFieldBusinessType;
import com.liferay.object.field.business.type.ObjectFieldBusinessTypeRegistry;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectField;
import com.liferay.object.rest.dto.v1_0.ListEntry;
import com.liferay.object.rest.dto.v1_0.ObjectEntry;
import com.liferay.object.rest.dto.v1_0.Status;
import com.liferay.object.scope.ObjectScopeProvider;
import com.liferay.object.scope.ObjectScopeProviderRegistry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryLocalServiceUtil;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.reflect.ReflectionUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.*;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.module.configuration.ConfigurationProviderUtil;
import com.liferay.portal.kernel.sanitizer.Sanitizer;
import com.liferay.portal.kernel.sanitizer.SanitizerUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.vulcan.pagination.Pagination;
import com.liferay.sales.engineering.task.connector.api.TaskConnector;
import com.liferay.sales.engineering.task.connector.configuration.TaskConnectorConfiguration;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import com.liferay.portal.kernel.language.Language;

import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.liferay.sales.engineering.task.connector.SalesforceTaskConnectorConstants.*;

/**
 * @author peterrichards
 */
@Component(
        configurationPid = TaskConnectorConfiguration.PID,
        service = TaskConnector.class
)
public class SalesforceTaskConnectorImpl implements TaskConnector {
    private static final Log _log = LogFactoryUtil.getLog(
            SalesforceTaskConnectorImpl.class);
    @Reference
    protected ObjectScopeProviderRegistry _objectScopeProviderRegistry;
    @Reference
    private JSONFactory _jsonFactory;
    @Reference
    private Language _language;
    @Reference
    private ListTypeEntryLocalService _listTypeEntryLocalService;
    @Reference
    private ObjectDefinitionLocalService _objectDefinitionLocalService;
    @Reference
    private ObjectEntryLocalService _objectEntryLocalService;
    @Reference
    private ObjectFieldBusinessTypeRegistry _objectFieldBusinessTypeRegistry;
    @Reference
    private ObjectFieldLocalService _objectFieldLocalService;
    @Reference
    private Portal _portal;
    @Reference
    private UserLocalService _userLocalService;

    private JSONObject _authenticate(final CloseableHttpClient httpClient, final TaskConnectorConfiguration configuration) throws IOException {
        final String authEndpoint = configuration.loginURL() +
                SF_AUTH_PATH;

        final HttpPost request = new HttpPost(authEndpoint);

        final List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair(SF_CLIENT_ID_KEY, configuration.consumerKey()));
        urlParameters.add(new BasicNameValuePair(SF_CLIENT_SECRET_KEY, configuration.consumerSecret()));
        urlParameters.add(new BasicNameValuePair(SF_GRANT_TYPE_KEY, SF_GRANT_TYPE_VALUE));
        urlParameters.add(new BasicNameValuePair(SF_PASSWORD_KEY, configuration.password()));
        urlParameters.add(new BasicNameValuePair(SF_USERNAME_KEY, configuration.username()));

        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        try (final CloseableHttpResponse response = httpClient.execute(request)) {
            final String responseJSON = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                if (_log.isDebugEnabled()) {
                    _log.debug(
                            StringBundler.concat(
                                    "Response code ", response.getStatusLine().getStatusCode(), ": ",
                                    responseJSON));
                }
                return null;
            }

            return _jsonFactory.createJSONObject(responseJSON);
        } catch (JSONException exception) {
            if (_log.isDebugEnabled()) {
                _log.debug(exception);
            }
            return null;
        }
    }

    private TaskConnectorConfiguration _getConfiguration(
            long companyId) {
        try {
            return ConfigurationProviderUtil.getCompanyConfiguration(
                    TaskConnectorConfiguration.class, companyId);
        } catch (ConfigurationException configurationException) {
            return ReflectionUtil.throwException(configurationException);
        }
    }

    private DateFormat _getDateTimeFormat() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    }

    private long _getGroupId(ObjectDefinition objectDefinition) throws Exception {
        ObjectScopeProvider objectScopeProvider =
                _objectScopeProviderRegistry.getObjectScopeProvider(
                        objectDefinition.getScope());

        if (objectScopeProvider.isGroupAware()) {
            if (Objects.equals(objectDefinition.getScope(), "site")) {
                final String errorMessage = "The Liferay object cannot be site scoped";
                if (_log.isDebugEnabled()) {
                    _log.debug(errorMessage);
                }
                throw new Exception(errorMessage);
            }
        }
        return 0;
    }

    private ObjectDefinition _getObjectDefinition(long companyId, String externalReferenceCode) {
        return _objectDefinitionLocalService.fetchObjectDefinitionByExternalReferenceCode(externalReferenceCode, companyId);
    }

    private ObjectField _getObjectFieldByName(
            String name, List<ObjectField> objectFields) {
        for (ObjectField objectField : objectFields) {
            if (Objects.equals(name, objectField.getName())) {
                return objectField;
            }
        }
        return null;
    }

    private int _getPageSize(final Map<String, String> properties) {
        int defaultPageSize = Integer.parseInt(DEFAULT_PAGE_SIZE);
        int pageSize = Integer.parseInt(properties.getOrDefault(PAGE_SIZE_PROPERTY_KEY, DEFAULT_PAGE_SIZE), DEFAULT_PROPERTY_RADIX);
        if (pageSize < 0) {
            return defaultPageSize;
        }
        return pageSize;
    }

    private String _getSalesforcePagination(Pagination pagination) {
        return StringBundler.concat(
                " LIMIT ", pagination.getPageSize(), " OFFSET ",
                pagination.getStartPosition());
    }

    private ServiceContext _getServiceContext() {
        return new ServiceContext();
    }

    private int _getStartPage(final Map<String, String> properties) {
        int defaultStartPage = Integer.parseInt(DEFAULT_START_PAGE);
        int startPage = Integer.parseInt(properties.getOrDefault(START_PAGE_PROPERTY_KEY, DEFAULT_START_PAGE), DEFAULT_PROPERTY_RADIX);
        return Math.max(startPage, defaultStartPage);
    }

    private Page<ObjectEntry> _getTasks(final CloseableHttpClient httpClient, final String instanceUrl, final String accessToken, final long companyId, final Pagination pagination, final String languageId, final ObjectDefinition objectDefinition, final Status workflowStatus) throws Exception {
        final String tasksEndpoint = instanceUrl + SF_QUERY_PATH;

        final HttpGet request = new HttpGet(tasksEndpoint);

        request.setHeader(AUTH_HEADER_KEY, AUTH_HEADER_VALUE_PREFIX + accessToken);

        List<NameValuePair> nameValuePairs = new ArrayList<>();

        final String query = StringBundler.concat("SELECT FIELDS(ALL) FROM ",
                SF_OBJECT_NAME, _getSalesforcePagination(pagination));

        if (_log.isTraceEnabled()) {
            _log.trace(StringBundler.concat("_getTasks query : ", query));
        }

        nameValuePairs.add(new BasicNameValuePair(SF_QUERY_KEY, query));

        URI uri = new URIBuilder(request.getURI())
                .addParameters(nameValuePairs)
                .build();
        request.setURI(uri);

        try (final CloseableHttpResponse response = httpClient.execute(request)) {
            final String responseJSON = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                if (_log.isDebugEnabled()) {
                    _log.debug(
                            StringBundler.concat(
                                    "Response code ", response.getStatusLine().getStatusCode(), ": ",
                                    responseJSON));
                }
                return null;
            }

            final JSONObject queryResponse = _jsonFactory.createJSONObject(responseJSON);
            if (_log.isDebugEnabled()) {
                final long recordCount = queryResponse.getLong(SALESFORCE_FIELDS.TOTAL_SIZE.fieldName);
                _log.debug(
                        recordCount == 1 ?
                                StringBundler.concat(
                                        recordCount, " record has been returned") :
                                StringBundler.concat(
                                        recordCount, " records have been returned")
                );
            }

            final JSONArray tasks = queryResponse.getJSONArray(SALESFORCE_FIELDS.RECORDS.fieldName);

            return Page.of(
                    _toObjectEntries(companyId, languageId, objectDefinition, workflowStatus, tasks),
                    pagination,
                    _getTotalCount(httpClient, instanceUrl, accessToken));
        }
    }

    private int _getTotalCount(final CloseableHttpClient httpClient, final String instanceUrl, final String accessToken) throws Exception {
        final String tasksEndpoint = instanceUrl + SF_QUERY_PATH;
        final HttpGet request = new HttpGet(tasksEndpoint);

        request.setHeader(AUTH_HEADER_KEY, AUTH_HEADER_VALUE_PREFIX + accessToken);

        List<NameValuePair> nameValuePairs = new ArrayList<>();

        final String query = StringBundler.concat("SELECT COUNT(Id) FROM ",
                SF_OBJECT_NAME);

        if (_log.isTraceEnabled()) {
            _log.trace(StringBundler.concat("_getTotalCount query : ", query));
        }

        nameValuePairs.add(new BasicNameValuePair(SF_QUERY_KEY, query));

        URI uri = new URIBuilder(request.getURI())
                .addParameters(nameValuePairs)
                .build();
        request.setURI(uri);

        try (final CloseableHttpResponse response = httpClient.execute(request)) {
            final String responseJSON = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                if (_log.isDebugEnabled()) {
                    _log.debug(
                            StringBundler.concat(
                                    "Response code ", response.getStatusLine().getStatusCode(), ": ",
                                    responseJSON));
                }

                return -1;
            }

            final JSONObject queryResponse = _jsonFactory.createJSONObject(responseJSON);

            JSONArray jsonArray = queryResponse.getJSONArray(SALESFORCE_FIELDS.RECORDS.fieldName);

            return jsonArray.getJSONObject(
                    0
            ).getInt(
                    SALESFORCE_FIELDS.FIRST_EXPRESSION.fieldName
            );
        }
    }

    private long _getUserId(long companyId, Map<String, String> properties) throws Exception {
        String value = properties.get(USER_ID_PROPERTY_KEY);
        if (value == null) {
            try {
                User user = UserLocalServiceUtil.getDefaultUser(companyId);
                if (user == null) {
                    final String errorMessage = StringBundler.concat("Default user not found for ", companyId);
                    if (_log.isDebugEnabled()) {
                        _log.debug(errorMessage);
                    }
                    throw new Exception(errorMessage);
                }
                return user.getUserId();
            } catch (PortalException exception) {
                final String errorMessage = "Unable to find default user";
                if (_log.isDebugEnabled()) {
                    _log.debug(errorMessage);
                }
                throw new Exception(errorMessage, exception);
            }
        }
        return Long.parseLong(value, DEFAULT_PROPERTY_RADIX);
    }

    private Date _toDate(Locale locale, String valueString) {
        if (Validator.isNull(valueString)) {
            return null;
        }

        try {
            return DateUtil.parseDate(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", valueString, locale);
        } catch (ParseException parseException1) {
            if (_log.isTraceEnabled()) {
                _log.trace(parseException1);
            }

            try {
                return DateUtil.parseDate("yyyy-MM-dd", valueString, locale);
            } catch (ParseException parseException2) {
                final String errorMessage = "Unable to parse date that does not conform to ISO-8601";
                if (_log.isDebugEnabled()) {
                    _log.debug(errorMessage);
                }
                throw new BadRequestException(
                        errorMessage,
                        parseException2);
            }
        }
    }

    private Collection<ObjectEntry> _toObjectEntries(
            long companyId, String languageId, ObjectDefinition objectDefinition,
            Status workflowStatus, JSONArray jsonArray) throws Exception {

        return JSONUtil.toList(
                jsonArray,
                jsonObject -> _toObjectEntry(
                        companyId, languageId, objectDefinition, workflowStatus, jsonObject));
    }

    private ObjectEntry _toObjectEntry(long companyId, String languageId, ObjectDefinition objectDefinition, Status workflowStatus, JSONObject jsonObject) throws ParseException {
        DateFormat dateTimeFormat = _getDateTimeFormat();

        List<ObjectField> objectFields =
                _objectFieldLocalService.getObjectFields(
                        objectDefinition.getObjectDefinitionId());

        ObjectEntry objectEntry = new ObjectEntry() {
            {
                actions = HashMapBuilder.put(
                        "delete", Collections.<String, String>emptyMap()
                ).build();
                id = 0L;
                creator = CreatorUtil.toCreator(
                        _portal, Optional.empty(),
                        _userLocalService.fetchUserByExternalReferenceCode(
                                jsonObject.getString(SALESFORCE_FIELDS.USER.fieldName), companyId));
                dateCreated = dateTimeFormat.parse(
                        jsonObject.getString(SALESFORCE_FIELDS.CREATED.fieldName));
                dateModified = dateTimeFormat.parse(
                        jsonObject.getString(SALESFORCE_FIELDS.LAST_MODIFIED.fieldName));
                externalReferenceCode = jsonObject.getString(SALESFORCE_FIELDS.ID.fieldName);
                status = workflowStatus;
            }
        };

        Iterator<String> iterator = jsonObject.keys();

        while (iterator.hasNext()) {
            final String key = iterator.next();
            final String fieldName = FIELD_MAPPING.get(key);
            final ObjectField objectField = _getObjectFieldByName(fieldName, objectFields);

            if (objectField == null) {
                continue;
            }

            Object value;
            final Map<String, Object> properties = objectEntry.getProperties();

            if (SALESFORCE_FIELDS.TASK_DUE_DATE.fieldName.equals(key) ||
                    SALESFORCE_FIELDS.TASK_DESCRIPTION.fieldName.equals(key)) {
                value = jsonObject.get(key);
            } else if (SALESFORCE_FIELDS.TASK_STATUS.fieldName.equals(key)) {
                String valueString = jsonObject.getString(key);
                ListTypeEntry listTypeEntry =
                        _listTypeEntryLocalService.
                                fetchListTypeEntryByExternalReferenceCode(
                                        valueString, companyId,
                                        objectField.getListTypeDefinitionId());

                if (listTypeEntry == null) {
                    continue;
                }

                value = new ListEntry() {
                    {
                        key = listTypeEntry.getKey();
                        name = listTypeEntry.getName(
                                languageId);
                    }
                };
            } else {
                if (_log.isWarnEnabled()) {
                    _log.warn(
                            StringBundler.concat(
                                    "Unknown field : ", key));
                }
                continue;
            }
            properties.put(objectField.getName(), value);
        }
        return objectEntry;
    }

    private Map<String, Serializable> _toObjectValues(
            long groupId, long userId, ObjectDefinition objectDefinition,
            ObjectEntry objectEntry, Locale locale)
            throws Exception {

        Map<String, Serializable> values = new HashMap<>();

        for (ObjectField objectField :
                _objectFieldLocalService.getObjectFields(
                        objectDefinition.getObjectDefinitionId())) {

            if (Objects.equals(
                    objectField.getName(), "status")) {
                values.put(
                        objectField.getName(),
                        objectEntry.getStatus());

                continue;
            }

            Object value = getValue(
                    objectField, userId,
                    objectEntry.getProperties());

            if (Objects.equals(
                    objectField.getName(), "externalReferenceCode") &&
                    Validator.isNull(value) &&
                    Validator.isNotNull(objectEntry.getExternalReferenceCode())) {

                values.put(
                        objectField.getName(),
                        objectEntry.getExternalReferenceCode());

                continue;
            }

            if ((value == null) && (!objectField.isRequired())) {
                continue;
            }

            if (Objects.equals(
                    ObjectFieldConstants.BUSINESS_TYPE_RICH_TEXT,
                    objectField.getBusinessType())) {

                values.put(
                        objectField.getName(),
                        SanitizerUtil.sanitize(
                                objectField.getCompanyId(), groupId,
                                objectField.getUserId(),
                                objectDefinition.getClassName(), 0,
                                ContentTypes.TEXT_HTML, Sanitizer.MODE_ALL,
                                String.valueOf(value), null));
            } else if (Objects.equals(
                    objectField.getDBType(),
                    ObjectFieldConstants.DB_TYPE_DATE)) {

                values.put(
                        objectField.getName(),
                        _toDate(locale, String.valueOf(value)));
            } else if (objectField.getListTypeDefinitionId() != 0) {
                ListEntry listEntry = (ListEntry) value;
                if (listEntry == null) {
                    final String errorMessage = "The list entry is null";
                    if (_log.isDebugEnabled()) {
                        _log.debug(errorMessage);
                    }
                    throw new Exception(errorMessage);
                }
                if (listEntry.getKey() == null) {
                    final String errorMessage = StringBundler.concat("The list entry key for ", value, " is null");
                    if (_log.isDebugEnabled()) {
                        _log.debug(errorMessage);
                    }
                    throw new Exception(errorMessage);
                }
                values.put(objectField.getName(), listEntry.getKey());
            } else {
                values.put(objectField.getName(), (Serializable) value);
            }
        }

        return values;
    }

    public Object getValue(
            ObjectField objectField,
            long userId, Map<String, Object> values)
            throws PortalException {

        try {
            ObjectFieldBusinessType objectFieldBusinessType =
                    _objectFieldBusinessTypeRegistry.getObjectFieldBusinessType(
                            objectField.getBusinessType());

            return objectFieldBusinessType.getValue(objectField, values);
        } catch (NoSuchObjectEntryException noSuchObjectEntryException) {
            if (_log.isDebugEnabled()) {
                _log.debug(noSuchObjectEntryException);
            }

            com.liferay.object.model.ObjectEntry objectEntry = _objectEntryLocalService.addObjectEntry(
                    noSuchObjectEntryException.getExternalReferenceCode(), userId,
                    _objectDefinitionLocalService.getObjectDefinition(
                            noSuchObjectEntryException.getObjectDefinitionId()));

            return objectEntry.getObjectEntryId();
        }
    }

    private Status getWorkflowStatus(final Map<String, String> properties, Locale locale) {
        final String label = properties.getOrDefault(WORKFLOW_STATUS_PROPERTY_KEY, DEFAULT_WORKFLOW_STATUS);
        final int statusCode = WorkflowConstants.getLabelStatus(label);
        return new Status() {
            {
                code = statusCode;
                label = WorkflowConstants.getStatusLabel(
                        statusCode);
                label_i18n = _language.get(locale, WorkflowConstants.getStatusLabel(
                        statusCode));
            }
        };
    }

    @Override
    public void synchronise(final long companyId, final Map<String, String> properties) {
        try {
            final TaskConnectorConfiguration configuration = _getConfiguration(companyId);

            final String objectExternalReferenceCode = properties.get(OBJECT_EXTERNAL_REFERENCE_CODE_PROPERTY_KEY);

            if (objectExternalReferenceCode == null) {
                final String errorMessage = StringBundler.concat("No ", OBJECT_EXTERNAL_REFERENCE_CODE_PROPERTY_KEY, " was provided in the job scheduler properties");
                if (_log.isDebugEnabled()) {
                    _log.debug(errorMessage);
                }
                throw new Exception(errorMessage);
            }

            final ObjectDefinition objectDefinition = _getObjectDefinition(companyId, objectExternalReferenceCode);

            if (objectDefinition == null) {
                final String errorMessage = StringBundler.concat("No object definition was found for ", OBJECT_EXTERNAL_REFERENCE_CODE_PROPERTY_KEY);
                if (_log.isDebugEnabled()) {
                    _log.debug(errorMessage);
                }
                throw new Exception(errorMessage);
            }

            final String languageId = properties.getOrDefault(LANGUAGE_ID_PROPERTY_KEY, objectDefinition.getDefaultLanguageId());
            final Locale locale = LocaleUtil.fromLanguageId(languageId);
            final Status workflowStatus = getWorkflowStatus(properties, locale);
            final int startPage = _getStartPage(properties);
            final int pageSize = _getPageSize(properties);
            final long userId = _getUserId(companyId, properties);
            final long groupId = _getGroupId(objectDefinition);

            try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
                final JSONObject authResponse = _authenticate(httpClient, configuration);
                if (authResponse == null) {
                    final String errorMessage = "Unable to authenticate to obtain access token. Check configuration";
                    if (_log.isDebugEnabled()) {
                        _log.debug(errorMessage);
                    }
                    throw new RuntimeException(errorMessage);
                }

                final String instanceUrl = authResponse.getString(SF_INSTANCE_URL);
                final String accessToken = authResponse.getString(SF_ACCESS_TOKEN);

                final int totalCount = _getTotalCount(httpClient, instanceUrl, accessToken);
                if (_log.isDebugEnabled()) {
                    _log.debug(StringBundler.concat("Total count : ", totalCount));
                }

                final int lastPage = (int) Math.ceil((float) (totalCount / pageSize)) + startPage; // pages start at 1
                for (int currentPage = startPage; currentPage <= lastPage; currentPage++) {
                    final Pagination pagination = Pagination.of(currentPage, pageSize);
                    final Page<ObjectEntry> objectEntries = _getTasks(httpClient, instanceUrl, accessToken, companyId, pagination, languageId, objectDefinition, workflowStatus);

                    if (objectEntries == null)
                        continue;

                    for (ObjectEntry objectEntry : objectEntries.getItems()) {
                        if (_log.isDebugEnabled()) {
                            _log.debug(StringBundler.concat("Page : ", currentPage, " Record : ",
                                    objectEntry));
                        }

                        final Map<String, Serializable> objectValues = _toObjectValues(
                                groupId,
                                userId,
                                objectDefinition,
                                objectEntry,
                                locale);

                        com.liferay.object.model.ObjectEntry temp = ObjectEntryLocalServiceUtil.addOrUpdateObjectEntry(
                                objectEntry.getExternalReferenceCode(),
                                userId, groupId, objectDefinition.getObjectDefinitionId(),
                                objectValues,
                                _getServiceContext());

                        ObjectEntryLocalServiceUtil.updateStatus(
                                userId,
                                temp.getObjectEntryId(),
                                objectEntry.getStatus().getCode(),
                                _getServiceContext());
                    }
                }
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}