package org.dllearner.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.algorithms.celoe.OEHeuristicRuntime;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.KnowledgeSource;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.learningproblems.PosNegLPStandard;
import org.dllearner.reasoning.MaterializableFastInstanceChecker;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.owl.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.model.OWLOntology;

public class SARChallenge {
    private static final Logger logger = Logger.getLogger(SARChallenge.class.getName());
//    private final static String dumpFilePath = "../examples/sar/dump.nt";
    private final static String dumpFilePath = "../examples/sar/sampled_0020_5.nt";
    private final static String responderFilePathTemplate = "../examples/sar/responder_%04d.txt";
    private final static String nonResponderFilePathTemplate = "../examples/sar/non_responder_%04d.txt";
    private final static int numSamples = 20;  // possible values: 500, 200, 100, 50, 20, 10 5

    public static void main(String[] args) throws Exception {
        logger.setLevel(Level.DEBUG);
        ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());

        // loading ontology
        logger.debug("Loading ontology...");
        long start = System.currentTimeMillis();
        OWLOntology ontology = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new File(dumpFilePath)); 
        long end = System.currentTimeMillis();
        logger.debug("Operation took " + (end - start) + "ms");

        // read positive and negative examples
        Set<Individual> posExamples = new HashSet<Individual>();
        Set<Individual> negExamples = new HashSet<Individual>();

        String responderFilePath = String.format(responderFilePathTemplate, numSamples);
        String nonResponderFilePath = String.format(nonResponderFilePathTemplate, numSamples);

        BufferedReader buffRead = new BufferedReader(new FileReader(new File(responderFilePath)));
        String line;
        while ((line = buffRead.readLine()) != null) {
            line = line.trim();
            line = line.substring(1, line.length()-1);  // strip off quotes
            posExamples.add(new Individual(line));
        }
        buffRead.close();
        
        buffRead = new BufferedReader(new FileReader(new File(nonResponderFilePath)));
        while ((line = buffRead.readLine()) != null) {
            line = line.trim();
            line = line.substring(1, line.length()-1);  // strip off quotes
            negExamples.add(new Individual(line));
        }

        // set up knowledge source
        KnowledgeSource ks = new OWLAPIOntology(ontology);
        ks.init();

        // set up reasoner
        logger.info("initializing reasoner...");
        OWLAPIReasoner baseReasoner = new OWLAPIReasoner(ks);
        baseReasoner.setReasonerTypeString("trowl");
        baseReasoner.init();

        // set up reasoner component
        MaterializableFastInstanceChecker rc = new MaterializableFastInstanceChecker(ks);
        rc.setReasonerComponent(baseReasoner);
        rc.setHandlePunning(true);
        rc.setUseMaterializationCaching(false);
        rc.init();

        PosNegLPStandard lp = new PosNegLPStandard(rc);
        lp.setPositiveExamples(posExamples);
        lp.setNegativeExamples(negExamples);
        lp.init();

        // set up learning algorithm
        logger.info("initializing learning algorithm...");
        AbstractCELA la;
        OEHeuristicRuntime heuristic = new OEHeuristicRuntime();
        heuristic.setExpansionPenaltyFactor(0.01);
        la = new CELOE(lp, rc);
        ((CELOE) la).setHeuristic(heuristic);
        ((CELOE) la).setMaxExecutionTimeInSeconds(180);
        ((CELOE) la).setNoisePercentage(50);
        ((CELOE) la).setMaxNrOfResults(100);
        ((CELOE) la).setWriteSearchTree(true);
        ((CELOE) la).setReplaceSearchTree(true);
        ((CELOE) la).setStartClass(new NamedClass("http://xmlns.com/foaf/0.1/Person"));
        RhoDRDown op = new RhoDRDown();
        op.setUseHasValueConstructor(true);
        op.setInstanceBasedDisjoints(true);
        op.setUseNegation(false);
        op.setStartClass(new NamedClass("http://xmlns.com/foaf/0.1/Person"));
        op.setUseHasValueConstructor(false);
        op.setReasoner(rc);
        op.setSubHierarchy(rc.getClassHierarchy());
        op.setObjectPropertyHierarchy(rc.getObjectPropertyHierarchy());
        op.setDataPropertyHierarchy(rc.getDatatypePropertyHierarchy());
        op.init();
        ((CELOE) la).setOperator(op);
        
        la.init();
        la.start();
        /* Note: the line numbers may differ since this trace is from the
         * SourceForge repo
         * 
         * Exception in thread "main" java.util.NoSuchElementException
         *     at java.util.TreeMap.key(TreeMap.java:1221)
         *     at java.util.TreeMap.lastKey(TreeMap.java:292)
         *     at java.util.TreeSet.last(TreeSet.java:401)
         *     at org.dllearner.algorithms.celoe.CELOE.getNextNodeToExpand(CELOE.java:618)
         *     at org.dllearner.algorithms.celoe.CELOE.start(CELOE.java:514)
         *     at org.dllearner.SARChallenge.main(SARChallenge.java:106)
         */
    }
}
