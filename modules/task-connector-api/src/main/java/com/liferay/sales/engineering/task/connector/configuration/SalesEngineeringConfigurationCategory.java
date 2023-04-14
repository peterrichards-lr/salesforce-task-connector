package com.liferay.sales.engineering.task.connector.configuration;

import com.liferay.configuration.admin.category.ConfigurationCategory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import org.osgi.service.component.annotations.Component;

/**
 * @author peterrichards
 */
@Component(service = ConfigurationCategory.class)
public class SalesEngineeringConfigurationCategory implements ConfigurationCategory {
    private static final String _CATEGORY_ICON = "myspace";
    private static final String _CATEGORY_KEY = "sales-engineering";
    private static final String _CATEGORY_SECTION = "other";
    private static final Log _log = LogFactoryUtil.getLog(
            SalesEngineeringConfigurationCategory.class);

    @Override
    public String getCategoryIcon() {
        if (_log.isDebugEnabled()) {
            _log.debug("Inside getCategoryIcon()");
        }
        return _CATEGORY_ICON;
    }

    @Override
    public String getCategoryKey() {
        if (_log.isDebugEnabled()) {
            _log.debug("Inside getCategoryKey()");
        }
        return _CATEGORY_KEY;
    }

    @Override
    public String getCategorySection() {
        if (_log.isDebugEnabled()) {
            _log.debug("Inside getCategorySection()");
        }
        return _CATEGORY_SECTION;
    }
}
