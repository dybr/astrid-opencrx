package ru.otdelit.astrid.opencrx.api;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import javax.xml.xpath.XPathExpressionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ru.otdelit.astrid.opencrx.sync.OpencrxActivityProcessGraph;
import ru.otdelit.astrid.opencrx.sync.OpencrxActivityProcessState;
import ru.otdelit.astrid.opencrx.sync.OpencrxActivityProcessTransition;
import ru.otdelit.astrid.opencrx.sync.OpencrxContact;
import ru.otdelit.astrid.opencrx.sync.OpencrxResourceAssignment;
import android.text.TextUtils;
import android.util.Log;

import com.todoroo.andlib.utility.Pair;

/**
 * Class with methods invoked remotely on OpenCRX server.
 * 
 * @author Andrey Marchenko <igendou@gmail.com>
 */
@SuppressWarnings("nls")
public class OpencrxInvoker {

	private final static String XRI_PREFIX = "xri://@openmdx*";

	private final static String XRI_ACTIVITY_PATTERN = "org.opencrx.kernel.activity1/provider/%{provider}/segment/%{segment}/activity";
	private final static String XRI_CREATOR_PATTERN = "org.opencrx.kernel.activity1/provider/%{provider}/segment/%{segment}/activityCreator";
	private final static String XRI_ACCOUNT_PATTERN = "org.opencrx.kernel.account1/provider/%{provider}/segment/%{segment}/account";
	private final static String XRI_RESOURCE_PATTERN = "org.opencrx.kernel.activity1/provider/%{provider}/segment/%{segment}/resource";
	private final static String XRI_ACTIVITY_PROCESS_PATTERN = "org.opencrx.kernel.activity1/provider/%{provider}/segment/%{segment}/activityProcess";
	private static final String XRI_USER_HOME_PATTERN = "org.opencrx.kernel.home1/provider/%{provider}/segment/%{segment}/userHome";

	private final static String KEY_QUERY = "query";
	private final static String KEY_QUERY_TYPE = "queryType";
	private final static String KEY_SIZE = "size";
	private final static String KEY_POSITION = "position";

	private final static String CLASS_CREATOR = "org:opencrx:kernel:activity1:ActivityCreator";
	private final static String CLASS_ACTIVITY = "org:opencrx:kernel:activity1:Activity";
	private final static String CLASS_RESOURCE = "org:opencrx:kernel:activity1:Resource";
	private final static String CLASS_ACTIVITY_PROCESS = "org:opencrx:kernel:activity1:ActivityProcess";
	private final static String CLASS_ACTIVITY_PROCESS_TRANSITION = "org:opencrx:kernel:activity1:ActivityProcessTransition";
	private final static String CLASS_ACTIVITY_PROCESS_STATE = "org:opencrx:kernel:activity1:ActivityProcessState";
	private final static String CLASS_ACTIVITY_FOLLOW_UP = "org:opencrx:kernel:activity1:ActivityFollowUp";
	private final static String CLASS_PROPERTY_SET = "org:opencrx:kernel:generic:PropertySet";
	private final static String CLASS_PROPERTY = "org:opencrx:kernel:base:Property";

	private final static String QUERY_NOT_DISABLED = "forAllDisabled().isFalse()";
	private final static String QUERY_NAME = "name().equalTo(\"\")";
	private final static String QUERY_ACTIVITY_STATE_NOT_CLOSED = "activityState().notEqualTo(:short:20)";
	private final static String QUERY_CREATED_AFTER = "createdAt().greaterThan(:datetime:%s)";

	private final static String OPERATION_NEW_ACTIVITY = "newActivity";
	private final static String OPERATION_REAPPLY_ACTIVITY_CREATOR = "reapplyActivityCreator";
	private final static String OPERATION_ASSIGN_TO = "assignTo";
	private final static String OPERATION_FOLLOW_UP = "doFollowUp";
	private final static String OPERATION_ADD_WORK_RECORD = "addWorkRecord";

