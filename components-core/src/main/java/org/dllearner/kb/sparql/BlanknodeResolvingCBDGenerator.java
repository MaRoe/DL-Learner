/**
 * 
 */
package org.dllearner.kb.sparql;

import java.util.List;

import org.aksw.jena_sparql_api.model.QueryExecutionFactoryModel;

import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * @author Lorenz Buehmann
 *
 */
public class BlanknodeResolvingCBDGenerator implements ConciseBoundedDescriptionGenerator{
	
	private QueryExecutionFactoryModel qef;
	boolean resolveBlankNodes = true;
	private Model extendedModel;

	public BlanknodeResolvingCBDGenerator(Model model) {
		String query = "prefix : <http://dl-learner.org/ontology/> "
				+ "construct { ?s ?p ?o ; ?type ?s .} "
				+ "where {  ?s ?p ?o .  bind( if(isIRI(?s),:sameIRI,:sameBlank) as ?type )}";
//				+ "where {  ?s ?p ?o . bind( if(isBlank(?s),:sameBlank,BNODE()) as ?type )}";
	
		qef = new QueryExecutionFactoryModel(model);
		QueryExecution qe = qef.createQueryExecution(query);
		extendedModel = qe.execConstruct();
		qe.close();
		
		qef = new QueryExecutionFactoryModel(extendedModel);
	}
	
	/**
	 * @return the extendedModel
	 */
	public Model getExtendedModel() {
		return extendedModel;
	}
	
	/**
	 * @return the qef
	 */
	public QueryExecutionFactoryModel getQef() {
		return qef;
	}

	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#getConciseBoundedDescription(java.lang.String)
	 */
	@Override
	public Model getConciseBoundedDescription(String resourceURI) {
		return getConciseBoundedDescription(resourceURI, 1);
	}

	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#getConciseBoundedDescription(java.lang.String, int)
	 */
	@Override
	public Model getConciseBoundedDescription(String resourceURI, int depth) {
		return getConciseBoundedDescription(resourceURI, depth, false);
	}
	
	public Model getConciseBoundedDescription2(String resourceURI, int depth){
		String queryString = "prefix : <http://dl-learner.org/ontology/> "
				+ "CONSTRUCT{?x ?px ?ox. ?z ?pz ?oz .} WHERE {?s (!<u>|!<v>){,%DEPTH%} ?x. ?x ?px ?ox . FILTER(?px NOT IN (:sameIRI, :sameBlank)) OPTIONAL{?ox ((!<u>|!<v>)/:sameBlank)* ?z . FILTER NOT EXISTS{?ox :sameIRI ?ox} ?z ?pz ?oz . FILTER(?pz NOT IN (:sameIRI, :sameBlank))}}";
		queryString = queryString.replace("%DEPTH%", String.valueOf(depth-1));
		
		ParameterizedSparqlString query = new ParameterizedSparqlString(queryString);
		query.setIri("s", resourceURI);
		
		System.out.println(QueryFactory.create(query.toString(), Syntax.syntaxARQ));
		
		QueryExecution qe = qef.createQueryExecution(QueryFactory.create(query.toString(), Syntax.syntaxARQ));
		Model cbd = qe.execConstruct();
		qe.close();
		return cbd;
		
	}

	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#getConciseBoundedDescription(java.lang.String, int, boolean)
	 */
	@Override
	public Model getConciseBoundedDescription(String resourceURI, int depth, boolean withTypesForLeafs) {
		if(depth < 1){
			throw new IllegalArgumentException("Min depth for CBD is 1.");
		}
		StringBuilder constructTemplate;
		if(depth == 1 && resolveBlankNodes){
			constructTemplate = new StringBuilder("?x ?p ?o .");
		} else {
			constructTemplate = new StringBuilder("?s0 ?p0 ?o0 .");
			for(int i = 1; i < depth; i++){
				constructTemplate.append("?o").append(i-1).append(" ?p").append(i).append(" ?o").append(i).append(" .");
			}
			if(resolveBlankNodes){
				constructTemplate.append("?x ?p ?o .");
			}
		}
		
		
		
		
		String blankNodeExpression = "((!<x>|!<y>)/:sameBlank)* ?x . ?x ?p ?o .filter(?p NOT IN(:sameIRI, :sameBlank))";
		StringBuilder triplesTemplate;
		if(depth == 1 && resolveBlankNodes){
			triplesTemplate = new StringBuilder("?s0 " + blankNodeExpression);
		} else {
			triplesTemplate = new StringBuilder("?s0 ?p0 ?o0 .");
			triplesTemplate.append("FILTER(?p0 !=:sameIRI)");
			for(int i = 1; i < depth; i++){
				triplesTemplate.append("OPTIONAL{").append("?o").append(i-1).append(" ?p").append(i).append(" ?o").append(i).append(" .");
				triplesTemplate.append("FILTER(").append("?p").append(i).append(" NOT IN (:sameIRI, :sameBlank))");
			}
			if(resolveBlankNodes){
				triplesTemplate.append("OPTIONAL{?o").append(depth-1).append(blankNodeExpression);
				triplesTemplate.append("FILTER NOT EXISTS{?o").append(depth-1).append(" :sameIRI ?o").append(depth-1).append("}");
				triplesTemplate.append("}");
			}
			for(int i = 1; i < depth; i++){
				triplesTemplate.append("}");
			}
		}
		
		StringBuilder queryString = new StringBuilder("prefix : <http://dl-learner.org/ontology/> ");
		queryString.append("CONSTRUCT{").append(constructTemplate).append("}");
		queryString.append(" WHERE {").append(triplesTemplate).append("}");
		
		ParameterizedSparqlString query = new ParameterizedSparqlString(queryString.toString());
		query.setIri("s0", resourceURI);
//		System.out.println(query.asQuery());
		QueryExecution qe = qef.createQueryExecution(query.toString());
		Model cbd = qe.execConstruct();
		qe.close();
		return cbd;
	}

	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#setRestrictToNamespaces(java.util.List)
	 */
	@Override
	public void setRestrictToNamespaces(List<String> namespaces) {
	}

	/* (non-Javadoc)
	 * @see org.dllearner.kb.sparql.ConciseBoundedDescriptionGenerator#setRecursionDepth(int)
	 */
	@Override
	public void setRecursionDepth(int maxRecursionDepth) {
	}

}
