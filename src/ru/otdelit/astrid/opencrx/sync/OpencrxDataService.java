/**
 * See the file "LICENSE" for the full license governing this code.
 */
package ru.otdelit.astrid.opencrx.sync;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ru.otdelit.astrid.opencrx.OpencrxUtilities;
import ru.otdelit.astrid.opencrx.api.ApiUtilities;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.Time;

import com.todoroo.andlib.data.Property;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.data.Property.StringProperty;
import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.Join;
import com.todoroo.andlib.sql.Order;
import com.todoroo.andlib.sql.Query;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.MetadataApiDao.MetadataCriteria;
import com.todoroo.astrid.data.StoreObject;
import com.todoroo.astrid.data.StoreObjectApiDao;
import com.todoroo.astrid.data.StoreObjectApiDao.StoreObjectCriteria;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.data.Update;
import ru.otdelit.astrid.opencrx.api.UpdateApiDao;
import ru.otdelit.astrid.opencrx.api.SyncMetadataService;
import com.todoroo.astrid.sync.SyncProviderUtilities;

/**
 * Singleton. Provides access to Astrid DataService.
 * 
 * Adapted from Producteev plugin by Tim Su <tim@todoroo.com>
 * 
 * @author Andrey Marchenko <igendou@gmail.com>
 */
