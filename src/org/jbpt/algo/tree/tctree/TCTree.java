package org.jbpt.algo.tree.tctree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.jbpt.graph.abs.AbstractTree;
import org.jbpt.graph.abs.IEdge;
import org.jbpt.graph.abs.IGraph;
import org.jbpt.hypergraph.abs.IVertex;

/**
 * This class takes a biconnected graph and decomposes it into triconnected components.
 * 
 *  @assumption A given graph is biconnected.
 * 
 * This implementation is an adaption of the algorithm implemented by Martin Mader. 
 * The general process of this decomposition is described in his master's thesis.
 * 
 * @author Martin Mader
 * @author Artem Polyvyanyy
 * @author Christian Wiggert
 *
 * @param <E> Edge template.
 * @param <V> Vertex template.
 */
public class TCTree<E extends IEdge<V>, V extends IVertex> extends AbstractTree<TCTreeNode<E,V>> {
	// debug output control
	// TODO: remove this
	private static boolean showDebugInformation = false;
	
	protected IGraph<E,V> graph;
	
	protected E backEdge;
	
	public TCTree(IGraph<E,V> graph) {
		if (graph==null) return;
		if (graph.getEdges().isEmpty()) return;
		
		this.graph = graph;
		this.backEdge = graph.getEdges().iterator().next(); 
		
		this.construct();
	}

	public TCTree(IGraph<E,V> graph, E backEdge) {
		if (graph==null) return;
		if (!graph.contains(backEdge)) return;
		
		this.graph = graph;
		this.backEdge = backEdge;
		
		this.construct();
	}
	
	private void construct() {
		Vector<EdgeList<E,V>> components = new Vector<EdgeList<E,V>>();
		
		EdgeMap<E,V> virtualEdgeMap = this.createEdgeMap(this.graph); 
		virtualEdgeMap.initialiseWithFalse();
		virtualEdgeMap.put(backEdge,true);
		EdgeMap<E,V> assignedVirtEdgeMap = this.createEdgeMap(this.graph);
		EdgeMap<E,V> isHiddenMap = this.createEdgeMap(this.graph);
		isHiddenMap.initialiseWithFalse();
		
		MetaInfoContainer meta = new MetaInfoContainer();
		meta.setMetaInfo(MetaInfo.VIRTUAL_EDGES, virtualEdgeMap);
		meta.setMetaInfo(MetaInfo.ASSIGNED_VIRTUAL_EDGES, assignedVirtEdgeMap);
		meta.setMetaInfo(MetaInfo.HIDDEN_EDGES, isHiddenMap);
		
		// discover triconnected components
		TCSkeleton<E,V> mainSkeleton = new TCSkeleton<E,V>(this.graph);
		this.splitOffInitialMultipleEdges(mainSkeleton,components,virtualEdgeMap,assignedVirtEdgeMap,isHiddenMap);
		this.findSplitComponents(mainSkeleton,components,virtualEdgeMap,assignedVirtEdgeMap,isHiddenMap,meta,backEdge.getV1());
		
		// construct TCTreeNodes and Skeletons from components 
		for (EdgeList<E,V> el : components) {
			TCTreeNode<E,V> node = new TCTreeNode<E,V>();
			for (E edge : el) {
				if (virtualEdgeMap.getBool(edge))
					node.skeleton.addVirtualEdge(edge.getV1(),edge.getV2());
				else
					node.skeleton.addEdge(edge.getV1(),edge.getV2());
			}
			this.addVertex(node);
		}

		// classify triconnected components into polygons, bonds, and rigids
		this.classifyComponents();
		
		// merge bonds and polygons
		// TODO: implement
		this.mergePolygonsAndBonds();
		
		// construct tree of components
		this.constructTree();
	}

	private void mergePolygonsAndBonds() {
		
	}
	
	/**
	 * TODO: get rid of hash
	 */
	private void constructTree() {
		// construct index
		Map<Integer,TCTreeNode<E,V>> ve2nodes = new HashMap<Integer,TCTreeNode<E,V>>();
		TCTreeNode<E,V> tobeRoot = null;
		for (TCTreeNode<E,V> node : this.getVertices()) {
			if (tobeRoot == null) {
				Collection<E> edges = node.getSkeleton().getEdges(this.backEdge.getV1(),this.backEdge.getV2());
				if (edges.size()==1 && !node.getSkeleton().isVirtual(edges.iterator().next()))
					tobeRoot = node;	
			}
			
			for (E e : node.getSkeleton().getVirtualEdges()) {
				Integer hash = e.getV1().hashCode() + e.getV2().hashCode();
				if (ve2nodes.get(hash)==null) {
					ve2nodes.put(hash,node);
				}
				else
					this.addEdge(node,ve2nodes.get(hash));
			}
		}
		
		this.reRoot(tobeRoot);
	}

