/**
 * 
 */
package org.dllearner.algorithms.isle.index;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLEntity;

/**
 * @author Lorenz Buehmann
 *
 */
public interface Index {
	
	/**
     * Returns a set of documents based on how the underlying index is processing the given
     * search string.
     *
     * @param searchString query specifying the documents to retrieve
     * @return set of documents retrieved based on the given query string
     */
	Set<AnnotatedDocument> getDocuments(OWLEntity entity);
	
	/**
     * Returns a set of documents based on how the underlying index is processing the given
     * search string.
     *
     * @param searchString query specifying the documents to retrieve
     * @return set of documents retrieved based on the given query string
     */
	long getNumberOfDocumentsFor(OWLEntity entity);
	
	/**
     * Returns a set of documents based on how the underlying index is processing the given
     * search string.
     *
     * @param searchString query specifying the documents to retrieve
     * @return set of documents retrieved based on the given query string
     */
	long getNumberOfDocumentsFor(OWLEntity... entities);

	/**
     * Returns the total number of documents contained in the index.
     *
     * @return the total number of documents contained in the index
     */
	long getTotalNumberOfDocuments();
}
