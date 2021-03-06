/**
 * 
 */
package org.dllearner.test;

import java.util.SortedSet;

import org.apache.jena.riot.RDFDataMgr;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.kb.LocalModelBasedSparqlEndpointKS;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.reasoning.SPARQLReasoner;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.owl.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLIndividual;

import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * @author Lorenz Buehmann
 *
 */
public class SPARQLReasonerTest {


	public static void main(String[] args) throws Exception{
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		Model model = RDFDataMgr.loadModel("../examples/swore/swore.rdf");
        AbstractReasonerComponent rc = new SPARQLReasoner(new LocalModelBasedSparqlEndpointKS(model));
        rc.init();
        
        OWLClass classToDescribe = new OWLClassImpl(IRI.create("http://ns.softwiki.de/req/CustomerRequirement"));
        SortedSet<OWLIndividual> posExamples = rc.getIndividuals(classToDescribe);
        SortedSet<OWLIndividual> negExamples = rc.getIndividuals();
        negExamples.removeAll(posExamples);
		
        RhoDRDown op = new RhoDRDown();
        op.setReasoner(rc);
        op.setUseAllConstructor(false);
        op.setUseHasValueConstructor(false);
        op.setUseNegation(false);
        op.init();
		
//		PosNegLP lp = new PosNegLPStandard(rc);
//		lp.setPositiveExamples(posExamples);
//		lp.setNegativeExamples(negExamples);
//		lp.init();
		
		ClassLearningProblem lp = new ClassLearningProblem(rc);
		lp.setUseInstanceChecks(false);
		lp.setClassToDescribe(classToDescribe);
		lp.init();
		
		CELOE alg = new CELOE(lp, rc);
		alg.setOperator(op);
		alg.setMaxExecutionTimeInSeconds(60);
		alg.setWriteSearchTree(true);
		alg.setSearchTreeFile("log/search-tree.log");
		alg.setReplaceSearchTree(true);
		alg.init();
		
		alg.start();
	}

}
