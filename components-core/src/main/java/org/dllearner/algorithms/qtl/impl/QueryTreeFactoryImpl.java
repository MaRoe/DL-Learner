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
package org.dllearner.algorithms.qtl.impl;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.dllearner.algorithms.qtl.QueryTreeFactory;
import org.dllearner.algorithms.qtl.datastructures.impl.QueryTreeImpl;
import org.dllearner.algorithms.qtl.datastructures.impl.QueryTreeImpl.NodeType;
import org.dllearner.algorithms.qtl.filters.Filter;
import org.dllearner.algorithms.qtl.filters.Filters;
import org.dllearner.algorithms.qtl.filters.KeywordBasedStatementFilter;
import org.dllearner.algorithms.qtl.filters.ZeroFilter;
import org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator;
import org.dllearner.kb.sparql.ConciseBoundedDescriptionGeneratorImpl;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.OWL2;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * 
 * @author Lorenz Bühmann
 *
 */
public class QueryTreeFactoryImpl implements QueryTreeFactory<String> {
	
	private int nodeId;
	private Comparator<Statement> comparator;
	private Set<String> predicateFilters;
	
	private Filter predicateFilter = new ZeroFilter();
	private Filter objectFilter = new ZeroFilter();
	private Selector statementSelector = new SimpleSelector();
	private com.hp.hpl.jena.util.iterator.Filter<Statement> keepFilter;
	
	private int maxDepth = 3;
	
	private Set<String> allowedNamespaces = Sets.newHashSet(RDF.getURI());
	private Set<String> ignoredProperties = Sets.newHashSet(OWL.sameAs.getURI());
	
	public QueryTreeFactoryImpl(){
		comparator = new StatementComparator();
		predicateFilters = new HashSet<String>(Filters.getAllFilterProperties());
	}
	
	public void setPredicateFilter(Filter filter){
		this.predicateFilter = filter;
	}
	
	public void setObjectFilter(Filter filter){
		this.objectFilter = filter;
	}
	
