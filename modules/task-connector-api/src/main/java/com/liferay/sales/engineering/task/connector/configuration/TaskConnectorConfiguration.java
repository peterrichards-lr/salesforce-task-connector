package com.liferay.sales.engineering.task.connector.configuration;

import aQute.bnd.annotation.metatype.Meta;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * @author peterrichards
 */
@ExtendedObjectClassDefinition(
	category = "sales-engineering", scope = ExtendedObjectClassDefinition.Scope.COMPANY
)
@Meta.OCD(
	id = TaskConnectorConfiguration.PID,
	localization = "content/Language",
	name = "task-connector-configuration-name"
)
public interface TaskConnectorConfiguration {
    String PID = "com.liferay.sales.engineering.task.connector.configuration.TaskConnectorConfiguration";

	@Meta.AD(name = "login-url", required = false)
	String loginURL();

	@Meta.AD(name = "consumer-key", required = false)
	String consumerKey();

	@Meta.AD(name = "consumer-secret", required = false)
	String consumerSecret();

	@Meta.AD(name = "username", required = false)
	String username();

	@Meta.AD(name = "password", required = false, type = Meta.Type.Password)
	String password();
}
