package org.jbpt.petri;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of a net system.
 * 
 * TODO lift to interfaces
 * 
 * @author Artem Polyvyanyy
 */
public abstract class AbstractNetSystem<F extends IFlow<N>, N extends INode, P extends IPlace, T extends ITransition, M extends IMarking<F,N,P,T>> 
		extends AbstractPetriNet<F,N,P,T> 
		implements INetSystem<F,N,P,T,M> 
{
	
	protected M marking = null;
	
	@SuppressWarnings("unchecked")
	public AbstractNetSystem() {
		super();
		try {
			this.marking = (M) Marking.class.newInstance();
			this.marking.setPetriNet(this);
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public N removeNode(N n) {
		N result = super.removeNode(n);
		if (result!=null && n instanceof IPlace) {
			this.marking.remove(n);
		}
		return result;
	}

	@Override
	public Collection<N> removeNodes(Collection<N> ns) {
		Collection<N> result = super.removeNodes(ns);
		if (result!=null) {
			for (N n : result) {
				if (n instanceof IPlace)
					this.marking.remove(n);
			}
		}
		return result;
	}

	@Override
	public P removePlace(P p) {
		P result = super.removePlace(p);
		if (result!=null) {
			this.marking.remove(p);
		}
		return result;
	}

	@Override
	public Collection<P> removePlaces(Collection<P> ps) {
		Collection<P> result = super.removePlaces(ps);
		if (result!=null) {
			for (P p : result) {
				this.marking.remove(p);
			}
		}
		return result;
	}

	@Override
	public M getMarking() {
		return (M)this.marking;
	}

	@Override
	public Set<P> getMarkedPlaces() {
		return this.marking.keySet();
	}

	@Override
	public Set<T> getEnabledTransitions() {
		Set<T> result = new HashSet<T>();
		Set<P> marked = new HashSet<P>(this.getMarkedPlaces());
		
		for (T t : this.getTransitions()) {
			if (marked.containsAll(this.getPreset(t)))
				result.add(t);
		}
		
		return result;
	}

	@Override
	public boolean isEnabled(T t) {
		if (!this.getTransitions().contains(t)) return false;
		
		for (P p : this.getPreset(t))
			if (!this.isMarked(p))
				return false;
			
		return true;
	}
	
	@Override
	public boolean isMarked(P p) {
		return this.marking.isMarked(p);
	}

	@Override
	public boolean fire(T t) {
		if (!this.getTransitions().contains(t)) return false;
		
		if (!this.isEnabled(t)) return false;
		
		for (P p : this.getPreset(t))
			this.marking.put(p, this.marking.get(p)-1);
		
		for (P p : this.getPostset(t))
			this.marking.put(p, this.marking.get(p)+1);
		
		return true;
	}

	@Override
	public String toDOT() {
		String result = "digraph G {\n";
		result += "graph [fontname=\"Helvetica\" fontsize=10 nodesep=0.35 ranksep=\"0.25 equally\"];\n";
		result += "node [fontname=\"Helvetica\" fontsize=10 fixedsize style=filled fillcolor=white penwidth=\"2\"];\n";
		result += "edge [fontname=\"Helvetica\" fontsize=10 arrowhead=normal color=black];\n";
		result += "\n";
		result += "node [shape=circle];\n";
		
		for (P p : this.getPlaces()) {
			Integer n = this.marking.get(p);
			String label = ((n == 0) || (n == null)) ? p.getLabel() : p.getLabel() + "[" + n.toString() + "]"; 
			result += String.format("\tn%s[label=\"%s\" width=\".3\" height=\".3\"];\n", p.getId().replace("-", ""), label);
		}
		
		result += "\n";
		result += "node [shape=box];\n";
		
		for (T t : this.getTransitions()) {
			String fillColor = this.isEnabled(t) ? " fillcolor=\"#9ACD32\"" : "";
			if (t.getName()=="")
				result += String.format("\tn%s[label=\"%s\" width=\".3\""+fillColor+" height=\".1\"];\n", t.getId().replace("-", ""), t.getName());
			else 
				result += String.format("\tn%s[label=\"%s\" width=\".3\""+fillColor+" height=\".3\"];\n", t.getId().replace("-", ""), t.getName());
		}
		
		result += "\n";
		for (F f: this.getFlow()) {
			result += String.format("\tn%s->n%s;\n", f.getSource().getId().replace("-", ""), f.getTarget().getId().replace("-", ""));
		}
		result += "}\n";
		
		return result;
	}
	
	@Override
	public AbstractNetSystem<F,N,P,T,M> clone() {
		Map<N,N> nodeMapping = new HashMap<N,N>();
		@SuppressWarnings("unchecked")
		AbstractNetSystem<F,N,P,T,M> clone = (AbstractNetSystem<F,N,P,T,M>) super.clone(nodeMapping);
		cloneHelper(clone, nodeMapping);
		
		return clone;
	}
	
	@Override
	public AbstractNetSystem<F,N,P,T,M> clone(Map<N,N> nodeMapping) {
		if (nodeMapping==null)
			nodeMapping = new HashMap<N,N>();
		
		@SuppressWarnings("unchecked")
		AbstractNetSystem<F,N,P,T,M> clone = (AbstractNetSystem<F,N,P,T,M>) super.clone(nodeMapping);
		cloneHelper(clone,nodeMapping);
		
		return clone;
	}
	
	/**
	 * This method clones the marking of the net system. 
	 * 
	 * @param clone A clone object.
	 * @param nodeMapping Mapping of nodes of the original net system to nodes of its clone object. 
	 */
	@SuppressWarnings("unchecked")
	private void cloneHelper(AbstractNetSystem<F,N,P,T,M> clone, Map<N,N> nodeMapping) {
		try {
			clone.marking = (M) Marking.class.newInstance();
			clone.marking.setPetriNet(clone);
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		
		// initialise marking according to original net system
		for (P p : this.getMarkedPlaces()) 
			clone.putTokens((P) nodeMapping.get(p), this.getTokens(p));
	}

	@Override
	public Integer putTokens(P p, Integer tokens) {
		return this.marking.put(p, tokens);
	}
	
	@Override
	public Integer getTokens(P p) {
		return this.marking.get(p);
	}
	
	@Override
	public void loadNaturalMarking() {
		this.marking.clear();
		for (P p : this.getSourcePlaces()) {
			this.marking.put(p,1);
		}
	}
	
	@Override
	public void loadMarking(M newMarking) {
		if (newMarking.getPetriNet()!=this) return;
		
		this.marking.clear();
		for (Map.Entry<P,Integer> entry : newMarking.entrySet()) {
			this.marking.put(entry.getKey(),entry.getValue());
		}
	}
}
