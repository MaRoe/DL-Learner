package org.dllearner.utilities.owl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

public class OWLEntityTypeAdder {

	/**
	 * Infers the type of predicates p_i by analyzing the object of the triples using p_i and adds the
	 * entity type assertion to the model, i.e. for a data property dp <dp a owl:DatatypeProperty>
	 * will be added.
	 * @param model
	 */
	public static void addEntityTypes(Model model){
		StmtIterator iterator = model.listStatements();
		Set<Property> objectPropertyPredicates = new HashSet<Property>();
		Set<Property> dataPropertyPredicates = new HashSet<Property>();
		while(iterator.hasNext()){
			Statement st = iterator.next();
			Property predicate = st.getPredicate();
			if(!predicate.getURI().startsWith(RDF.getURI()) && !predicate.getURI().startsWith(RDFS.getURI()) 
					&& !predicate.getURI().startsWith(OWL.getURI())){
				RDFNode object = st.getObject();
				if(object.isLiteral()){
					dataPropertyPredicates.add(predicate);
				} else if(object.isResource()){
					objectPropertyPredicates.add(predicate);
				}
			}
		}
		iterator.close();
		for (Property property : dataPropertyPredicates) {
			if(!objectPropertyPredicates.contains(property)){
				model.add(property, RDF.type, OWL.DatatypeProperty);
			}
		}
		for (Property property : objectPropertyPredicates) {
			if(!dataPropertyPredicates.contains(property)){
				model.add(property, RDF.type, OWL.ObjectProperty);
			}
		}
	}
	
	private void getClasses(Model model){
		//get all classes
		NodeIterator iterator = model.listObjectsOfProperty(RDF.type);
		while(iterator.hasNext()){
			RDFNode object = iterator.next();
			if(object.isURIResource()){
				String uri = object.asResource().getURI();
				if(!uri.startsWith(RDF.getURI()) 
						&& !uri.startsWith(RDFS.getURI()) 
						&& !uri.startsWith(OWL.getURI())){
					System.out.println("<" + uri + "> <" + RDF.type.getURI() + "> <" + OWL.Class.getURI() + "> .");
					
				}

			}
		}
		iterator.close();
	}
	
	private void getPropertyTypes(Model model){
		Set<Property> objectProperties = new HashSet<Property>();
		Set<Property> dataProperties = new HashSet<Property>();
		Set<Property> annotationProperties = new HashSet<Property>();
		
		//get all properties
		Set<Property> properties = new HashSet<Property>();
		StmtIterator iterator = model.listStatements();
		while(iterator.hasNext()){
			Statement st = iterator.next();
			Property predicate = st.getPredicate();
			if(!predicate.getURI().startsWith(RDF.getURI()) 
					&& !predicate.getURI().startsWith(RDFS.getURI()) 
					&& !predicate.getURI().startsWith(OWL.getURI())){
				properties.add(predicate);
			}
		}
		iterator.close();
		
		boolean useLinkedData = true;
		boolean useInstanceData = true;
		Iterator<Property> propertiesIter = properties.iterator();
		while(propertiesIter.hasNext()) {
			Property property = propertiesIter.next();
			
			//check for type in model
			boolean declared = false;
			if(model.contains(property, RDF.type, OWL.ObjectProperty)){
				objectProperties.add(property);
				declared = true;
			} else if(model.contains(property, RDF.type, OWL.DatatypeProperty)){
				dataProperties.add(property);
				declared = true;
			} else if(model.contains(property, RDF.type, OWL.AnnotationProperty)){
				annotationProperties.add(property);
				declared = true;
			}
			
			//use Linked Data
			if(useLinkedData && !declared){
				try {
					Model ldModel = ModelFactory.createDefaultModel();
					ldModel.read(property.getURI());
					
					if(ldModel.contains(property, RDF.type, OWL.ObjectProperty)){
						objectProperties.add(property);
						declared = true;
					} else if(ldModel.contains(property, RDF.type, OWL.DatatypeProperty)){
						dataProperties.add(property);
						declared = true;
					} else if(model.contains(property, RDF.type, OWL.AnnotationProperty)){
						annotationProperties.add(property);
						declared = true;
					}
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
			
			//use instance data
			if(useInstanceData && !declared){
				NodeIterator objectsIterator = model.listObjectsOfProperty(property);
				boolean isObjectProperty = false;
				boolean isDataProperty = false;
				while(objectsIterator.hasNext()){
					RDFNode object = objectsIterator.next();
					
					if(object.isLiteral()){
						isDataProperty = true;
					} else if(object.isResource()){
						isObjectProperty = true;
					}
				}
				objectsIterator.close();
				
				if(isObjectProperty && !isDataProperty){
					objectProperties.add(property);
				} else if(isDataProperty && !isObjectProperty){
					dataProperties.add(property);
				}
			}
		}
		
		for (Property property : dataProperties) {
			model.add(property, RDF.type, OWL.DatatypeProperty);
			System.out.println("<" + property.getURI() + "> <" + RDF.type.getURI() + "> <" + OWL.DatatypeProperty.getURI() + "> .");
		}
		for (Property property : objectProperties) {
			model.add(property, RDF.type, OWL.ObjectProperty);
			System.out.println("<" + property.getURI() + "> <" + RDF.type.getURI() + "> <" + OWL.ObjectProperty.getURI() + "> .");
		}
		for (Property property : annotationProperties) {
			model.add(property, RDF.type, OWL.AnnotationProperty);
			System.out.println("<" + property.getURI() + "> <" + RDF.type.getURI() + "> <" + OWL.AnnotationProperty.getURI() + "> .");
		}
	}

}
