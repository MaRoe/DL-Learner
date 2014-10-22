package org.dllearner.scripts;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.riot.Lang;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.algorithms.celoe.OEHeuristicRuntime;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.ComponentInitException;
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
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;


public class MouseDiabetes {
    private static final Logger logger = Logger.getLogger(MouseDiabetes.class);
    private static final String dir = "/home/me/work/datasets/mouse/";
    private static final String goFilePath = dir + "go_xp_all-merged_w_refs.nt";
    private static final String mpFilePath = dir + "mp_w_refs.nt";
    private static final String mpEqFilePath = dir + "mp-equivalence-axioms.nt";
    private static final String genoDiseaseFilePath = dir + "geno_disease.nt";
    private static final String genoNotDiseaseFilePath = dir + "geno_notdisease.nt";
    private static final String posExamplesFilePath = dir + "pos_examples.txt";
    private static final String negExamplesFilePath = dir + "neg_examples.txt";

    public static void main(String[] args) throws OWLOntologyCreationException, IOException, ComponentInitException {
        setUp();
        logger.debug("starting...");
        OWLOntology ontology = readDumpFiles();
        logger.debug("reading positive and negative examples...");
        Set<Individual> posExamples = readExamples(posExamplesFilePath);
        Set<Individual> negExamples = readExamples(negExamplesFilePath);
        logger.debug("finished reading examples");
        System.out.println(ontology.getObjectPropertiesInSignature());
        logger.debug("initializing knowledge source...");
        KnowledgeSource ks = new OWLAPIOntology(ontology);
        ks.init();
        logger.debug("finished initializing knowledge source");
        
        logger.debug("initializing reasoner...");
        OWLAPIReasoner baseReasoner = new OWLAPIReasoner(ks);
        baseReasoner.setReasonerTypeString("hermit");
        baseReasoner.init();
        logger.debug("finished initializing reasoner");
        logger.debug("initializing reasoner component...");
        MaterializableFastInstanceChecker rc = new MaterializableFastInstanceChecker(ks);
        rc.setMaterializeExistentialRestrictions(false);
        rc.setReasonerComponent(baseReasoner);
        rc.setHandlePunning(false);
        rc.init();
        logger.debug("finished initializing reasoner");
        
        logger.debug("initializing learning problem...");
        PosNegLPStandard lp = new PosNegLPStandard(rc);
        lp.setPositiveExamples(posExamples);
        lp.setNegativeExamples(negExamples);
        lp.init();
        logger.debug("finished initializing learning problem");
        
        logger.debug("initializing learning algorithm...");
        AbstractCELA la;
        OEHeuristicRuntime heuristic = new OEHeuristicRuntime();
        heuristic.setExpansionPenaltyFactor(0.1);
        la = new CELOE(lp, rc);
        ((CELOE) la).setHeuristic(heuristic);
        ((CELOE) la).setMaxExecutionTimeInSeconds(180);
        ((CELOE) la).setNoisePercentage(50);
        ((CELOE) la).setMaxNrOfResults(100);
        ((CELOE) la).setWriteSearchTree(true);
        ((CELOE) la).setReplaceSearchTree(true);
        ((CELOE) la).setStartClass(new NamedClass("http://dl-learner.org/smallis/Allelic_info"));
        logger.debug("finished initializing learning algorithm");
        logger.debug("initializing operator...");
        RhoDRDown op = new RhoDRDown();
        op.setUseHasValueConstructor(true);
        op.setInstanceBasedDisjoints(true);
        op.setUseNegation(false);
        op.setStartClass(new NamedClass("http://dl-learner.org/smallis/Allelic_info"));
        op.setUseHasValueConstructor(false);
        op.setReasoner(rc);
        op.setSubHierarchy(rc.getClassHierarchy());
        op.setObjectPropertyHierarchy(rc.getObjectPropertyHierarchy());
        op.setDataPropertyHierarchy(rc.getDatatypePropertyHierarchy());
        op.init();
        logger.debug("finished initializing operator");
        ((CELOE) la).setOperator(op);
        
        la.init();
        la.start();
    }
    
    private static Set<Individual> readExamples(String filePath) throws IOException {
        Set<Individual> indivs = new HashSet<Individual>();
        BufferedReader buffRead = new BufferedReader(new FileReader(new File(filePath)));
        String line;
        while ((line = buffRead.readLine()) != null) {
            line = line.trim();
            line = line.substring(1, line.length()-1);  // strip off quotes
            indivs.add(new Individual(line));
        }
        buffRead.close();
        return indivs;
    }
    private static void setUp() {
        logger.setLevel(Level.DEBUG);
        ToStringRenderer.getInstance().setRenderer(new DLSyntaxObjectRenderer());
    }

    private static OWLOntology readDumpFiles() throws OWLOntologyCreationException, FileNotFoundException {
        List<String> filePaths = new ArrayList<String>(Arrays.asList(
                mpFilePath, mpEqFilePath, genoDiseaseFilePath,
                genoNotDiseaseFilePath
//                , goFilePath
                ));
        Model model = ModelFactory.createDefaultModel();
        for (String filePath : filePaths) {
            logger.debug("reading " + filePath);
            model.read(new FileInputStream(new File(filePath)), null,
                    Lang.NTRIPLES.getName());
        }
        logger.debug("finished reading files");
        logger.debug("converting to OWLApi ontology...");
        //convert JENA model to OWL API ontology
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.write(baos , "N-TRIPLES");
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = man.loadOntologyFromOntologyDocument(
                new ByteArrayInputStream(baos.toByteArray()));
        logger.debug("finished conversion");
        
        return ontology;
    }
}
