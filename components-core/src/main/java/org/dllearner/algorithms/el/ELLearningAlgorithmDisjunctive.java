/**
 * Copyright (C) 2007-2011, Jens Lehmann
 *
 * This file is part of DL-Learner.
 *
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dllearner.algorithms.el;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.refinementoperators.ELDown3;
import org.dllearner.utilities.owl.OWLAPIRenderers;
import org.dllearner.utilities.owl.OWLClassExpressionMinimizer;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;

/**
 * A learning algorithm for EL, which will based on an
 * ideal refinement operator.
 * 
 * The algorithm learns disjunctions of EL trees as follows:
 * - given pos. and neg. examples, noise in %, min coverage per tree x %
 * - it searches for an EL tree, which covers at least x % of all positive examples
 *   and at most (coverage_on_positives * noise) negative examples
 * - the covered examples are removed from the pos. and neg. examples
 * - termination: all(?) positive examples covered
 * 
 * ev. besser: feste Suchzeiten pro Baum => es wird dann jeweils der beste Baum gewählt
 * => Terminierung, wenn alles gecovered ist oder kein Baum mit ausreichender Qualität
 * in dem Zeitfenster gefunden wird
 * 
 * In contrast to many other algorithms, only one solution is returned. Additionally,
 * the algorithm is not really an anytime algorithm, since the solution is constructed
 * stepwise as a set of trees. 
 * 
 * Parameter optimisation:
 *  - runtime per tree: 10 seconds
 *  - tradeoff pos/neg: 1.0 1.2 1.4 1.6. 1.8 2.0
 *  - min score: 0 -2.5 -5 -7.5 -10
 *  - tests: 30
 *  - runtime per test: 200 seconds => 2000 seconds cross val => 60000 seconds overall
 * 
 * Next idea: 
 *  - reduce tradeoff for each tree added (start with 2.0 and reduce by 0.1)
 *  - for the last tress it is not very important to cover less negatives
 *  - minimum is something between 0 and -1 (ensures that in the worst case as many
 *    positives as negatives are covered)
 *  - only high impact parameter is runtime (and maybe start tradeoff)
 * 
 * @author Jens Lehmann
 *
 */
@ComponentAnn(name="Disjunctive ELTL", shortName="deltl", version=0.5, description="Disjunctive ELTL is an algorithm based on the refinement operator in http://jens-lehmann.org/files/2009/el_ilp.pdf with support for disjunctions.")
public class ELLearningAlgorithmDisjunctive extends AbstractCELA {

	private static Logger logger = Logger.getLogger(ELLearningAlgorithmDisjunctive.class);	
	
	String baseURI;
	Map<String,String> prefixes;
	
	private ELDown3 operator;
	private OWLClassExpressionMinimizer minimizer;
	
	private boolean isRunning = false;
	private boolean stop = false;
	
	private SearchTreeNode startNode;
	private ELHeuristic heuristic;
	private TreeSet<SearchTreeNode> candidates;
	// all trees (for fast redundancy check)
	private TreeSet<ELDescriptionTree> trees;
	private OWLClass classToDescribe;
	private double noise;
	
	@ConfigOption(name = "treeSearchTimeSeconds", defaultValue = "1.0", description="Specifies how long the algorithm should search for a partial solution (a tree).")
	private double treeSearchTimeSeconds = 1.0;
	
	@ConfigOption(name = "tryFullCoverage", defaultValue = "false", description="If yes, then the algorithm tries to cover all positive examples. Note that while this improves accuracy on the testing set, it may lead to overfitting.")
	private boolean tryFullCoverage = false;
	
	@ConfigOption(name = "stopOnFirstDefinition", defaultValue="false", description="algorithm will terminate immediately when a correct definition is found")
	private boolean stopOnFirstDefinition = false;
	
	@ConfigOption(name = "noisePercentage", defaultValue="0.0", description="the (approximated) percentage of noise within the examples")
	private double noisePercentage = 0.0;
	
	// the class with which we start the refinement process
	@ConfigOption(name = "startClass", defaultValue="owl:Thing", description="You can specify a start class for the algorithm. To do this, you have to use Manchester OWL syntax without using prefixes.")
	private OWLClassExpression startClass;
	
//	private double noise = 0;
	private List<ELDescriptionTree> currentSolution = new LinkedList<ELDescriptionTree>();
	private EvaluatedDescription bestEvaluatedDescription;
	// how important not to cover negatives
	private double posWeight = 1.2; // 2;
	private int startPosExamplesSize;
//	private int startNegExamplesSize;
	private Set<OWLIndividual> currentPosExamples;
	private Set<OWLIndividual> currentNegExamples;
	private SearchTreeNode bestCurrentNode;
	private double bestCurrentScore = 0;
	private long treeStartTime;
	// minimum score a tree must have to be part of the solution
	private double minimumTreeScore = -1;

