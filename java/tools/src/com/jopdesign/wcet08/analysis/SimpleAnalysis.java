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
package com.jopdesign.wcet08.analysis;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.generic.INVOKESTATIC;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.NEWARRAY;
import org.apache.log4j.Logger;
import org.jgrapht.DirectedGraph;

import com.jopdesign.build.ClassInfo;
import com.jopdesign.build.MethodInfo;
import com.jopdesign.wcet.WCETInstruction;
import com.jopdesign.wcet08.Config;
import com.jopdesign.wcet08.Project;
import com.jopdesign.wcet08.analysis.CacheConfig.CacheApproximation;
import com.jopdesign.wcet08.frontend.BasicBlock;
import com.jopdesign.wcet08.frontend.FlowGraph;
import com.jopdesign.wcet08.frontend.CallGraph.CallGraphNode;
import com.jopdesign.wcet08.frontend.FlowGraph.BasicBlockNode;
import com.jopdesign.wcet08.frontend.FlowGraph.DedicatedNode;
import com.jopdesign.wcet08.frontend.FlowGraph.FlowGraphEdge;
import com.jopdesign.wcet08.frontend.FlowGraph.FlowGraphNode;
import com.jopdesign.wcet08.frontend.FlowGraph.InvokeNode;
import com.jopdesign.wcet08.ipet.LocalAnalysis;
import com.jopdesign.wcet08.ipet.MaxCostFlow;
import com.jopdesign.wcet08.ipet.LocalAnalysis.CostProvider;

/**
 * Simple and fast local analysis with cache approximation.
 *
 * @author Benedikt Huber (benedikt.huber@gmail.com)
 *
 */
public class SimpleAnalysis {
	class WcetKey {
		MethodInfo m;
		CacheApproximation alwaysHit;
		public WcetKey(MethodInfo m, CacheApproximation mode) {
			this.m = m; this.alwaysHit = mode;
		}
		@Override
		public boolean equals(Object that) {
			return (that instanceof WcetKey) ? equalsKey((WcetKey) that) : false;
		}
		private boolean equalsKey(WcetKey key) {
			return this.m.equals(key.m) && (this.alwaysHit == key.alwaysHit);
		}
		@Override
		public int hashCode() {
			return m.getFQMethodName().hashCode()+this.alwaysHit.hashCode();
		}
		@Override
		public String toString() {
			return this.m.getFQMethodName()+"["+this.alwaysHit+"]";
		}
	}
	public class WcetCost implements Serializable {
		private static final long serialVersionUID = 1L;
		private long localCost = 0;
		private long cacheCost = 0;
		private long nonLocalCost = 0;
		public WcetCost() { }
		public long getCost() { return nonLocalCost+getLocalAndCacheCost(); }
		
		public long getLocalCost() { return localCost; }
		public long getCacheCost() { return cacheCost; }
		public long getNonLocalCost() { return nonLocalCost; }
		
		public void addLocalCost(long c) { this.localCost += c; }
		public void addNonLocalCost(long c) { this.nonLocalCost += c; }
		public void addCacheCost(long c) { this.cacheCost += c; }
		
		public long getLocalAndCacheCost() { return this.localCost + this.cacheCost; }
		@Override public String toString() {
			if(getCost() == 0) return "0";
			return ""+getCost()+" (local: "+localCost+",cache: "+cacheCost+",non-local: "+nonLocalCost+")";
		}
		public WcetCost getFlowCost(Long flow) {
			WcetCost flowcost = new WcetCost();
			if(this.getCost() == 0 || flow == 0) return flowcost;
			flowcost.localCost = localCost*flow;
			flowcost.cacheCost = cacheCost*flow;
			flowcost.nonLocalCost = nonLocalCost*flow;
			if(flowcost.getCost() / flow != getCost()) {
				throw new ArithmeticException("getFlowCost: Arithmetic Error");
			}
			return flowcost;
		}
	}
	/* provide cost given a node->cost table */
	private class MapCostProvider<T> implements CostProvider<T> {
		private Map<T, WcetCost> costMap;
		public MapCostProvider(Map<T,WcetCost> costMap) {
			this.costMap = costMap;
		}
		public long getCost(T obj) {
			WcetCost cost = costMap.get(obj);
			if(cost == null) throw new NullPointerException("Missing entry for "+obj+" in cost map");
			return cost.getCost();
		}
		
	}
	
	private static final Logger logger = Logger.getLogger(SimpleAnalysis.class);
	private Project project;
	private Hashtable<WcetKey, WcetCost> wcetMap;
	private CacheConfig config;
	private LocalAnalysis localAnalysis;

