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

package org.dllearner.learningproblems;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.options.BooleanConfigOption;
import org.dllearner.core.options.CommonConfigOptions;
import org.dllearner.core.options.StringConfigOption;
import org.dllearner.core.options.StringSetConfigOption;
import org.dllearner.utilities.Helper;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * @author Jens Lehmann
 *
 */
public abstract class PosNegLP extends AbstractLearningProblem {
	private static Logger logger = Logger.getLogger(PosNegLP.class);

	protected Set<OWLIndividual> positiveExamples = new TreeSet<OWLIndividual>();
	protected Set<OWLIndividual> negativeExamples = new TreeSet<OWLIndividual>();
	protected Set<OWLIndividual> allExamples = new TreeSet<OWLIndividual>();

    @org.dllearner.core.config.ConfigOption(name = "useRetrievalForClassification", description = "\"Specifies whether to use retrieval or instance checks for testing a concept. - NO LONGER FULLY SUPPORTED.",defaultValue = "false")
    private boolean useRetrievalForClassification = false;
    @org.dllearner.core.config.ConfigOption(name = "useMultiInstanceChecks", description = "Use The Multi Instance Checks", defaultValue = "UseMultiInstanceChecks.TWOCHECKS", required = false, propertyEditorClass = StringTrimmerEditor.class)
    private UseMultiInstanceChecks useMultiInstanceChecks = UseMultiInstanceChecks.TWOCHECKS;
    @org.dllearner.core.config.ConfigOption(name = "percentPerLengthUnit", description = "Percent Per Length Unit", defaultValue = "0.05", required = false)
    private double percentPerLengthUnit = 0.05;


    /**
	 * If instance checks are used for testing concepts (e.g. no retrieval), then
	 * there are several options to do this. The enumeration lists the supported
	 * options. These options are only important if the reasoning mechanism 
	 * supports sending several reasoning requests at once as it is the case for
	 * DIG reasoners.
	 * 
	 * @author Jens Lehmann
	 *
	 */
	public enum UseMultiInstanceChecks {
		/**
		 * Perform a separate instance check for each example.
		 */
		NEVER,
		/**
		 * Perform one instance check for all positive and one instance check
		 * for all negative examples.
		 */
		TWOCHECKS,
		/**
		 * Perform all instance checks at once.
		 */
		ONECHECK
	};


    public PosNegLP(){

    }

	public PosNegLP(AbstractReasonerComponent reasoningService) {
		super(reasoningService);
	}
	
	public static Collection<org.dllearner.core.options.ConfigOption<?>> createConfigOptions() {
		Collection<org.dllearner.core.options.ConfigOption<?>> options = new LinkedList<org.dllearner.core.options.ConfigOption<?>>();
		options.add(new StringSetConfigOption("positiveExamples",
				"positive examples",null, true, false));
		options.add(new StringSetConfigOption("negativeExamples",
				"negative examples",null, true, false));
		options.add(new BooleanConfigOption("useRetrievalForClassficiation", 
				"Specifies whether to use retrieval or instance checks for testing a concept. - NO LONGER FULLY SUPPORTED.", false));
		options.add(CommonConfigOptions.getPercentPerLenghtUnitOption(0.05));
		StringConfigOption multiInstanceChecks = new StringConfigOption("useMultiInstanceChecks", "See UseMultiInstanceChecks enum. - NO LONGER FULLY SUPPORTED.","twoChecks");
		multiInstanceChecks.setAllowedValues(new String[] {"never", "twoChecks", "oneCheck"});
		options.add(multiInstanceChecks);
		return options;
	}	

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dllearner.core.Component#init()
	 */
	@Override
	public void init() throws ComponentInitException {
		// check if some positive examples have been set
		if(positiveExamples.isEmpty()) {
			throw new ComponentInitException("No positive examples have been set.");
		}
		
		// check if some negative examples have been set and give warning if not
		if(negativeExamples.isEmpty()) {
			logger.warn("No negative examples have been set, but you decided to use the positive-negative learning"
					+ "problem. We recommend to use the positive-only learning problem for the case of no negative examples instead.");
		}
		
		// check if there is some overlap between positive and negative examples and give warning
		// in that case
		SetView<OWLIndividual> overlap = Sets.intersection(positiveExamples, negativeExamples);
		if(!overlap.isEmpty()) {
			logger.warn("You declared some individuals as both positive and negative examples.");
		}
		
		allExamples = Helper.union(positiveExamples, negativeExamples);
		
		if(reasoner != null && !reasoner.getIndividuals().containsAll(allExamples)) {
            Set<OWLIndividual> missing = Helper.difference(allExamples, reasoner.getIndividuals());
            double percentage = (double) (missing.size()/allExamples.size());
            percentage = Math.round(percentage * 1000) / 1000;
			String str = "The examples (" + (percentage * 100) + " % of total) below are not contained in the knowledge base (check spelling and prefixes)\n";
			str += missing.toString();
            if(missing.size()==allExamples.size())    {
                throw new ComponentInitException(str);
            } if(percentage < 0.10) {
                logger.warn(str);
            } else {
                logger.error(str);
            }
		}
	}
	
	public Set<OWLIndividual> getNegativeExamples() {
		return negativeExamples;
	}

	public Set<OWLIndividual> getPositiveExamples() {
		return positiveExamples;
	}
	
	public void setNegativeExamples(Set<OWLIndividual> set) {
		this.negativeExamples=set;
	}

	public void setPositiveExamples(Set<OWLIndividual> set) {
		this.positiveExamples=set;
	}
	
	public abstract int coveredNegativeExamplesOrTooWeak(OWLClassExpression concept);

	public double getPercentPerLengthUnit() {
		return percentPerLengthUnit;
	}

    public void setPercentPerLengthUnit(double percentPerLengthUnit) {
        this.percentPerLengthUnit = percentPerLengthUnit;
    }

    public boolean isUseRetrievalForClassification() {
        return useRetrievalForClassification;
    }

    public void setUseRetrievalForClassification(boolean useRetrievalForClassification) {
        this.useRetrievalForClassification = useRetrievalForClassification;
    }

    public UseMultiInstanceChecks getUseMultiInstanceChecks() {
        return useMultiInstanceChecks;
    }

    public void setUseMultiInstanceChecks(UseMultiInstanceChecks useMultiInstanceChecks) {
        this.useMultiInstanceChecks = useMultiInstanceChecks;
    }


}