	private boolean instanceBasedDisjoints;

	private DecimalFormat decFormat = new DecimalFormat("0.00"); 
	
	public ELLearningAlgorithmDisjunctive() {
		
	}	
	
	public ELLearningAlgorithmDisjunctive(AbstractLearningProblem problem, AbstractReasonerComponent reasoner) {
		super(problem, reasoner);
	}
	
	public static String getName() {
		return "disjunctive EL learning algorithm";
	}	
	
	public static Collection<Class<? extends AbstractLearningProblem>> supportedLearningProblems() {
		Collection<Class<? extends AbstractLearningProblem>> problems = new LinkedList<Class<? extends AbstractLearningProblem>>();
		problems.add(PosNegLP.class);
		return problems;
	}
	
	
//	public static Collection<ConfigOption<?>> createConfigOptions() {
//		Collection<ConfigOption<?>> options = new LinkedList<ConfigOption<?>>();
//		options.add(CommonConfigOptions.getNoisePercentage());
//		options.add(new StringConfigOption("startClass", "the named class which should be used to start the algorithm (GUI: needs a widget for selecting a class)"));
//		options.add(CommonConfigOptions.getInstanceBasedDisjoints());
//		return options;
//	}		
	
	@Override
	public void init() throws ComponentInitException {
		heuristic = new DisjunctiveHeuristic();
		candidates = new TreeSet<SearchTreeNode>(heuristic);
		trees = new TreeSet<ELDescriptionTree>(new ELDescriptionTreeComparator());
		
		if(startClass == null) {
			startClass = dataFactory.getOWLThing();
		}
		operator = new ELDown3(reasoner, instanceBasedDisjoints);
		
//		noise = configurator.getNoisePercentage()/(double)100;
		
		baseURI = reasoner.getBaseURI();
		prefixes = reasoner.getPrefixes();
		
		minimizer = new OWLClassExpressionMinimizer(dataFactory, reasoner);
		
		noise = noisePercentage/100d;
	}	
	
