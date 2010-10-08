/**
 * Copyright (C) 2007-2010, Jens Lehmann
 *
 * This file is part of DL-Learner.
 * 
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.dllearner.sparqlquerygenerator.operations.nbr.strategy;

import java.util.HashSet;
import java.util.Set;

import org.dllearner.sparqlquerygenerator.datastructures.QueryTree;
import org.dllearner.sparqlquerygenerator.datastructures.impl.QueryTreeImpl;

/**
 * 
 * @author Lorenz Bühmann
 *
 */
public class BruteForceNBRStrategy<N> implements NBRStrategy<N> {

	@Override
	public QueryTree<N> computeNBR(QueryTree<N> posExampleTree, Set<QueryTree<N>> negExampleTrees) {
		QueryTree<N> nbr = new QueryTreeImpl<N>(posExampleTree);
		if(subsumesTrees(posExampleTree, negExampleTrees)){
			return nbr;
		}
		
		Set<QueryTree<N>> tested = new HashSet<QueryTree<N>>();
		Object edge;
		QueryTree<N> parent;
		while(!(tested.size() == nbr.getLeafs().size()) ){
			for(QueryTree<N> leaf : nbr.getLeafs()){
				if(leaf.isRoot()){
					return nbr;
				}
				parent = leaf.getParent();
				edge = parent.getEdge(leaf);
				parent.removeChild((QueryTreeImpl<N>)leaf);
				boolean isSubsumedBy = false;
				for(QueryTree<N> negTree : negExampleTrees){
					isSubsumedBy = negTree.isSubsumedBy(nbr);
					if(isSubsumedBy){
						break;
					}
				}
				if(isSubsumedBy){
					tested.add(leaf);
					parent.addChild((QueryTreeImpl<N>)leaf, edge);
				}
				
			}
		}
		return nbr;
	}

	@Override
	public Set<QueryTree<N>> computeNBRs(QueryTree<N> posExampleTree,
			Set<QueryTree<N>> negExampleTrees) {
		
		Set<QueryTree<N>> nbrs = new HashSet<QueryTree<N>>();
		
		compute(posExampleTree, negExampleTrees, nbrs);
		
		return nbrs;
	}
	
	private void compute(QueryTree<N> posExampleTree,
			Set<QueryTree<N>> negExampleTrees, Set<QueryTree<N>> nbrs) {
		
		QueryTree<N> nbr = new QueryTreeImpl<N>(posExampleTree);
		if(subsumesTrees(posExampleTree, negExampleTrees)){
//			nbrs.add(posExampleTree);
			return;
		}
		
		for(QueryTree<N> n : nbrs){
			removeTree(nbr, n);
		}
		if(!subsumesTrees(nbr, negExampleTrees)){
			Set<QueryTree<N>> tested = new HashSet<QueryTree<N>>();
			Object edge;
			QueryTree<N> parent;
			while(!(tested.size() == nbr.getLeafs().size()) ){
				for(QueryTree<N> leaf : nbr.getLeafs()){
					parent = leaf.getParent();
					edge = parent.getEdge(leaf);
					parent.removeChild((QueryTreeImpl<N>)leaf);
					boolean isSubsumedBy = false;
					for(QueryTree<N> negTree : negExampleTrees){
						isSubsumedBy = negTree.isSubsumedBy(nbr);
						if(isSubsumedBy){
							break;
						}
					}
					if(isSubsumedBy){
						tested.add(leaf);
						parent.addChild((QueryTreeImpl<N>)leaf, edge);
					}
					
				}
			}
			nbrs.add(nbr);
			compute(posExampleTree, negExampleTrees, nbrs);
			
		}
		
	}
	
	private boolean subsumesTrees(QueryTree<N> posExampleTree,
			Set<QueryTree<N>> negExampleTrees){
		boolean subsumesTree = false;
		for(QueryTree<N> negTree : negExampleTrees){
			subsumesTree = negTree.isSubsumedBy(posExampleTree);
			if(subsumesTree){
				break;
			}
		}
		return subsumesTree;
	}
	
	private void removeTree(QueryTree<N> tree, QueryTree<N> node){
		Object edge;
		for(QueryTree<N> child1 : node.getChildren()){
			edge = node.getEdge(child1);
			for(QueryTree<N> child2 : tree.getChildren(edge)){
				if(child1.isLeaf() && child1.getUserObject().equals(child2.getUserObject())){
					child2.getParent().removeChild((QueryTreeImpl<N>) child2);
				} else {
					removeTree(child2, child1);
				}
			}
		}
	}

}