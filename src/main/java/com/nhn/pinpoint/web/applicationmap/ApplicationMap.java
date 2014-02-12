package com.nhn.pinpoint.web.applicationmap;

import java.util.*;

import com.nhn.pinpoint.common.ServiceType;
import com.nhn.pinpoint.web.applicationmap.rawdata.ResponseHistogram;
import com.nhn.pinpoint.web.dao.MapResponseDao;
import com.nhn.pinpoint.web.service.NodeId;
import com.nhn.pinpoint.web.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Node map
 * 
 * @author netspider
 * @author emeroad
 */
public class ApplicationMap {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NodeList nodeList = new NodeList();
    private final LinkList linkList = new LinkList();

	private final Set<String> applicationNames = new HashSet<String>();

	private TimeSeriesStore timeSeriesStore;
	
	ApplicationMap() {
	}


	public List<Node> getNodes() {
		return this.nodeList.getNodeList();
	}

	public List<Link> getLinks() {
		return this.linkList.getLinks();
	}

	void indexingNode() {
        this.nodeList.markSequence();
	}

	Node findApplication(NodeId applicationId) {
        return this.nodeList.find(applicationId);
	}

    void addNode(List<Node> nodeList) {
        for (Node node : nodeList) {
            this.addNodeName(node);
        }
        this.nodeList.buildApplication(nodeList);
    }

	void addNodeName(Node node) {
		if (!node.getServiceType().isRpcClient()) {
			applicationNames.add(node.getApplicationName());
		}

	}

    void addLink(List<Link> relationList) {
        linkList.buildLink(relationList);
    }


	public TimeSeriesStore getTimeSeriesStore() {
		return timeSeriesStore;
	}

	public void setTimeSeriesStore(TimeSeriesStore timeSeriesStore) {
		this.timeSeriesStore = timeSeriesStore;
	}

    public void buildNode() {
        this.nodeList.build();
    }

    public boolean containsApplicationName(String applicationName) {
        return applicationNames.contains(applicationName);
    }

    public void appendResponseTime(Range range, MapResponseDao mapResponseDao) {
        List<Node> nodes = this.nodeList.getNodeList();
        for (Node node : nodes) {
            if (node.getServiceType().isWas()) {
                // was일 경우 자신의 response 히스토그램을 조회하여 채운다.
                final Application application = new Application(node.getApplicationName(), node.getServiceType());
                final List<RawResponseTime> responseHistogram = mapResponseDao.selectResponseTime(application, range);
                ResponseHistogramSummary histogramSummary = createHistogramSummary(application, responseHistogram);
                node.setResponseHistogramSummary(histogramSummary);
            } else if(node.getServiceType().isTerminal() || node.getServiceType().isUnknown()) {
                // 터미널 노드인경우, 자신을 가리키는 link값을 합하여 histogram을 생성한다.
                Application nodeApplication = new Application(node.getApplicationName(), node.getServiceType());
                final ResponseHistogramSummary summary = new ResponseHistogramSummary(nodeApplication);

                List<Link> linkList = this.linkList.getLinks();
                for (Link link : linkList) {
                    com.nhn.pinpoint.web.applicationmap.Node toNode = link.getTo();
                    String applicationName = toNode.getApplicationName();
                    ServiceType serviceType = toNode.getServiceType();
                    Application destination = new Application(applicationName, serviceType);
                    // destnation이 자신을 가리킨다면 데이터를 머지함.
                    if (nodeApplication.equals(destination)) {
                        ResponseHistogram linkHistogram = link.getHistogram();
//                        summary.addTotal(linkHistogram);
                        summary.addLinkHistogram(linkHistogram);
                    }
                }
                node.setResponseHistogramSummary(summary);
            } else if(node.getServiceType().isUser()) {
                // User노드인 경우 source 링크를 찾아 histogram을 생성한다.
                Application nodeApplication = new Application(node.getApplicationName(), node.getServiceType());
                final ResponseHistogramSummary summary = new ResponseHistogramSummary(nodeApplication);

                List<Link> linkList = this.linkList.getLinks();
                for (Link link : linkList) {
                    com.nhn.pinpoint.web.applicationmap.Node fromNode = link.getFrom();
                    String applicationName = fromNode.getApplicationName();
                    ServiceType serviceType = fromNode.getServiceType();
                    Application source = new Application(applicationName, serviceType);
                    // destnation이 자신을 가리킨다면 데이터를 머지함.
                    if (nodeApplication.equals(source)) {
                        ResponseHistogram linkHistogram = link.getHistogram();
//                        summary.addTotal(linkHistogram);
                        summary.addLinkHistogram(linkHistogram);
                    }
                }
                node.setResponseHistogramSummary(summary);
            } else {
                // 그냥 데미 데이터
                Application nodeApplication = new Application(node.getApplicationName(), node.getServiceType());
                ResponseHistogramSummary dummy = new ResponseHistogramSummary(nodeApplication);
                node.setResponseHistogramSummary(dummy);
            }

        }

    }

    private ResponseHistogramSummary createHistogramSummary(Application application, List<RawResponseTime> responseHistogram) {
        final ResponseHistogramSummary summary = new ResponseHistogramSummary(application);
        for (RawResponseTime rawResponseTime : responseHistogram) {
            final List<ResponseHistogram> responseHistogramList = rawResponseTime.getResponseHistogramList();
            for (ResponseHistogram histogram : responseHistogramList) {
                summary.addTotal(histogram);
            }
        }
        return summary;
    }
}
