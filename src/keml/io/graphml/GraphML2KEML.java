package keml.io.graphml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FilenameUtils;

import keml.Author;
import keml.Conversation;
import keml.ConversationPartner;
import keml.ITargetable;
import keml.Information;
import keml.InformationLink;
import keml.KemlFactory;
import keml.Message;
import keml.NewInformation;
import keml.PreKnowledge;
import keml.ReceiveMessage;
import keml.SendMessage;


import org.w3c.dom.*;
import org.xml.sax.SAXException;


public class GraphML2KEML {
	
	static KemlFactory factory = KemlFactory.eINSTANCE;
	
	public Conversation readFromPath (String path) throws ParserConfigurationException, FileNotFoundException, IOException, SAXException {
		
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc;
		try (FileInputStream is = new FileInputStream(path)) {
			doc = builder.parse(is);			
		}
		
		doc.getDocumentElement().normalize();
			
		return graph2keml(doc, FilenameUtils.getBaseName(path));	
	}
	
	private Conversation graph2keml(Document doc, String title) {
		
		Conversation conversation = factory.createConversation();
		conversation.setTitle(title);
		Author author = factory.createAuthor();
		conversation.setAuthor(author);
		
		HashMap<String, Object> kemlNodes = new HashMap<String, Object>();
		HashMap<String, NodeType> nodeTypes = new HashMap<String, NodeType>();
		
		//helper information: determine messages via positions, also group nodes and hence edges
		PositionalInformation authorPosition = null;	
		HashMap<String, PositionalInformation> conversationPartnerXs = new HashMap<String, PositionalInformation>(); // helper map for all conversation partners' positions	
		HashMap<String, PositionalInformation> potentialMessageXs = new HashMap<String, PositionalInformation>(); // helper map for all possible messages
		List<String> interrupts = new ArrayList<String>();
		
		HashMap<String, PositionalInformation> informationPositions = new HashMap<String, PositionalInformation>();
		HashMap<String, PositionalInformation> informationIsInstructionPositions = new HashMap<String, PositionalInformation>();
		HashMap<String, PositionalInformation> informationIsNoInstructionPositions = new HashMap<String, PositionalInformation>();
		
		HashMap<String, String> ignoreNodes = new HashMap<String, String>();
		
		
		// first work on nodes, fill the lists above:
		NodeList nodeList = doc.getElementsByTagName("node");

		for (int i = 0; i< nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			String id = node.getAttributes().getNamedItem("id").getNodeValue();

			
			NodeList data = node.getChildNodes();

			for (int j = 0; j < data.getLength(); j++) {
				Node cur = data.item(j);
				if (cur.getNodeName().equals("data") && cur.getAttributes().item(0).getNodeValue().equals("d6")) { 
					//only these data nodes hold the relevant properties
					// we can now distinguish by shape:
					NodeList children = cur.getChildNodes();
					for (int k = 0; k < children.getLength(); k++) {
						Node childNode = children.item(k);
						String nodeName = childNode.getNodeName();

						switch (nodeName) {
							case "#text": break;
							case "y:SVGNode": {
								// these nodes represent the conversation partners and the instruction icons on information
								// each one with a label forms a new conversation partner (except for Author)
								String label = GraphMLUtils.readLabel(childNode);
								if (!label.equals("")) {
									// this is a life line, determine Position
									PositionalInformation x = new PositionalInformation(childNode);
									if (label.equals("Author")) {
										nodeTypes.put(id, NodeType.AUTHOR);
										authorPosition = x;
									} else {
										nodeTypes.put(id, NodeType.CONVERSATION_PARTNER);										
										ConversationPartner p = factory.createConversationPartner();
										p.setName(label);
										kemlNodes.put(id,  p);
										conversationPartnerXs.put(id, x);
									}
								} else { //just icons on information (isInstruction = true) and the extra computer on the LLM
									// TODO maybe need because of edges pointing here rather than on the main part? Currently skip (ignoreNodes) 	
									ignoreNodes.put(id, id);
								}								
								break;
							}
							case "y:GenericNode": {
								// we need the pre-knowledge from it, that has <y:GenericNode configuration="com.yworks.bpmn.Artifact.withShadow">
								if (childNode.getAttributes().item(0).getNodeValue().equals("com.yworks.bpmn.Artifact.withShadow")) {
									nodeTypes.put(id, NodeType.PRE_KNOWLEDGE);
									String label = GraphMLUtils.readLabel(childNode);
									PreKnowledge pre = 	factory.createPreKnowledge();
									pre.setMessage(label);
									kemlNodes.put(id, pre);
									PositionalInformation pos = new PositionalInformation(childNode);
									// also store positions to find corresponding ! or person
									informationPositions.put(id, pos);
									author.getPreknowledge().add(pre);
								}
								// else it might be an interrupt:
								else if (childNode.getAttributes().item(0).getNodeValue().equals("com.yworks.bpmn.Gateway.withShadow")) {
									nodeTypes.put(id, NodeType.INTERRUPT);
									interrupts.add(id);
								}
								break;
							}
							case "y:ShapeNode": {
								String color = GraphMLUtils.readColor(childNode);
								PositionalInformation pos = new PositionalInformation(childNode);

								switch (color) {
									case "#FFFF99":  //light-yellow, used on information by WebBrowser
									case "#CCFFFF": { // light-blue, used on information by LLM
										String label = GraphMLUtils.readLabel(childNode);
										nodeTypes.put(id, NodeType.NEW_INFORMATION);
										NewInformation info = factory.createNewInformation();
										info.setMessage(label);
										kemlNodes.put(id, info);
										// also store positions to find corresponding ! or person
										informationPositions.put(id, pos);								
										break;
									}
									case "#99CC00": { //green, used on facts (!)
										nodeTypes.put(id, NodeType.NEW_INFORMATION);
										informationIsNoInstructionPositions.put(id, pos);								
										break;
									}
									case "#FFCC00": { // yellow, behind human icon (isInstruction = true)
										nodeTypes.put(id, NodeType.NEW_INFORMATION);
										informationIsInstructionPositions.put(id, pos);								
										break;
										
									}
									case "#C0C0C0": { //grey, used for messages
										//we need this to complete the edges, we will just model the messageSpecs on author explicitly but first put all into the MessageXs
										// also need y position to order them on the author
										nodeTypes.put(id, NodeType.MESSAGE);
										potentialMessageXs.put(id, pos);
										break;
									}
									case "#FFCC99": {  // NEW: orange, used for intermediate nodes
										nodeTypes.put(id, NodeType.INTERMEDIATE_NODE);
										ignoreNodes.put(id, id); // NEW: these nodes are just a workaround for graphml files so they are not used in KEML files
										break;
									}
									default: {
										throw new IllegalArgumentException("Unrecognized color: "+color);
									}
								}	
								break;
							}
							default: {
								throw new IllegalArgumentException("Unrecognized shape: "+nodeName);
							}
						}
					}		
				}
			}								
		}
		
		// nodeForwardList: HashMap<String, String> lookup for which real information node is used (we have 2 nodes form message + icon)
		Map<String, String> informationNodeForwardMap = createNodeForwardList(informationPositions, informationIsInstructionPositions, informationIsNoInstructionPositions, kemlNodes);
			
		// ************* edges ****************************
		NodeList edgeList = doc.getElementsByTagName("edge");
		List<GraphEdge> edges = IntStream.range(0, edgeList.getLength())
			    .mapToObj(edgeList::item)
			    .map(s -> new GraphEdge(s))
			    .collect(Collectors.toList());
		
		// now work on edges to separate them: we need those of the sequence diagram to arrange the messages and can already define all relations between information in a second method
		List<GraphEdge> sequenceDiagramEdges = new ArrayList<GraphEdge>();
		List<GraphEdge> informationConnection = new ArrayList<GraphEdge>();
		List<GraphEdge> informationINTInConnection = new ArrayList<GraphEdge>();  // NEW: list of incoming edges (intermediate nodes)
		List<GraphEdge> informationINTOutConnection = new ArrayList<GraphEdge>();  // NEW: list of outgoing edges (intermediate nodes)
		List<Map.Entry<GraphEdge, GraphEdge>> informationAAAConnection = new ArrayList<>(); // NEW: list (e1,e2) where e1 attacks e2
		List<GraphEdge> usedBy = new ArrayList<GraphEdge>();
		List<GraphEdge> generates = new ArrayList<GraphEdge>();
		edges.forEach(e -> 
		{
			NodeType src = nodeTypes.get(e.getSource());
			if (src == null)
				System.err.println("No type for source node " + e.getSource());
			NodeType targetType = nodeTypes.get(e.getTarget());
			if (targetType == null)
				System.err.println("No type for target node " + e.getTarget());
			switch (src) {
				case MESSAGE: {
					switch(targetType) {
						case MESSAGE: case INTERRUPT: {
							sequenceDiagramEdges.add(e);
							break;
						}
						case NEW_INFORMATION: {
							generates.add(e);
							break;
						}
						default: {
							throw new IllegalArgumentException("Node "+ e.getTarget() + " of type " + targetType + " not valid on edge from " +src);
						}
					}
					break;
				}
				case AUTHOR: case CONVERSATION_PARTNER: {
					if (nodeTypes.get(e.getTarget()) == NodeType.MESSAGE) {
						sequenceDiagramEdges.add(e);
					} else {
						throw new IllegalArgumentException("Node "+ e.getTarget() + " of type " + targetType + " not valid on edge from "+src);
					}
					break;
				}
				case NEW_INFORMATION: {
					switch(targetType) {
						case MESSAGE: {
							usedBy.add(e);
							break;
						}
						case NEW_INFORMATION: case PRE_KNOWLEDGE: {
							informationConnection.add(e);
							break;
						}
						case INTERMEDIATE_NODE: {  // NEW: save edge with intermediate node as target
							informationINTInConnection.add(e);
							break;
						}
						default: {
							throw new IllegalArgumentException("Node "+ e.getTarget() + " of type " + targetType + " not valid on edge from " +src);
						}
					}
					break;				
				}
				case INTERMEDIATE_NODE: {  // NEW: save edge with intermediate node as source
					informationINTOutConnection.add(e);
					switch(targetType) {
						case INTERMEDIATE_NODE: { // NEW: special case for edges that target edges and are targeted by edges
							informationINTInConnection.add(e);
						}
						default: {
							
						}
					}
					break;
				}
				case PRE_KNOWLEDGE: {
					switch(targetType) {
						case MESSAGE: {
							usedBy.add(e);
							break;
						}
						case NEW_INFORMATION: {
							informationConnection.add(e);
							break;
						}
						default:
							throw new IllegalArgumentException("Node "+ e.getTarget() + " of type " + targetType + " not valid on edge from "+src);
					}
					break;
				}
			}
		});
		
		// ***************** Sequence Diagram ********************************
		ArrayList<String> msgsInOrder = buildSequenceDiagram(conversation, authorPosition, conversationPartnerXs,
				kemlNodes, potentialMessageXs, sequenceDiagramEdges, interrupts);
		
		// TODO we could use them to save preKnowledge in order

		// ***************** Intermediate Nodes ********************** (NEW: connects edge that is divides by intermediate node and change target of recursive edge to the link)
		class Counter {    // counter that counts number of edges without arrow tips
		    int count = 0;
		}
		List<GraphEdge> isARecInformationConnection = new ArrayList<GraphEdge>(); // saves edges that target edges
		List<GraphEdge> isATargetedInformationConnection = new ArrayList<GraphEdge>(); // saves edges that are targeted by edges
		informationINTOutConnection.forEach(e2 -> {
			String e2Source = e2.getSource();
			Counter noneCounter = new Counter();
			informationINTInConnection.forEach(e1 -> {
				if (e1.getTarget().equals(e2Source)) {
					String arrowHead = e1.getInformationLinkTypeString();
					switch(arrowHead) {
						case "none": { // e2 and e1 are connected to on link in the KEML file, the edge must not have the same color or line style
							e2.setSource(e1.getSource());
							e2.setLabel(e2.getLabel().concat(e1.getLabel()));
							informationConnection.add(e2); 
							noneCounter.count++;
							break;
						}
						case "cross": {
							informationAAAConnection.add(new AbstractMap.SimpleEntry<>(e1, e2));
							isARecInformationConnection.add(e1); // save e1 as recursive edge
							isATargetedInformationConnection.add(e2); // save e1 as targeted edge
							break;
						}
						case "crows_foot_many": {
							informationAAAConnection.add(new AbstractMap.SimpleEntry<>(e1, e2));
							isARecInformationConnection.add(e1); // save e1 as recursive edge
							isATargetedInformationConnection.add(e2); // save e1 as targeted edge
							break;
						}
						default:
							throw new IllegalArgumentException("Not a common arrow tip: " + arrowHead);
					}
				}
			});
			if (noneCounter.count != 1)
				throw new IllegalArgumentException("There must be exactly 1 edge with no arrow tip!");
			else
				noneCounter.count = 0;
		});
		
		// ***************** Connecting information and sequence diagram ********
		addGeneratesAndRepeats(generates, informationNodeForwardMap, kemlNodes);
		usedBy.forEach(e -> {
			Information info = getInformationFromKeml(e.getSource(), informationNodeForwardMap, kemlNodes);
			SendMessage msg = (SendMessage) kemlNodes.get(e.getTarget());
			info.getIsUsedOn().add(msg);
		});
		
		// ***************** Information Connections **********************
		/*informationConnection.forEach(e -> {
			Information source = getInformationFromKeml(e.getSource(), informationNodeForwardMap, kemlNodes);
			Information target = getInformationFromKeml(e.getTarget(), informationNodeForwardMap, kemlNodes);
			InformationLink i = factory.createInformationLink();
			i.setLinkText(e.getLabel());
			i.setTarget(target);
			i.setType(e.getInformationLinkType());
			source.getCauses().add(i);
			informationAAAConnection.forEach(e2 -> {
				if (e.equals(e2.getValue())) {
					//AoAInformationLink i2 = factory.createAoAInformationLink();
					//i2.setLinkText(e2.getKey().getLabel());
					//System.out.println("2:"+e.getLabel());
					//i2.setAoALinkType(e2.getKey().getInformationLinkType());
					//i2.setTarget(i);
					System.out.println(i);
					//i.setTargetedBy(i2);
					Information source2 = getInformationFromKeml(e2.getKey().getSource(), informationNodeForwardMap, kemlNodes);
					//source2.getCausesAOA().add(i2);
					
					InformationLink i3 = factory.createInformationLink();
					i3.setLinkText(e2.getKey().getLabel());
					i3.setType(e2.getKey().getInformationLinkType());
					i3.setAttacks(i);
					source2.getCauses().add(i3);
				}
			});
		});*/
		
		// ***************** Information Connections ********************** (NEW: revised KEML edge creation)
		List<Map.Entry<GraphEdge, ITargetable>> informationConnectionMapGraphMl2KEML = new ArrayList<>(); // saves the KEML edge that matches the graphml edge
		List<GraphEdge> alreadyCreated = new ArrayList<GraphEdge>(); // saves all edges for which a KEML file has already been created
		informationConnection.forEach(e -> {
			if (alreadyCreated.contains(e)) { 
				// this is the special case of KEML edge creation where an recursive edge is targeted
				informationConnectionMapGraphMl2KEML.forEach(e3 -> {
					if (e.equals(e3.getKey())) {
						ITargetable i = e3.getValue();
						informationAAAConnection.forEach(e2 -> {
							if (e.equals(e2.getValue())) {
								Information source = getInformationFromKeml(e2.getKey().getSource(), informationNodeForwardMap, kemlNodes);
								ITargetable target = (ITargetable) i;
								InformationLink iRecursive = factory.createInformationLink();
								iRecursive.setLinkText(e2.getKey().getLabel());
								iRecursive.setType(e2.getKey().getInformationLinkType());
								iRecursive.setTarget(target);
								source.getCauses().add(iRecursive);
								alreadyCreated.add(e2.getKey());
							}
						});
					}
				});
			} else {
				// this is the standard case of KEML edge creation
				Information source = getInformationFromKeml(e.getSource(), informationNodeForwardMap, kemlNodes);
				ITargetable target = getTargetFromKeml(e.getTarget(), informationNodeForwardMap, kemlNodes);
				InformationLink i = factory.createInformationLink();
				i.setLinkText(e.getLabel());
				i.setTarget(target);
				i.setType(e.getInformationLinkType());
				source.getCauses().add(i);
				informationConnectionMapGraphMl2KEML.add(new AbstractMap.SimpleEntry<>(e, i));
				// this is the case of KEML edge creation where an edge is targeted
				informationAAAConnection.forEach(e2 -> {
					if (e.equals(e2.getValue())) {
						Information sourceRec = getInformationFromKeml(e2.getKey().getSource(), informationNodeForwardMap, kemlNodes);
						ITargetable targetRec = (ITargetable) i;
						InformationLink iRecursive = factory.createInformationLink();
						iRecursive.setLinkText(e2.getKey().getLabel());
						iRecursive.setType(e2.getKey().getInformationLinkType());
						iRecursive.setTarget(targetRec);
						sourceRec.getCauses().add(iRecursive);
						informationConnectionMapGraphMl2KEML.add(new AbstractMap.SimpleEntry<>(e2.getKey(), iRecursive));
						alreadyCreated.add(e2.getKey());
					}
				});
			}
		});
		
		System.out.println("Read "+ nodeList.getLength() + " nodes and " + edgeList.getLength() + " edges into a conversation with "
		+ kemlNodes.size() + " matching KEML nodes, including " + informationConnection.size()+ " information links.");
		System.out.println("Ignored "+ ignoreNodes.size() + " nodes.");
		
		return conversation;
	}
	