	/**
	 * Classify triconnected components into polygons, bonds, and rigids
	 */
	private void classifyComponents() {
		int Pc, Bc, Rc;
		Pc = Bc = Rc = 0;
		
		for (TCTreeNode<E,V> n : this.getVertices()) {
			if (n.getSkeleton().countVertices()==2) { 
				n.setType(TCType.B); 
				n.setName("B" + Bc++); 
				continue;
			}
			
			boolean isPolygon = true;
			Iterator<V> vs = n.getSkeleton().getVertices().iterator();
			while (vs.hasNext()) {
				V v = vs.next();
				if (n.getSkeleton().getEdges(v).size()!=2) {
					isPolygon = false;
					break;
				}
			}
			
			if (isPolygon) {
				n.setType(TCType.P);
				n.setName("P" + Pc++);
			}
			else {
				n.setType(TCType.R);
				n.setName("R" + Rc++);
			}
		}
	}
	
	/**
	 * Runs the different DFS algorithms and creates the triconnected components based on the given graph and maps.
	 */
	@SuppressWarnings("unchecked")
	protected void findSplitComponents(IGraph<E,V> graph,
			Vector<EdgeList<E,V>> components, EdgeMap<E,V> virtEdgeMap,
			EdgeMap<E,V> assignedVirtEdgeMap, EdgeMap<E,V> isHiddenMap,
			MetaInfoContainer meta, V root) {
		// initial adjacency map
		NodeMap<V> adjMap = this.createNodeMap(graph);
		for (V v:graph.getVertices()){
			EdgeList<E,V> adj = new EdgeList<E,V>();
			for (E e:graph.getEdges(v)){
				adj.add(e);
			}
			adjMap.put(v, adj);
		}
		meta.setMetaInfo(MetaInfo.DFS_ADJ_LISTS, adjMap);
		// first DFS -- calculate lowpoint information
		LowAndDescDFS<E,V> dfs1 = new LowAndDescDFS<E,V>(graph, meta, adjMap);
		dfs1.start(root);
		
		// debug
		if(showDebugInformation) {
			System.out.println("\nDFS status after first DFS...");
			for (V n:graph.getVertices()) {
				System.out.println("Node "+n+": "+
						" DFSNum " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NUM)).getInt(n) +
						" CplNum " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_COMPL_NUM)).getInt(n) +
						" State " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NODE_STATE)).getInt(n) +
						" Low1Num " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT1_NUM)).getInt(n) +
						" Low2Num " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT2_NUM)).getInt(n) +
						" NumDesc " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NUM_DESC)).getInt(n) +
						" Parent " + ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_PARENT)).get(n)
				);
				
			}
			for (E e:graph.getEdges()) {
				System.out.println("Edge " + e + ": " +
						" startsPath " + ((EdgeMap<E,V>) meta.getMetaInfo(MetaInfo.DFS_STARTS_NEW_PATH)).get(e));
			}
		}
		// order adjacency lists according to low-point values
		NodeMap<V> orderedAdjMap = orderAdjLists(graph, meta);
		
		NodeMap<V> copiedOrderedAdjMap = new NodeMap<V>();
		for (V node:orderedAdjMap.keySet()) {
			copiedOrderedAdjMap.put(node, ((EdgeList<E,V>) orderedAdjMap.get(node)).clone());
		}
		// second DFS -- renumber the vertices
		NumberDFS<E,V> dfs2 = new NumberDFS<E,V>(graph, meta, copiedOrderedAdjMap);
		dfs2.start(root);
		
		// debug
		if(showDebugInformation) {
			System.out.print("\nHigh-Points after second DFS");
			for (V n:graph.getVertices()){
				System.out.print("\nNode "+n+": ");
				NodeList<V> hpList = (NodeList<V>) ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_HIGHPT_LISTS)).get(n);
				for (V node:hpList) {
					System.out.print(" " + node + " ");
				}
			}
		}
		
		// workaround to circumvent a problem in the JBPT framework
		// which leads to not properly removed virtual edges in the TCTreeSkeleton
		// therefore this count is used to store the current state during dfs3
		NodeMap<V> edgeCount = new NodeMap<V>();
		for (V node:graph.getVertices()) {
			edgeCount.put(node, graph.getEdges(node).size());
		}
		meta.setMetaInfo(MetaInfo.DFS_EDGE_COUNT, edgeCount);
		// third DFS -- find the actual split components
		if(showDebugInformation) System.out.println("\n\nThird DFS - Finding split components...");
		SplitCompDFS<E,V> dfs3 = new SplitCompDFS<E,V>(graph, meta, copiedOrderedAdjMap, components, dfs2
				.getParentMap(), dfs2.getTreeArcMap(), dfs2.getHighptMap(),
				dfs2.getEdgeTypeMap(), virtEdgeMap, assignedVirtEdgeMap, 
				isHiddenMap);
		if (showDebugInformation) dfs3.doShowDebugInformation(true);
		dfs3.start(root);
		
		//debug
		if(showDebugInformation) {
			for (EdgeList<E,V> el : components) {
				System.out.print("\n Component: ");
				for (E e : el) {
					System.out.print(" (" + e + ")");
					if (virtEdgeMap.getBool(e))
						System.out.print("v ");
					else
						System.out.print(" ");
				}
			}
		}
		
	}

	/**
	 *  
	 */
	@SuppressWarnings("unchecked")
	protected NodeMap<V> orderAdjLists(IGraph<E,V> graph, MetaInfoContainer meta) {
		if(showDebugInformation) System.out.println("\nOrdering adjacency lists w.r.t. low-point values...");
		Collection<E> edges = graph.getEdges();
		ArrayList<EdgeList<E,V>> bucket = new ArrayList<EdgeList<E,V>>();
		int bucketSize = 3 * (graph.countVertices()) + 2;
		if(showDebugInformation) System.out.println("\n vertices: " + graph.countVertices() + ", bucket size: " + bucketSize);
		for (int i=0; i< bucketSize; i++){
			bucket.add(new EdgeList<E,V>());
		}
		int phi;
		if(showDebugInformation) System.out.println("Potentials: ");
		for (E e:edges) {

			phi = -1;
			// assign each edge its potential phi
			if (((EdgeMap<E,V>) meta.getMetaInfo(MetaInfo.DFS_EDGE_TYPE)).getInt(e) == AbstractDFS.TREE_EDGE){
				// e is tree edge
				if (((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT2_NUM)).getInt(e.getV2()) 
						< ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NUM)).getInt(e.getV1())){
					// low2(w) < v
					phi = 3 * ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT1_NUM)).getInt(e.getV2());
				} else {
					// low2(w) >= v
					phi = 3 * ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT1_NUM)).getInt(e.getV2()) + 2;
				}
			} else {
				// e is back edge
				phi = 3 * ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NUM)).getInt(e.getV2()) + 1;
			}
			if(showDebugInformation) System.out.print(" ["+e+"]="+phi);
			
			// put edge into bucket according to phi
			// ! bucket's index start with 0
			(bucket.get(phi-1)).add(e);
		}
		
		// create a new node map for the ordered adj list
		NodeMap<V> orderedAdjMap = this.createNodeMap(graph);
		for (V node:graph.getVertices()) {
			EdgeList<E,V> adj = new EdgeList<E,V>();
			orderedAdjMap.put(node, adj);
		}
		meta.setMetaInfo(MetaInfo.DFS_ORDERED_ADJ_LISTS, orderedAdjMap);
		
		// put edges into adj list according to order in buckets
		for (EdgeList<E,V> el : bucket){
			while (!el.isEmpty()){
				E e = el.pop();
				((EdgeList<E ,V>) orderedAdjMap.get(e.getV1())).add(e);
			}
		}
		if(showDebugInformation) System.out.println("\nOrdering finished");
		return orderedAdjMap;
	}

	/**
	 * This method is used for the deletion of multiple edges. 
	 * The edges are sorted in a manner, so that multiple edges
	 * are positioned consecutively in the returned EdgeList.
	 */
	@SuppressWarnings("unchecked") 
	protected EdgeList<E,V> sortConsecutiveMultipleEdges(IGraph<E,V> g){
		NodeMap<V> indices = new NodeMap<V>();
		int count = 0;
		for (V vertex:g.getVertices()) {
			indices.put(vertex, count++);
		}
		// bucketSort edges such that multiple edges come after each other
		if(showDebugInformation) System.out.println("\nSorting edges...");
		ArrayList<E> edges = (ArrayList<E>) g.getEdges();
		ArrayList<EdgeList<E,V>> bucket = new ArrayList<EdgeList<E,V>>();
		// place edges into buckets according to vertex with smaller index
		for (int i = 0; i < g.getVertices().size(); i++) {//edges.size(); i++) {
			bucket.add(new EdgeList<E,V>());
		}
		for (E e:edges) {
			int i = Math.min((Integer) indices.get( e.getV1()), (Integer) indices.get(e.getV2()));
			bucket.get(i).add(e);
		}
		
		// sort within buckets according to enDP_NAMESoint with larger index
		EdgeList<E,V> sortedEdges = new EdgeList<E,V>();
		for  (EdgeList<E,V> l : bucket){
			HashMap<Integer, EdgeList<E,V>> map = new HashMap<Integer, EdgeList<E,V>>();
			for (Object e : l){
				// add up indices of enDP_NAMESoints
				Integer i = (Integer) indices.get(((E)e).getV1()) + (Integer) indices.get(((E)e).getV2());
				// take this as key for the map
				EdgeList<E,V> el = map.get(i);
				// and add the edge to the corresponding edge list
				if (el == null) {
					el = new EdgeList<E,V>();
					el.add((E) e);
					map.put(i, el);
				} else {
					el.add((E) e);
				}
			}
			// put edges into output list
			Collection<EdgeList<E,V>> col = map.values();
			for (EdgeList<E,V> el : col){
				if (el != null){
					sortedEdges.addAll(el);
				}
			}
			
		}
		
		//debug
		if(showDebugInformation) {
			System.out.println();
			for (E e : sortedEdges){
				System.out.println(" [" + e.toString() + "]");
			}
		}
		
		return sortedEdges;
	}
	
	/**
	 * Simply deletes found multiple edges in the given graph.
	 */
	protected void splitOffInitialMultipleEdges(TCSkeleton<E,V> skeleton, 
			Vector<EdgeList<E,V>> components, 
			EdgeMap<E,V> virtEdgeMap, 
			EdgeMap<E,V> assignedVirtEdgeMap, 
			EdgeMap<E,V> isHiddenMap) {
		
		// sort edges such that multiple edges are consecutive
		EdgeList<E,V> edges = this.sortConsecutiveMultipleEdges(skeleton);
		
		// split off multiple edge components
		EdgeList<E,V> tempComp = new EdgeList<E,V>();
		E lastEdge=null, currentEdge=null;
		int tempCompSize = 0;
		for (E e : edges){
			currentEdge = e;
			if (lastEdge != null){
				// multiple edge if enDP_NAMESoint correspond to lastEdge's enDP_NAMESoints
				if ((currentEdge.getV1()==lastEdge.getV1() && 
						currentEdge.getV2()==lastEdge.getV2())
						|| 
						(currentEdge.getV1()==lastEdge.getV2() &&
						currentEdge.getV2()==lastEdge.getV1())){
					// add lastEdge to new component
					tempComp.add(lastEdge);
					tempCompSize++;
				}
				// current edge is different from last edge
				else{
					// if tempCompSize is greater than zero, there has been split off
					// at least one edge, and the corresponding component needs to be
					// finished
					if (tempCompSize>0){
						// add lastEdge to component
						tempComp.add(lastEdge);
						// finish component, i.e. add virtual edge and store the component
						this.newComponent(skeleton, components, tempComp, virtEdgeMap, 
								assignedVirtEdgeMap, isHiddenMap,
								lastEdge.getV1(), lastEdge.getV2());
						// look for new multiple edges next time
						tempComp = new EdgeList<E,V>();
						tempCompSize=0;
					}
				}
			}
			lastEdge = currentEdge;
		}
		// possible finishing of the last component due to multiple edges
		if (tempCompSize>0){
			// add lastEdge to component
			tempComp.add(lastEdge);
			System.out.println("Here");
			// finish component, i.e. add virtual edge and store the component
			this.newComponent(skeleton, components, tempComp, virtEdgeMap, 
					assignedVirtEdgeMap, isHiddenMap,
					lastEdge.getV1(), lastEdge.getV2());
		}
	}

	/**
	 * Creates a new component based on the given list of contained edges.
	 */
	protected void newComponent(TCSkeleton<E,V> skeleton,
			Vector<EdgeList<E,V>> components, 
			EdgeList<E,V> tempComp,
			EdgeMap<E,V> virtEdgeMap, 
			EdgeMap<E,V> assignedVirtEdgeMap,
			EdgeMap<E,V> isHiddenMap, V v1, V v2) {
		
		// remove edges from graph
		for (E e : tempComp) {
			skeleton.removeEdge(e);
			isHiddenMap.put(e, true);
		}
		
		// create virtual edge and add edges to component
		E virtualEdge = skeleton.addVirtualEdge(v1,v2);
		virtEdgeMap.put(virtualEdge, true);
		tempComp.add(0, virtualEdge);
		// assign virtual edge
		
		for(E e:tempComp){
			assignedVirtEdgeMap.put(e, virtualEdge);
		}
		
		components.add(tempComp);
	}

	/**
	 * Creates an edgeMap for the given graph containing all edges of the graph.
	 * @param g
	 */
	protected EdgeMap<E,V> createEdgeMap(IGraph<E,V> g) {
		EdgeMap<E,V> map = new EdgeMap<E,V>();
		for (E e:g.getEdges()) {
			map.put(e, null);
		}
		return map;
	}
	
	/**
	 * Creates a NodeMap for the given graph containing all nodes of the graph.
	 * -- Move this method to graph algorithms. --
	 * @param g
	 * @return
	 */
	protected NodeMap<V> createNodeMap(IGraph<E,V> g) {
		NodeMap<V> map = new NodeMap<V>();
		for (V v:g.getVertices()) {
			map.put(v, null);
		}
		return map;
	}

}
