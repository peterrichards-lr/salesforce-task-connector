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
	name = "task-connector-configuration-name",
	description = "task-connector-configuration-description"
)
public interface TaskConnectorConfiguration {
    String PID = "com.liferay.sales.engineering.task.connector.configuration.TaskConnectorConfiguration";

	@Meta.AD(name = "task-connector-configuration-sf-login-url-name", description = "task-connector-configuration-sf-login-url-description", required = false)
	String loginURL();

	@Meta.AD(name = "task-connector-configuration-sf-consumer-key-name", description = "task-connector-configuration-sf-consumer-key-description", required = false)
	String consumerKey();

	@Meta.AD(name = "task-connector-configuration-sf-consumer-secret-name", description = "task-connector-configuration-sf-consumer-secret-description", required = false, type = Meta.Type.Password)
	String consumerSecret();

	@Meta.AD(name = "task-connector-configuration-sf-username-name", description = "task-connector-configuration-sf-username-description", required = false)
	String username();

	@Meta.AD(name = "task-connector-configuration-sf-password-name", description = "task-connector-configuration-sf-password-description", required = false, type = Meta.Type.Password)
	String password();
}
