package ru.otdelit.astrid.opencrx.api;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import ru.otdelit.astrid.opencrx.sync.OpencrxActivityProcessGraph;
import ru.otdelit.astrid.opencrx.sync.OpencrxActivityProcessState;
import ru.otdelit.astrid.opencrx.sync.OpencrxActivityProcessTransition;
import ru.otdelit.astrid.opencrx.sync.OpencrxContact;
import ru.otdelit.astrid.opencrx.sync.OpencrxResourceAssignment;
import ru.otdelit.astrid.opencrx.xml.ActivityCreationResultParser;
import ru.otdelit.astrid.opencrx.xml.ActivityCreatorParser;
import ru.otdelit.astrid.opencrx.xml.ActivityCurrentProcessParser;
import ru.otdelit.astrid.opencrx.xml.ActivityParser;
import ru.otdelit.astrid.opencrx.xml.ActivityProcessParser;
import ru.otdelit.astrid.opencrx.xml.ActivityProcessStateParser;
import ru.otdelit.astrid.opencrx.xml.ActivityProcessTransitionParser;
import ru.otdelit.astrid.opencrx.xml.BaseParser;
import ru.otdelit.astrid.opencrx.xml.ContactParser;
import ru.otdelit.astrid.opencrx.xml.CreatorActivityGroupParser;
import ru.otdelit.astrid.opencrx.xml.PropertySetParser;
import ru.otdelit.astrid.opencrx.xml.ReferencePropertyParser;
import ru.otdelit.astrid.opencrx.xml.ResourceAssignmentParser;
import ru.otdelit.astrid.opencrx.xml.ResourceContactIdParser;
import ru.otdelit.astrid.opencrx.xml.ResourceParser;
import ru.otdelit.astrid.opencrx.xml.UserHomeParser;
import ru.otdelit.astrid.opencrx.xml.WorkRecordParser;


import android.text.TextUtils;

import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.andlib.utility.Pair;
/**
* 
* Utility methods for parsing http response from OpenCRX. 
* Guarantees that InputStream will be closed after using.
*
* @author Andrey Marchenko <igendou@gmail.com>
*/
@SuppressWarnings("nls")
public class OpencrxUtils {

    /** saved credentials in case we need to re-log in */
    private String retryLogin;
    private String retryPassword;
    
    private final OpencrxRestClient restClient = new OpencrxRestClient();
    
    public static final String TAG = "Opencrx";

    private static SimpleDateFormat opencrxTimeFormatter;
    private static SimpleDateFormat opencrxToProducteevTimeFormatter;

    private static SAXParserFactory factory = SAXParserFactory.newInstance();
    private static SAXParser xmlParser;

    public void setCredentials(String login, String password) {
        retryLogin = login;
        retryPassword = password;
    }

