/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * https://github.com/CILEA/dspace-cris/wiki/License
 */
package org.dspace.app.webui.cris.metrics;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.dspace.app.cris.metrics.common.model.ConstantMetrics;
import org.dspace.app.cris.model.ACrisObject;
import org.dspace.app.cris.util.ICrisHomeProcessor;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.plugin.PluginException;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

public class MetricsCrisHomeProcessor<ACO extends ACrisObject> implements ICrisHomeProcessor<ACO> {
	private Logger log = Logger.getLogger(this.getClass());
	private List<Integer> rankingLevels;
	private List<String> metricTypes;
	private Class<ACO> clazz;
	private SearchService searchService;
	private MetricsProcessorConfigurator configurator;

	private static final String COLUMN_METRIC_COUNT = "metriccount";
	private static final String COLUMN_REMARK = "remark";
	private static final String COLUMN_ACQUISITION_TIME = "timestampcreated";
	
	private String QUERY_METRICS_YEAR = "select " + COLUMN_REMARK + ", " + COLUMN_METRIC_COUNT +", " +COLUMN_ACQUISITION_TIME+"  from cris_metrics where resourceid = ? AND resourcetypeid = ? AND "+COLUMN_ACQUISITION_TIME+" > ?::timestamp AND "+COLUMN_ACQUISITION_TIME+" < ?::timestamp AND metrictype = ? ORDER BY "+COLUMN_ACQUISITION_TIME+" DESC LIMIT 1;";
    private String QUERY_METRICS_YEAR_AVAILABLE = "select distinct extract(year from "+COLUMN_ACQUISITION_TIME+") as year from cris_metrics where resourceid = ? AND resourcetypeid = ? AND metrictype = ?;";
	
