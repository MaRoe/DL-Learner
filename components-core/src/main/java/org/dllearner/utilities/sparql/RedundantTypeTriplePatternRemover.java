/**
 * 
 */
package org.dllearner.utilities.sparql;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.dllearner.kb.sparql.QueryExecutionFactoryHttp;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.ElementVisitorBase;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * @author Lorenz Buehmann
 *
 */
public class RedundantTypeTriplePatternRemover extends ElementVisitorBase{
	
	private static final ParameterizedSparqlString superClassesQueryTemplate = new ParameterizedSparqlString(
			"SELECT ?sup WHERE {?sub rdfs:subClassOf+ ?sup .}");
	
	
	private QueryExecutionFactory qef;

	public RedundantTypeTriplePatternRemover(QueryExecutionFactory qef) {
		this.qef = qef;
	}
	
	/**
	 * Returns a pruned copy of the given query.
	 * @param query
	 * @return
	 */
	public Query pruneQuery(Query query) {
		Query copy = query.cloneQuery();
		copy.getQueryPattern().visit(this);
		return copy;
	}
	
	private Set<Node> getSuperClasses(Node cls){
		Set<Node> superClasses = new HashSet<Node>();
		
		superClassesQueryTemplate.setIri("sub", cls.getURI());
		
		String query = superClassesQueryTemplate.toString();
		QueryExecution qe = qef.createQueryExecution(query);
		ResultSet rs = qe.execSelect();
		while(rs.hasNext()){
			QuerySolution qs = rs.next();
			superClasses.add(qs.getResource("sup").asNode());
		}
		qe.close();
		
		return superClasses;
	}
	
	@Override
	public void visit(ElementGroup el) {
		for (Iterator<Element> iterator = el.getElements().iterator(); iterator.hasNext();) {
			Element e = iterator.next();
			e.visit(this);
		}
	}

	@Override
	public void visit(ElementOptional el) {
		el.getOptionalElement().visit(this);
	}

	@Override
	public void visit(ElementTriplesBlock el) {
		// get all rdf:type triple patterns
		Multimap<Node, Triple> subject2TypeTriples = HashMultimap.create();
		for (Iterator<Triple> iterator = el.patternElts(); iterator.hasNext();) {
			Triple t = iterator.next();
			if(t.getPredicate().matches(RDF.type.asNode())) {
				subject2TypeTriples.put(t.getSubject(), t);
			}
		}
		
		// check for semantically redundant triple patterns
		Set<Triple> redundantTriples = new HashSet<Triple>();
		for (Entry<Node, Collection<Triple>> entry : subject2TypeTriples.asMap().entrySet()) {
			Collection<Triple> triples = entry.getValue();
			
			// get all super classes
			Set<Node> superClasses = new HashSet<Node>();
			for (Triple triple : triples) {
				Node cls = triple.getObject();
				superClasses.addAll(getSuperClasses(cls));
			}
			
			for (Triple triple : triples) {
				Node cls = triple.getObject();
				if(superClasses.contains(cls)) {
					redundantTriples.add(triple);
				}
			}
		}
		
		// remove redundant triple patterns
		for (Iterator<Triple> iterator = el.patternElts(); iterator.hasNext();) {
			Triple t = iterator.next();
			if(redundantTriples.contains(t)) {
				iterator.remove();
			}
		}
	}

	@Override
	public void visit(ElementPathBlock el) {
		// get all rdf:type triple patterns
		Multimap<Node, Triple> subject2TypeTriples = HashMultimap.create();
		for (Iterator<TriplePath> iterator = el.patternElts(); iterator.hasNext();) {
			TriplePath t = iterator.next();
			if (t.isTriple() && t.getPredicate().matches(RDF.type.asNode())) {
				subject2TypeTriples.put(t.getSubject(), t.asTriple());
			}
		}

		// check for semantically redundant triple patterns
		Set<Triple> redundantTriples = new HashSet<Triple>();
		for (Entry<Node, Collection<Triple>> entry : subject2TypeTriples.asMap().entrySet()) {
			Collection<Triple> triples = entry.getValue();

			// get all super classes
			Set<Node> superClasses = new HashSet<Node>();
			for (Triple triple : triples) {
				Node cls = triple.getObject();
				superClasses.addAll(getSuperClasses(cls));
			}

			for (Triple triple : triples) {
				Node cls = triple.getObject();
				if (superClasses.contains(cls)) {
					redundantTriples.add(triple);
				}
			}
		}

		// remove redundant triple patterns
		for (Iterator<TriplePath> iterator = el.patternElts(); iterator.hasNext();) {
			TriplePath t = iterator.next();
			if (t.isTriple() && redundantTriples.contains(t.asTriple())) {
				iterator.remove();
			}
		}
	}

	@Override
	public void visit(ElementUnion el) {
		for (Iterator<Element> iterator = el.getElements().iterator(); iterator.hasNext();) {
			Element e = iterator.next();
			e.visit(this);
		}
	}
	
	public static void main(String[] args) throws Exception {
		String query = "SELECT DISTINCT  ?x0\n" + 
				"WHERE\n" + 
				"  { ?x0  <http://dbpedia.org/ontology/capital>  ?x7 ;\n" + 
				"         <http://dbpedia.org/ontology/currency>  <http://dbpedia.org/resource/West_African_CFA_franc> ;\n" + 
				"         <http://dbpedia.org/ontology/foundingDate>  ?x12 ;\n" + 
				"         <http://dbpedia.org/ontology/governmentType>  ?x13 ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Country> ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Place> ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/PopulatedPlace> ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Wikidata:Q532> .\n" + 
				"    ?x7  <http://dbpedia.org/ontology/country>  ?x8 ;\n" + 
				"         <http://dbpedia.org/ontology/elevation>  ?x9 ;\n" + 
				"         <http://dbpedia.org/ontology/isPartOf>  ?x10 ;\n" + 
				"         <http://dbpedia.org/ontology/populationTotal>  ?x11 ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Place> ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/PopulatedPlace> ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Settlement> ;\n" + 
				"         <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>  <http://dbpedia.org/ontology/Wikidata:Q532> .\n" + 
				"  }";
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
		QueryExecutionFactory qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		RedundantTypeTriplePatternRemover remover = new RedundantTypeTriplePatternRemover(qef);
		System.out.println(remover.pruneQuery(QueryFactory.create(query)));
	}
	
	

}