	private void addOrderedConversationPartners(Conversation conversation, HashMap<String, PositionalInformation> conversationPartnerXs,
			HashMap<String, Object> kemlNodes) {
		conversationPartnerXs.entrySet().stream()
		.sorted((s,t) -> Float.compare(s.getValue().getxLeft(),t.getValue().getxLeft()))
		.forEach(c -> {
			conversation.getConversationPartners().add((ConversationPartner) kemlNodes.get(c.getKey()));
		});
	}
	
	private void addGeneratesAndRepeats(List<GraphEdge> edges, Map<String, String> informationNodeForwardMap, Map<String, Object> kemlNodes) {
		edges.stream().collect(Collectors.groupingBy(GraphEdge::getTarget))
			.forEach(
				(target, elist) -> {
					//System.out.println(target + ": "+ elist);
					NewInformation info = (NewInformation) kemlNodes.get(informationNodeForwardMap.get(target)); // follow helper anyway (no preknowledge there)
					//now order messages to distinguish generates and repeats
					ArrayList<ReceiveMessage> receives = elist.stream().map(e -> {
						ReceiveMessage msg;
						try {
							msg = (ReceiveMessage) kemlNodes.get(e.getSource());
						} catch (ClassCastException ex) {
							System.err.println("Edge "+ e + " seems to start on wrong node " + e.getSource() + " (send not receive).");
							throw ex;
						}
						return msg;
					}).collect(Collectors.toCollection(ArrayList::new));
					ReceiveMessage min = receives.stream().min(Comparator.comparingInt(ReceiveMessage::getTiming)).get();
					receives.remove(min);
					min.getGenerates().add(info);
					receives.forEach(r -> {
						r.getRepeats().add(info);
					});
				}
			);
	}
	
