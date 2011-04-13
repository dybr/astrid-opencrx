package ru.otdelit.astrid.opencrx;

import com.todoroo.andlib.utility.Preferences;
import com.todoroo.astrid.sync.SyncProviderUtilities;

/**
 * Displays synchronization preferences and an action panel so users can
 * initiate actions from the menu. <br />
 *
 * Adapted from Producteev plugin by timsu
 *
 * @author Andrey Marchenko <igendou@gmail.com>
 */
public class OpencrxUtilities extends SyncProviderUtilities {

    /** add-on identifier */
    public static final String IDENTIFIER = "crx"; //$NON-NLS-1$

    public static final OpencrxUtilities INSTANCE = new OpencrxUtilities();

    /** Notification Manager id for sync notifications */
    public static final int NOTIFICATION_SYNC = -1;
    
   /** setting for creator to not synchronize */
    public static final long CREATOR_NO_SYNC = -1;

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public int getSyncIntervalKey() {
        return R.string.opencrx_PPr_interval_key;
    }

    // --- opencrx-specific preferences

    public static final String PREF_SERVER_LAST_SYNC = IDENTIFIER + "_last_server"; //$NON-NLS-1$

    public static final String PREF_SERVER_LAST_NOTIFICATION = IDENTIFIER + "_last_notification"; //$NON-NLS-1$

    public static final String PREF_SERVER_LAST_ACTIVITY = IDENTIFIER + "_last_activity"; //$NON-NLS-1$

    /** OpenCRX logged user's contact id */
    public static final String PREF_USER_ID = IDENTIFIER + "_userid"; //$NON-NLS-1$

    /** OpenCRX logged user's resource id */
    public static final String PREF_RESOURCE_ID = IDENTIFIER + "_resourceid"; //$NON-NLS-1$

    /**
     * Gets default creator from setting
     * @return CREATOR_NO_SYNC if should not sync, otherwise remote id
     */
    public long getDefaultCreator() {

        String defCreatorString = Preferences.getStringValue(R.string.opencrx_PPr_defaultcreator_key);

        long defaultCreatorId = CREATOR_NO_SYNC ;
        try{
            defaultCreatorId = Long.parseLong(defCreatorString);
        }catch(Exception ex){
            defaultCreatorId = CREATOR_NO_SYNC;
        }
        return defaultCreatorId;
    }

    public String getHostAddress(){
        return Preferences.getStringValue(R.string.opencrx_PPr_host_key);
    }

    public String getSegment(){
        return Preferences.getStringValue(R.string.opencrx_PPr_segment_key);
    }

    private OpencrxUtilities() {
        // prevent instantiation
    }

}