	@Override
	public void start() {
//		System.out.println("starting disjunctive ELTL algorithm");
		stop = false;
		isRunning = true;
		reset();
		int treeCount = 0;
		
		while(!stop && !stoppingCriteriaSatisfied()) {
			
			treeStartTime = System.nanoTime();
			// create start node
			ELDescriptionTree startTree = new ELDescriptionTree(reasoner, startClass);
			addDescriptionTree(startTree, null);
//			bestCurrentTree = top;
			bestCurrentScore = Double.NEGATIVE_INFINITY;
			
			// main loop
			int loop = 0;
			while(!stop && !treeCriteriaSatisfied()) {
				// pick the best candidate according to the heuristic
				SearchTreeNode best = candidates.pollLast();
//				System.out.println("best: " + best);
				
				// apply operator
				System.out.print("applying operator ...");
				List<ELDescriptionTree> refinements = operator.refine(best.getDescriptionTree());
				System.out.println("done " + refinements.size() + " refinements");
				// add all refinements to search tree, candidates, best descriptions
				for(ELDescriptionTree refinement : refinements) {
					addDescriptionTree(refinement, best);
				}
				loop++;
				// logging
				if(logger.isTraceEnabled()) {
					logger.trace("Choosen node " + best);
					logger.trace(startNode.getTreeString());
					logger.trace("Loop " + loop + " completed.");
				}
				
//				for(SearchTreeNode node : candidates) {
//					System.out.println(node);
//				}
//				System.out.println(candidates.last());
//				System.out.println(candidates.first());
//				System.out.println("==");
			}
			
			if(bestCurrentScore > minimumTreeScore) {
				// we found a tree (partial solution)
				currentSolution.add(bestCurrentNode.getDescriptionTree());
				OWLClassExpression bestDescription = bestCurrentNode.getDescriptionTree().transformToDescription();
				OWLClassExpression bestCombinedDescription = bestDescription;
				// form union of trees found so far with 
				if(treeCount==0) {
					bestEvaluatedDescription = learningProblem.evaluate(bestDescription);
					bestEvaluatedDescriptions.add(bestEvaluatedDescription);
				} else {
					if(!bestEvaluatedDescription.equals(dataFactory.getOWLThing())){
						bestCombinedDescription = dataFactory.getOWLObjectUnionOf(bestEvaluatedDescription.getDescription(), bestDescription);
					}
					bestEvaluatedDescription = learningProblem.evaluate(bestCombinedDescription);
					bestEvaluatedDescriptions.add(bestEvaluatedDescription);
				}
				
				// remove already covered examples
				Iterator<OWLIndividual> it = currentPosExamples.iterator();
				int posCov = 0;
				while(it.hasNext()) {
					OWLIndividual ind = it.next();
					if(reasoner.hasType(bestDescription, ind)) {
//						System.out.println("covered pos: " + ind);
						it.remove();
						posCov++;
					}
				}
				it = currentNegExamples.iterator();
				int negCov = 0;
				while(it.hasNext()) {
					OWLIndividual ind = it.next();
					if(reasoner.hasType(bestDescription, ind)) {
//						System.out.println("covered neg: " + ind);
						it.remove();
						negCov++;
					}
				}
				logger.info("tree found: " + OWLAPIRenderers.toManchesterOWLSyntax(bestDescription) + " (" + posCov + " pos covered, " + currentPosExamples.size() + " remaining, " + negCov + " neg covered, " + currentNegExamples.size() + " remaining, score: " + bestCurrentNode.getScore() + ")");
				logger.info("combined accuracy: " + decFormat.format(bestEvaluatedDescription.getAccuracy()));
			} else {
				logger.info("no tree found, which satisfies the minimum criteria - the best was: " + OWLAPIRenderers.toManchesterOWLSyntax(bestCurrentNode.getDescriptionTree().transformToDescription()) + " with score " + bestCurrentNode.getScore());
			}
			
			logger.info(trees.size() + " trees checked");
			
			// reduce importance of not covering negatives
			posWeight = Math.max(1.0, posWeight-0.1);
			
			// reset temporary variables
			candidates.clear();
			trees.clear();
			
			treeCount++;
		}
		
		// simplify solution (in particular necessary when start class is specified)
		OWLClassExpression niceDescription = minimizer.minimizeClone(bestEvaluatedDescription.getDescription());
		bestEvaluatedDescription = learningProblem.evaluate(niceDescription);
		
		// print solution
		logger.info("solution : " + OWLAPIRenderers.toManchesterOWLSyntax(bestEvaluatedDescription.getDescription()) + "(acc: " + bestEvaluatedDescription.getAccuracy() + ")");
		
		isRunning = false;
	}

	// evaluates a OWLClassExpression in tree form
	private void addDescriptionTree(ELDescriptionTree descriptionTree, SearchTreeNode parentNode) {
		
		// redundancy check
		boolean nonRedundant = trees.add(descriptionTree);
		if(!nonRedundant) {
			return;
		}
		
		// create search tree node
		SearchTreeNode node = new SearchTreeNode(descriptionTree);
		
		// compute score
		double score = getTreeScore(descriptionTree);
		node.setScore(score);
		
		// link to parent (unless start node)
		if(parentNode == null) {
			startNode = node;
		} else {
			parentNode.addChild(node);
		}
		
		// TODO: define "too weak" as a coverage on negative examples, which is
		// too high for the tree to be considered
		if(score != Double.NEGATIVE_INFINITY) {
			candidates.add(node);
		}
		
		// check whether this is the best tree
		if(score > bestCurrentScore) {
			bestCurrentNode = node;
			bestCurrentScore = score;
		}
	}
	
	private double getTreeScore(ELDescriptionTree tree) {
		
		OWLClassExpression d = tree.transformToDescription();
		
		double score = 0;
		
		// test coverage on current positive examples
		int posCovered = 0;
		for(OWLIndividual ind : currentPosExamples) {
			if(reasoner.hasType(d, ind)) {
				posCovered++;
				score += 1;
			}
		}
//		double posPercentage = posCovered/(double)currentPosExamples.size();
		
		// penalty if a minimum coverage is not achieved (avoids too many trees where
		// each tree has only little impact)
		if((startPosExamplesSize > 10 && posCovered<3) || posCovered < 1) {
//			score -= 100;
			// further refining such a tree will not cover more positives
			// => reject
			return Double.NEGATIVE_INFINITY;
		}
		
		// test coverage on current negative examples
		int negCovered = 0;
		for(OWLIndividual ind : currentNegExamples) {
			if(reasoner.hasType(d, ind)) {
				negCovered++;
				score -= posWeight;
			}
		}
//		double negPercentage = negCovered/(double)currentNegExamples.size();
		
		// remove - does not make sense
		// check whether tree is too weak, i.e. covers more than noise % negatives
//		int maxNegCov = (int) Math.round(noise * currentNegExamples.size());
//		if(negCovered > maxNegCov) {
//			return Double.NEGATIVE_INFINITY;
//		}
		
		// length penalty
		score -= 0.1*tree.getSize();
		
//		System.out.println("score: " + score);
		
		return score;
	}
	
