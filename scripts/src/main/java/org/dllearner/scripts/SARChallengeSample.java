package org.dllearner.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.dllearner.utilities.OWLAxiomCBDGenerator;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.manchester.cs.owl.owlapi.OWLDataPropertyImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLNamedIndividualImpl;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

public class SARChallengeSample {
    private static final Logger logger = Logger.getLogger(SARChallengeSample.class.getName());
    private final static String dumpFilePath = "../examples/sar/dump_shrunken.nt";
    private final static String responderFilePathTemplate = "../examples/sar/responder_%04d.txt";
    private final static String nonResponderFilePathTemplate = "../examples/sar/non_responder_%04d.txt";
    private final static String sampledDatasetPathTemplate = "../examples/sar/sampled__%04d_%d.nt";
    private final static OWLDataProperty nonResponder = new OWLDataPropertyImpl(IRI.create("http://bio2rdf.org/ra.challenge_vocabulary:non-responder"));

    public static void main(String[] args) throws Exception {
        logger.setLevel(Level.DEBUG);
        Set<String> sampleURIs = new HashSet<String>();

        logger.debug("loading dataset...");
        OWLOntology wholeDump = readDump(dumpFilePath);
        logger.debug("finished loading dataset");
        


        OWLAxiomCBDGenerator cbdGenerator = new OWLAxiomCBDGenerator(wholeDump);


        List<Integer> sampeSizes = new ArrayList<Integer>(Arrays.asList(5, 10, 20));

        for (Integer sampleSize : sampeSizes) {
            logger.debug("loading samples...");
            String responderFilePath = String.format(responderFilePathTemplate, sampleSize);
            sampleURIs.addAll(readSamples(responderFilePath));
            String nonResponderFilePath = String.format(nonResponderFilePathTemplate, sampleSize);
            sampleURIs.addAll(readSamples(nonResponderFilePath));
            logger.debug("finished loading samples");

            int cbdDepth = 1;
            while (cbdDepth < 10) {
                Set<OWLAxiom> sampledAxioms = sample(wholeDump, sampleURIs, cbdGenerator, cbdDepth);
                String sampledDatasetPath = String.format(sampledDatasetPathTemplate, sampleSize, cbdDepth);
                
                OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                OWLOntology sampleOnt = manager.createOntology();
                manager.addAxioms(sampleOnt, sampledAxioms);
                FileOutputStream outputStream = new FileOutputStream(new File(sampledDatasetPath));
                manager.saveOntology(sampleOnt, new TurtleOntologyFormat(), outputStream);

                cbdDepth++;
            }
        }
    }

