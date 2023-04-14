package com.liferay.sales.engineering.task.connector.service;

import com.liferay.headless.delivery.dto.v1_0.util.CreatorUtil;
import com.liferay.list.type.model.ListTypeEntry;
import com.liferay.list.type.service.ListTypeEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectField;
import com.liferay.object.rest.dto.v1_0.ListEntry;
import com.liferay.object.rest.dto.v1_0.ObjectEntry;
import com.liferay.object.rest.dto.v1_0.Status;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.reflect.ReflectionUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.json.*;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.module.configuration.ConfigurationProviderUtil;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
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

import java.io.IOException;
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
    private JSONFactory _jsonFactory;
    @Reference
    private ListTypeEntryLocalService _listTypeEntryLocalService;
    @Reference
    private ObjectDefinitionLocalService _objectDefinitionLocalService;
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

    private ObjectField _getObjectFieldByName(
            String name, List<ObjectField> objectFields) {
        for (ObjectField objectField : objectFields) {
            if (Objects.equals(name, objectField.getName())) {
                return objectField;
            }
        }
        return null;
    }

    private String _getSalesforcePagination(Pagination pagination) {
        return StringBundler.concat(
                " LIMIT ", pagination.getPageSize(), " OFFSET ",
                pagination.getStartPosition());
    }

    private Page<ObjectEntry> _getTasks(final CloseableHttpClient httpClient, final String instanceUrl, final String accessToken, final long companyId, final Pagination pagination, final String languageId) throws Exception {
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
                    _toObjectEntries(companyId, languageId, tasks),
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

    private Collection<ObjectEntry> _toObjectEntries(
            long companyId, String languageId,
            JSONArray jsonArray) throws Exception {

        return JSONUtil.toList(
                jsonArray,
                jsonObject -> _toObjectEntry(
                        companyId, languageId, jsonObject));
    }

    private ObjectEntry _toObjectEntry(long companyId, String languageId, JSONObject jsonObject) throws ParseException {
        DateFormat dateTimeFormat = _getDateTimeFormat();

        ObjectDefinition objectDefinition = _objectDefinitionLocalService.fetchObjectDefinitionByExternalReferenceCode("c0679ecb-5900-f3e1-4899-d098eed9ae74", companyId);

        List<ObjectField> objectFields =
                _objectFieldLocalService.getObjectFields(
                        objectDefinition.getObjectDefinitionId());

        ObjectEntry objectEntry = new ObjectEntry() {
            {
                actions = HashMapBuilder.put(
                        "delete", Collections.<String, String>emptyMap()
                ).build();
                creator = CreatorUtil.toCreator(
                        _portal, Optional.empty(),
                        _userLocalService.fetchUserByExternalReferenceCode(
                                jsonObject.getString(SALESFORCE_FIELDS.USER.fieldName), companyId));
                dateCreated = dateTimeFormat.parse(
                        jsonObject.getString(SALESFORCE_FIELDS.CREATED.fieldName));
                dateModified = dateTimeFormat.parse(
                        jsonObject.getString(SALESFORCE_FIELDS.LAST_MODIFIED.fieldName));
                externalReferenceCode = jsonObject.getString(SALESFORCE_FIELDS.ID.fieldName);
                status = new Status() {
                    {
                        code = 0;
                        label = "approved";
                        label_i18n = "Approved";
                    }
                };
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

    @Override
    public void synchronise(long companyId, Map<String, String> properties) {
        try {
            final TaskConnectorConfiguration configuration = _getConfiguration(companyId);

            final String languageId = properties.getOrDefault(LANGUAGE_ID_PROPERTY_KEY, DEFAULT_LANGUAGE_ID);
            final int startPage = Integer.parseInt(properties.getOrDefault(START_PAGE_PROPERTY_KEY, DEFAULT_START_PAGE), DEFAULT_INTEGER_PROPERTY_RADIX);
            final int pageSize = Integer.parseInt(properties.getOrDefault(PAGE_SIZE_PROPERTY_KEY, DEFAULT_PAGE_SIZE), DEFAULT_INTEGER_PROPERTY_RADIX);

            try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
                final JSONObject authResponse = _authenticate(httpClient, configuration);
                if (authResponse == null) {
                    throw new RuntimeException("Unable to authenticate to obtain access token. Check configuration");
                }

                final String instanceUrl = authResponse.getString(SF_INSTANCE_URL);
                final String accessToken = authResponse.getString(SF_ACCESS_TOKEN);

                final int totalCount = _getTotalCount(httpClient, instanceUrl, accessToken);
                if (_log.isDebugEnabled()) {
                    _log.debug(StringBundler.concat("Total count : ", totalCount));
                }

                final int lastPage = (int)Math.ceil((float)(totalCount / pageSize)) + 1; // pages start at 1
                for (int currentPage = startPage; currentPage <= lastPage; currentPage++) {
                    Pagination pagination = Pagination.of(currentPage, pageSize);
                    Page<ObjectEntry> objectEntries = _getTasks(httpClient, instanceUrl, accessToken, companyId, pagination, languageId);

                    if (objectEntries == null)
                        continue;

                    for (ObjectEntry objectEntry : objectEntries.getItems()) {
                        if (_log.isDebugEnabled()) {
                            _log.debug(StringBundler.concat("Page : ", currentPage, " Record : ",
                                    objectEntry));
                        }
                    }
                }
            }
        } catch (Exception exception) {
            throw new RuntimeException("Unable to synchronise Task. See inner exception", exception);
        }
    }
}