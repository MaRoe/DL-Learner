/**
 * 
 */
package org.dllearner.learningproblems;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.learningproblems.Heuristics.HeuristicType;
import org.dllearner.utilities.owl.OWLClassExpressionUtils;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A learning problem in which positive and negative examples are classes, i.e.
 * the whole learning is done on the schema level.
 * 
 * Instead of doing instance checks to compute the quality of a given class
 * expression, we
 * check for subclass relationship.
 * 
 * @author Lorenz Buehmann
 *
 */
public class ClassAsInstanceLearningProblem extends AbstractLearningProblem {

	private static final Logger logger = LoggerFactory.getLogger(ClassAsInstanceLearningProblem.class);

	@org.dllearner.core.config.ConfigOption(name = "percentPerLengthUnit", description = "Percent Per Length Unit", defaultValue = "0.05", required = false)
	private double percentPerLengthUnit = 0.05;

	private HeuristicType heuristic = HeuristicType.PRED_ACC;

	protected Set<OWLClass> positiveExamples = new TreeSet<OWLClass>();
	protected Set<OWLClass> negativeExamples = new TreeSet<OWLClass>();

	/* (non-Javadoc)
	 * @see org.dllearner.core.Component#init()
	 */
	@Override
	public void init() throws ComponentInitException {
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.AbstractLearningProblem#computeScore(org.dllearner.core.owl.Description)
	 */
	@Override
	public ScorePosNeg<OWLClass> computeScore(OWLClassExpression description) {
		SortedSet<OWLClass> posAsPos = new TreeSet<OWLClass>();
		SortedSet<OWLClass> posAsNeg = new TreeSet<OWLClass>();
		SortedSet<OWLClass> negAsPos = new TreeSet<OWLClass>();
		SortedSet<OWLClass> negAsNeg = new TreeSet<OWLClass>();

		// for each positive example, we check whether it is a subclass of the given concept
		for (OWLClass example : positiveExamples) {
			if (getReasoner().isSuperClassOf(description, example)) {
				posAsPos.add(example);
			} else {
				posAsNeg.add(example);
			}
		}
		// for each negative example, we check whether it is not a subclass of the given concept
		for (OWLClass example : negativeExamples) {
			if (getReasoner().isSuperClassOf(description, example)) {
				negAsPos.add(example);
			} else {
				negAsNeg.add(example);
			}
		}

		// compute the accuracy
		double accuracy = getAccuracy(description);

		return new ScoreTwoValued<OWLClass>(OWLClassExpressionUtils.getLength(description), percentPerLengthUnit, posAsPos, posAsNeg,
				negAsPos, negAsNeg, accuracy);
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.AbstractLearningProblem#evaluate(org.dllearner.core.owl.Description)
	 */
	@Override
	public EvaluatedDescription evaluate(OWLClassExpression description) {
		ScorePosNeg<OWLClass> score = computeScore(description);
		return new EvaluatedDescriptionPosNeg(description, score);
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.AbstractLearningProblem#getAccuracy(org.dllearner.core.owl.Description)
	 */
	@Override
	public double getAccuracy(OWLClassExpression description) {
		return getAccuracyOrTooWeak(description, 1.0);
	}

	/* (non-Javadoc)
	 * @see org.dllearner.core.AbstractLearningProblem#getAccuracyOrTooWeak(org.dllearner.core.owl.Description, double)
	 */
	@Override
	public double getAccuracyOrTooWeak(OWLClassExpression description, double noise) {
		return 0;
	}

	public double getAccuracyOrTooWeakExact(OWLClassExpression description, double noise) {
		switch (heuristic) {
		case PRED_ACC:
			return getPredAccuracyOrTooWeakExact(description, noise);
		case FMEASURE:
			return getFMeasureOrTooWeakExact(description, noise);
		default:
			throw new Error("Heuristic " + heuristic + " not implemented.");
		}
	}

	public double getPredAccuracyOrTooWeakExact(OWLClassExpression description, double noise) {

		int maxNotCovered = (int) Math.ceil(noise * positiveExamples.size());

		int notCoveredPos = 0;
		int notCoveredNeg = 0;

		for (OWLClass example : positiveExamples) {
			if (!getReasoner().isSuperClassOf(description, example)) {
				notCoveredPos++;

				if (notCoveredPos >= maxNotCovered) {
					return -1;
				}
			}
		}
		for (OWLClass example : negativeExamples) {
			if (!getReasoner().isSuperClassOf(description, example)) {
				notCoveredNeg++;
			}
		}

		int tp = positiveExamples.size() - notCoveredPos;
		int tn = notCoveredNeg;
		int fp = notCoveredPos;
		int fn = negativeExamples.size() - notCoveredNeg;

		return (tp + tn) / (double) (tp + fp + tn + fn);
	}

	public double getFMeasureOrTooWeakExact(OWLClassExpression description, double noise) {
		int additionalInstances = 0;
		for (OWLClass example : negativeExamples) {
			if (getReasoner().isSuperClassOf(description, example)) {
				additionalInstances++;
			}
		}

		int coveredInstances = 0;
		for (OWLClass example : positiveExamples) {
			if (getReasoner().isSuperClassOf(description, example)) {
				coveredInstances++;
			}
		}

		double recall = coveredInstances / (double) positiveExamples.size();

		if (recall < 1 - noise) {
			return -1;
		}

		double precision = (additionalInstances + coveredInstances == 0) ? 0 : coveredInstances
				/ (double) (coveredInstances + additionalInstances);

		return Heuristics.getFScore(recall, precision);
	}

	/**
	 * @param positiveExamples the positiveExamples to set
	 */
	public void setPositiveExamples(Set<OWLClass> positiveExamples) {
		this.positiveExamples = positiveExamples;
	}

	/**
	 * @return the positiveExamples
	 */
	public Set<OWLClass> getPositiveExamples() {
		return positiveExamples;
	}

	/**
	 * @param negativeExamples the negativeExamples to set
	 */
	public void setNegativeExamples(Set<OWLClass> negativeExamples) {
		this.negativeExamples = negativeExamples;
	}

	/**
	 * @return the negativeExamples
	 */
	public Set<OWLClass> getNegativeExamples() {
		return negativeExamples;
	}

	/**
	 * @return the percentPerLengthUnit
	 */
	public double getPercentPerLengthUnit() {
		return percentPerLengthUnit;
	}

	/**
	 * @param percentPerLengthUnit the percentPerLengthUnit to set
	 */
	public void setPercentPerLengthUnit(double percentPerLengthUnit) {
		this.percentPerLengthUnit = percentPerLengthUnit;
	}

}
