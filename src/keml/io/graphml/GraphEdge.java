package keml.io.graphml;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import keml.InformationLinkType;

public class GraphEdge {
	
	private String id;
	private String source;
	private String target;
	private String label;
	private InformationLinkType informationLinkType; //only used on InformationLink edges

	public GraphEdge(String id, String sourceNodeId, String targetNodeId, String label, InformationLinkType informationLinkType) {
		super();
		this.id = id;
		this.source = sourceNodeId;
		this.target = targetNodeId;
		this.label = label;
		this.informationLinkType = informationLinkType;
	}
		
	// parse from e.g.  <edge id="e94" source="n104" target="n105">
	public GraphEdge(Node node) {	
		Element e = (Element) node;
		this.id = e.getAttributes().getNamedItem("id").getNodeValue();
		this.source = e.getAttributes().getNamedItem("source").getNodeValue();
		this.target = e.getAttributes().getNamedItem("target").getNodeValue();
		this.label = GraphMLUtils.readLabel(e, "y:EdgeLabel");
		this.informationLinkType = determineInformationLinkType(e);		
	}
	
	private static InformationLinkType determineInformationLinkType(Element e) {
		String targetShape = arrowHead(e);
		
		switch (targetShape) {
			case "white_circle": case "transparent_circle": case "circle": return InformationLinkType.SUPPLEMENT;
			case "crows_foot_many": {
				if (isDashed(e))
					return InformationLinkType.SUPPORT;
				else
					return InformationLinkType.STRONG_SUPPORT; 
			}
			case "cross": {
				 if (isDashed(e))
					return InformationLinkType.ATTACK;
				else
					return InformationLinkType.STRONG_ATTACK;
			}
			case "concave": { // --->|
				if (arrowTail(e).equals("dash")) // -|----
					return InformationLinkType.SNEGATED_IMPLICATION; // S for source (i.e., source is being negated)
				else 
					return InformationLinkType.IMPLICATION; // nothing is being negated
			}
			case "dash": // ----|-
				return InformationLinkType.TNEGATED_IMPLICATION; // T for target (i.e., target is being negated)
			case "standard": case "none": return null;
			default: {
				System.err.println(targetShape);
				return null;	
			}
		}	
	}
	
	private static boolean isDashed(Element e) {	
		NamedNodeMap style = e.getElementsByTagName("y:LineStyle").item(0).getAttributes();
		return style.getNamedItem("type").getNodeValue().equals("dashed");
	}
	
	private static String arrowHead(Element e) {	
		NamedNodeMap style = e.getElementsByTagName("y:Arrows").item(0).getAttributes();
		return style.getNamedItem("target").getNodeValue();
	}
	
	/**
	 * Method to fetch the type of arrow used on the edge's tail
	 * @param e Edge element
	 * @return type of arrow tail as String
	 */
	private static String arrowTail(Element e) {	
		NamedNodeMap style = e.getElementsByTagName("y:Arrows").item(0).getAttributes();
		return style.getNamedItem("source").getNodeValue();
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public String getTarget() {
		return target;
	}
	public void setTarget(String target) {
		this.target = target;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public InformationLinkType getInformationLinkType() {
		return informationLinkType;
	}
	@Override
	public String toString() {
		return "GraphEdge [id=" + id + ", source=" + source + ", target=" + target + ", label=" + label + ", informationLinkType " + informationLinkType + "]";
	}
}
