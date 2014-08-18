/**
 * 
 */
package org.dllearner.reasoning;

import java.util.HashSet;
import java.util.Set;

import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;

import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

/**
 * @author Lorenz Buehmann
 *
 */
public class OWLPunningDetector {
	
	/**
	 * This object property is used to connect individuals with classes that are also individuals, thus, lead to punning.
	 */
	public static final ObjectProperty punningProperty = new ObjectProperty("http://dl-learner.org/punning/relatedTo");
	
	/**
	 * Checks whether the same IRI denotes both a class and an individual in the ontology.
	 * @param ontology
	 * @param iri
	 * @return
	 */
	public static boolean hasPunning(OWLOntology ontology, NamedClass cls){
		return hasPunning(ontology, IRI.create(cls.getName()));
	}
	
	/**
	 * Checks whether the same IRI denotes both a class and an individual in the ontology.
	 * @param ontology
	 * @param iri
	 * @return
	 */
	public static boolean hasPunning(OWLOntology ontology, IRI iri){
		boolean isClass = ontology.getClassesInSignature().contains(ontology.getOWLOntologyManager().getOWLDataFactory().getOWLClass(iri));
		boolean isIndividual = ontology.getIndividualsInSignature().contains(ontology.getOWLOntologyManager().getOWLDataFactory().getOWLNamedIndividual(iri));
		return isClass && isIndividual;
	}
	
	/**
	 * Returns the classes of the ontology that are also used as individuals, i.e. types of other classes.
	 * @param ontology
	 * @param iri
	 * @return
	 */
	public static Set<OWLClass> getPunningClasses(OWLOntology ontology){
		Set<OWLClass> classes = new HashSet<OWLClass>();
		Set<OWLNamedIndividual> individualsInSignature = ontology.getIndividualsInSignature();
		for (OWLClass cls : ontology.getClassesInSignature()) {
			if(individualsInSignature.contains(new OWLNamedIndividualImpl(cls.getIRI()))){
				classes.add(cls);
			}
//			for (OWLNamedIndividual ind : ontology.getIndividualsInSignature()) {
//				if(cls.getIRI().equals(ind.getIRI())){
//					classes.add(cls);
//					break;
//				}
//			}
		}
		return classes;
	}
	
	/**
	 * Returns the classes of the ontology that are also used as individuals, i.e. types of other classes.
	 * @param ontology
	 * @param iri
	 * @return
	 */
	public static Set<IRI> getPunningIRIs(OWLOntology ontology){
		Set<IRI> classes = new HashSet<IRI>();
		Set<OWLNamedIndividual> individualsInSignature = ontology.getIndividualsInSignature();
		for (OWLClass cls : ontology.getClassesInSignature()) {
			if(individualsInSignature.contains(new OWLNamedIndividualImpl(cls.getIRI()))){
				classes.add(cls.getIRI());
			}
//			for (OWLNamedIndividual ind : ontology.getIndividualsInSignature()) {
//				if(cls.getIRI().equals(ind.getIRI())){
//					classes.add(cls);
//					break;
//				}
//			}
		}
		return classes;
	}
	
	/**
	 * Checks whether the same IRI denotes both a class and an individual in the ontology.
	 * @param ontology
	 * @param iri
	 * @return
	 */
	public static boolean hasPunning(OWLOntology ontology, String iri){
		return hasPunning(ontology, IRI.create(iri));
	}

}
