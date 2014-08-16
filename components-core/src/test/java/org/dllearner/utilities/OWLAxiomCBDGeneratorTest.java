/**
 * 
 */
package org.dllearner.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.dllearner.utilities.owl.DLSyntaxObjectRenderer;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

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

/**
 * @author Lorenz Buehmann
 *
 */
public class OWLAxiomCBDGeneratorTest {

	/**
	 * Test method for {@link org.dllearner.utilities.OWLAxiomCBDGenerator#getCBD(org.semanticweb.owlapi.model.OWLIndividual, int)}.
	 * @throws OWLOntologyCreationException 
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testGetCBD() throws OWLOntologyCreationException, FileNotFoundException {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
//		File f = new File("../examples/sar/dump_cleaned_10.nt");
//		Model model = ModelFactory.createDefaultModel();
//		model.read(new FileInputStream(f), null, "TURTLE");
//		getClasses(model);
//		getPropertyTypes(model);
		
		
		File file = new File("../examples/sar/dump_cleaned_10.nt");
//		file = new File("punning_bug.ttl");
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(file);
//		System.out.println(ontology.getClassesInSignature());
//		System.out.println(ontology.getIndividualsInSignature());
		
//		System.out.println(ontology.getIndividualsInSignature());
		
		
		
		OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(ontology);
		Set<OWLAxiom> cbdAxioms = cbdGenerator.getCBD(df.getOWLNamedIndividual(IRI.create("http://bio2rdf.org/ra.challenge:1000000")), 2);
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
