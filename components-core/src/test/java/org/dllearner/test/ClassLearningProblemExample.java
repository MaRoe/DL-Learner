/**
 * 
 */
package org.dllearner.test;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractKnowledgeSource;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.learningproblems.EvaluatedDescriptionClass;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owlapi.dlsyntax.DLSyntaxObjectRenderer;

/**
 * Simple test class that learns a description of a given class.
 * @author Lorenz Buehmann
 *
 */
public class ClassLearningProblemExample {
	
	public static void main(String[] args) throws Exception {
		ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
		
		// load a knowledge base
		String ontologyPath = "../examples/swore/swore.rdf";
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = man.loadOntologyFromOntologyDocument(new File(ontologyPath));
		AbstractKnowledgeSource source = new OWLAPIOntology(ontology);
		source.init();
		
		// set up a closed-world reasoner
		AbstractReasonerComponent reasoner = new ClosedWorldReasoner(source);
		reasoner.init();
		
		// create a learning problem and set the class to describe
		ClassLearningProblem lp = new ClassLearningProblem(reasoner);
		lp.setClassToDescribe(new OWLClassImpl(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")));
		lp.init();
		
		// create the learning algorithm
		final AbstractCELA la = new CELOE(lp, reasoner);
		la.init();
	
		Timer timer = new Timer();
		timer.schedule(new TimerTask(){
			int progress = 0;
			List<EvaluatedDescriptionClass> result;
			@Override
			public void run() {
				if(la.isRunning()){
					System.out.println(la.getCurrentlyBestEvaluatedDescriptions());
				}
			}
			
		}, 1000, 500);
		
		// start the algorithm and print the best concept found
		la.start();
		timer.cancel();
		List<? extends EvaluatedDescription> currentlyBestEvaluatedDescriptions = la.getCurrentlyBestEvaluatedDescriptions(0.8);
		System.out.println(currentlyBestEvaluatedDescriptions);
	}

}
