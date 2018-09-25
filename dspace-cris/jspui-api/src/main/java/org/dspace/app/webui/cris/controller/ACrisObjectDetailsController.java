/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * https://github.com/CILEA/dspace-cris/wiki/License
 */
package org.dspace.app.webui.cris.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.cris.model.ACrisObject;
import org.dspace.app.cris.model.OrganizationUnit;
import org.dspace.app.cris.util.ICrisHomeProcessor;
import org.dspace.app.webui.cris.metrics.ItemMetricsDTO;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.plugin.PluginException;

import it.cilea.osd.jdyna.model.AnagraficaSupport;
import it.cilea.osd.jdyna.model.Containable;
import it.cilea.osd.jdyna.model.PropertiesDefinition;
import it.cilea.osd.jdyna.model.Property;
import it.cilea.osd.jdyna.web.IPropertyHolder;
import it.cilea.osd.jdyna.web.Tab;
import it.cilea.osd.jdyna.web.controller.SimpleDynaController;

/**
 * Abstract class used to perform common code for the entity DSpace-CRIS object controller
 */
public abstract class ACrisObjectDetailsController<A extends AnagraficaSupport<P, TP>, P extends Property<TP>, TP extends PropertiesDefinition, H extends IPropertyHolder<Containable>, T extends Tab<H>>
        extends
        SimpleDynaController<A, P, TP, H, T>
{

    public ACrisObjectDetailsController(
            Class<A> anagraficaObjectClass)
            throws InstantiationException, IllegalAccessException
    {
        super(anagraficaObjectClass);
    }
    
    public ACrisObjectDetailsController(
            Class<A> anagraficaObjectClass,
            Class<TP> classTP)
            throws InstantiationException, IllegalAccessException
    {
        super(anagraficaObjectClass, classTP);
    }

    public ACrisObjectDetailsController(
            Class<A> anagraficaObjectClass,
            Class<TP> classTP, Class<T> classT, Class<H> classH)
            throws InstantiationException, IllegalAccessException
    {
        super(anagraficaObjectClass, classTP, classT, classH);
    }

    public <T extends ACrisObject> void addMetricsInformationToRequest(HttpServletRequest request,
            HttpServletResponse response, T ou, Context context, List<ICrisHomeProcessor<T>> processors)
            throws PluginException, AuthorizeException
    {
        Map<String, Object> extraTotal = new HashMap<String, Object>();
        Map<String, ItemMetricsDTO> metricsTotal = new HashMap<String, ItemMetricsDTO>();
        List<String> metricsTypeTotal = new ArrayList<String>();
        Set<Integer> yearsTotal = new HashSet<Integer>();
        for (ICrisHomeProcessor processor : processors)
        {
            if (OrganizationUnit.class.isAssignableFrom(processor.getClazz()))
            {
                processor.process(context, request, response, ou);
                Map<String, Object> extra = (Map<String, Object>)request.getAttribute("extra");
                if(extra!=null && !extra.isEmpty()) {
                    Map<String, ItemMetricsDTO> metrics = (Map<String, ItemMetricsDTO>)extra.get("metrics");
                    List<String> metricTypes = (List<String>)extra.get("metricTypes");
                    Map<String, List<Integer>> years = (Map<String, List<Integer>>)extra.get("years");
                    if(metrics!=null && !metrics.isEmpty()) {
                        metricsTotal.putAll(metrics);
                    }
                    if(metricTypes!=null && !metricTypes.isEmpty()) {
                        metricsTypeTotal.addAll(metricTypes);
                    }
                    if(years!=null && !years.isEmpty()) {
                        for(String metric : metricsTypeTotal) {
                            if(years.containsKey(metric)) {
                                yearsTotal.addAll(years.get(metric));
                            }
                        }
                    }
                }
            }
        }
        extraTotal.put("metricTypes", metricsTypeTotal);
        extraTotal.put("metrics", metricsTotal);
        extraTotal.put("years", yearsTotal);
        request.setAttribute("extra", extraTotal);
    }

}
