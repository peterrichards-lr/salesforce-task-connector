package com.liferay.sales.engineering.task.connector.dispatch.executor;

import com.liferay.dispatch.executor.BaseDispatchTaskExecutor;
import com.liferay.dispatch.executor.DispatchTaskExecutor;
import com.liferay.dispatch.executor.DispatchTaskExecutorOutput;
import com.liferay.dispatch.model.DispatchTrigger;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.sales.engineering.task.connector.api.TaskConnector;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Date;

/**
 * @author peterrichards
 */
@Component(
        property = {
                "dispatch.task.executor.name=" + TaskConnectorDispatchTaskExecutor.KEY,
                "dispatch.task.executor.type=" + TaskConnectorDispatchTaskExecutor.KEY
        },
        service = DispatchTaskExecutor.class
)
public class TaskConnectorDispatchTaskExecutor extends BaseDispatchTaskExecutor {

    public static final String KEY =
            "se-task-connector";
    private static final Log _log = LogFactoryUtil.getLog(
            TaskConnectorDispatchTaskExecutor.class);
    @Reference
    private TaskConnector taskConnector;

    @Override
    public void doExecute(final DispatchTrigger dispatchTrigger, final DispatchTaskExecutorOutput dispatchTaskExecutorOutput) throws Exception {
        final UnicodeProperties dispatchTaskSettingsUnicodeProperties =
                dispatchTrigger.getDispatchTaskSettingsUnicodeProperties();

        try {
            taskConnector.synchronise(dispatchTrigger.getCompanyId(), dispatchTaskSettingsUnicodeProperties);

            dispatchTaskExecutorOutput.setOutput(
                    StringBundler.concat(
                            "Ran the Task synchronisation at ",
                            new Date()));
        } catch (RuntimeException exception) {
            if (_log.isDebugEnabled()) {
                _log.debug(exception);
            }
            dispatchTaskExecutorOutput.setError(exception.getCause().getMessage());
            throw new Exception("Failed to synchronise Tasks", exception);
        }
    }

    @Override
    public String getName() {
        return KEY;
    }

}