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
package org.dllearner.algorithms.qtl;

import java.util.Set;

import org.dllearner.algorithms.qtl.datastructures.impl.QueryTreeImpl;
import org.dllearner.algorithms.qtl.filters.Filter;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.Statement;

/**
 * 
 * @author Lorenz Bühmann
 *
 */
public interface QueryTreeFactory<N> {
	
	QueryTreeImpl<N> getQueryTree(String example, Model model);
	
	QueryTreeImpl<N> getQueryTree(String example, Model model, int maxEdges);
	
	QueryTreeImpl<N> getQueryTree(Resource example, Model model);
	
	QueryTreeImpl<N> getQueryTree(String example);
	
	void setPredicateFilter(Filter filter);
	
	void setObjectFilter(Filter filter);
	
	void setStatementSelector(Selector selector);
	
	void setStatementFilter(com.hp.hpl.jena.util.iterator.Filter<Statement> filter);
	
	void addAllowedNamespaces(Set<String> allowedNamespaces);
	
	void addIgnoredPropperties(Set<String> ignoredProperties);
	
	void setMaxDepth(int maxDepth);

}
