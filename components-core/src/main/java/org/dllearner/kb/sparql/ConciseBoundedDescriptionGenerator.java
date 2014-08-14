package org.dllearner.kb.sparql;

import java.util.List;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * Generate a Concise Bounded Description (CBD) following the definition in <a href=\"http://www.w3.org/Submission/CBD/\">http://www.w3.org/Submission/CBD/</a>
 * 
 * <p>
 * Given a particular node (the starting node) in a particular RDF graph (the
 * source graph), a subgraph of that particular graph, taken to comprise a
 * concise bounded description of the resource denoted by the starting node, can
 * be identified as follows:
 * 
 * <ol>
 * <li>Include in the subgraph all statements in the source graph where the subject
 * of the statement is the starting node;</li>
 * <li>Recursively, for all statements identified in the subgraph thus far having a
 * blank node object, include in the subgraph all statements in the source graph
 * where the subject of the statement is the blank node in question and which
 * are not already included in the subgraph.</li>
 * <li>Recursively, for all statements included in the subgraph thus far, for all
 * reifications of each statement in the source graph, include the concise
 * bounded description beginning from the rdf:Statement node of each
 * reification.</li>
 * This results in a subgraph where the object nodes are either URI references,
 * literals, or blank nodes not serving as the subject of any statement in the
 * graph.
 * </p>
 * @author Lorenz Buehmann
 *
 */
public interface ConciseBoundedDescriptionGenerator {

	/**
	 * Returns the CBD of depth 1, i.e. all outgoing triples for the given resource.
	 * @param resourceURI the resource
	 * @return the CBD
	 */
	public Model getConciseBoundedDescription(String resourceURI);
	
	/**
	 * Returns the CBD of depth {@code depth}, i.e. starting from the given resource {@code resourceURI} 
	 * all outgoing triples (depth=1) and for each object in the triples also recursively the CBD.
	 * @param resourceURI the resource
	 * @return the CBD
	 */
	public Model getConciseBoundedDescription(String resourceURI, int depth);
	
	/**
	 * Returns the CBD of depth with additional information about the types of the leafs.
	 * @param resourceURI
	 * @param depth
	 * @param withTypesForLeafs
	 * @return
	 */
	public Model getConciseBoundedDescription(String resourceURI, int depth, boolean withTypesForLeafs);
	
	public void setRestrictToNamespaces(List<String> namespaces);
	
	public void setRecursionDepth(int maxRecursionDepth);
	
}