	@Override
	public void process(Context context, HttpServletRequest request, HttpServletResponse response, ACO item)
			throws PluginException, AuthorizeException {

	    Map<String, ItemMetricsDTO> metrics = new HashMap<String, ItemMetricsDTO>();
	    List<ItemMetricsDTO> metricsList = new ArrayList<ItemMetricsDTO>();
	    String field = ConstantMetrics.PREFIX_FIELD;
	    String yearString = request.getParameter("metricsyear");
	    if(StringUtils.isNotBlank(yearString) && !"all".equals(yearString)) {
	        int year = Integer.parseInt(yearString);
	        String from = year + "-01-01";
	        String to = (year+1) + "-01-01";
	        try
            {
                for (String t : metricTypes)
                {
                    ItemMetricsDTO dto = new ItemMetricsDTO();
                    dto.type = t;
                    dto.setFormatter(configurator.getFormatter(t));
                    
                    String metricfield = t;
                    String metriclast1 = t + "_last1";
                    String metriclast2 = t + "_last2";
                    String metricranking = t + "_ranking";
//                    String metrictime = t + "_time";
//                    String metricremark = t + "_remark";
                    
                    String[] tt = new String[] { metricfield,
                            metriclast1, metriclast2,
                            metricranking
                    };
                    
                    for (String ttt : tt)
                    {
                        TableRow row = DatabaseManager.querySingle(context,
                                QUERY_METRICS_YEAR, item.getID(),
                                item.getType(), from, to, ttt);
                        if (row != null)
                        {
                            if (ttt.equals(metricfield))
                            {
                                dto.counter = (Double) row
                                        .getDoubleColumn(COLUMN_METRIC_COUNT);
                                dto.moreLink = (String) row
                                        .getStringColumn(COLUMN_REMARK);
                                dto.time = (Date) row
                                        .getDateColumn(COLUMN_ACQUISITION_TIME);
                            }
                            else if (ttt.equals(metriclast1))
                            {
                                dto.last1 = (Double) row
                                        .getDoubleColumn(COLUMN_METRIC_COUNT);
                            }
                            else if (ttt.equals(metriclast2))
                            {
                                dto.last2 = (Double) row
                                        .getDoubleColumn(COLUMN_METRIC_COUNT);
                            }
                            else if (ttt.equals(metricranking))
                            {
                                dto.ranking = (Double) row
                                        .getDoubleColumn(COLUMN_METRIC_COUNT);
                            }

                            if (dto.ranking != null)
                            {
                                for (int lev : rankingLevels)
                                {
                                    if ((dto.ranking * 100) < lev)
                                    {
                                        dto.rankingLev = lev;
                                        break;
                                    }
                                }
                            }
                        }

                    }
                    metricsList.add(dto);
                }
            }
            catch (SQLException e)
            {
                log.error(LogManager.getHeader(context, "MetricsItemHomeProcessor", e.getMessage()), e);
            }
	    }
	    else {
    	    SolrQuery solrQuery = new SolrQuery();
    		
    		solrQuery.setQuery("search.uniqueid:"+ item.getType() + "-"+item.getID());
    		solrQuery.setRows(1);
            for (String t : metricTypes) {
    			solrQuery.addField(field+t);
    			solrQuery.addField(field+t+"_last1");
    			solrQuery.addField(field+t+"_last2");
    			solrQuery.addField(field+t+"_ranking");
    			solrQuery.addField(field+t+"_remark");
    			solrQuery.addField(field+t+"_time");
    		}
    		try {
    			QueryResponse resp = searchService.search(solrQuery);
    			if (resp.getResults().getNumFound() != 1) {
    				return;
    			}
    			SolrDocument doc = resp.getResults().get(0);
    			
    			for (String t : metricTypes) {	
    				ItemMetricsDTO dto = new ItemMetricsDTO();
    				dto.type=t;
    				dto.setFormatter(configurator.getFormatter(t));
    				dto.counter=(Double) doc.getFieldValue(field+t);
    				dto.last1=(Double) doc.getFieldValue(field+t+"_last1");
    				dto.last2=(Double) doc.getFieldValue(field+t+"_last2");;
    				dto.ranking=(Double) doc.getFieldValue(field+t+"_ranking");
    				dto.time=(Date) doc.getFieldValue(field+t+"_time");
    				if (dto.ranking != null) {
    					for (int lev : rankingLevels) {
    						if ((dto.ranking * 100) < lev) {
    							dto.rankingLev = lev;
    							break;
    						}
    					}
    				}
    				dto.moreLink=(String) doc.getFieldValue(field+t+"_remark");
    				metricsList.add(dto);
    			}
    
    		} catch (SearchServiceException e) {
    			log.error(LogManager.getHeader(context, "MetricsItemHomeProcessor", e.getMessage()), e);
    		}
	    }
	            
	    metrics = getMapFromList(metricsList);
	    
        Map<String, Object> extra = new HashMap<String, Object>();
        extra.put("metricTypes", metricTypes);
        extra.put("metrics", metrics);

        Map<String, List<Integer>> years = new HashMap<String, List<Integer>>();
        
        for (String ttt : metricTypes)
        {
            try
            {
                List<Integer> subYears = new ArrayList<Integer>();
                
                TableRowIterator tri = DatabaseManager.query(context,
                        QUERY_METRICS_YEAR_AVAILABLE, item.getID(),
                        item.getType(), ttt);
                if (tri != null)
                {
                    while (tri.hasNext())
                    {
                        TableRow row = tri.next();
                        if (row != null)
                        {
                            BigDecimal year = row.getNumericColumn("year");
                            subYears.add(year.intValue());
                        }
                    }
                }
                
                years.put(ttt, subYears);
            }
            catch (SQLException e)
            {
                log.error(LogManager.getHeader(context,
                        "MetricsItemHomeProcessor", e.getMessage()), e);
            }
        }
        extra.put("years", years);
        request.setAttribute("extra", extra);

	}

	private Map<String, ItemMetricsDTO> getMapFromList(List<ItemMetricsDTO> metricsList) {
		Map<String, ItemMetricsDTO> result = new HashMap<String, ItemMetricsDTO>();
		for (ItemMetricsDTO dto : metricsList) {
			result.put(dto.type, dto);
		}
		return result;
	}

    public List<Integer> getRankingLevels()
    {
        return rankingLevels;
    }

    public void setRankingLevels(List<Integer> rankingLevels)
    {
        this.rankingLevels = rankingLevels;
    }

    public List<String> getMetricTypes()
    {
        return metricTypes;
    }

    public void setMetricTypes(List<String> metricTypes)
    {
        this.metricTypes = metricTypes;
    }

    public Class<ACO> getClazz()
    {
        return clazz;
    }

    public void setClazz(Class<ACO> clazz)
    {
        this.clazz = clazz;
    }

    public SearchService getSearchService()
    {
        return searchService;
    }

    public void setSearchService(SearchService searchService)
    {
        this.searchService = searchService;
    }

    public void setConfigurator(MetricsProcessorConfigurator configurator)
    {
        this.configurator = configurator;
    }
    
}