public final class OpencrxDataService extends
		SyncMetadataService<OpencrxTaskContainer> {

	// --- constants
	/** Property for reading tag values */
	public static final StringProperty TAG = Metadata.VALUE1;

	/** Utility for joining tasks with metadata */
	public static final Join METADATA_JOIN = Join.left(Metadata.TABLE,
			Task.ID.eq(Metadata.TASK));

	/**
	 * ATTENTION: duplicates UpdateAdapter.UPDATE_TASK_COMMENT
	 */
	public static final String UPDATE_TASK_COMMENT = "task_comment"; //$NON-NLS-1$

	// --- singleton

	private static OpencrxDataService instance = null;

	public static synchronized OpencrxDataService getInstance() {
		if (instance == null)
			instance = new OpencrxDataService(ContextManager.getContext());
		return instance;
	}

	// --- instance variables

	protected final Context context;

	private StoreObjectApiDao storeObjectDao;
	private UpdateApiDao updateDao;

	private OpencrxDataService(Context context) {
		super(context);
		this.context = context;

		storeObjectDao = new StoreObjectApiDao(context);
		updateDao = new UpdateApiDao(context);
	}

	// --- task and metadata methods

	/**
	 * Clears metadata information. Used when user logs out of service
	 */
	@Override
	public void clearMetadata() {
		super.clearMetadata();
		storeObjectDao.deleteWhere(StoreObject.TYPE
				.eq(OpencrxActivityCreator.TYPE));
		storeObjectDao.deleteWhere(StoreObject.TYPE.eq(OpencrxContact.TYPE));
	}

	public TodorooCursor<Task> getSyncedTasks(Property<?>[] properties) {
		TodorooCursor<Task> tasks = taskDao.query(Query.select(Task.ID)
				.orderBy(Order.asc(Task.ID)));

		return joinWithOpencrxMetadata(tasks, true, properties);
	}

	private void readCreators() {
		if (creators == null) {
			creators = readStoreObjects(OpencrxActivityCreator.TYPE);
			creatorExists = new boolean[creators.length];
		}
	}

	private void readContacts() {
		if (contacts == null) {
			contacts = readStoreObjects(OpencrxContact.TYPE);
			contactExists = new boolean[contacts.length];
		}
	}

	/**
	 * Reads store objects.
	 */
	public StoreObject[] readStoreObjects(String type) {
		StoreObject[] ret;
		TodorooCursor<StoreObject> cursor = storeObjectDao
				.query(Query.select(StoreObject.PROPERTIES).where(
						StoreObjectCriteria.byType(type)));
		try {
			ret = new StoreObject[cursor.getCount()];
			for (int i = 0; i < ret.length; i++) {
				cursor.moveToNext();
				StoreObject object = new StoreObject(cursor);
				ret[i] = object;
			}
		} finally {
			cursor.close();
		}

		return ret;
	}

	// --- Update methods

	public Update[] readNewComments(Time lastSync, long taskId) {
		Update[] ret = null;
		TodorooCursor<Update> cursor = updateDao.query(Query.select(
				Update.PROPERTIES).where(
				Criterion.and(Update.ACTION_CODE.eq(UPDATE_TASK_COMMENT),
						Update.TASK_LOCAL.eq(taskId),
						Update.CREATION_DATE.gt(lastSync.toMillis(false)))));

		try {
			ret = new Update[cursor.getCount()];
			for (int i = 0; i < ret.length; ++i) {
				cursor.moveToNext();
				Update upd = new Update(cursor);
				ret[i] = upd;
			}
		} finally {
			cursor.close();
		}

		return ret;
	}

	public boolean storeNewComment(String text, long taskId, String taskTitle) {
		Update upd = new Update();

		upd.setValue(Update.ACTION_CODE, UPDATE_TASK_COMMENT);
		upd.setValue(Update.TASK_LOCAL, taskId);
		upd.setValue(Update.MESSAGE, text);
		upd.setValue(Update.USER_ID, 0L);
		upd.setValue(Update.CREATION_DATE, DateUtilities.now());
		upd.setValue(Update.TARGET_NAME, taskTitle);

		return updateDao.save(upd);
	}

	// --- dashboard methods

	private StoreObject[] creators = null;
	private boolean[] creatorExists = null; // array of flags to determine
											// whether creator still exists on
											// remote server

	/**
	 * @return a list of creators
	 */
	public StoreObject[] getCreators() {
		readCreators();
		return creators;
	}

	/**
	 * Reads creators
	 * 
	 * @throws JSONException
	 */
	@SuppressWarnings("nls")
	public void updateCreators(JSONArray changedCreators) throws JSONException {
		readCreators();

		for (int i = 0; i < changedCreators.length(); i++) {
			JSONObject remote = changedCreators.getJSONObject(i).getJSONObject(
					"dashboard");
			updateCreator(remote, false);
		}

		// check if there are dashboards which does not exist on remote server
		for (int i = 0; i < creators.length; ++i) {
			if (!creatorExists[i])
				storeObjectDao.delete(creators[i].getId());
		}

		// clear dashboard cache
		creators = null;
		creatorExists = null;
	}

	@SuppressWarnings("nls")
	public StoreObject updateCreator(JSONObject remote, boolean reinitCache)
			throws JSONException {
		if (reinitCache)
			readCreators();
		long id = remote.getLong("id_dashboard");
		StoreObject local = null;
		for (int i = 0; i < creators.length; ++i) {
			if (creators[i].getValue(OpencrxActivityCreator.REMOTE_ID).equals(
					id)) {
				local = creators[i];
				creatorExists[i] = true;
				break;
			}
		}

		if (local == null)
			local = new StoreObject();

		local.setValue(StoreObject.TYPE, OpencrxActivityCreator.TYPE);
		local.setValue(OpencrxActivityCreator.REMOTE_ID, id);
		local.setValue(OpencrxActivityCreator.NAME,
				ApiUtilities.decode(remote.getString("title")));
		local.setValue(OpencrxActivityCreator.CRX_ID,
				remote.getString("crx_id"));

		storeObjectDao.save(local);
		if (reinitCache)
			creators = null;
		return local;
	}

	// user methods

	private StoreObject[] contacts = null;
	private boolean[] contactExists = null;

	/**
	 * @return a list of users
	 */
	public StoreObject[] getContacts() {
		readContacts();
		return contacts;
	}

	/**
	 * Reads users
	 */
	public void updateContacts(OpencrxContact[] remoteUsers) {
		readContacts();

		for (int i = 0; i < remoteUsers.length; i++) {
			OpencrxContact remote = remoteUsers[i];
			updateContact(remote, false);
		}

		// check if there are users which does not exist on remote server
		for (int i = 0; i < contacts.length; ++i) {
			if (!contactExists[i])
				storeObjectDao.delete(contacts[i].getId());
		}

		// clear user cache
		contacts = null;
		contactExists = null;
	}

	public StoreObject updateContact(OpencrxContact remote, boolean reinitCache) {
		if (reinitCache)
			readContacts();

		long id = remote.getId();

		StoreObject local = null;

		for (int i = 0; i < contacts.length; ++i) {
			if (contacts[i].getValue(OpencrxContact.REMOTE_ID).equals(id)) {
				local = contacts[i];
				contactExists[i] = true;
				break;
			}
		}

		if (local == null)
			local = new StoreObject();

		local.setValue(StoreObject.TYPE, OpencrxContact.TYPE);
		local.setValue(OpencrxContact.REMOTE_ID, id);
		local.setValue(OpencrxContact.FIRST_NAME, remote.getFirstname());
		local.setValue(OpencrxContact.LAST_NAME, remote.getLastname());
		local.setValue(OpencrxContact.CRX_ID, remote.getCrxId());

		storeObjectDao.save(local);

		if (reinitCache) {
			contacts = null;
			contactExists = null;
		}

		return local;
	}

	public String getCreatorCrxId(long idCreator) {
		TodorooCursor<StoreObject> res = storeObjectDao.query(Query.select(
				OpencrxActivityCreator.CRX_ID).where(
				Criterion.and(StoreObject.TYPE.eq(OpencrxActivityCreator.TYPE),
						OpencrxActivityCreator.REMOTE_ID.eq(idCreator))));

		try {
			if (res.getCount() > 0) {
				res.moveToFirst();
				String id = res.get(OpencrxActivityCreator.CRX_ID);
				return id;
			} else {
				return null;
			}
		} finally {
			res.close();
		}

	}

	public String getContactCrxId(long idUser) {
		TodorooCursor<StoreObject> res = storeObjectDao.query(Query.select(
				OpencrxContact.CRX_ID).where(
				Criterion.and(StoreObject.TYPE.eq(OpencrxContact.TYPE),
						OpencrxContact.REMOTE_ID.eq(idUser))));

		try {
			if (res.getCount() > 0) {
				res.moveToFirst();
				String id = res.get(OpencrxContact.CRX_ID);
				return id;
			} else {
				return null;
			}
		} finally {
			res.close();
		}

	}

	@SuppressWarnings("nls")
	public String getUserName(long idUser) {
		TodorooCursor<StoreObject> res = storeObjectDao.query(Query.select(
				OpencrxContact.LAST_NAME, OpencrxContact.FIRST_NAME).where(
				Criterion.and(StoreObject.TYPE.eq(OpencrxContact.TYPE),
						OpencrxContact.REMOTE_ID.eq(idUser))));

		try {
			if (res.getCount() > 0) {
				res.moveToFirst();
				String firstName = res.get(OpencrxContact.FIRST_NAME);
				String lastName = res.get(OpencrxContact.LAST_NAME);

				boolean hasFirstName = !TextUtils.isEmpty(firstName);
				boolean hasLastName = !TextUtils.isEmpty(lastName);

				return TextUtils.concat(hasFirstName ? firstName : "",
						hasFirstName && hasLastName ? " " : "",
						hasLastName ? lastName : "").toString();
			} else {
				return null;
			}
		} finally {
			res.close();
		}

	}

	@SuppressWarnings("nls")
	public String getCreatorName(long idCreator) {
		TodorooCursor<StoreObject> res = storeObjectDao.query(Query.select(
				OpencrxActivityCreator.NAME).where(
				Criterion.and(StoreObject.TYPE.eq(OpencrxActivityCreator.TYPE),
						OpencrxActivityCreator.REMOTE_ID.eq(idCreator))));

		try {
			if (res.getCount() > 0) {
				res.moveToFirst();
				String name = res.get(OpencrxActivityCreator.NAME);

				return name;
			} else {
				return null;
			}
		} finally {
			res.close();
		}

	}

	public void deleteTaskAndMetadata(long taskId) {
		taskDao.delete(taskId);
		metadataDao.deleteWhere(Metadata.TASK.eq(taskId));
	}

	@Override
	public OpencrxTaskContainer createContainerFromLocalTask(Task task,
			ArrayList<Metadata> metadata) {
		return new OpencrxTaskContainer(task, metadata);
	}

	@Override
	public Criterion getLocalMatchCriteria(OpencrxTaskContainer remoteTask) {
		return OpencrxActivity.ID.eq(remoteTask.pdvTask
				.getValue(OpencrxActivity.ID));
	}

	@Override
	public Criterion getMetadataCriteria() {
		return Criterion.or(
				MetadataCriteria.withKey(OpencrxActivity.METADATA_KEY),
				MetadataCriteria.withKey(TAG_KEY));
	}

	@Override
	public String getMetadataKey() {
		return OpencrxActivity.METADATA_KEY;
	}

	@Override
	public Criterion getMetadataWithRemoteId() {
		return OpencrxActivity.ID.gt(0);
	}

	@Override
	public SyncProviderUtilities getUtilities() {
		return OpencrxUtilities.INSTANCE;
	}

	private TodorooCursor<Task> joinWithOpencrxMetadata(
			TodorooCursor<Task> tasks, boolean both, Property<?>... properties) {
		try {
			TodorooCursor<Metadata> metadata = getRemoteTaskOpencrxMetadata();
			try {
				ArrayList<Long> matchingRows = new ArrayList<Long>();
				joinRowsOpencrx(tasks, metadata, matchingRows, both);

				return taskDao.query(Query.select(properties).where(
						Task.ID.in(matchingRows.toArray(new Long[matchingRows
								.size()]))));
			} finally {
				metadata.close();
			}
		} finally {
			tasks.close();
		}
	}

	/**
	 * Gets cursor across all task metadata for joining
	 * 
	 * @return cursor
	 */
	private TodorooCursor<Metadata> getRemoteTaskOpencrxMetadata() {
		return metadataDao.query(Query
				.select(Metadata.TASK)
				.where(Criterion.and(
						MetadataCriteria.withKey(getMetadataKey()),
						OpencrxActivity.ID.gt(1)))
				.orderBy(Order.asc(Metadata.TASK)));
	}

	/**
	 * Join rows from two cursors on the first column, assuming its an id column
	 * 
	 * @param left
	 * @param right
	 * @param matchingRows
	 * @param both
	 *            - if false, returns left join, if true, returns both join
	 */
	private static void joinRowsOpencrx(TodorooCursor<?> left,
			TodorooCursor<?> right, ArrayList<Long> matchingRows, boolean both) {

		left.moveToPosition(-1);
		right.moveToFirst();

		while (true) {
			left.moveToNext();
			if (left.isAfterLast())
				break;
			long leftValue = left.getLong(0);

			// advance right until it is equal or bigger
			while (!right.isAfterLast() && right.getLong(0) < leftValue) {
				right.moveToNext();
			}

			if (right.isAfterLast()) {
				if (!both)
					matchingRows.add(leftValue);
				continue;
			}

			if ((right.getLong(0) == leftValue) == both)
				matchingRows.add(leftValue);
		}
	}

}
