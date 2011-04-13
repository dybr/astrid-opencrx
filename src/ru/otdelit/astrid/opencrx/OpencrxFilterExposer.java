/**
 * See the file "LICENSE" for the full license governing this code.
 */
package ru.otdelit.astrid.opencrx;

import java.util.TreeSet;

import ru.otdelit.astrid.opencrx.sync.OpencrxActivity;
import ru.otdelit.astrid.opencrx.sync.OpencrxActivityCreator;
import ru.otdelit.astrid.opencrx.sync.OpencrxContact;
import ru.otdelit.astrid.opencrx.sync.OpencrxDataService;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import ru.otdelit.astrid.opencrx.R;
import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.QueryTemplate;
import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.api.FilterCategory;
import com.todoroo.astrid.api.FilterListHeader;
import com.todoroo.astrid.api.FilterListItem;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.MetadataApiDao;
import com.todoroo.astrid.data.StoreObject;
import com.todoroo.astrid.data.TaskApiDao;

/**
 * Exposes filters based on Opencrx creators and assigned contacts. <br />
 *
 * Adapted from Producteev plugin version by Arne Jans <arne.jans@gmail.com>
 * 
 * @author Andrey Marchenko <igendou@gmail.com> 
 */
public class OpencrxFilterExposer extends BroadcastReceiver {

    /**
     * @param context
     */
    public static Filter filterFromList(Context context, OpencrxActivityCreator creator) {
        String creatorName = creator.getName();
        String title = creator.getName();
        ContentValues values = new ContentValues();
        values.put(Metadata.KEY.name, OpencrxActivity.METADATA_KEY);
        values.put(OpencrxActivity.ACTIVITY_CREATOR_ID.name, creator.getId());
        values.put(OpencrxActivity.ID.name, 0);
        values.put(OpencrxActivity.USERCREATOR_ID.name, 0);
        values.put(OpencrxActivity.ASSIGNED_TO_ID.name, 0);
        Filter filter = new Filter(creatorName, title, new QueryTemplate().join(
                OpencrxDataService.METADATA_JOIN).where(Criterion.and(
                		MetadataApiDao.MetadataCriteria.withKey(OpencrxActivity.METADATA_KEY),
                		TaskApiDao.TaskCriteria.isActive(),
                		TaskApiDao.TaskCriteria.isVisible(),
                        OpencrxActivity.ACTIVITY_CREATOR_ID.eq(creator.getId()))),
                values);

        return filter;
    }

    private Filter filterFromUser(Context context, OpencrxContact user) {
        String title = context.getString(R.string.opencrx_FEx_responsible_title, user.toString());
        ContentValues values = new ContentValues();
        values.put(Metadata.KEY.name, OpencrxActivity.METADATA_KEY);
        values.put(OpencrxActivity.ID.name, 0);
        values.put(OpencrxActivity.USERCREATOR_ID.name, 0);
        values.put(OpencrxActivity.ASSIGNED_TO_ID.name, user.getId());
        Filter filter = new Filter(user.toString(), title, new QueryTemplate().join(
                OpencrxDataService.METADATA_JOIN).where(Criterion.and(
                		MetadataApiDao.MetadataCriteria.withKey(OpencrxActivity.METADATA_KEY),
                		TaskApiDao.TaskCriteria.isActive(),
                		TaskApiDao.TaskCriteria.isVisible(),
                        OpencrxActivity.ASSIGNED_TO_ID.eq(user.getId()))),
                        values);

        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ContextManager.setContext(context);
        // if we aren't logged in, don't expose features
        if(!OpencrxUtilities.INSTANCE.isLoggedIn())
            return;

        StoreObject[] creators = OpencrxDataService.getInstance().getCreators();

        // If user does not have any dashboards, don't show this section at all
        if(creators.length == 0)
            return;

        FilterListHeader opencrxHeader = new FilterListHeader(context.getString(R.string.opencrx_FEx_header));

        // load dashboards
        Filter[] creatorFilters = new Filter[creators.length];
        for(int i = 0; i < creators.length; i++)
            creatorFilters[i] = filterFromList(context, new OpencrxActivityCreator(creators[i]));
        FilterCategory opencrxCreators = new FilterCategory(context.getString(R.string.opencrx_FEx_dashboard),
                creatorFilters);

        // load responsible people
        TreeSet<OpencrxContact> contacts = loadResponsiblePeople();
        Filter[] contactFilters = new Filter[contacts.size()];
        int index = 0;
        for (OpencrxContact contact : contacts)
            contactFilters[index++] = filterFromUser(context, contact);
        FilterCategory opencrxContacts = new FilterCategory(context.getString(R.string.opencrx_FEx_responsible),
                contactFilters);

        // transmit filter list
        FilterListItem[] list = new FilterListItem[3]; // ATTENTION!!!!!!!!
        list[0] = opencrxHeader;
        list[1] = opencrxCreators;
        list[2] = opencrxContacts;
        Intent broadcastIntent = new Intent(AstridApiConstants.BROADCAST_SEND_FILTERS);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_ADDON, OpencrxUtilities.IDENTIFIER);
        broadcastIntent.putExtra(AstridApiConstants.EXTRAS_RESPONSE, list);
        context.sendBroadcast(broadcastIntent, AstridApiConstants.PERMISSION_READ);
    }

    /**
     *
     * @return
     */
    private TreeSet<OpencrxContact> loadResponsiblePeople() {
        StoreObject[] usersData = OpencrxDataService.getInstance().getContacts();
        TreeSet<OpencrxContact> users = new TreeSet<OpencrxContact>();

        for (StoreObject user : usersData){
            users.add(new OpencrxContact(user));
        }

        return users;
    }

}
