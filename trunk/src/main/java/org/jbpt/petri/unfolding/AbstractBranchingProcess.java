package org.jbpt.petri.unfolding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jbpt.petri.IFlow;
import org.jbpt.petri.IMarking;
import org.jbpt.petri.INetSystem;
import org.jbpt.petri.INode;
import org.jbpt.petri.IPlace;
import org.jbpt.petri.ITransition;
import org.jbpt.petri.PetriNet;
import org.jbpt.petri.Place;
import org.jbpt.petri.Transition;

public class AbstractBranchingProcess<BPN extends IBPNode<N>, C extends ICondition<BPN,C,E,F,N,P,T,M>, E extends IEvent<BPN,C,E,F,N,P,T,M>, F extends IFlow<N>, N extends INode, P extends IPlace, T extends ITransition, M extends IMarking<F,N,P,T>> 
		implements IBranchingProcess<BPN,C,E,F,N,P,T,M> {
	// originative net system
	protected INetSystem<F,N,P,T,M> sys = null;
	
	protected Set<E> events	= new HashSet<E>();	
	protected Set<C> conds	= new HashSet<C>();
	protected ICut<BPN,C,E,F,N,P,T,M> iniBP = (ICut<BPN,C,E,F,N,P,T,M>)this.createCut();
	
	// causality: maps node of unfolding to a set of preceding nodes
	protected Map<BPN,Set<BPN>> ca = new HashMap<BPN,Set<BPN>>();
	// concurrency: maps node of unfolding to a set of concurrent nodes
	protected Map<BPN,Set<BPN>> co = new HashMap<BPN,Set<BPN>>();
	
	// indexes for conflict and concurrency relations 
	private Map<BPN,Set<BPN>> EX    = new HashMap<BPN,Set<BPN>>();
	private Map<BPN,Set<BPN>> notEX = new HashMap<BPN,Set<BPN>>();
	private Map<BPN,Set<BPN>> CO    = new HashMap<BPN,Set<BPN>>();
	private Map<BPN,Set<BPN>> notCO = new HashMap<BPN,Set<BPN>>();
	
	protected AbstractBranchingProcess() {}
	
	protected AbstractBranchingProcess(INetSystem<F,N,P,T,M> sys) {
		this.sys = sys;
		this.constructInitialBranchingProcess();
	}

	private void constructInitialBranchingProcess() {
		for (P p : this.sys.getMarking().toMultiSet()) {
			C c = this.createCondition(p,null);
			this.appendCondition(c);
			this.iniBP.add(c);
		}
	}

	@Override
	public Set<C> getConditions() {
		return this.conds;
	}

	@Override
	public Set<C> getConditions(P place) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<E> getEvents() {
		return this.events;
	}

	@Override
	public Set<E> getEvents(T transition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public INetSystem<F,N,P,T,M> getOriginativeNetSystem() {
		return this.sys;
	}

	@Override
	public boolean areCausal(BPN n1, BPN n2) {
		if (this.ca.get(n2)==null) {
			if (n2 instanceof AbstractEvent) {
				@SuppressWarnings("unchecked")
				E e = (E) n2;
				if (e.getPreConditions().contains(n1)) return true;
				for (C c : e.getPreConditions())
					if (this.ca.get(c).contains(n1))
						return true;
				
				return false;
			}
			else {
				@SuppressWarnings("unchecked")
				C c = (C) n2;
				if (c.getPreEvent().equals(n1)) return true;
				if (this.ca.get(c.getPreEvent()).contains(n1)) return true;
				
				return false;
			}
		}
		
		return this.ca.get(n2).contains(n1);
	}

	@Override
	public boolean areInverseCausal(BPN n1, BPN n2) {
		return this.areCausal(n2,n1);
	}

	@Override
	public boolean areConcurrent(BPN n1, BPN n2) {
		Set<BPN> co = this.CO.get(n1);
		if (co!=null)
			if (co.contains(n2)) return true;
		
		Set<BPN> notCo = this.notCO.get(n1);
		if (notCo!=null)
			if (notCo.contains(n2)) return false;
		
		boolean result = !this.areCausal(n1,n2) && !this.areInverseCausal(n1,n2) && !this.areInConflict(n1,n2);
		
		if (result)
			this.index(this.CO,n1,n2);
		else
			this.index(this.notCO,n1,n2);
		
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean areInConflict(BPN n1, BPN n2) {
		Set<BPN> ex = this.EX.get(n1);
		if (ex!=null)
			if (ex.contains(n2)) return true;
		
		Set<BPN> notEx = this.notEX.get(n1);
		if (notEx!=null)
			if (notEx.contains(n2)) return false;
		
		if (n1.equals(n2)) {
			this.index(this.notEX,n1,n2);
			return false;
		}
		
		Set<BPN> ca1 = new HashSet<BPN>(this.ca.get(n1));
		ca1.add(n1);
		Set<BPN> ca2 = new HashSet<BPN>(this.ca.get(n2));
		ca2.add(n2);
		
		for (BPN nn1 : ca1) {
			if (nn1.isCondition()) continue;
			E e1 = (E) nn1;
			for (BPN nn2 : ca2) {
				if (nn2.isCondition()) continue;
				E e2 = (E) nn2;
				if (e1.equals(e2)) continue;				
				if (!this.overlap(e1.getPreConditions(),e2.getPreConditions())) continue;
				
				this.index(this.EX,n1,n2);
				return true;
			}
		}
		
		this.index(this.notEX,n1,n2);
		return false;
	}

	@Override
	public OrderingRelationType getOrderingRelation(BPN n1, BPN n2) {
		if (this.areCausal(n1,n2)) return OrderingRelationType.CAUSAL;
		if (this.areInverseCausal(n1,n2)) return OrderingRelationType.INVERSE_CAUSAL;
		if (this.areInConflict(n1,n2)) return OrderingRelationType.CONFLICT;
		return OrderingRelationType.CONCURRENT;
	}

	@SuppressWarnings("unchecked")
	@Override
	public C createCondition(P place, E event) {
		C c = null;
		try {
			c = (C) Condition.class.newInstance();
			c.setPlace(place);
			c.setPreEvent(event);
			return c;
		} catch (InstantiationException | IllegalAccessException exception) {
			exception.printStackTrace();
		}
		
		return c;
	}

	@SuppressWarnings("unchecked")
	@Override
	public E createEvent(T transition, ICoSet<BPN,C,E,F,N,P,T,M> preConditions) {
		E e = null;
		try {
			e = (E) Event.class.newInstance();
			e.setTransition(transition);
			e.setPreConditions(preConditions);
			return e;
		} catch (InstantiationException | IllegalAccessException exception) {
			exception.printStackTrace();
		}
		
		return e;
	}
	
	protected void appendCondition(C c) {
		this.conds.add(c);
		this.updateCausalityCondition(c);
	}
		
	@SuppressWarnings("unchecked")
	protected void updateCausalityCondition(C c) {
		if (this.ca.get(c)==null)
			this.ca.put((BPN)c,new HashSet<BPN>());
		
		E e = c.getPreEvent();
		if (e==null) return;
		
		this.ca.get(c).addAll(this.ca.get(e));
		this.ca.get(c).add((BPN)e);
	}
	
	@SuppressWarnings("unchecked")
	protected boolean appendEvent(E e) {
		this.events.add(e);		
		this.updateCausalityEvent(e);
		
		// add conditions that correspond to post-places of transition that corresponds to new event
		ICoSet<BPN,C,E,F,N,P,T,M> postConditions = null;
		try {
			postConditions = (ICoSet<BPN,C,E,F,N,P,T,M>) AbstractCoSet.class.newInstance();
		} catch (InstantiationException | IllegalAccessException e1) {
			e1.printStackTrace();
		}
		
		for (P s : this.sys.getPostset(e.getTransition())) {
			C c = this.createCondition(s,e);
			postConditions.add(c);
			this.appendCondition(c);
		}
		e.setPostConditions(postConditions);

		return true;
	}
	
	@SuppressWarnings("unchecked")
	private void updateCausalityEvent(E e) {
		if (this.ca.get(e)==null)
			this.ca.put((BPN)e, new HashSet<BPN>());
		
		for (C c : e.getPreConditions()) {
			this.ca.get(e).addAll(this.ca.get(c));
		}
		
		for (C c : e.getPreConditions())
			this.ca.get(e).add((BPN)c);
	}
	
	private void index(Map<BPN,Set<BPN>> map, BPN n1, BPN n2) {
		Set<BPN> s1 = map.get(n1);
		if (s1==null) {
			Set<BPN> ss1 = new HashSet<BPN>();
			ss1.add(n2);
			map.put(n1,ss1);
		}
		else
			s1.add(n2);
		
		Set<BPN> s2 = map.get(n2);
		if (s2==null) {
			Set<BPN> ss2 = new HashSet<BPN>();
			ss2.add(n1);
			map.put(n2,ss2);
		}
		else
			s2.add(n1);
	}
	
	private boolean overlap(ICoSet<BPN,C,E,F,N,P,T,M> s1, ICoSet<BPN,C,E,F,N,P,T,M> s2) {
		for (C n : s1)
			if (s2.contains(n))
				return true;
		
		return false;
	}

	@Override
	public ICut<BPN,C,E,F,N,P,T,M> getInitialCut() {
		return this.iniBP;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ICoSet<BPN,C,E,F,N,P,T,M> createCoSet() {
		try {
			return (ICoSet<BPN,C,E,F,N,P,T,M>) CoSet.class.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public ICut<BPN,C,E,F,N,P,T,M> createCut() {
		try {
			return (ICut<BPN,C,E,F,N,P,T,M>) Cut.class.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Set<BPN> getCausalPredecessors(BPN node) {
		return this.ca.get(node);
	}

	@Override
	public boolean isConflictFree() {
		Map<C,E> c2e = new HashMap<C,E>();
		for (E e : this.events) {
			for (C c : e.getPreConditions()) {
				if (c2e.get(c)!=null) return false;
				c2e.put(c,e);
			}
		}
		
		return true;
	}
	
	/*protected boolean appendEvent2(Event e) {
		this.events.add(e);
		this.updateCausalityEvent(e);
		
		if (t2es.get(e.getTransition())!=null) t2es.get(e.getTransition()).add(e);
		else {
			Set<Event> es = new HashSet<Event>();
			es.add(e);
			t2es.put(e.getTransition(), es);
		}
		
		// add conditions that correspond to post-places of transition that corresponds to new event
		CoSet postConds = new CoSet(this.sys);						// collection of new post conditions
		for (Place s : this.sys.getPostset(e.getTransition())) {	// iterate over places in the postset
			Condition c = new Condition(s,e);	 					// construct new condition
			postConds.add(c);
			this.addCondition(c);									// add condition to unfolding
		}
		e.setPostConditions(postConds);								// set post conditions of event
		
		// compute new cuts of unfolding
		for (Cut cut : c2cut.get(e.getPreConditions().iterator().next())) {
			if (contains(cut,e.getPreConditions())) {
				Cut newCut = new Cut(this.sys,cut);
				newCut.removeAll(e.getPreConditions());
				newCut.addAll(postConds);
				if (!this.addCut(newCut)) return false;
			}
		}
		
		this.countEvents++;
		return true;
	}*/
	
	@Override
	public PetriNet getPetriNet() {
		PetriNet result = new PetriNet();
		
		Map<Transition,E> t2e = new HashMap<Transition,E>();
		Map<Place,C>  p2c = new HashMap<Place,C>();
		Map<E,Transition> e2t = new HashMap<E,Transition>();
		Map<C,Place>  c2p = new HashMap<C,Place>();
		
		for (E e : this.getEvents()) {
			Transition t = new Transition(e.getName());
			result.addTransition(t);
			e2t.put(e,t);
			t2e.put(t,e);
		}
			
		for (C c : this.getConditions()) {
			Place p = new Place(c.getName());
			result.addPlace(p);
			c2p.put(c,p);
			p2c.put(p,c);
		}
		
		for (E e : this.getEvents()) {
			for (C c : e.getPreConditions()) {
				result.addFlow(c2p.get(c), e2t.get(e));
			}
		}
		
		for (C c : this.getConditions()) {
			result.addFlow(e2t.get(c.getPreEvent()),c2p.get(c));
		}
		
		return result;
	}
}