	// needs to follow node forward map to get information and icon together, also not follow if the info is preknowledge
	// no longer needed i guess
	private Information getInformationFromKeml(String infoName, Map<String, String> informationNodeForwardMap, Map<String, Object> kemlNodes) {
		Information info = (Information) kemlNodes.get(infoName);			
		if (info == null) { // infoName is neither preknowledge, nor the real node -> need to follow helper map (it does not contain pre-knowledge)
			info = (Information) kemlNodes.get(informationNodeForwardMap.get(infoName));			
		}
		return info;
	}
	
	// NEW: revised function above for new meta model
	private ITargetable getTargetFromKeml(String targetName, Map<String, String> informationNodeForwardMap, Map<String, Object> kemlNodes) {
	    ITargetable target = (ITargetable) kemlNodes.get(targetName);
	    
	    if (target == null) { 
	        target = (ITargetable) kemlNodes.get(informationNodeForwardMap.get(targetName));
	    }
	    
	    return target;
	}
	
	private ArrayList<String> buildSequenceDiagram(Conversation conversation, PositionalInformation authorPosition,
			HashMap<String, PositionalInformation> conversationPartnerXs,
			HashMap<String, Object> kemlNodes, HashMap<String, PositionalInformation> potentialMessageXs,
			List<GraphEdge> edges, List<String> interrupts) {
		
		addOrderedConversationPartners(conversation, conversationPartnerXs, kemlNodes);
		
		Author author = conversation.getAuthor();
		// just work by position: all message Specs should be below their LifeLine in order
		ArrayList<String> authorMessagesInOrder = potentialMessageXs.entrySet().stream()
				.filter(s -> s.getValue().isOnLine(authorPosition))
				.sorted((s,t) -> Float.compare(s.getValue().getyHigh(),t.getValue().getyHigh()))
				.map(Map.Entry::getKey)
				.collect(Collectors 
                        .toCollection(ArrayList::new));
				
		// for all other lifelines create a map of node id to lifeline id 
		// TODO current code uses filter and is terribly inefficient if we ever have many conversation partners
		HashMap<String, String> lifeLineFinder = new HashMap<String, String>();
		conversationPartnerXs.entrySet().stream().forEach(partner -> {
			potentialMessageXs.entrySet().stream()
			.filter(s -> s.getValue().isOnLine(partner.getValue()))
			.forEach(v -> {
				lifeLineFinder.put(v.getKey(), partner.getKey());	
			});
		});		
		
		//now find messages (edges) that connect the author with another lifeline
		// we can first eliminate all that have no label to improve efficiency
		edges.removeIf(s -> s.getLabel().isEmpty());
				
		Message[] msgs = new Message[authorMessagesInOrder.size()];
		
		// now first work on interrupts as special cases:
		interrupts.forEach(interrupt -> {
			List<GraphEdge> interruptEdges = edges.stream().filter(edge -> edge.getTarget().equals(interrupt)).toList();
			if (interruptEdges.size()!=2)
				throw new IllegalArgumentException("Cannot parse interrupt "+interrupt+ ", it has "+interruptEdges.size()+" edges.");
			OptionalInt authorIndex = indexOnAuthor(interruptEdges.get(0).getSource(), authorMessagesInOrder);
			GraphEdge relevant;
			if (authorIndex.isPresent()) {
				relevant = interruptEdges.get(1);
			} else {
				authorIndex = indexOnAuthor(interruptEdges.get(1).getSource(), authorMessagesInOrder);
				relevant = interruptEdges.get(0);
			}
			int index = authorIndex.getAsInt();		
			ReceiveMessage msg = factory.createReceiveMessage();
			msg.setIsInterrupted(true);
			msg.setContent(relevant.getLabel());
			msg.setTiming(index);
			msg.setCounterPart((ConversationPartner) kemlNodes.get(lifeLineFinder.get(relevant.getSource())));
			kemlNodes.put(authorMessagesInOrder.get(index), msg);
			msgs[index] = msg;	
		});

		edges.forEach(e -> {
			OptionalInt srcIndexOnAuth = indexOnAuthor(e.getSource(), authorMessagesInOrder);
			if (srcIndexOnAuth.isPresent()) {
				int index = srcIndexOnAuth.getAsInt();
				String partnerId = lifeLineFinder.get(e.getTarget());
				if (partnerId != null) {
					//create a send message
					SendMessage msg = factory.createSendMessage();
					msg.setContent(e.getLabel());
					msg.setTiming(index);
					msg.setCounterPart((ConversationPartner) kemlNodes.get(partnerId));
					kemlNodes.put(authorMessagesInOrder.get(index), msg);
					msgs[index] = msg;
				}
			} else {
				OptionalInt targetIndexOnAuth = indexOnAuthor(e.getTarget(), authorMessagesInOrder);
				if (targetIndexOnAuth.isPresent()) {
					int index = targetIndexOnAuth.getAsInt();
					String partnerId = lifeLineFinder.get(e.getSource());
					if (partnerId != null) {
						//create a receive message
						ReceiveMessage msg = factory.createReceiveMessage();
						msg.setContent(e.getLabel());
						msg.setTiming(index);
						msg.setCounterPart((ConversationPartner) kemlNodes.get(partnerId));
						kemlNodes.put(authorMessagesInOrder.get(index), msg);
						msgs[index] = msg;
					}
				}
			}
		});
		//now finally insert all messages in order
		author.getMessages().addAll(Arrays.asList(msgs));
		
		return authorMessagesInOrder;
	}
	