	private final static String ACTIVITY_PROPERTY_ASSIGNED_TO = "assignedTo";
	private final static String ACTIVITY_PROPERTY_REPORTING_ACCOUNT = "reportingAccount";
	private final static String ACTIVITY_PROPERTY_NAME = "name";
	private static final String ACTIVITY_PROPERTY_DETAILED_DESCRIPTION = "detailedDescription";
	private final static String ACTIVITY_PROPERTY_DUE_BY = "dueBy";
	private final static String ACTIVITY_PROPERTY_PRIORITY = "priority";
	private final static String ACTIVITY_PROPERTY_ASSIGNED_RESOURCE = "assignedResource";
	private final static String ACTIVITY_PROPERTY_SCHEDULED_START = "scheduledStart";
	private final static String ACTIVITY_PROPERTY_FOLLOW_UP = "followUp";
	private final static String ACTIVITY_PROCESS_PROPERTY_TRANSITIONS = "transition";
	private final static String ACTIVITY_PROCESS_PROPERTY_STATES = "state";

	private final static String PROPERTY_SET = "propertySet";
	private final static String PROPERTY = "property";

	private final static int SIZE = 1000;

	private String XRI_ACTIVITY;
	private String XRI_CREATOR;
	private String XRI_ACCOUNT;
	private String XRI_RESOURCE;
	private String XRI_ACTIVITY_PROCESS;
	private String XRI_USER_HOME;

	private String opencrxUrl;

	// --- authentication and time

	public void authenticate(String login, String password) throws IOException {

		setCredentials(login, password);

		utils.executeGet(TextUtils
				.concat(opencrxUrl, XRI_USER_HOME, "/", login).toString());
	}

	public void setCredentials(String login, String password) {
		utils.setCredentials(login, password);
	}

	public void setOpencrxPreferences(String host, String segment,
			String provider) {
		opencrxUrl = createOpencrxUrl(host);

		renewXri(segment, provider);
	}

	// --- dashboards

	public JSONArray dashboardsShowListOpencrx() throws ApiServiceException,
			IOException {

		int position = 0;

		JSONArray ret = new JSONArray();

		for (;;) {
			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_CREATOR).toString(),
					KEY_QUERY_TYPE, CLASS_CREATOR, KEY_QUERY,
					QUERY_NOT_DISABLED, KEY_POSITION, position, KEY_SIZE, SIZE);

			boolean hasMore = utils.convertOpencrxCreatorsXmlToJson(ret, url);

