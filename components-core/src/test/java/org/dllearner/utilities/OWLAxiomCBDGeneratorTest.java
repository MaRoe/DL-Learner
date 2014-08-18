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
import java.util.HashSet;
import java.util.Set;

import org.dllearner.kb.sparql.BlanknodeResolvingCBDGenerator;
import org.dllearner.utilities.owl.DLSyntaxObjectRenderer;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import com.google.common.base.Charsets;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import edu.stanford.nlp.util.Sets;

/**
 * @author Lorenz Buehmann
 *
 */
public class OWLAxiomCBDGeneratorTest {
	
	private static final Logger logger = LoggerFactory.getLogger(OWLAxiomCBDGeneratorTest.class);
	
	@Test
	public void testCompareCBDToSyntacticModule() throws OWLOntologyCreationException, FileNotFoundException {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		String s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty ."
				+ ":B1 a owl:Class .:B2 a owl:Class .:C1 a owl:Class .:C2 a owl:Class ."
				+ ":a :p :b . "
				+ ":b :p :c . "
				+ ":b a [owl:intersectionOf(:B1 :B2)] . "
				+ ":c a [owl:intersectionOf(:C1 :C2)] .";
		
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new StringDocumentSource(s));
		
		
		OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(ontology);
		cbdGenerator.setFetchCompleteRelatedTBox(true);
		
		OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create("http://ex.org/a"));
		
		SyntacticLocalityModuleExtractor moduleExtractor = new SyntacticLocalityModuleExtractor(man, ontology, ModuleType.STAR);
		Set<OWLAxiom> moduleAxioms = moduleExtractor.extract(ind.getSignature());
		Set<OWLAxiom> cbdAxioms = cbdGenerator.getCBD(ind, 3);
		
		System.out.println("Size:");
		System.out.println("CBD:" + cbdAxioms.size());
		System.out.println("Module:" + moduleAxioms.size());
		System.out.println("Only in CBD: " + Sets.diff(cbdAxioms, moduleAxioms) + "\nOnly in module:" + Sets.diff(moduleAxioms, cbdAxioms).toString());
	}

	/**
	 * Test method for {@link org.dllearner.utilities.OWLAxiomCBDGenerator#getCBD(org.semanticweb.owlapi.model.OWLIndividual, int)}.
	 * @throws OWLOntologyCreationException 
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testGetCBD() throws OWLOntologyCreationException, FileNotFoundException {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		String s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty ."
				+ ":B1 a owl:Class .:B2 a owl:Class .:C1 a owl:Class .:C2 a owl:Class ."
				+ ":a :p :b . "
				+ ":b :p :c . "
				+ ":b a [owl:intersectionOf(:B1 :B2)] . "
				+ ":c a [owl:intersectionOf(:C1 :C2)] .";
		
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new StringDocumentSource(s));
		
		
		OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(ontology);
		cbdGenerator.setFetchCompleteRelatedTBox(true);
		
		OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create("http://ex.org/a"));
		
		// depth 1
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty ."
				+ ":a :p :b . ";
		Set<OWLAxiom> referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		Set<OWLAxiom> cbdAxioms = cbdGenerator.getCBD(ind, 1);
		Assert.assertTrue(referenceAxioms.equals(cbdAxioms));
		
		// depth 2
		s = "@prefix : <http://ex.org/> . "
						+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
						+ ":p a owl:ObjectProperty . :B1 a owl:Class . :B2 a owl:Class ."
						+ ":a :p :b . "
						+ ":b :p :c . "
						+ ":b a [owl:intersectionOf(:B1 :B2)] . ";
		referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		cbdAxioms = cbdGenerator.getCBD(ind, 2);
		Assert.assertTrue(referenceAxioms.equals(cbdAxioms));
		
		// depth 3
		cbdAxioms = cbdGenerator.getCBD(ind, 3);
		Assert.assertTrue(ontology.getLogicalAxioms().equals(cbdAxioms));
	}
	
	/**
	 * Test method for {@link org.dllearner.utilities.OWLAxiomCBDGenerator#getCBD(org.semanticweb.owlapi.model.OWLIndividual, int)}.
	 * @throws OWLOntologyCreationException 
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testGetCBDWithPunning() throws OWLOntologyCreationException, FileNotFoundException {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		String s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty ."
				+ ":B a owl:Class . :C a owl:Class . :D a owl:Class . :E a owl:Class . :F a owl:Class . :F1 a owl:Class . :F2 a owl:Class ."
				+ ":a :p :b . "
				+ ":b a :B ."
				+ ":B rdfs:subClassOf :C . :C rdfs:subClassOf :D ."
				+ ":B :p :e ."
				+ ":B a :E ."
				+ ":E rdfs:subClassOf :F . :F rdfs:subClassOf [owl:intersectionOf(:F1 :F2)] ."
				+ ":e :p :f .";
		
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = man.getOWLDataFactory();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new StringDocumentSource(s));
		
		
		OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(ontology);
		cbdGenerator.setFetchCompleteRelatedTBox(false);
		cbdGenerator.setAllowPunning(false);
		
		OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create("http://ex.org/a"));
		
		// depth 1
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty ."
				+ ":a :p :b . ";
		Set<OWLAxiom> referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		Set<OWLAxiom> cbdAxioms = cbdGenerator.getCBD(ind, 1);
		Assert.assertTrue("Only in CBD: " + Sets.diff(cbdAxioms, referenceAxioms) + "\nOnly in Reference:" + Sets.diff(referenceAxioms, cbdAxioms).toString(),
				referenceAxioms.equals(cbdAxioms));
		
		// depth 2
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty . :B a owl:Class ."
				+ ":a :p :b . "
				+ ":b a :B . ";
		referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		cbdAxioms = cbdGenerator.getCBD(ind, 2);
		Assert.assertTrue("Only in CBD: " + Sets.diff(cbdAxioms, referenceAxioms) + "\nOnly in Reference:" + Sets.diff(referenceAxioms, cbdAxioms).toString(),
				referenceAxioms.equals(cbdAxioms));
		
		// depth 3
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty . :B a owl:Class . :C a owl:Class ."
				+ ":a :p :b . "
				+ ":b a :B . "
				+ ":B rdfs:subClassOf :C . ";
		referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		cbdAxioms = cbdGenerator.getCBD(ind, 3);
		Assert.assertTrue("Only in CBD: " + Sets.diff(cbdAxioms, referenceAxioms) + "\nOnly in Reference:" + Sets.diff(referenceAxioms, cbdAxioms).toString(),
				referenceAxioms.equals(cbdAxioms));
		
		// depth 3 + full related TBox retrieval
		cbdGenerator.setFetchCompleteRelatedTBox(true);
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty . :B a owl:Class . :C a owl:Class . :D a owl:Class ."
				+ ":a :p :b . "
				+ ":b a :B . "
				+ ":B rdfs:subClassOf :C . :C rdfs:subClassOf :D .";
		referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		cbdAxioms = cbdGenerator.getCBD(ind, 3);
		Assert.assertTrue("Only in CBD: " + Sets.diff(cbdAxioms, referenceAxioms) + "\nOnly in Reference:" + Sets.diff(referenceAxioms, cbdAxioms).toString(),
				referenceAxioms.equals(cbdAxioms));
		
		// depth 3 + OWL punning
		cbdGenerator.setFetchCompleteRelatedTBox(false);
		cbdGenerator.setAllowPunning(true);
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty . :B a owl:Class . :C a owl:Class . :E a owl:Class ."
				+ ":a :p :b . "
				+ ":b a :B . "
				+ ":B rdfs:subClassOf :C . "
				+ ":B :p :e ."
				+ ":B a :E .";
		referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		cbdAxioms = cbdGenerator.getCBD(ind, 3);
		Assert.assertTrue("Only in CBD: " + Sets.diff(cbdAxioms, referenceAxioms) + "\nOnly in Reference:" + Sets.diff(referenceAxioms, cbdAxioms).toString(),
				referenceAxioms.equals(cbdAxioms));
		
		// depth 3 + OWL punning + TBox retrieval
		cbdGenerator.setFetchCompleteRelatedTBox(true);
		cbdGenerator.setAllowPunning(true);
		s = "@prefix : <http://ex.org/> . "
				+ "@prefix owl: <http://www.w3.org/2002/07/owl#> . "
				+ ":p a owl:ObjectProperty . :B a owl:Class . :C a owl:Class . :D a owl:Class . :E a owl:Class . :F a owl:Class . :F1 a owl:Class . :F2 a owl:Class ."
				+ ":a :p :b . "
				+ ":b a :B . "
				+ ":B rdfs:subClassOf :C . :C rdfs:subClassOf :D ."
				+ ":B :p :e ."
				+ ":B a :E ."
				+ ":E rdfs:subClassOf :F . :F rdfs:subClassOf [owl:intersectionOf(:F1 :F2)] ."
				+ ":e :p :f .";
		referenceAxioms = new HashSet<OWLAxiom>(man.loadOntologyFromOntologyDocument(new StringDocumentSource(s)).getLogicalAxioms());
		cbdAxioms = cbdGenerator.getCBD(ind, 3);
		Assert.assertTrue("Only in CBD: " + Sets.diff(cbdAxioms, referenceAxioms) + "\nOnly in Reference:" + Sets.diff(referenceAxioms, cbdAxioms).toString(),
				referenceAxioms.equals(cbdAxioms));
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
		System.out.println("Total size:" + ontology.getLogicalAxiomCount());
		System.out.println("OPs:" + ontology.getObjectPropertiesInSignature());
		
		OWLAxiomCBDGenerator axiomCBDGenerator = new OWLAxiomCBDGenerator(ontology);
		axiomCBDGenerator.setAllowPunning(true);
		axiomCBDGenerator.setFetchCompleteRelatedTBox(true);
		BlanknodeResolvingCBDGenerator tripleCBDGenerator = new BlanknodeResolvingCBDGenerator(model);
		
		String resourceURI = "http://bio2rdf.org/ra.challenge:1000000";
		
		System.out.println("Comparing CBDs...");
		for(int depth = 1; depth <= 8; depth++){
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
			Model cbd = tripleCBDGenerator.getConciseBoundedDescription(resourceURI, 1, true);
			baos = new ByteArrayOutputStream();
			cbd.write(baos, "TURTLE");
			OWLOntology ont2 = man.loadOntologyFromOntologyDocument(new ByteArrayInputStream(baos.toByteArray())); 
			end = System.currentTimeMillis();
			System.out.println("Triple based CBD took " + (end - start) + "ms");
			cbd.write(new FileOutputStream(new File(outputDir, "cbd-" + depth + ".ttl")), "TURTLE", "http://bio2rdf.org/");
			
//			String queryString = tripleCBDGenerator.getQuery().toString();
//			queryString = "PREFIX : <http://dl-learner.org/ontology/> SELECT * WHERE " + queryString.substring(queryString.indexOf("WHERE")+5);
//			
//			com.google.common.io.Files.write(
//					ResultSetFormatter.asText(tripleCBDGenerator.getQef().createQueryExecution(queryString).execSelect()), 
//					new File(outputDir, "cbd_resultset-" + depth + ".txt"), Charsets.UTF_8); 
			
			
			// compare both ontologies
			System.out.println("Size:");
			System.out.println("Axiom based:" + ont1.getLogicalAxiomCount());
			System.out.println("Triple based:" + ont2.getLogicalAxiomCount());
			System.out.println("Classes: ");
//			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getClassesInSignature(), ont2.getClassesInSignature()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getClassesInSignature(), ont2.getClassesInSignature()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getClassesInSignature(), ont1.getClassesInSignature()));
			System.out.println("Object Properties: ");
//			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getObjectPropertiesInSignature(), ont2.getObjectPropertiesInSignature()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getObjectPropertiesInSignature(), ont2.getObjectPropertiesInSignature()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getObjectPropertiesInSignature(), ont1.getObjectPropertiesInSignature()));
			System.out.println("Data Properties: ");
//			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getDataPropertiesInSignature(), ont2.getDataPropertiesInSignature()));
			System.out.println("\t\t\tOnly axiom  based:" + Sets.diff(ont1.getDataPropertiesInSignature(), ont2.getDataPropertiesInSignature()));
			System.out.println("\t\t\tOnly triple based:" + Sets.diff(ont2.getDataPropertiesInSignature(), ont1.getDataPropertiesInSignature()));
			System.out.println("Logical axioms: ");
//			System.out.println("\t\t\tOverlap:" + Sets.intersection(ont1.getLogicalAxioms(), ont2.getLogicalAxioms()));
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
