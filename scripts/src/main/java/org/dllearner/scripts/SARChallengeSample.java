package org.dllearner.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.riot.Lang;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.BlanknodeResolvingCBDGenerator;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;

public class SARChallengeSample {
    private static final Logger logger = Logger.getLogger(SARChallengeSample.class.getName());
    private final static String dumpFilePath = "../examples/sar/dump.nt";
    private final static String responderFilePathTemplate = "../examples/sar/responder_%04d.txt";
    private final static String nonResponderFilePathTemplate = "../examples/sar/non_responder_%04d.txt";
    private final static String sampledDatasetPathTemplate = "../examples/sar/sampled_%04d_%d.nt";

    public static void main(String[] args) throws Exception {
        logger.setLevel(Level.DEBUG);
        Set<String> sampleURIs = new HashSet<String>();

        logger.debug("loading dataset...");
        Model wholeDump = readDump(dumpFilePath);
        logger.debug("finished loading dataset");

        List<Integer> sampeSizes = new ArrayList<Integer>(Arrays.asList(5, 10, 20));

        for (Integer sampleSize : sampeSizes) {
            logger.debug("loading samples...");
            String responderFilePath = String.format(responderFilePathTemplate, sampleSize);
            sampleURIs.addAll(readSamples(responderFilePath));
            String nonResponderFilePath = String.format(nonResponderFilePathTemplate, sampleSize);
            sampleURIs.addAll(readSamples(nonResponderFilePath));
            logger.debug("finished loading samples");

            int cbdDepth = 1;
            while (cbdDepth < 6) {
                Model sampledDataset = sample(wholeDump, sampleURIs, cbdDepth);
                String sampledDatasetPath = String.format(sampledDatasetPathTemplate, sampleSize, cbdDepth);
                sampledDataset.write(new FileOutputStream(new File(sampledDatasetPath)), Lang.NTRIPLES.getName());
                cbdDepth++;
            }
        }
    }

    private static Model sample(Model wholeDump, Set<String> sampleURIs, int cbdDepth) {
        logger.info("sampling dataset with depth " + cbdDepth + "...");
        long start = System.currentTimeMillis();
        Model sampledDataset = ModelFactory.createDefaultModel();
        // FIXME: Don't call this here
        BlanknodeResolvingCBDGenerator cbdGenerator = new BlanknodeResolvingCBDGenerator(wholeDump);
        for (String uriStr : sampleURIs) {
            logger.debug("getting CBD for " + uriStr + "...");
            Model cbd = cbdGenerator.getConciseBoundedDescription(uriStr, cbdDepth);
            postProcessCBD(cbd, wholeDump);
            cbd.removeAll(ResourceFactory.createResource(uriStr), ResourceFactory.createProperty("http://bio2rdf.org/ra.challenge_vocabulary:non-responder"), (RDFNode) null);
            sampledDataset.add(cbd);
        }
        long end = System.currentTimeMillis();
        logger.info("finished sampling dataset with depth " + cbdDepth + " in " + (end-start) + "ms");

        return sampledDataset;
    }

    private static Model postProcessCBD(Model cbd, Model dataset) {
        Model newAxioms = ModelFactory.createDefaultModel();
        StmtIterator sttmntIt = cbd.listStatements();

        while (sttmntIt.hasNext()) {
            Statement sttmnt = sttmntIt.next();
            // get information about properties
            Property prop = sttmnt.getPredicate();
            newAxioms.add(getPropertyAxioms(prop, dataset));
        }

        // add class type statements and further information about the class
        // from the dataset
        StmtIterator clsSttmntIt = cbd.listStatements(null, RDF.type, (RDFNode) null);
        while (clsSttmntIt.hasNext()) {
            Statement sttmnt = clsSttmntIt.next();
            RDFNode object = sttmnt.getObject();
            Statement typeSttmnt = ResourceFactory.createStatement(
                    object.asResource(), RDF.type, OWL.Class);
            newAxioms.add(typeSttmnt);
            newAxioms.add(getResourceAxioms(object.asResource(), dataset));
        }

        cbd.add(newAxioms);
        return cbd;
    }

    private static Model getPropertyAxioms(Property prop, Model dataset) {
        Model propAxioms = ModelFactory.createDefaultModel();
        StmtIterator propSttmntIt = dataset.listStatements(prop.asResource(), null, (RDFNode) null);

        while (propSttmntIt.hasNext()) {
            Statement propSttmnt = propSttmntIt.next();
            // TODO: get and add further information?
            propAxioms.add(propSttmnt);
        }

        String constructStr =
                "CONSTRUCT { ?s ?p ?o } " +
                    "WHERE { " +
                        "?s <" + OWL.onProperty.getURI() + "> <" + prop.getURI() + "> ." +
                        "?s ?p ?o " +
                    "}";
        Query query = QueryFactory.create(constructStr);
        QueryExecution qe = QueryExecutionFactory.create(query, dataset);
        Model restrModel = qe.execConstruct();
        propAxioms.add(restrModel);

        return propAxioms;
    }

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

    private static Model readDump(String dumpFilePath) throws FileNotFoundException {
        InputStream dumpStream = new FileInputStream(new File(dumpFilePath));
        Model dump = ModelFactory.createDefaultModel();
        dump.read(dumpStream, null, Lang.NTRIPLES.getName());

        return dump;
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
