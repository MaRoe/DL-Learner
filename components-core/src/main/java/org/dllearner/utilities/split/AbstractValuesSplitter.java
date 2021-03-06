/**
 * 
 */
package org.dllearner.utilities.split;

import java.util.Set;

import org.dllearner.core.AbstractComponent;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.learningproblems.PosNegLP;
import org.semanticweb.owlapi.model.OWLDataProperty;

/**
 * @author Lorenz Buehmann
 *
 */
public abstract class AbstractValuesSplitter extends AbstractComponent implements ValuesSplitter {
	
	protected AbstractReasonerComponent reasoner;
	
	protected Set<OWLDataProperty> numericDataProperties;

	public AbstractValuesSplitter(AbstractReasonerComponent reasoner) {
		this.reasoner = reasoner;
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.core.Component#init()
	 */
	@Override
	public void init() throws ComponentInitException {
		numericDataProperties = reasoner.getNumericDataProperties();
	}


}
