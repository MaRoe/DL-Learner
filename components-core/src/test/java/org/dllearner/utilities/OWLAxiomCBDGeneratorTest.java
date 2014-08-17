/**
 * 
 */
package org.dllearner.utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Set;

import org.dllearner.kb.sparql.BlanknodeResolvingCBDGenerator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import edu.stanford.nlp.util.Sets;

/**
 * @author Lorenz Buehmann
 *
 */
public class OWLAxiomCBDGeneratorTest {
	
	private static final Logger logger = LoggerFactory.getLogger(OWLAxiomCBDGeneratorTest.class);

	/**
	 * Test method for {@link org.dllearner.utilities.OWLAxiomCBDGenerator#getCBD(org.semanticweb.owlapi.model.OWLIndividual, int)}.
	 * @throws OWLOntologyCreationException 
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testGetCBD() throws OWLOntologyCreationException, FileNotFoundException {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
<<<<<<< HEAD
//		File f = new File("../examples/sar/dump_complete.nt");
//		Model model = ModelFactory.createDefaultModel();
//		model.read(new FileInputStream(f), null, "TURTLE");
//		getClasses(model);
//		getPropertyTypes(model);
		
		
		File file = new File("../examples/sar/dump_cleaned_10.nt");
=======
		File f = new File("../examples/sar/dump_cleaned_10.nt");
		Model model = ModelFactory.createDefaultModel();
		model.read(new FileInputStream(f), null, "TURTLE");
		
		File file = new File("../examples/sar/dump_cleaned.nt");
>>>>>>> de37ad91cdf9a15adf14c5372ddef578376a341e
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(file);
//		System.out.println(ontology.getClassesInSignature());
//		System.out.println(ontology.getIndividualsInSignature());
		
//		System.out.println(ontology.getIndividualsInSignature());
		
		
		
		OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(ontology);
		Set<OWLAxiom> cbdAxioms = cbdGenerator.getCBD(df.getOWLNamedIndividual(IRI.create("http://bio2rdf.org/ra.challenge:1000000")), 3);
		
		cbdAxioms = cbdGenerator.getCBD(df.getOWLNamedIndividual(IRI.create("http://bio2rdf.org/ra.challenge:1000000")), 4);
		
		cbdGenerator.setFetchCompleteRelatedTBox(true);
		cbdAxioms = cbdGenerator.getCBD(df.getOWLNamedIndividual(IRI.create("http://bio2rdf.org/ra.challenge:1000000")), 4);
	}
	
	@Test
	public void testCompareAxiomBasedCBDToTripleBasedCBD() throws Exception{
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		File file = new File("../examples/sar/dump_cleaned.nt");
		File outputDir = new File("data");
		outputDir.mkdir();
		
		System.out.println("Loading data...");
		long start = System.currentTimeMillis();
		// load into JENA model
		Model model = ModelFactory.createDefaultModel();
		model.read(new FileInputStream(file), null, "TURTLE");
		
		// load into OWL API ontology
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		model.write(baos, "TURTLE");
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new ByteArrayInputStream(baos.toByteArray()));
//		OWLOntology ontology = man.loadOntologyFromOntologyDocument(file);
		long end = System.currentTimeMillis();
		System.out.println("...done in " + (end - start) + "ms");
		
		OWLAxiomCBDGenerator axiomCBDGenerator = new OWLAxiomCBDGenerator(ontology);
//		axiomCBDGenerator.setFetchCompleteRelatedTBox(true);
		BlanknodeResolvingCBDGenerator tripleCBDGenerator = new BlanknodeResolvingCBDGenerator(model);
		
		String resourceURI = "http://bio2rdf.org/ra.challenge:1000000";
		
		System.out.println("Comparing CBDs...");
		for(int depth = 1; depth <= 5; depth++){
			System.out.println("#############CBD-" + depth);
			
			// compute CBD based on OWL axioms
			start = System.currentTimeMillis();
			Set<OWLAxiom> cbdAxioms = axiomCBDGenerator
					.getCBD(df.getOWLNamedIndividual(IRI.create(resourceURI)), depth);
			OWLOntology ont1 = man.createOntology(cbdAxioms);
			end = System.currentTimeMillis();
			System.out.println("Axiom based CBD took " + (end - start) + "ms");

			// compute CBD based on triples and convert to OWL API axioms
			start = System.currentTimeMillis();
			Model cbd = tripleCBDGenerator.getConciseBoundedDescription(resourceURI, depth, true);
			baos = new ByteArrayOutputStream();
			cbd.write(baos, "TURTLE");
			OWLOntology ont2 = man.loadOntologyFromOntologyDocument(new ByteArrayInputStream(baos.toByteArray())); 
			end = System.currentTimeMillis();
			System.out.println("Triple based CBD took " + (end - start) + "ms");
			cbd.write(new FileOutputStream(new File(outputDir, "cbd-" + depth + ".ttl")), "TURTLE", "http://bio2rdf.org/");
			
			// compare both ontologies
			System.out.println("Classes: ");
			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getClassesInSignature(), ont2.getClassesInSignature()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getClassesInSignature(), ont2.getClassesInSignature()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getClassesInSignature(), ont1.getClassesInSignature()));
			System.out.println("Object Properties: ");
			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getObjectPropertiesInSignature(), ont2.getObjectPropertiesInSignature()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getObjectPropertiesInSignature(), ont2.getObjectPropertiesInSignature()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getObjectPropertiesInSignature(), ont1.getObjectPropertiesInSignature()));
			System.out.println("Data Properties: ");
			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getDataPropertiesInSignature(), ont2.getDataPropertiesInSignature()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getDataPropertiesInSignature(), ont2.getDataPropertiesInSignature()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getDataPropertiesInSignature(), ont1.getDataPropertiesInSignature()));
			System.out.println("Logical axioms: ");
			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getLogicalAxioms(), ont2.getLogicalAxioms()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getLogicalAxioms(), ont2.getLogicalAxioms()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getLogicalAxioms(), ont1.getLogicalAxioms()));
		}
	}
	
	@Test
	public void testGetCBD2() throws OWLOntologyCreationException, FileNotFoundException {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new File("../examples/swore/swore.rdf"));
		
		OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(ontology);
		Set<OWLAxiom> cbdAxioms = cbdGenerator.getCBD(df.getOWLNamedIndividual(IRI.create("http://ns.softwiki.de/req/CreateModernGUIDesign")), 3);
	}
	
	

}