			if (hasMore)
				position += SIZE;
			else
				break;

		}

		return ret;
	}

	// --- tasks

	public JSONArray tasksShowListOpencrx(OpencrxActivityProcessGraph graph)
			throws IOException, ApiServiceException {
		int position = 0;

		JSONArray ret = new JSONArray();

		for (;;) {
			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY).toString(),
					KEY_QUERY_TYPE,
					CLASS_ACTIVITY,
					KEY_QUERY,
					TextUtils.concat(QUERY_NOT_DISABLED, ";",
							QUERY_ACTIVITY_STATE_NOT_CLOSED).toString(),
					KEY_POSITION, position, KEY_SIZE, SIZE);

			boolean hasMore = utils.getOpencrxActivities(ret, url, graph);

			if (hasMore)
				position += SIZE;
			else
				break;

		}

		return ret;
	}

	public JSONObject tasksCreateOpencrx(String title, String idCreator,
			String idContact, String dueBy, int priority,
			OpencrxActivityProcessGraph graph) throws IOException {

		// url of operation createNewActivity
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_CREATOR,
				"/", idCreator, "/", OPERATION_NEW_ACTIVITY).toString());
		// parameters of new activity
		String createActivityRequest = OpencrxUtils.createNewActivityParams(
				title, dueBy, priority);
		// execute creation, retrieve creation result
		// retrieve CRX_ID of new activity
		String newActivityId = utils.createNewActivity(url,
				createActivityRequest);

		renewActivityWithTrackerProperties(newActivityId, idCreator);

		// we must modify new activity: set assignedTo idContact, not necessary
		// current user
		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_ASSIGNED_TO,
				TextUtils.concat(XRI_PREFIX, XRI_ACCOUNT, "/", idContact)
						.toString());
		url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/",
				newActivityId).toString());
		// put modification params, as a result retrieve activity
		return utils.modifyActivity(url, modifyData, graph);
	}

	private String getPropertySetAddressFromObjects(List<String> xris)
			throws IOException {
		for (String xri : xris) {
			String propertySet = this.getPropertySetFromObject(xri);

			if (propertySet != null)
				return propertySet;
		}

		return null;
	}

	private String getPropertySetFromObject(String objectXri)
			throws IOException {
		String url = createFetchUrl(
				TextUtils.concat(opencrxUrl, objectXri.replace(XRI_PREFIX, ""),
						"/", PROPERTY_SET).toString(), KEY_QUERY_TYPE,
				CLASS_PROPERTY_SET, KEY_QUERY,
				QUERY_NAME.replace("\"\"", "\"Extension\""));

		return utils.getPropertySet(url);
	}

	private String getReferencePropertyValueFromPropertySet(
			String propertyName, String propertySetAddress) throws IOException {
		String url = createFetchUrl(
				TextUtils.concat(propertySetAddress, "/", PROPERTY).toString(),
				KEY_QUERY_TYPE,
				CLASS_PROPERTY,
				KEY_QUERY,
				QUERY_NAME.replace("\"\"",
						TextUtils.concat("\"", propertyName, "\"").toString()));

		return utils.getReferencePropertyValueXri(url);
	}

	private String getReferencePropertyAddressFromPropertySet(
			String propertyName, String propertySetAddress) throws IOException {
		String url = createFetchUrl(
				TextUtils.concat(propertySetAddress, "/", PROPERTY).toString(),
				KEY_QUERY_TYPE,
				CLASS_PROPERTY,
				KEY_QUERY,
				QUERY_NAME.replace("\"\"",
						TextUtils.concat("\"", propertyName, "\"").toString()));

		return utils.getReferencePropertyAddress(url);
	}

	public String createPropertySet(String objectAddress, String propertySetName)
			throws IOException {
		String url = createFetchUrl(TextUtils.concat(objectAddress, "/",
				PROPERTY_SET).toString());
		String modifyData = OpencrxUtils
				.createNewPropertySetParams(propertySetName);
		return utils.createPropertySet(url, modifyData);
	}

	public void createReferenceProperty(String propertySetAddress,
			String propertyName, String propertyValue) throws IOException {
		String url = createFetchUrl(TextUtils.concat(propertySetAddress, "/",
				PROPERTY).toString());
		String modifyData = OpencrxUtils.createNewReferencePropertyParams(
				propertyName, propertyValue);
		utils.executePost(url, modifyData);
	}

	public void setReferenceProperty(String propertyAddress,
			String propertyName, String propertyValue) throws IOException {
		String url = createFetchUrl(propertyAddress);
		String modifyData = OpencrxUtils.createNewReferencePropertyParams(
				propertyName, propertyValue);
		utils.executePut(url, modifyData);
	}

	public void taskSetReportingAccount(String accountXri, String activityId)
			throws IOException {
		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_REPORTING_ACCOUNT, accountXri);
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", activityId).toString());

		try {
			utils.executePut(url, modifyData);
		} catch (Exception e) {
			// possible type mismatch - ignore
		}
	}

	public JSONObject tasksViewOpencrx(String idActivity,
			OpencrxActivityProcessGraph graph) throws ApiServiceException,
			IOException {

		return utils.getOpencrxActivity(
				createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/",
						idActivity).toString()), graph);
	}

	public void taskSetCreator(String idActivity, String idCreator)
			throws IOException, ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity, "/", OPERATION_REAPPLY_ACTIVITY_CREATOR)
				.toString());
		String applyCreatorData = OpencrxUtils
				.createReappplyActivityCreatorParams(TextUtils.concat(
						XRI_PREFIX, XRI_CREATOR, "/", idCreator).toString());
		utils.executePost(url, applyCreatorData);

		renewActivityWithTrackerProperties(idActivity, idCreator);
	}

	public void renewActivityWithTrackerProperties(String idActivity,
			String idCreator) throws IOException, ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_CREATOR,
				"/", idCreator).toString());
		List<String> activityGroups = utils.getActivityGroupXris(url);

		String propertySet = getPropertySetAddressFromObjects(activityGroups);
		if (propertySet == null)
			return;

		String reference = getReferencePropertyValueFromPropertySet(
				"x_LegalEntity", propertySet);
		if (reference != null)
			taskSetReportingAccount(reference, idActivity);

		reference = getReferencePropertyValueFromPropertySet("x_Product",
				propertySet);

		if (reference != null) {
			String newPropertySet = getPropertySetFromObject(TextUtils.concat(
					XRI_ACTIVITY, "/", idActivity).toString());
			if (newPropertySet == null) {
				newPropertySet = createPropertySet(
						TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/",
								idActivity).toString(), "Extension");
			}

			String productProperty = getReferencePropertyAddressFromPropertySet(
					"x_Product", newPropertySet);
			if (productProperty == null) {
				createReferenceProperty(newPropertySet, "x_Product", reference);
			} else {
				setReferenceProperty(productProperty, "x_Product", reference);
			}
		}
	}

	public void tasksDeleteOpencrx(String crxId) throws ApiServiceException,
			IOException {
		utils.executeDelete(TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/",
				crxId).toString());
	}

	public void taskSetAssignedTo(String idActivity, String idContact)
			throws IOException, ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_ASSIGNED_TO,
				TextUtils.concat(XRI_PREFIX, XRI_ACCOUNT, "/", idContact)
						.toString());

		// put modification params, as a result retrieve activity xml
		utils.executePut(url, modifyData);

	}

	public void taskSetName(String idActivity, String name) throws IOException,
			ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_NAME, name);

		// put modification params, as a result retrieve activity xml
		utils.executePut(url, modifyData);

	}

	public void taskSetDetailedDescription(String idActivity, String description)
			throws IOException, ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_DETAILED_DESCRIPTION, description);

		// put modification params, as a result retrieve activity xml
		utils.executePut(url, modifyData);

	}

	public void taskSetPriority(String idActivity, Integer priority)
			throws IOException, ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_PRIORITY, priority.toString());

		// put modification params, as a result retrieve activity xml
		utils.executePut(url, modifyData);
	}

	public void tasksSetDueBy(String idActivity, String dueBy)
			throws IOException, ApiServiceException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_DUE_BY, dueBy);

		// put modification params, as a result retrieve activity xml
		utils.executePut(url, modifyData);

	}

	public void taskSetScheduledStart(String idActivity, long scheduledStartUnix)
			throws IOException, ApiServiceException {
		String scheduledStart = OpencrxUtils
				.formatAsOpencrx(scheduledStartUnix);

		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		String modifyData = OpencrxUtils.createActiviftyModificationParams(
				ACTIVITY_PROPERTY_SCHEDULED_START, scheduledStart);

		// put modification params, as a result retrieve activity xml
		utils.executePut(url, modifyData);
	}

	public void taskAddNote(String idActivity, String note,
			OpencrxActivityProcessGraph graph) throws IOException {

		// first: we get info about ActivityProcess and current ProcessState
		Pair<String, String> processAndState = getActivityProcessAndState(idActivity);

		String processId = processAndState.getLeft();
		String stateId = processAndState.getRight();

		// second: we find transition "Add Note" in current activity process
		OpencrxActivityProcessTransition transAddNote = graph
				.getTransitionByName("Add Note");

		if (transAddNote == null || transAddNote.getPrevState() == null
				|| transAddNote.getNextState() == null)
			return;

		// third: if we are not in the right state to execute AddNote follow-up
		// then we must find transition to get there
		if (!stateId.equals(transAddNote.getPrevState().getId())) {

			OpencrxActivityProcessTransition transAssign = graph
					.getTransitionByStates(stateId, transAddNote.getPrevState()
							.getId());

			if (transAssign == null)
				return;

			executeFollowUp(idActivity, processId, transAssign.getId(),
					"Assign", "");

		}

		// fourth: at last we execute AddNote follow-up
		executeFollowUp(idActivity, processId, transAddNote.getId(),
				"Astrid Note", note);
	}

	public List<String> getAddNotes(String idActivity,
			OpencrxActivityProcessGraph graph, String lastSync)
			throws ApiServiceException {
		List<String> ret = new LinkedList<String>();

		OpencrxActivityProcessTransition transAddNote = graph
				.getTransitionByName("Add Note");
		int position = 0;

		for (;;) {
			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/", idActivity,
							"/", ACTIVITY_PROPERTY_FOLLOW_UP).toString(),
					KEY_POSITION, position, KEY_SIZE, SIZE, KEY_QUERY_TYPE,
					CLASS_ACTIVITY_FOLLOW_UP, KEY_QUERY,
					String.format(QUERY_CREATED_AFTER, lastSync));

			boolean hasMore = utils.getFollowUpsAddNote(ret, url,
					transAddNote.getId());

			if (hasMore)
				position += SIZE;
			else
				break;
		}

		return ret;
	}

	public void taskFollowUpToInProgress(String idActivity,
			OpencrxActivityProcessGraph graph) throws IOException {
		Pair<String, String> processAndState = getActivityProcessAndState(idActivity);

		String processId = processAndState.getLeft();
		String stateId = processAndState.getRight();

		OpencrxActivityProcessState inProgressState = graph
				.getStateByName("In Progress");

		if (inProgressState == null || inProgressState.getId() == null)
			return;

		if (stateId.equals(inProgressState.getId()))
			return;

		OpencrxActivityProcessTransition transAssign = graph
				.getTransitionByStates(stateId, inProgressState.getId());

		if (transAssign == null)
			return;

		executeFollowUp(idActivity, processId, transAssign.getId(),
				"Assign Resource", "");
	}

	public boolean taskClose(String idActivity,
			OpencrxActivityProcessGraph graph) throws IOException {
		return this.executeFollowUpsToState(idActivity, "Closed", graph);
	}

	public boolean taskComplete(String idActivity,
			OpencrxActivityProcessGraph graph) throws IOException {
		return this.executeFollowUpsToState(idActivity, "Complete", graph);
	}

	public boolean taskOpen(String idActivity, OpencrxActivityProcessGraph graph)
			throws IOException {
		return this.executeFollowUpsToState(idActivity, "In Progress", graph);
	}

	public OpencrxActivityProcessGraph getActivityProcessGraph()
			throws IOException {
		String url = createFetchUrl(
				TextUtils.concat(opencrxUrl, XRI_ACTIVITY_PROCESS).toString(),
				KEY_QUERY_TYPE, CLASS_ACTIVITY_PROCESS, KEY_QUERY,
				QUERY_NAME
						.replace("\"\"", "\"Bug + feature tracking process\""));

		String processId = utils.getActivityProcessId(url);

		int position = 0;
		List<OpencrxActivityProcessTransition> transitions = new LinkedList<OpencrxActivityProcessTransition>();

		for (;;) {
			url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY_PROCESS, "/",
							processId, "/",
							ACTIVITY_PROCESS_PROPERTY_TRANSITIONS).toString(),
					KEY_POSITION, position, KEY_SIZE, SIZE, KEY_QUERY_TYPE,
					CLASS_ACTIVITY_PROCESS_TRANSITION, KEY_QUERY,
					QUERY_NOT_DISABLED);

			boolean hasMore = utils.getActivityProcessTransitions(transitions,
					url);

			if (hasMore)
				position += SIZE;
			else
				break;
		}

		position = 0;
		List<OpencrxActivityProcessState> states = new LinkedList<OpencrxActivityProcessState>();

		for (;;) {

			url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY_PROCESS, "/",
							processId, "/", ACTIVITY_PROCESS_PROPERTY_STATES)
							.toString(), KEY_POSITION, position, KEY_SIZE,
					SIZE, KEY_QUERY_TYPE, CLASS_ACTIVITY_PROCESS_STATE,
					KEY_QUERY, QUERY_NOT_DISABLED);

			System.out.println(url);

			boolean hasMore = utils.getActivityProcessStates(states, url);

			if (hasMore)
				position += SIZE;
			else
				break;
		}

		return new OpencrxActivityProcessGraph(transitions, states);
	}

	public void executeFollowUp(String idActivity, String idProcess,
			String idTransition, String name, String description)
			throws ApiServiceException, IOException {
		String url;
		url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/",
				idActivity, "/", OPERATION_FOLLOW_UP).toString());

		String data = OpencrxUtils.createFollowUpParams(
				name,
				description,
				TextUtils.concat(XRI_PREFIX, XRI_ACTIVITY_PROCESS, "/",
						idProcess, "/transition/", idTransition).toString());

		utils.executePost(url, data);
	}

	public boolean executeFollowUps(String idActivity, String idProcess,
			Stack<OpencrxActivityProcessTransition> path)
			throws ApiServiceException, IOException {

		if (path == null)
			return false;

		while (!path.isEmpty()) {
			OpencrxActivityProcessTransition t = path.pop();
			this.executeFollowUp(idActivity, idProcess, t.getId(), t.getName(),
					"");
		}

		return true;
	}

	public boolean executeFollowUpsToState(String idActivity, String stateName,
			OpencrxActivityProcessGraph graph) throws ApiServiceException,
			IOException {

		Pair<String, String> processAndState = getActivityProcessAndState(idActivity);

		String processId = processAndState.getLeft();
		String stateId = processAndState.getRight();

		OpencrxActivityProcessState current = graph.getStateById(stateId);
		OpencrxActivityProcessState target = graph.getStateByName(stateName);

		return this.executeFollowUps(idActivity, processId,
				graph.getPath(current, target));

	}

	public Pair<String, String> getActivityProcessAndState(String idActivity)
			throws ApiServiceException, IOException {
		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", idActivity).toString());

		Pair<String, String> processAndState = utils
				.getProcessIdAndStateId(url);
		return processAndState;
	}

	// --- labels

	// --- contacts

	public JSONObject userUpdateOpencrx() throws IOException,
			XPathExpressionException, JSONException, ParseException {
		return utils.converOpencrxUserHomeToJson(opencrxUrl, XRI_USER_HOME);
	}

	public OpencrxContact[] usersShowListOpencrx() throws ApiServiceException,
			IOException {
		int position = 0;

		List<OpencrxContact> ret = new LinkedList<OpencrxContact>();
		List<String> ids = new LinkedList<String>();

		for (;;) {

			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_RESOURCE).toString(),
					KEY_QUERY_TYPE, CLASS_RESOURCE, KEY_QUERY,
					QUERY_NOT_DISABLED, KEY_POSITION, position, KEY_SIZE, SIZE);

			boolean hasMore = utils.getContactIdsFromResources(ids, url);

			if (hasMore)
				position += SIZE;
			else
				break;

		}

		for (String id : ids) {

			if (TextUtils.isEmpty(id))
				continue;

			String url = createFetchUrl(TextUtils.concat(opencrxUrl,
					XRI_ACCOUNT, "/", id).toString());

			try {
				utils.getOpencrxContacts(ret, url);
			} catch (ApiServiceException ex) {
				// catch it here if we don't have rights to retrieve this
				// contact
			}

		}

		return ret.toArray(new OpencrxContact[0]);
	}

	// resources

	public JSONArray resourcesShowList() throws ApiServiceException,
			IOException {
		int position = 0;

		JSONArray ret = new JSONArray();

		for (;;) {

			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_RESOURCE).toString(),
					KEY_QUERY_TYPE, CLASS_RESOURCE, KEY_QUERY,
					QUERY_NOT_DISABLED, KEY_POSITION, position, KEY_SIZE, SIZE);

			boolean hasMore = utils.convertOpencrxResourcesToJson(ret, url);

			if (hasMore)
				position += SIZE;
			else
				break;

		}

		return ret;
	}

	public JSONArray resourcesShowForTask(String activityId)
			throws ApiServiceException, IOException {

		int position = 0;

		JSONArray arr = new JSONArray();

		for (;;) {

			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/", activityId,
							"/", ACTIVITY_PROPERTY_ASSIGNED_RESOURCE)
							.toString(), KEY_POSITION, position, KEY_SIZE, SIZE);

			List<OpencrxResourceAssignment> assignments = new LinkedList<OpencrxResourceAssignment>();

			boolean hasMore = utils.getResourceAssignments(assignments, url);

			for (OpencrxResourceAssignment assignment : assignments) {

				if (TextUtils.isEmpty(assignment.getResourceId()))
					continue;

				url = createFetchUrl(
						TextUtils.concat(opencrxUrl, XRI_RESOURCE, "/",
								assignment.getResourceId()).toString(),
						KEY_POSITION, position, KEY_SIZE, SIZE);

				JSONObject resource = utils.convertResourceToJson(url);
				if (resource != null)
					arr.put(resource);
			}

			if (hasMore)
				position += SIZE;
			else
				break;

		}

		return arr;
	}

	public List<OpencrxResourceAssignment> resourceAssignmentsShowForTask(
			String activityId) throws IOException {

		int position = 0;

		List<OpencrxResourceAssignment> assignments = new LinkedList<OpencrxResourceAssignment>();

		for (;;) {

			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/", activityId,
							"/", ACTIVITY_PROPERTY_ASSIGNED_RESOURCE)
							.toString(), KEY_POSITION, position, KEY_SIZE, SIZE);

			boolean hasMore = utils.getResourceAssignments(assignments, url);

			if (hasMore)
				position += SIZE;
			else
				break;

		}

		return assignments;
	}

	public void resourceAssignmentDelete(String activityId, String assignmentId)
			throws IOException {
		utils.executeDelete(TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/",
				activityId, "/", ACTIVITY_PROPERTY_ASSIGNED_RESOURCE, "/",
				assignmentId).toString());
	}

	public void taskAssignResource(String activityId, String resourceId)
			throws ApiServiceException, IOException {

		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", activityId, "/", OPERATION_ASSIGN_TO).toString());

		String data = OpencrxUtils.createAssignToResourceParams(TextUtils
				.concat(XRI_PREFIX, XRI_RESOURCE, "/", resourceId).toString());

		utils.executePost(url, data);
	}

	public int getSecondsSpentOnTask(String activityId, String resourceId)
			throws ApiServiceException, IOException {
		int position = 0;

		int seconds = 0;

		for (;;) {
			String url = createFetchUrl(
					TextUtils.concat(opencrxUrl, XRI_ACTIVITY, "/", activityId,
							"/", "workReportEntry").toString(), KEY_POSITION,
					position, KEY_SIZE, SIZE);

			int res = utils.getWorkTimeInSeconds(url, resourceId);

			seconds += Math.abs(res);

			if (res < 0)
				position += SIZE;
			else
				break;
		}

		return seconds;
	}

	public void createWorkRecord(String activityId, String resourceId,
			int seconds) throws ApiServiceException, IOException {

		String url = createFetchUrl(TextUtils.concat(opencrxUrl, XRI_ACTIVITY,
				"/", activityId, "/", OPERATION_ADD_WORK_RECORD).toString());

		String data = OpencrxUtils.createActivityAddWorkRecordParams(
				"Work on task", seconds / 3600, // hours
				(seconds % 3600) / 60, // minutes
				new Date(), // startedAt
				TextUtils.concat(XRI_PREFIX, XRI_RESOURCE, "/", resourceId)
						.toString());

		utils.executePost(url, data);
	}

	// --- invocation

	private final OpencrxUtils utils = new OpencrxUtils();

	String createFetchUrl(String url, Object... getParameters)
			throws ApiServiceException {

		ArrayList<Pair<String, Object>> params = new ArrayList<Pair<String, Object>>();
		for (int i = 0; i < getParameters.length; i += 2)
			params.add(new Pair<String, Object>(getParameters[i].toString(),
					getParameters[i + 1]));

		StringBuilder requestBuilder = new StringBuilder(url).append('?');
		for (Pair<String, Object> entry : params) {
			if (entry.getRight() == null)
				continue;

			String key = entry.getLeft();
			String value = entry.getRight().toString();
			String encoded;

			try {
				encoded = URLEncoder.encode(value, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new ApiServiceException(e);
			}

			requestBuilder.append(key).append('=').append(encoded).append('&');

		}

		requestBuilder.deleteCharAt(requestBuilder.length() - 1);

		return requestBuilder.toString();
	}

	private String createOpencrxUrl(String host) {
		StringBuilder ret = new StringBuilder();

		if (!host.startsWith("http://"))
			ret.append("http://");

		ret.append(host);

		if (!host.endsWith("/"))
			ret.append("/");

		return ret.toString();
	}

	private void renewXri(String segment, String provider) {

		XRI_ACCOUNT = XRI_ACCOUNT_PATTERN.replace("%{segment}", segment)
				.replace("%{provider}", provider);
		XRI_ACTIVITY = XRI_ACTIVITY_PATTERN.replace("%{segment}", segment)
				.replace("%{provider}", provider);
		XRI_CREATOR = XRI_CREATOR_PATTERN.replace("%{segment}", segment)
				.replace("%{provider}", provider);
		XRI_RESOURCE = XRI_RESOURCE_PATTERN.replace("%{segment}", segment)
				.replace("%{provider}", provider);
		XRI_ACTIVITY_PROCESS = XRI_ACTIVITY_PROCESS_PATTERN.replace(
				"%{segment}", segment).replace("%{provider}", provider);
		XRI_USER_HOME = XRI_USER_HOME_PATTERN.replace("%{segment}", segment)
				.replace("%{provider}", provider);

	}
}
