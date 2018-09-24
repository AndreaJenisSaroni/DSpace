/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.webui.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.app.bulkedit.BulkEditChange;
import org.dspace.app.bulkedit.DSpaceCSV;
import org.dspace.app.bulkedit.MetadataExport;
import org.dspace.app.bulkedit.MetadataImport;
import org.dspace.app.webui.util.JSPManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.ItemIterator;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;

/**
 * Servlet to clone metadata
 */
public class MetadataCloneServlet extends DSpaceServlet
{
    /** log4j category */
    private static Logger log = Logger.getLogger(MetadataCloneServlet.class);

    @Override
    protected void doDSGet(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException
    {     
        doDSPost(context, request, response);
    }
    
    /**
     * Respond to a post request
     *
     * @param context a DSpace Context object
     * @param request the HTTP request
     * @param response the HTTP response
     *
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    protected void doDSPost(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        // Get the handle requested for the export
        String handle = request.getParameter("handle");
        MetadataExport exporter = null;
        MetadataImport importer = null;
        if (handle != null)
        {
            context.turnOffAuthorisationSystem();
            context.turnOffItemWrapper();
            log.info(LogManager.getHeader(context, "metadataclone", "clone_handle:" + handle));
            DSpaceObject thing = HandleManager.resolveToObject(context, handle);
            if (thing != null)
            {
                if (thing.getType() == Constants.ITEM)
                {
                    List<Integer> item = new ArrayList<Integer>();
                    item.add(thing.getID());
                    exporter = new MetadataExport(context, new ItemIterator(context, item), false);
                }
                else 
                {
                    log.info("Operation not supported for type " + thing.getType() + " handle:" + handle);
                    JSPManager.showIntegrityError(request, response);                    
                }

                if (exporter != null)
                {
                    // Perform the export
                    DSpaceCSV csv = exporter.export();
                    importer = new MetadataImport(context, csv);
                    try
                    {
                        importer.runImport(true, true, false, false, true);
                    }
                    catch (Exception e)
                    {
                        JSPManager.showInternalError(request, response);
                    }
                    response.sendRedirect(request.getContextPath() + "/mydspace");
                    return;
                }
            }
            context.restoreAuthSystemState();
            context.restoreItemWrapperState();
        }

        // Something has gone wrong
        JSPManager.showIntegrityError(request, response);
    }
}