	/**
	 * @param maxDepth the maxDepth to set
	 */
	public void setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}
	
	@Override
	public void setStatementSelector(Selector selector) {
		this.statementSelector = selector;
		
	}
	
	@Override
	public void setStatementFilter(com.hp.hpl.jena.util.iterator.Filter<Statement> statementFilter) {
		this.keepFilter = statementFilter;
		
	}
	
	@Override
	public QueryTreeImpl<String> getQueryTree(String example, Model model) {
		if(keepFilter == null){
			return createTree(model.getResource(example), model);
		} else {
			return createTreeOptimized(model.getResource(example), model);
		}
	}
	
	@Override
	public QueryTreeImpl<String> getQueryTree(String example, Model model, int maxEdges) {
		if(keepFilter == null){
			return createTree(model.getResource(example), model);
		} else {
			return createTreeOptimized(model.getResource(example), model, maxEdges);
		}
	}

	@Override
	public QueryTreeImpl<String> getQueryTree(Resource example, Model model) {
		return createTree(example, model);
	}
	
	@Override
	public QueryTreeImpl<String> getQueryTree(String example) {
		return new QueryTreeImpl<String>(example);
	}
	
	private QueryTreeImpl<String> createTreeOptimized(Resource s, Model model, int maxEdges){
		nodeId = 0;
		SortedMap<String, SortedSet<Statement>> resource2Statements = new TreeMap<String, SortedSet<Statement>>();
		
		fillMap(s, model, resource2Statements);	
	
		KeywordBasedStatementFilter filter = (KeywordBasedStatementFilter)keepFilter;
		Set<Statement> statements;
		int diff = valueCount(resource2Statements) - maxEdges;
		main:while(diff > 0){
			double oldThreshold = filter.getThreshold();
			statements = filter.getStatementsBelowThreshold(oldThreshold+0.1);
			for(SortedSet<Statement> set : resource2Statements.values()){
				for(Statement st : statements){
					if(set.remove(st)){
						diff--;
						if(diff == 0){
							break main;
						}
					}
				}
			}
		}
		
		
		QueryTreeImpl<String> tree = new QueryTreeImpl<String>("?", NodeType.VARIABLE);
		int depth = 0;
		fillTree(s.toString(), tree, resource2Statements, depth);
				
		return tree;
	}
	
	private int valueCount(SortedMap<String, SortedSet<Statement>> map){
		int cnt = 0;
		for(SortedSet<Statement> statements : map.values()){
			cnt += statements.size();
		}
		return cnt;
	}
	
	private QueryTreeImpl<String> createTreeOptimized(Resource s, Model model){
		if(keepFilter != null){
			Model filteredModel = ModelFactory.createDefaultModel();
			ExtendedIterator<Statement> iter = model.listStatements().filterKeep(keepFilter);
			while(iter.hasNext()){
				Statement st = iter.next();
				filteredModel.add(st);
			}
			model = filteredModel;
		}
		nodeId = 0;
		SortedMap<String, SortedSet<Statement>> resource2Statements = new TreeMap<String, SortedSet<Statement>>();
		
		fillMap(s, model, resource2Statements);	
		
		QueryTreeImpl<String> tree = new QueryTreeImpl<String>("?", NodeType.VARIABLE);
		int depth = 0;
		fillTree(s.toString(), tree, resource2Statements, depth);
				
		return tree;
	}
	
	private void fillMap(Resource s, Model model, SortedMap<String, SortedSet<Statement>> resource2Statements){
		com.hp.hpl.jena.util.iterator.Filter<Statement> nsFilter = null;
		if(allowedNamespaces.size() > 1){
			nsFilter = new com.hp.hpl.jena.util.iterator.Filter<Statement>() {

				@Override
				public boolean accept(Statement st) {
					boolean valid = false;
					if(st.getSubject().isURIResource()){
						for (String ns : allowedNamespaces) {
							if(st.getSubject().getURI().startsWith(ns)){
								valid = true;
								break;
							}
						}
					} else {
						valid = true;
					}
					if(valid){
						valid = false;
						if(!ignoredProperties.contains(st.getPredicate().getURI())){
							for (String ns : allowedNamespaces) {
								if(st.getPredicate().getURI().startsWith(ns)){
									valid = true;
									break;
								}
							}
						}
					}
					if(valid){
						if(st.getObject().isURIResource()){
							valid = false;
							for (String ns : allowedNamespaces) {
								if(st.getObject().asResource().getURI().startsWith(ns)){
									valid = true;
									break;
								}
							}
						}
					}
					
					return valid;
				}
			};
		}
		Iterator<Statement> it;
		if(nsFilter != null){
			it = model.listStatements(s, null, (RDFNode)null).filterKeep(nsFilter);
		} else {
			it = model.listStatements(s, null, (RDFNode)null);
		}
		
		Statement st;
		SortedSet<Statement> statements;
		while(it.hasNext()){
			st = it.next();
			if(!ignoredProperties.contains(st.getPredicate().getURI())){
				statements = resource2Statements.get(st.getSubject().toString());
				if(statements == null){
					statements = new TreeSet<Statement>(comparator);
					resource2Statements.put(st.getSubject().toString(), statements);
				}
				statements.add(st);
				if((st.getObject().isResource()) && !resource2Statements.containsKey(st.getObject().toString())){
					fillMap(st.getObject().asResource(), model, resource2Statements);
				}
			}
		}
	}
	
	private void filter(Model model){
		model.remove(model.listStatements(null, RDF.type, OWL.Class));
		model.remove(model.listStatements(null, RDF.type, OWL2.NamedIndividual));
	}
	
	private QueryTreeImpl<String> createTree(Resource s, Model model){
		filter(model);
		nodeId = 0;
		SortedMap<String, SortedSet<Statement>> resource2Statements = new TreeMap<String, SortedSet<Statement>>();
		
//		Statement st;
//		SortedSet<Statement> statements;
//		Iterator<Statement> it = model.listStatements(statementSelector);
//		while(it.hasNext()){
//			st = it.next();
//			statements = resource2Statements.get(st.getSubject().toString());
//			if(statements == null){
//				statements = new TreeSet<Statement>(comparator);
//				resource2Statements.put(st.getSubject().toString(), statements);
//			}
//			statements.add(st);
//		}
		
		fillMap(s, model, resource2Statements);	
		
		QueryTreeImpl<String> tree = new QueryTreeImpl<String>("?", NodeType.VARIABLE);
		tree.setId(nodeId++);
		int depth = 0;
		fillTree(s.toString(), tree, resource2Statements, depth);
				
		tree.setUserObject("?");
		return tree;
	}
	
	private void fillTree(String root, QueryTreeImpl<String> tree, SortedMap<String, SortedSet<Statement>> resource2Statements, int depth){
		depth++;
			if(resource2Statements.containsKey(root)){
				QueryTreeImpl<String> subTree;
				Property predicate;
				RDFNode object;
				for(Statement st : resource2Statements.get(root)){
					predicate = st.getPredicate();
					object = st.getObject();
					if(!predicateFilter.isRelevantResource(predicate.getURI())){
						continue;
					}
					if(predicateFilters.contains(st.getPredicate().toString())){
						continue;
					}
					if(object.isLiteral()){
						Literal lit = st.getLiteral();
						String escapedLit = lit.getLexicalForm().replace("\"", "\\\"");
						StringBuilder sb = new StringBuilder();
						sb.append("\"").append(escapedLit).append("\"");
						if(lit.getDatatypeURI() != null){
							sb.append("^^<").append(lit.getDatatypeURI()).append(">");
						}
						if(!lit.getLanguage().isEmpty()){
							sb.append("@").append(lit.getLanguage());
						}
						subTree = new QueryTreeImpl<String>(sb.toString(), NodeType.LITERAL);
//						subTree = new QueryTreeImpl<String>(lit.toString());
						subTree.setId(nodeId++);
						if(lit.getDatatype() == XSDDatatype.XSDinteger 
								|| lit.getDatatype() == XSDDatatype.XSDdouble 
								|| lit.getDatatype() == XSDDatatype.XSDdate
								|| lit.getDatatype() == XSDDatatype.XSDint
								|| lit.getDatatype() == XSDDatatype.XSDdecimal){
							subTree.addLiteral(lit);
						} else {
							subTree.addLiteral(lit);
						}
						
						tree.addChild(subTree, st.getPredicate().toString());
					} else if(objectFilter.isRelevantResource(object.asResource().getURI())){
						if(!tree.getUserObjectPathToRoot().contains(st.getObject().toString())){
							subTree = new QueryTreeImpl<String>(st.getObject().toString(), NodeType.RESOURCE);
							subTree.setId(nodeId++);
							tree.addChild(subTree, st.getPredicate().toString());
							if(depth < maxDepth){
								fillTree(st.getObject().toString(), subTree, resource2Statements, depth);
							}
							if(object.isAnon()){
								subTree.setIsBlankNode(true);
							}
							
						}
					} else if(object.isAnon()){
						if(depth < maxDepth &&
								!tree.getUserObjectPathToRoot().contains(st.getObject().toString())){
							subTree = new QueryTreeImpl<String>(st.getObject().toString(), NodeType.RESOURCE);
							subTree.setIsResourceNode(true);
							subTree.setId(nodeId++);
							tree.addChild(subTree, st.getPredicate().toString());
							fillTree(st.getObject().toString(), subTree, resource2Statements, depth);
						}
					}
				}
			}
		depth--;
	}
	
	class StatementComparator implements Comparator<Statement>{

		@Override
		public int compare(Statement s1, Statement s2) {
//			if(s1.getPredicate() == null && s2.getPredicate() == null){
//				return 0;
//			}
//			return s1.getPredicate().toString().compareTo(s2.getPredicate().toString())
//			+ s1.getObject().toString().compareTo(s2.getObject().toString());
			if(s1.getPredicate() == null && s2.getPredicate() == null){
				return 0;
			}
			
			if(s1.getPredicate().toString().compareTo(s2.getPredicate().toString()) == 0){
				return s1.getObject().toString().compareTo(s2.getObject().toString());
			} else {
				return s1.getPredicate().toString().compareTo(s2.getPredicate().toString());
			}
			
		}

		
		
	}
	
	public static String encode(String s) {
        char [] htmlChars = s.toCharArray();
        StringBuffer encodedHtml = new StringBuffer();
        for (int i=0; i<htmlChars.length; i++) {
            switch(htmlChars[i]) {
            case '<':
                encodedHtml.append("&lt;");
                break;
            case '>':
                encodedHtml.append("&gt;");
                break;
            case '&':
                encodedHtml.append("&amp;");
                break;
            case '\'':
                encodedHtml.append("&#39;");
                break;
            case '"':
                encodedHtml.append("&quot;");
                break;
            case '\\':
                encodedHtml.append("&#92;");
                break;
            case (char)133:
                encodedHtml.append("&#133;");
                break;
            default:
                encodedHtml.append(htmlChars[i]);
                break;
            }
        }
        return encodedHtml.toString();
    }

	/* (non-Javadoc)
	 * @see org.dllearner.algorithms.qtl.QueryTreeFactory#addAllowedNamespaces(java.util.Set)
	 */
	@Override
	public void addAllowedNamespaces(Set<String> allowedNamespaces) {
		this.allowedNamespaces.addAll(allowedNamespaces);
	}

	/* (non-Javadoc)
	 * @see org.dllearner.algorithms.qtl.QueryTreeFactory#addIgnoredPropperties(java.util.Set)
	 */
	@Override
	public void addIgnoredPropperties(Set<String> ignoredProperties) {
		this.ignoredProperties.addAll(ignoredProperties);
	}
	
	public static void main(String[] args) throws Exception {
		QueryTreeFactoryImpl factory = new QueryTreeFactoryImpl();
		ConciseBoundedDescriptionGenerator cbdGen = new ConciseBoundedDescriptionGeneratorImpl(SparqlEndpoint.getEndpointDBpediaLOD2Cloud());
		String resourceURI = "http://dbpedia.org/resource/Athens";
		Model cbd = cbdGen.getConciseBoundedDescription(resourceURI, 0);
		QueryTreeImpl<String> queryTree = factory.getQueryTree(resourceURI, cbd);
		System.out.println(queryTree.toSPARQLQueryString());
	}

}
