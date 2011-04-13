/**
 * See the file "LICENSE" for the full license governing this code.
 */
package ru.otdelit.astrid.opencrx;

import ru.otdelit.astrid.opencrx.sync.OpencrxActivity;
import ru.otdelit.astrid.opencrx.sync.OpencrxDataService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.todoroo.andlib.service.ContextManager;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.data.Metadata;

/**
 * Exposes Task Details for OpenCRX: <br/>
 * - activity creator <br />
 * - user to whom activity is assigned <br /><br /> 
 * 
 * Adapted from Producteev plugin by Tim Su <tim@todoroo.com>
 *
 * @author Andrey Marchenko <igendou@gmail.com>
 */
public class OpencrxDetailExposer extends BroadcastReceiver {
	
	public static final String DETAIL_SEPARATOR = " | "; //$NON-NLS-1$

    @Override
    public void onReceive(Context context, Intent intent) {
        ContextManager.setContext(context);
        long taskId = intent.getLongExtra(AstridApiConstants.EXTRAS_TASK_ID, -1);
        if(taskId == -1)
            return;

        String taskDetail;
        try {
            taskDetail = getTaskDetails(context, taskId);
        } catch (Exception e) {
            return;
        }
        if(taskDetail == null)
            return;

        Intent broadcastIntent = new Intent(AstridApiConstants.BROADCAST_SEND_DETAILS);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_ADDON, OpencrxUtilities.IDENTIFIER);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_TASK_ID, taskId);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_RESPONSE, taskDetail);
        context.sendBroadcast(broadcastIntent, AstridApiConstants.PERMISSION_READ);
    }

    @SuppressWarnings({"nls"})
    public String getTaskDetails(Context context, long id) {
        Metadata metadata = OpencrxDataService.getInstance().getTaskMetadata(id);
        if(metadata == null)
            return null;

        StringBuilder builder = new StringBuilder();

        if(!OpencrxUtilities.INSTANCE.isLoggedIn())
            return null;

        long creatorId = -1;
        if(metadata.containsNonNullValue(OpencrxActivity.ACTIVITY_CREATOR_ID))
            creatorId = metadata.getValue(OpencrxActivity.ACTIVITY_CREATOR_ID);
        long responsibleId = -1;
        if(metadata.containsNonNullValue(OpencrxActivity.ASSIGNED_TO_ID))
            responsibleId = metadata.getValue(OpencrxActivity.ASSIGNED_TO_ID);

        // display dashboard if not "no sync" or "default"
        if(creatorId != OpencrxUtilities.CREATOR_NO_SYNC) {
            String creatorName = OpencrxDataService.getInstance().getCreatorName(creatorId);
            builder.append("<img src='silk_folder'/> ").append(creatorName).append(DETAIL_SEPARATOR);
        }

        // display responsible user if not current one
        if(responsibleId > 0) {
            String user = OpencrxDataService.getInstance().getUserName(responsibleId);
            if(user != null)
                builder.append("<img src='silk_user_gray'/> ").append(user).append(DETAIL_SEPARATOR);
        }

        if(builder.length() == 0)
            return null;
        String result = builder.toString();
        return result.substring(0, result.length() - DETAIL_SEPARATOR.length());
    }

}
