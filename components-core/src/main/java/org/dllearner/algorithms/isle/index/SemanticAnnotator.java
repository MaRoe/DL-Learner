package org.dllearner.algorithms.isle.index;

import java.util.HashSet;
import java.util.Set;

import org.dllearner.algorithms.isle.EntityCandidateGenerator;
import org.dllearner.algorithms.isle.WordSenseDisambiguation;
import org.dllearner.core.owl.Entity;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * Provides methods to annotate documents.
 *
 * @author Daniel Fleischhacker
 */
public class SemanticAnnotator {
	
    private OWLOntology ontology;
	private WordSenseDisambiguation wordSenseDisambiguation;
	private EntityCandidateGenerator entityCandidateGenerator;
	private LinguisticAnnotator linguisticAnnotator;
	

    /**
     * Initialize this semantic annotator to use the entities from the provided ontology.
     *
     * @param ontology the ontology to use entities from
     */
    public SemanticAnnotator(OWLOntology ontology, WordSenseDisambiguation wordSenseDisambiguation, 
    		EntityCandidateGenerator entityCandidateGenerator, LinguisticAnnotator linguisticAnnotator) {
        this.ontology = ontology;
		this.wordSenseDisambiguation = wordSenseDisambiguation;
		this.entityCandidateGenerator = entityCandidateGenerator;
		this.linguisticAnnotator = linguisticAnnotator;
    }

    /**
     * Processes the given document and returns the annotated version of this document.
     *
     * @param document the document to annotate
     * @return the given document extended with annotations
     */
    public AnnotatedDocument processDocument(TextDocument document){
    	Set<Annotation> annotations = linguisticAnnotator.annotate(document);
    	Set<SemanticAnnotation> semanticAnnotations = new HashSet<SemanticAnnotation>();
    	for (Annotation annotation : annotations) {
    		Set<Entity> candidateEntities = entityCandidateGenerator.getCandidates(annotation);
    		SemanticAnnotation semanticAnnotation = wordSenseDisambiguation.disambiguate(annotation, candidateEntities);
    		semanticAnnotations.add(semanticAnnotation);
    		
		}
    	AnnotatedDocument annotatedDocument = new AnnotatedTextDocument(document, semanticAnnotations);
    	return annotatedDocument;
    }
}