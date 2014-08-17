/**
 * 
 */
package org.dllearner.kb.sparql;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.Test;

import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * @author Lorenz Buehmann
 *
 */
public class BlanknodeResolvingCBDGeneratorTest {
	
	String source = "@prefix : <http://ex.org/> ."
		+ ":A :p :B ;"
		+ "   :v :W ;"
		+ "   :q [ :r :C , :D ] ."
		+ ":B :q :D, :E ;"
		+ "   :t [ :p :K , :L ] ."
		+ ":C :r :F; "
		+ "   :t [ :p :K , :L ] .";
//		+ ":D :r :F , :G .";
//		+ "   :s :A , :B .";

	/**
	 * Test method for {@link org.dllearner.kb.sparql.BlanknodeResolvingCBDGenerator#getConciseBoundedDescription(java.lang.String, int)}.
	 */
	@Test
	public void testGetConciseBoundedDescription() {
		Model model = ModelFactory.createDefaultModel();
		model.read(new ByteArrayInputStream(source.getBytes()), null, "TURTLE");
		model.write(System.out, "TURTLE" , "http://ex.org/");
		
		BlanknodeResolvingCBDGenerator cbdGenerator = new BlanknodeResolvingCBDGenerator(model);
		cbdGenerator.getExtendedModel().write(System.out, "TURTLE" , "http://ex.org/");
		
		String query = "PREFIX  :     <http://dl-learner.org/ontology/> "
				+ "CONSTRUCT  { ?x ?p ?o .} "
				+ "WHERE  { "
				+ "<http://ex.org/A> ?p0 ?o0. ?o0 ((!<x>|!<y>)/:sameBlank)* ?x ."
				+ "    ?x ?p ?o "
				+ "   FILTER ( ! ( ?p IN (:sameIri, :sameBlank) ) )  "
				+ "}";
		
//		System.out.println(QueryFactory.create(query));
		
		Model test = cbdGenerator.getQef().createQueryExecution(query).execConstruct();
//		test.write(System.out, "TURTLE" , "http://ex.org/");
		
		String resource = "http://ex.org/A";
		Model cbd = cbdGenerator.getConciseBoundedDescription(resource, 4);
		
		cbd.write(System.out, "TURTLE" , "http://ex.org/");
	}
	
	/**
	 * Test method for {@link org.dllearner.kb.sparql.BlanknodeResolvingCBDGenerator#getConciseBoundedDescription(java.lang.String, int)}.
	 * @throws FileNotFoundException 
	 */
	@Test
	public void testGetConciseBoundedDescription2() throws FileNotFoundException {
		Model model = ModelFactory.createDefaultModel();
		model.read(new FileInputStream("../examples/sar/"), null, "TURTLE");
		
		BlanknodeResolvingCBDGenerator cbdGenerator = new BlanknodeResolvingCBDGenerator(model);
		cbdGenerator.getExtendedModel().write(System.out, "TURTLE" , "http://ex.org/");
		
		String resource = "http://bio2rdf.org/ra.challenge:1000000";
		Model cbd = cbdGenerator.getConciseBoundedDescription(resource, 1);
		
		cbd.write(System.out, "TURTLE" , "http://bio2rdf.org/ra.challenge:");
	}
	
	@Test
	public void test(){
		String s = "@prefix : <http://ex.org/> . @prefix owl: <http://www.w3.org/2002/07/owl#> . :a :p :b . :b :p :c . :c a [owl:intersectionOf(:o1 :o2)] .";
		
		Model model = ModelFactory.createDefaultModel();
		model.read(new ByteArrayInputStream(s.getBytes()), null, "TURTLE");
		model.write(System.out, "TURTLE" , "http://ex.org/");
		
		BlanknodeResolvingCBDGenerator cbdGenerator = new BlanknodeResolvingCBDGenerator(model);
		cbdGenerator.getExtendedModel().write(System.out, "TURTLE" , "http://ex.org/");
		
		Model cbd = cbdGenerator.getConciseBoundedDescription("http://ex.org/a", 2);
		cbd.write(System.out, "TURTLE" , "http://ex.org/");
	}

}
