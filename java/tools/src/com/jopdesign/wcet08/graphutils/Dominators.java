/*
  This file is part of JOP, the Java Optimized Processor
    see <http://www.jopdesign.com/>

  Copyright (C) 2008, Benedikt Huber (benedikt.huber@gmail.com)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.jopdesign.wcet08.graphutils;

import java.util.Hashtable;
import java.util.Vector;

import org.jgrapht.DirectedGraph;

public class Dominators<V,E> {
	private DirectedGraph<V, E> graph;
	private Vector<V> vertexPreOrder;
	private Hashtable<V,V> idom = null;
	private Hashtable<V, Integer> preOrderMap;

	protected int getOrder(V vertex) {
		return preOrderMap.get(vertex);
	}
	protected V getIDom(V vertex) {		
		return idom.get(vertex);
	}

	public Dominators(DirectedGraph<V,E> g, Vector<V> preOrder) {
		this.graph = g;
		this.vertexPreOrder = preOrder;
		this.preOrderMap = new Hashtable<V,Integer>();
		for(int i = 0; i < this.vertexPreOrder.size(); i++) {
			preOrderMap.put(vertexPreOrder.get(i),i);
		}
	}
	/* Compute Dominators, following:
	 *    	author = {Cooper, Keith  D.  and Harvey, Timothy  J.  and Kennedy, Ken  },
            title = {A Simple, Fast Dominance Algorithm},
	 */
	protected void computeDominators() {
		if(this.idom != null) return;
		this.idom = new Hashtable<V,V>();
		V firstElement = vertexPreOrder.firstElement();
		idom.put(firstElement,firstElement);
		if(! graph.incomingEdgesOf(vertexPreOrder.firstElement()).isEmpty())
			throw new AssertionError("The entry of the flow graph is not allowed to have incoming edges");
		boolean changed;
		do {
			changed = false;
			for(V v : vertexPreOrder) {
				if(v.equals(firstElement)) continue;
				V oldIdom = getIDom(v);
				V newIdom = null;
				for(E edge : graph.incomingEdgesOf(v)) {
					V pre = graph.getEdgeSource(edge);
					V preIdom = getIDom(pre);
					if(preIdom == null) continue;
					if(newIdom == null) {
						/* If we only have one (defined) predecessor pre, IDom(v) = pre */
						newIdom = pre;
					} else {
						/* compute the intersection of all defined predecessors of v */
						newIdom = intersectIDoms(preIdom,newIdom);
					}
				}
				if(newIdom == null) throw new AssertionError("newIDom == null !, for "+v);
				if(! newIdom.equals(oldIdom)) {
					changed = true;
					this.idom.put(v,newIdom);
				}
			}
		} while(changed);
	}
	private V intersectIDoms(V v1, V v2) {
		while(v1 != v2) {
			if(getOrder(v1) <  getOrder(v2)) {
				v2 = getIDom(v2);
			} else {
				v1 = getIDom(v1);
			}
		}
		return v1;
	}
	public Hashtable<V, V> getIDoms() {
		computeDominators();
		return this.idom;
	}
	public boolean dominates(V dominator, V dominated) {
		if(dominator.equals(dominated)) return true; // Domination is reflexive ;)
		computeDominators();
		V dom = getIDom(dominated);
		while(dom != null && ! dom.equals(dominator)) {
			dom = getIDom(dom);
		}
		return  dom.equals(dominator);
	}
}
