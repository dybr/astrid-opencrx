/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package ru.otdelit.astrid.opencrx.api;

import android.content.Context;

import com.todoroo.andlib.data.ContentResolverDao;
import com.todoroo.astrid.data.Update;

/**
 * Data access object for accessing Astrid's {@link Update} table.
 *
 * @author Andrey Marchenko <igendou@gmail.com>
 *
 */
public class UpdateApiDao extends ContentResolverDao<Update>{

    public UpdateApiDao(Context context) {
        super(Update.class, context, Update.CONTENT_URI);
    }

}