    private static Set<OWLAxiom> sample(OWLOntology wholeDump, Set<String> sampleURIs, OWLAxiomCBDGenerator cbdGenerator, int cbdDepth) {
        logger.info("sampling dataset with depth " + cbdDepth + "...");
        long start = System.currentTimeMillis();
        Set<OWLAxiom> cbd = new HashSet<OWLAxiom>();

        for (String uriStr : sampleURIs) {
            logger.debug("getting CBD for " + uriStr + "...");
            OWLIndividual ind = new OWLNamedIndividualImpl(IRI.create(uriStr));
            cbd.addAll(cbdGenerator.getCBD(ind, cbdDepth));
            
            for (Iterator<OWLAxiom> iter =  cbd.iterator(); iter.hasNext();) {
                OWLAxiom owlAxiom = iter.next();
                AxiomType<?> axiomType = owlAxiom.getAxiomType();
                // post processing
                // - OWLClassAsserionAxiom
                // - OWLObjectPropertyAxiom
                // - OWLDataPropertyAxiom
                if (!axiomType.equals(AxiomType.CLASS_ASSERTION) &&
                        !axiomType.equals(AxiomType.OBJECT_PROPERTY_ASSERTION) &&
                        !axiomType.equals(AxiomType.DATA_PROPERTY_ASSERTION)) {
                    iter.remove();
                } else if (owlAxiom.getAxiomType() == AxiomType.DATA_PROPERTY_ASSERTION) {
                    if (((OWLDataPropertyAssertionAxiom) owlAxiom).getProperty().equals(nonResponder)) {
                        iter.remove();
                    }
                }
            }
        }
        long end = System.currentTimeMillis();
        logger.info("finished sampling dataset with depth " + cbdDepth + " in " + (end-start) + "ms");

        return cbd;
    }

//    private static Model postProcessCBD(Model cbd, Model dataset) {
//        Model newAxioms = ModelFactory.createDefaultModel();
//        StmtIterator sttmntIt = cbd.listStatements();
//
//        while (sttmntIt.hasNext()) {
//            Statement sttmnt = sttmntIt.next();
//            // get information about properties
//            Property prop = sttmnt.getPredicate();
//            newAxioms.add(getPropertyAxioms(prop, dataset));
//        }
//
//        // add class type statements and further information about the class
//        // from the dataset
//        StmtIterator clsSttmntIt = cbd.listStatements(null, RDF.type, (RDFNode) null);
//        while (clsSttmntIt.hasNext()) {
//            Statement sttmnt = clsSttmntIt.next();
//            RDFNode object = sttmnt.getObject();
//            Statement typeSttmnt = ResourceFactory.createStatement(
//                    object.asResource(), RDF.type, OWL.Class);
//            newAxioms.add(typeSttmnt);
//            newAxioms.add(getResourceAxioms(object.asResource(), dataset));
//        }
//
//        cbd.add(newAxioms);
//        return cbd;
//    }
//
//    private static Model getPropertyAxioms(Property prop, Model dataset) {
//        Model propAxioms = ModelFactory.createDefaultModel();
//        StmtIterator propSttmntIt = dataset.listStatements(prop.asResource(), null, (RDFNode) null);
//
//        while (propSttmntIt.hasNext()) {
//            Statement propSttmnt = propSttmntIt.next();
//            // TODO: get and add further information?
//            propAxioms.add(propSttmnt);
//        }
//
//        String constructStr =
//                "CONSTRUCT { ?s ?p ?o } " +
//                    "WHERE { " +
//                        "?s <" + OWL.onProperty.getURI() + "> <" + prop.getURI() + "> ." +
//                        "?s ?p ?o " +
//                    "}";
//        Query query = QueryFactory.create(constructStr);
//        QueryExecution qe = QueryExecutionFactory.create(query, dataset);
//        Model restrModel = qe.execConstruct();
//        propAxioms.add(restrModel);
//
//        return propAxioms;
//    }

    private static Model getResourceAxioms(Resource res, Model dataset) {
        String constructStr =
                "Prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "Prefix owl: <http://www.w3.org/2002/07/owl#> " +
                "CONSTRUCT { ?s ?p ?o } " +
                    "WHERE { " +
                        "<" + res.getURI() + "> (rdfs:subClassOf|owl:equivalentClass|owl:disjointWith)* ?s . " +
                        "?s ?p ?o . " +
                    "}";
        Query query = QueryFactory.create(constructStr);
        QueryExecution qe = QueryExecutionFactory.create(query, dataset);
        Model resAxioms = qe.execConstruct();

        return resAxioms;
    }

    private static OWLOntology readDump(String dumpFilePath) throws OWLOntologyCreationException {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = man.loadOntologyFromOntologyDocument(new File(dumpFilePath));

        return ontology;
    }

    private static Set<String> readSamples(String filePath) throws IOException {
        Set<String> sampleURIs = new HashSet<String>();
        BufferedReader buffRead = new BufferedReader(new FileReader(
                new File(filePath)));

        String line;

        while ((line = buffRead.readLine()) != null) {
            sampleURIs.add(line.trim().substring(1, line.length()-1));
        }

        buffRead.close();

        return sampleURIs;
    }
}