	public SimpleAnalysis(Project project) {
		this.config = new CacheConfig(Config.instance());
		this.project = project;
		this.localAnalysis = new LocalAnalysis(project);
		this.wcetMap = new Hashtable<WcetKey,WcetCost>();
	}
	
	/**
	 * This is a simple analysis, which nevertheless incorporates the cache.
	 * We employ the following strategy:
	 *  - If the set of all methods reachable from m (including m) fits into the cache,
	 *    we calculate the 'alwaysHitCache' WCET, and the add the cost for missing each
	 *    method exactly once. 
	 *    NOTE: We do not need to add m, as it will be included in the cost of the caller.
	 *  - If the set of all methods reachable from m (including m) does NOT fit into the cache,
	 *    we compute an actual WCET for the reachable methods, and add the cost for missing
	 *    at the invoke nodes.
	 * @param m
	 * @param alwaysHit
	 * @return
	 */
	public WcetCost computeWCET(MethodInfo m, CacheApproximation cacheMode) {
		/* use a cache to speed up analysis */
		WcetKey key = new WcetKey(m,cacheMode);
		if(wcetMap.containsKey(key)) return wcetMap.get(key);

		/* analyse reachable */
		CacheApproximation recursiveMode = cacheMode;
		boolean allFit = getMaxCacheBlocks(m) <= config.cacheBlocks();
		boolean localHit = cacheMode == CacheApproximation.ALWAYS_HIT;
		long missCost = 0;		
		if(cacheMode == CacheApproximation.ANALYSE_REACHABLE) {
			if(allFit) {
				recursiveMode = CacheApproximation.ALWAYS_HIT;
				localHit = true;
				missCost = cacheMissPenalty(m);
			}			
		}
		/* build wcet map */
		FlowGraph fg = project.getFlowGraph(m);
		Map<FlowGraphNode,WcetCost> nodeCosts = buildNodeCostMap(fg,recursiveMode, localHit);
		CostProvider<FlowGraphNode> costProvider = new MapCostProvider<FlowGraphNode>(nodeCosts);
		MaxCostFlow<FlowGraphNode,FlowGraphEdge> problem = 
			localAnalysis.buildWCETProblem(key.toString(),project.getFlowGraph(m), costProvider);
		logger.debug("Max-Cost-Flow: "+m.getMethod()+", cache mode: "+cacheMode+
		            ", all methods fit?: "+allFit+ ", cache miss penalty: "+missCost);
		/* solve ILP */
		long maxCost = 0;
		Map<FlowGraphEdge, Long> flowMapOut = new HashMap<FlowGraphEdge, Long>();
		try {
			maxCost = Math.round(problem.solve(flowMapOut));
		} catch (Exception e) {
			throw new Error("Failed to solve LP problem",e);
		}
		/* extract node flow, local cost, cache cost, cummulative cost */
		Map<FlowGraphNode,Long> nodeFlow = new Hashtable<FlowGraphNode, Long>();
		DirectedGraph<FlowGraphNode, FlowGraphEdge> graph = fg.getGraph();
		for(FlowGraphNode n : fg.getGraph().vertexSet()) {
			if(graph.inDegreeOf(n) == 0) nodeFlow.put(n, 0L); // ENTRY and DEAD CODE (no flow)
			else {
				long flow = 0;
				for(FlowGraphEdge inEdge : graph.incomingEdgesOf(n)) {
					flow+=flowMapOut.get(inEdge);
				}
				nodeFlow.put(n, flow);
			}
		}
		/* Compute cost, sepearting local and non-local cost */
		/* Safety check: compare flow*cost to actual solution */
		WcetCost methodCost = new WcetCost();
		for(FlowGraphNode n : fg.getGraph().vertexSet()) {
			long flow = nodeFlow.get(n);
			methodCost.addLocalCost(flow * nodeCosts.get(n).getLocalAndCacheCost());
			methodCost.addNonLocalCost(flow * nodeCosts.get(n).getNonLocalCost());
		}
		if(methodCost.getCost() != maxCost) {
			throw new AssertionError("The solution implies that the flow graph cost is " 
									 + methodCost.getCost() + ", but the ILP solver reported "+maxCost);
		}
		methodCost.addCacheCost(missCost);
		wcetMap.put(key, methodCost); 
		/* Logging and Report */
		if(Config.instance().doGenerateWCETReport()) {
			Hashtable<FlowGraphNode, WcetCost> nodeFlowCosts = 
				new Hashtable<FlowGraphNode, WcetCost>();
			for(FlowGraphNode n : fg.getGraph().vertexSet()) {
				WcetCost nfc = nodeCosts.get(n).getFlowCost(nodeFlow.get(n));
				nodeFlowCosts .put(n,nfc);
			}
			logger.info("WCET for " + key + ": "+methodCost);
			Map<String,Object> stats = new Hashtable<String, Object>();
			stats.put("WCET",methodCost);
			stats.put("mode",cacheMode);
			stats.put("all-methods-fit-in-cache",allFit);
			stats.put("missCost",missCost);
			project.getReport().addDetailedReport(m,"WCET_"+cacheMode.toString(),stats,nodeFlowCosts,flowMapOut);
		}
		return methodCost;
	}