	private boolean treeCriteriaSatisfied() {
		// stop if there are no more candidates (unlikely, but can happen)
		if(candidates.isEmpty()) {
			return true;
		}		
		
		long runTime = System.nanoTime() - treeStartTime;
		double runTimeSeconds = runTime / (double) 1000000000;

        return runTimeSeconds >= treeSearchTimeSeconds;
	}
	
	private boolean stoppingCriteriaSatisfied() {
	
		// stop if we have a node covering all positives and none of the negatives
//		SearchTreeNode bestNode = candidates.last();
//		return (bestNode.getCoveredNegatives() == 0);
		
		// stop if there are no more positive examples to cover
		if(stopOnFirstDefinition && currentPosExamples.size()==0) {
			return true;
		}
		
		// we stop when the score of the last tree added is too low
		// (indicating that the algorithm could not find anything appropriate 
		// in the timeframe set)
		if(bestCurrentScore <= minimumTreeScore) {
			return true;
		}
		
		// stop when almost all positive examples have been covered
		if(tryFullCoverage) {
			return false;
		} else {
			int maxPosRemaining = (int) Math.ceil(startPosExamplesSize * 0.05d);
			return (currentPosExamples.size()<=maxPosRemaining);
		}
	}
	
	private void reset() {
		// set all values back to their default values (used for running
		// the algorithm more than once)
		candidates.clear();
		trees.clear();
		currentSolution.clear();
		bestEvaluatedDescription = learningProblem.evaluate(dataFactory.getOWLThing());
		// we need to clone in order not to modify the learning problem
		currentPosExamples = new TreeSet<OWLIndividual>(((PosNegLP)getLearningProblem()).getPositiveExamples());
		currentNegExamples = new TreeSet<OWLIndividual>(((PosNegLP)getLearningProblem()).getNegativeExamples());
		startPosExamplesSize = currentPosExamples.size();
//		startNegExamplesSize = currentNegExamples.size();
	}
	
	@Override
	public void stop() {
		stop = true;
	}
	
	@Override
	public boolean isRunning() {
		return isRunning;
	}	
	
	@Override
	public OWLClassExpression getCurrentlyBestDescription() {
		return bestEvaluatedDescription.getDescription();
	}
	
	@Override
	public EvaluatedDescription getCurrentlyBestEvaluatedDescription() {
		return bestEvaluatedDescription;
	}			
	
	/**
	 * @return the startNode
	 */
	public SearchTreeNode getStartNode() {
		return startNode;
	}

	public OWLClassExpression getStartClass() {
		return startClass;
	}

	public void setStartClass(OWLClassExpression startClass) {
		this.startClass = startClass;
	}

	public boolean isInstanceBasedDisjoints() {
		return instanceBasedDisjoints;
	}

	public void setInstanceBasedDisjoints(boolean instanceBasedDisjoints) {
		this.instanceBasedDisjoints = instanceBasedDisjoints;
	}

	public double getTreeSearchTimeSeconds() {
		return treeSearchTimeSeconds;
	}

	public void setTreeSearchTimeSeconds(double treeSearchTimeSeconds) {
		this.treeSearchTimeSeconds = treeSearchTimeSeconds;
	}

	public boolean isTryFullCoverage() {
		return tryFullCoverage;
	}

	public void setTryFullCoverage(boolean tryFullCoverage) {
		this.tryFullCoverage = tryFullCoverage;
	}	
	
	/**
	 * @return the noisePercentage
	 */
	public double getNoisePercentage() {
		return noisePercentage;
	}
	
	/**
	 * @param noisePercentage the noisePercentage to set
	 */
	public void setNoisePercentage(double noisePercentage) {
		this.noisePercentage = noisePercentage;
	}
	
	/**
	 * @param classToDescribe the classToDescribe to set
	 */
	public void setClassToDescribe(OWLClass classToDescribe) {
		this.classToDescribe = classToDescribe;
	}
}
