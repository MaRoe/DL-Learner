package org.dllearner.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.riot.Lang;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.BlanknodeResolvingCBDGenerator;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

public class SARChallengeSample {
    private static final Logger logger = Logger.getLogger(SARChallengeSample.class.getName());
    private final static String dumpFilePath = "../examples/sar/dump.nt";
    private final static String responderFilePathTemplate = "../examples/sar/responder_%04d.txt";
    private final static String nonResponderFilePathTemplate = "../examples/sar/non_responder_%04d.txt";
    private final static String sampledDatasetPathTemplate = "../examples/sar/sampled_%04d_%d.nt";

    public static void main(String[] args) throws Exception {
        logger.setLevel(Level.DEBUG);
        Set<String> sampleURIs = new HashSet<String>();
        
        logger.debug("loading samples...");
        int sampleSize = 20;
        String responderFilePath = String.format(responderFilePathTemplate, sampleSize);
        sampleURIs.addAll(readSamples(responderFilePath));
        String nonResponderFilePath = String.format(nonResponderFilePathTemplate, sampleSize);
        sampleURIs.addAll(readSamples(nonResponderFilePath));
        logger.debug("finished loading samples");
        
        logger.debug("loading dataset...");
        Model wholeDump = readDump(dumpFilePath);
        logger.debug("finished loading dataset");

        int cbdDepth = 1;
        while (cbdDepth < 6) {
            Model sampledDataset = sample(wholeDump, sampleURIs, cbdDepth);
            String sampledDatasetPath = String.format(sampledDatasetPathTemplate, sampleSize, cbdDepth);
            sampledDataset.write(new FileOutputStream(new File(sampledDatasetPath)), Lang.NTRIPLES.getName());
            cbdDepth++;
        }
    }

    private static Model sample(Model wholeDump, Set<String> sampleURIs, int cbdDepth) {
        logger.info("sampling dataset with depth " + cbdDepth + "...");
        long start = System.currentTimeMillis();
        Model sampledDataset = ModelFactory.createDefaultModel();
        BlanknodeResolvingCBDGenerator cbdGenerator = new BlanknodeResolvingCBDGenerator(wholeDump);
        for (String uriStr : sampleURIs) {
            logger.debug("getting CBD for " + uriStr + "...");
            sampledDataset.add(cbdGenerator.getConciseBoundedDescription(uriStr, cbdDepth));
        }
        long end = System.currentTimeMillis();
        logger.info("finished sampling dataset with depth " + cbdDepth + " in " + (end-start) + "ms");
        return sampledDataset;
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