    public String getActivityProcessId(String url) throws ApiServiceException {
    	InputStream xml = null;
    	
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ActivityProcessParser p = new ActivityProcessParser();

            parser.parse(xml, p);

            if (! p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.getId();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        } finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public Pair<String, String> getProcessIdAndStateId(String url) throws ApiServiceException {
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ActivityCurrentProcessParser p = new ActivityCurrentProcessParser();

            parser.parse(xml, p);

            return new Pair<String, String>(p.getProcessId(), p.getProcessStateId());

        } catch (Exception e) {
            throw new ApiServiceException(e);
        } finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getWorkTimeInSeconds(String url, String resourceId) throws ApiServiceException {
    	InputStream xml = null;
        try {

            if (TextUtils.isEmpty(resourceId))
                return 0;
            
            xml = restClient.get(url, retryLogin, retryPassword);

            SAXParser parser = getParser();

            WorkRecordParser p = new WorkRecordParser(resourceId);

            parser.parse(xml, p);

            if (!p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.hasMore() ? -p.getElapsedSeconds() : p.getElapsedSeconds();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        } finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void getOpencrxContacts(List<OpencrxContact> destination, String url) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ContactParser p = new ContactParser(destination);

            parser.parse(xml, p);

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String getPropertySetAddress(InputStream xml) throws ApiServiceException {
        try {
            SAXParser parser = getParser();

            PropertySetParser p = new PropertySetParser();

            parser.parse(xml, p);

            return p.getId();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public String createPropertySet(String url, String data) throws ApiServiceException{
    	InputStream xml = null;
    	try{
    		xml = restClient.post(url, data, retryLogin, retryPassword);
    		
    		return getPropertySetAddress(xml);
    	}catch (Exception ex){
    		throw new ApiServiceException(ex);
    	}
    }

    public String getPropertySet(String url) throws ApiServiceException{
    	InputStream xml = null;
    	try{
    		xml = restClient.get(url, retryLogin, retryPassword);
    		
    		return getPropertySetAddress(xml);
    	}catch (Exception ex){
    		throw new ApiServiceException(ex);
    	}
    }
    
    public String getReferencePropertyValueXri(String url) throws ApiServiceException {
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
            SAXParser parser = getParser();

            ReferencePropertyParser p = new ReferencePropertyParser();

            parser.parse(xml, p);

            return p.getReferenceValueXri();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getReferencePropertyAddress(String url) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ReferencePropertyParser p = new ReferencePropertyParser();

            parser.parse(xml, p);

            return p.getReferencePropertyAddress();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean getContactIdsFromResources(List<String> dest, String url) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ResourceContactIdParser p = new ResourceContactIdParser(dest);

            parser.parse(xml, p);

            if (! p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getActivityGroupXris(String url) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            List<String> ret = new LinkedList<String>();

            SAXParser parser = getParser();

            CreatorActivityGroupParser p = new CreatorActivityGroupParser(ret);

            parser.parse(xml, p);

            return ret;
        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean convertOpencrxActivitiesToJson(JSONArray destination, InputStream xml, OpencrxActivityProcessGraph graph) throws ApiServiceException{
        try {
            OpencrxActivityProcessState complete = graph.getStateByName("Complete");
            OpencrxActivityProcessState closed = graph.getStateByName("Closed");

            String completeId = "";
            String closedId = "";

            if (complete != null && complete.getId() != null)
                completeId = complete.getId();

            if (closed != null && closed.getId() != null)
                closedId = closed.getId();

            SAXParser parser = getParser();

            BaseParser p = new ActivityParser(destination, completeId, closedId);

            parser.parse(xml, p);

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private JSONObject convertOpencrxActivityToJson(InputStream xml, OpencrxActivityProcessGraph graph) throws ApiServiceException{
        JSONArray temp = new JSONArray();

        this.convertOpencrxActivitiesToJson(temp, xml, graph);

        try {
            return temp.length() == 0 ? null : temp.getJSONObject(0);
        } catch (JSONException e) {
            throw new ApiServiceException(e);
        }
    }

    
    public boolean getOpencrxActivities(JSONArray destination, String url, OpencrxActivityProcessGraph graph) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            return this.convertOpencrxActivitiesToJson(destination, xml, graph);

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }
    }
    
    public JSONObject modifyActivity(String url, String modificationData, OpencrxActivityProcessGraph graph) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.put(url, modificationData, retryLogin, retryPassword);
        	
            return this.convertOpencrxActivityToJson(xml, graph);

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }    	
    }
    
    public JSONObject getOpencrxActivity(String url, OpencrxActivityProcessGraph graph) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            return this.convertOpencrxActivityToJson(xml, graph);
        } catch (Exception e) {
            throw new ApiServiceException(e);
        }
    	
    }

    public boolean convertOpencrxCreatorsXmlToJson(JSONArray destination, String url) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
            SAXParser parser = getParser();

            ActivityCreatorParser p = new ActivityCreatorParser(destination);

            parser.parse(xml, p);

            if (!p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public boolean convertOpencrxResourcesToJson(JSONArray ret, String url) throws ApiServiceException {
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ResourceParser p = new ResourceParser(ret);

            parser.parse(xml, p);

            if (!p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public JSONObject convertResourceToJson(String url) throws ApiServiceException {
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            JSONArray ret = new JSONArray();
            ResourceParser p = new ResourceParser(ret);

            parser.parse(xml, p);

            return ret.length() == 0 ? null : ret.getJSONObject(0);

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean getResourceAssignments(
            List<OpencrxResourceAssignment> ret, String url) throws ApiServiceException {
    	InputStream xml = null;
        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ResourceAssignmentParser p = new ResourceAssignmentParser(ret);

            parser.parse(xml, p);

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public JSONObject converOpencrxUserHomeToJson(String opencrxUrl, String xriUserHome) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	String url = TextUtils.concat(opencrxUrl, xriUserHome, "/", retryLogin).toString();
        	
        	xml = restClient.get(url, retryLogin, retryPassword);
            SAXParser parser = getParser();

            UserHomeParser p = new UserHomeParser();

            parser.parse(xml, p);

            JSONObject ret = new JSONObject();
            ret.put("id_user", hash(p.getId()) );
            ret.put("crxid_user", p.getId() );

            return ret;
        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public String createNewActivity(String url, String createParams) throws ApiServiceException{
    	InputStream xml = null;
        try {
        	xml = restClient.post(url, createParams, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ActivityCreationResultParser p = new ActivityCreationResultParser();

            parser.parse(xml, p);

            return p.getId();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml != null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String createNewActivityParams(String title, String dueBy, int priority) {
        StringBuilder ret = new StringBuilder();

        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.activity1.NewActivityParams>");

        ret.append("<name>");
        ret.append("<![CDATA[");
        ret.append(title);
        ret.append("]]>");
        ret.append("</name>");

        ret.append("<dueBy>");
        ret.append(dueBy);
        ret.append("</dueBy>");

        ret.append("<priority>");
        ret.append(priority);
        ret.append("</priority>");

        ret.append("<icalType>");
        ret.append("0");
        ret.append("</icalType>");

        ret.append("</org.opencrx.kernel.activity1.NewActivityParams>");
        return ret.toString();
    }

    public static String createNewPropertySetParams(String name) {
        StringBuilder ret = new StringBuilder();
        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.generic.PropertySet>");

        ret.append("<name>");
        ret.append("<![CDATA[");
        ret.append(name);
        ret.append("]]>");
        ret.append("</name>");

        ret.append("</org.opencrx.kernel.generic.PropertySet>");

        return ret.toString();
    }

    public static String createNewReferencePropertyParams(String name, String reference) {
        StringBuilder ret = new StringBuilder();
        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.base.ReferenceProperty>");

        ret.append("<referenceValue>");
        ret.append(reference);
        ret.append("</referenceValue>");

        ret.append("<name>");
        ret.append("<![CDATA[");
        ret.append(name);
        ret.append("]]>");
        ret.append("</name>");

        ret.append("</org.opencrx.kernel.base.ReferenceProperty>");

        return ret.toString();
    }

    public static String createReappplyActivityCreatorParams(String activityCreator) {
        StringBuilder ret = new StringBuilder();

        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.activity1.ReapplyActivityCreatorParams>");

        ret.append("<activityCreator>");
        ret.append(activityCreator);
        ret.append("</activityCreator>");

        ret.append("</org.opencrx.kernel.activity1.ReapplyActivityCreatorParams>");

        return ret.toString();
    }

    public static String createActiviftyModificationParams(String... paramPairs) {
        StringBuilder ret = new StringBuilder();

        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.activity1.Activity>");

        for (int i = 0; i + 1 < paramPairs.length; i += 2){
            ret.append("<");
            ret.append(paramPairs[i]);
            ret.append(">");

            ret.append(paramPairs[i+1]);

            ret.append("</");
            ret.append(paramPairs[i]);
            ret.append(">");
        }

        ret.append("</org.opencrx.kernel.activity1.Activity>");

        return ret.toString();
    }

    public static String createAssignToResourceParams(String resource) {
        StringBuilder ret = new StringBuilder();

        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.activity1.ActivityAssignToParams>");

        ret.append("<resource>");
        ret.append(resource);
        ret.append("</resource>");

        ret.append("</org.opencrx.kernel.activity1.ActivityAssignToParams>");

        return ret.toString();
    }

    public static String createFollowUpParams(String name, String text, String transition) {
        StringBuilder ret = new StringBuilder();

        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.activity1.ActivityDoFollowUpParams>");

        ret.append("<followUpTitle>");
        ret.append(name);
        ret.append("</followUpTitle>");

        ret.append("<followUpText>");
        ret.append(text);
        ret.append("</followUpText>");

        ret.append("<transition>");
        ret.append(transition);
        ret.append("</transition>");

        ret.append("</org.opencrx.kernel.activity1.ActivityDoFollowUpParams>");

        return ret.toString();
    }

    public static String createActivityAddWorkRecordParams(String name, int hours, int minutes, Date startedAt, String resourceId) {

        StringBuilder ret = new StringBuilder();

        ret.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <org.opencrx.kernel.activity1.ActivityAddWorkRecordParams>");

        ret.append("<name>");
        ret.append(name);
        ret.append("</name>");

        ret.append("<depotSelector>");
        ret.append("0");
        ret.append("</depotSelector>");

        ret.append("<rateCurrency>");
        ret.append("0");
        ret.append("</rateCurrency>");

        ret.append("<recordType>");
        ret.append("0");
        ret.append("</recordType>");

        ret.append("<durationHours>");
        ret.append(String.valueOf(hours));
        ret.append("</durationHours>");

        ret.append("<durationMinutes>");
        ret.append(String.valueOf(minutes));
        ret.append("</durationMinutes>");

        ret.append("<startAt>");
        ret.append(formatAsOpencrx(startedAt.getTime()));
        ret.append("</startAt>");

        ret.append("<resource>");
        ret.append(resourceId);
        ret.append("</resource>");

        ret.append("</org.opencrx.kernel.activity1.ActivityAddWorkRecordParams>");

        return ret.toString();
    }

    public boolean getActivityProcessTransitions(List<OpencrxActivityProcessTransition> dest, String url)throws ApiServiceException {
    	InputStream xml = null;

        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ActivityProcessTransitionParser p = new ActivityProcessTransitionParser(dest);

            parser.parse(xml, p);

            if (!p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if(xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public boolean getActivityProcessStates(List<OpencrxActivityProcessState> dest, String url)throws ApiServiceException {
    	InputStream xml = null;

        try {
        	xml = restClient.get(url, retryLogin, retryPassword);
        	
            SAXParser parser = getParser();

            ActivityProcessStateParser p = new ActivityProcessStateParser(dest);

            parser.parse(xml, p);

            if (!p.isResultSet())
                throw new ApiServiceException("Wrong rest answer.");

            return p.hasMore();

        } catch (Exception e) {
            throw new ApiServiceException(e);
        }finally{
            try {
            	if (xml!=null)
            		xml.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void executeGet(String url) throws IOException{
    	InputStream res = null;
    	try{
    		res = restClient.get(url, retryLogin, retryPassword);
    	}finally{
    		if (res != null)
    			res.close();
    	}
    }
    
    public void executeDelete(String url) throws IOException{
    	InputStream res = null;
    	try{
    		res = restClient.delete(url, retryLogin, retryPassword);
    	}finally{
    		if (res != null)
    			res.close();
    	}
    }
    
    public void executePost(String url, String data) throws IOException{
    	InputStream res = null;
    	try{
    		res = restClient.post(url, data, retryLogin, retryPassword);
    	}finally{
    		if (res != null)
    			res.close();
    	}
    }
    
    public void executePut(String url, String data) throws IOException{
    	InputStream res = null;
    	try{
    		res = restClient.put(url, data, retryLogin, retryPassword);
    	}finally{
    		if (res != null)
    			res.close();
    	}
    }


    public static String getBaseXri(String xri) {
        if (xri == null)
            return null;

        String[] arr = xri.split("/");
        if (arr.length > 0)
            return arr[arr.length - 1];
        else
            return null;
    }

    /**
     * Hash function for string. Ensures that result is greater than one (TASK_ID_NOT_SYNCED).
     * @param str
     * @return
     */
    public static long hash(String str){
        long ret = 0;

        for (char c : str.toCharArray())
            ret += ret * 37L + c;

        while (ret <= 0)
            ret += Long.MAX_VALUE;

        if (ret == 1)
            return Long.MAX_VALUE;

        return ret;

    }

    public static Date parseFromOpencrx(String opencrxDate) throws ParseException{
        SimpleDateFormat format = getOpencrxDateFormat();

        return format.parse(opencrxDate);
    }

    public static String formatAsProducteev(Date date) throws ParseException{
        SimpleDateFormat format = getProducteevDateFormat();

        return format.format(date);
    }

    public static String formatAsOpencrx(long millis){
        SimpleDateFormat format = getOpencrxDateFormat();

        return format.format(DateUtilities.unixtimeToDate(millis));
    }


    // ------------------------------------------------------------------------------------------------
    // helper methods
    // ------------------------------------------------------------------------------------------------

    private static SAXParser getParser() throws ParserConfigurationException, SAXException{
        if (xmlParser == null){
            xmlParser = factory.newSAXParser();
        }
        return xmlParser;
    }

    private static SimpleDateFormat getProducteevDateFormat() {
        if (opencrxToProducteevTimeFormatter == null){
            opencrxToProducteevTimeFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        }
        return opencrxToProducteevTimeFormatter;
    }

    private static SimpleDateFormat getOpencrxDateFormat() {
        if (opencrxTimeFormatter == null){
            opencrxTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);;
            opencrxTimeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return opencrxTimeFormatter;
    }

}
