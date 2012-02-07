/**
 * 
 */
package de.hpi.bpt.process.bpmn;

import de.hpi.bpt.process.ControlFlow;
import de.hpi.bpt.process.FlowNode;
import de.hpi.bpt.process.IProcessModel;
import de.hpi.bpt.process.NonFlowNode;

/**
 * Interface for Bpmn class
 * @author Cindy F�hnrich
 *
 */
public interface IBpmn extends IProcessModel<ControlFlow<FlowNode>, FlowNode, NonFlowNode> {

	/**
	 * adds control/sequence flow to the process model
	 * @param from FlowNode
	 * @param to FlowNode
	 * @param condition for sequence flow to be executed
	 * @param defaultFlow whether this is a default conditional sequence flow
	 */
	public BpmnControlFlow<FlowNode> addControlFlow(FlowNode from, FlowNode to, String condition, boolean defaultFlow);
	
	/**
	 * adds control/sequence flow to the process model
	 * @param from FlowNode
	 * @param to FlowNode
	 * @param defaultFlow whether this is a default conditional sequence flow
	 */
	public BpmnControlFlow<FlowNode> addControlFlow(FlowNode from, FlowNode to, boolean defaultFlow);
	
	/**
	 * adds control/sequence flow to the process model
	 * @param from FlowNode
	 * @param to FlowNode
	 * @param condition for sequence flow to be executed
	 */
	public BpmnControlFlow<FlowNode> addControlFlow(FlowNode from, FlowNode to, String condition);
	
	/**
	 * adds an already created control flow to the container. necessary for control flows of subprocesses
	 * @param flow
	 */
	public void addControlFlow(BpmnControlFlow<FlowNode> flow);
	
	/**
	 * adds message flow to the process model
	 * @param from Node
	 * @param to Node
	 */
	public BpmnMessageFlow addMessageFlow(Object from, Object to);
	
	/**
	 * adds an already created message flow to the container. necessary for message flows of subprocesses
	 * @param flow
	 */
	public void addMessageFlow(BpmnMessageFlow flow);
	
}