	/**
	 * map flowgraph nodes to WCET
	 * if the node is a invoke, we need to compute the WCET for the invoked method
	 * otherwise, just take the basic block WCET
	 * @param fg 
	 * @param recursiveMode WCET mode for recursive calls 
	 * @param localHit whether we compute always-hit-cache WCET (locally)
	 * @return
	 */
	private Map<FlowGraphNode, WcetCost> 
		buildNodeCostMap(FlowGraph fg,CacheApproximation recursiveMode, boolean localHit) {
		
		HashMap<FlowGraphNode, WcetCost> nodeCost = new HashMap<FlowGraphNode,WcetCost>();
		for(FlowGraphNode n : fg.getGraph().vertexSet()) {
			if(n.getBasicBlock() != null) {
				nodeCost.put(n, computeCostOfNode(n, recursiveMode, localHit));
			} else {
				nodeCost.put(n, new WcetCost());
			}
		}
		return nodeCost;
	}
	
	private class WcetVisitor implements FlowGraph.FlowGraphVisitor {
		WcetCost cost;
		private CacheApproximation recursiveMode;
		private boolean localHit;
		public WcetVisitor(CacheApproximation recursiveMode, boolean localHit) {
			this.recursiveMode = recursiveMode;
			this.localHit = localHit;
			this.cost = new WcetCost();
		}
		public void visitSpecialNode(DedicatedNode n) {
		}
		public void visitBasicBlockNode(BasicBlockNode n) {
			BasicBlock bb = n.getBasicBlock();
			for(InstructionHandle ih : bb.getInstructions()) {
				addInstructionCost(n,ih);
			}
		}
		public void visitInvokeNode(InvokeNode n) {
			addInstructionCost(n,n.getInstructionHandle());
			if(n.getImpl() == null) {
				throw new AssertionError("Invoke node "+n.getReferenced()+" without implementation in WCET analysis - did you preprocess virtual methods ?");
			}
			recursiveWCET(n.getBasicBlock().getMethodInfo(), n.getImpl());
		}
		private void addInstructionCost(BasicBlockNode n, InstructionHandle ih) {
			Instruction ii = ih.getInstruction();
			if(WCETInstruction.isInJava(ii.getOpcode())) {
				/* FIXME: [NO THROW HACK] */
				if(ii instanceof ATHROW || ii instanceof NEW || 
				   ii instanceof NEWARRAY || ii instanceof ANEWARRAY) {
					logger.error("Unable to compute WCET of "+ii+". Approximating with 2000 cycles.");
					cost.addLocalCost(2000L);
				} else {
					visitJavaImplementedBC(n.getBasicBlock(),ih);
				}
			} else {
				int jopcode = project.getWcetAppInfo().getJOpCode(n.getBasicBlock().getClassInfo(), ii);
				int cycles = WCETInstruction.getCycles(jopcode,false,0);
				cost.addLocalCost(cycles);
			}			
		}
		/* add cost for invokestatic + return + the java implemented bc */
		private void visitJavaImplementedBC(BasicBlock bb, InstructionHandle ih) {
			logger.info("Java implemented bytecode: "+ ih.getInstruction());
			MethodInfo javaImpl = project.getWcetAppInfo().getJavaImpl(bb.getClassInfo(), ih.getInstruction());
			int cycles = WCETInstruction.getCycles(new INVOKESTATIC(0).getOpcode(),false,0);
			cost.addLocalCost(cycles);
			recursiveWCET(bb.getMethodInfo(), javaImpl);
		}
		private void recursiveWCET(MethodInfo invoker, MethodInfo invoked) {			
			logger.info("Recursive WCET computation: " + invoked.getMethod());
			cost.addNonLocalCost(computeWCET(invoked, recursiveMode).getCost());
			if(! localHit) {
				long missCost = getInvokeReturnMissCost(
					  			  project.getFlowGraph(invoker).getNumberOfBytes(),
								  project.getFlowGraph(invoked).getNumberOfBytes());
				logger.debug("Adding invoke/return miss cost: "+missCost);
				cost.addCacheCost(missCost);
				
			}			
		}
	}
	private WcetCost 
		computeCostOfNode(FlowGraphNode n,CacheApproximation recursiveMode, boolean localHit) {
		
		WcetVisitor wcetVisitor = new WcetVisitor(recursiveMode, localHit);
		n.accept(wcetVisitor);
		return wcetVisitor.cost;
	}
	/**
	 * Get an upper bound for the miss cost involved in invoking a method of length
	 * <pre>invokedBytes</pre> and returning to a method of length <pre>invokerBytes</pre> 
	 * @param invokerBytes
	 * @param invokedByes
	 * @return
	 */
	private long getInvokeReturnMissCost(int invokerBytes, int invokedBytes) {
		int invokerWords = (invokerBytes + 3) / 4;
		int invokedWords = (invokedBytes + 3) / 4;
		int invokerCost = Math.max(0,
									WCETInstruction.calculateB(false, invokedWords) - 
									WCETInstruction.MIN_HIDDEN_LOAD_CYCLES);
		int invokedCost = Math.max(0,
									WCETInstruction.calculateB(false, invokerWords) - 
									WCETInstruction.MIN_HIDDEN_LOAD_CYCLES);
		return invokerCost+invokedCost;
	}
	/**
	 * Compute the maximal cache-miss penalty when executing m.
	 * <p>
	 * Precondition: The set of all methods reachable from m fit into the cache
	 * </p>
	 * <p>
     * Algorithm: If all methods reachable from <code>m</code> (including <code>m</code>) fit 
     * into the cache, we can compute the WCET of <m> using the {@link ALWAYS_HIT@} cache
     * approximation, and then add the sum of cache miss penalties for every reachable method.
	 * <p>
	 * <strong>[UPDATED]</strong> 
	 * Explanation: We know that there is only one cache miss per method, but we do not know
	 * when it will occur (on return or invoke). Let <code>H</code> be the number of cycles
	 * hidden by <strong>any</strong> return or invoke instructions. Then the cache
	 * miss penalty is bounded by <code>(b-h)</code> per method. The root method is not
	 * taken into account, as the root's methods penalty is attributed to the caller.
	 * </p><p>
	 * <code>b</code> is giben by <code>b = 6 + (n+1) * (2+c)</code>, with <code>n</code>
	 * being the method length of the receiver (invoke) or caller (return) in words 
	 * and <code>c</code> being the cache-read wait time.
	 * </p>
     * 
	 * @param m The root method
	 * @return the cache miss penalty
	 * 
	 */
	private long cacheMissPenalty(MethodInfo m) {
		long miss = 0;
		Iterator<CallGraphNode> iter = project.getCallGraph().getReachableMethods(m);
		while(iter.hasNext()) {
			CallGraphNode n = iter.next();
			if(n.getMethodImpl() == null) continue;
			if(n.getMethodImpl().equals(m)) continue;
			int words = (project.getFlowGraph(n.getMethodImpl()).getNumberOfBytes() + 3) / 4;
			int thisMiss = Math.max(0,WCETInstruction.calculateB(false, words) - 
					                  WCETInstruction.MIN_HIDDEN_LOAD_CYCLES);	
			logger.debug("Adding cache miss penalty for "+n.getMethodImpl() + " from " + m + ": " + thisMiss);
			miss+=thisMiss;
		}
		return miss;
	}
	/**
	 * Compute the number of cache blocks which might be needed when calling this method
	 * @param mi
	 * @return the maximum number of cache blocks needed, s.t. we won't run out of cache
	 * blocks when invoking the given method
	 * @throws TypeException 
	 */
	public long getMaxCacheBlocks(MethodInfo mi) {
		long size = requiredNumberOfBlocks(mi);
		Iterator<CallGraphNode> iter = project.getCallGraph().getReachableMethods(mi);
		while(iter.hasNext()) {
			CallGraphNode n = iter.next();
			if(n.isAbstractNode()) continue;
			size+= requiredNumberOfBlocks(n.getMethodImpl());
		}
		return size;
	}
	private long requiredNumberOfBlocks(MethodInfo m) {
		int M = config.blockSize();
		return ((project.getFlowGraph(m).getNumberOfBytes()+M-1) / M);
	}
}