	private static OptionalInt indexOnAuthor(String elem, List<String> authorMessagesInOrder) {
		return IntStream.range(0, authorMessagesInOrder.size())
				   .filter(i -> authorMessagesInOrder.get(i).equals(elem))
				   .findFirst();
	}
		
	// works on the knowledge part of the graph to unite the information (with text) and its type (isInstruction)
	// also sets the isInstruction flag on the information
	private Map<String, String> createNodeForwardList(
			HashMap<String, PositionalInformation> informationPositions,
			HashMap<String, PositionalInformation> informationIsInstructionPositions,
			HashMap<String, PositionalInformation> informationIsNoInstructionPositions,
			HashMap<String, Object> kemlNodes) {
		
		if (informationPositions.size() != informationIsInstructionPositions.size() + informationIsNoInstructionPositions.size()) {
			throw new IllegalArgumentException("Sizes do not match!");
		}
		Map<String, String> forwardList = informationPositions.keySet().stream()
				.collect(Collectors.toMap(s -> s, s -> s)); // each information forwards to itself
			
		informationPositions.forEach((str, pos)-> {
			boolean matched = findMatchForInformation(str, pos, informationIsInstructionPositions, true, forwardList, kemlNodes);
			if (!matched) {
				boolean nowMatched = findMatchForInformation(str, pos, informationIsNoInstructionPositions, false, forwardList, kemlNodes);
				if (!nowMatched)
					throw new IllegalArgumentException("No match for information node "+str + " with: "+kemlNodes.get(str).toString());
			}	
		});
		return forwardList;
	}
	
	private boolean findMatchForInformation(String str, PositionalInformation pos, HashMap<String, PositionalInformation>posToMatch, boolean isInstr, Map<String, String>forwardList, HashMap<String,Object>kemlNodes ) {
		for (Map.Entry<String, PositionalInformation> e: posToMatch.entrySet()) {
			//type is on the right, so information (xr) and left side of pos (xl) match
			if (e.getValue().touchesOnRight(pos)) {
				forwardList.put(e.getKey(), str);
				Information info = (Information) kemlNodes.get(str);
				info.setIsInstruction(isInstr);
				return true;
			}
		}
		return false;
	}

}
