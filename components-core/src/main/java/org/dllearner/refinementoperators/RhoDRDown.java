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

package org.dllearner.refinementoperators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.Component;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.Reasoner;
import org.dllearner.core.config.BooleanEditor;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.core.options.CommonConfigOptions;
import org.dllearner.core.owl.ClassHierarchy;
import org.dllearner.core.owl.DatatypePropertyHierarchy;
import org.dllearner.core.owl.Hierarchy;
import org.dllearner.core.owl.OWLObjectIntersectionOfImplExt;
import org.dllearner.core.owl.OWLObjectUnionOfImplExt;
import org.dllearner.core.owl.ObjectPropertyHierarchy;
import org.dllearner.utilities.Helper;
import org.dllearner.utilities.owl.ConceptTransformation;
import org.dllearner.utilities.owl.OWLClassExpressionUtils;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi.model.OWLFacetRestriction;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNaryBooleanClassExpression;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.vocab.OWLFacet;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import uk.ac.manchester.cs.owl.owlapi.OWLClassImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A downward refinement operator, which makes use of domains
 * and ranges of properties. The operator is currently under
 * development. Its aim is to span a much "cleaner" and smaller search
 * tree compared to RhoDown by omitting many class descriptions,
 * which are obviously too weak, because they violate 
 * domain/range restrictions. Furthermore, it makes use of disjoint
 * classes in the knowledge base.
 * 
 * Note: Some of the code has moved to {@link Utility} in a modified
 * form to make it accessible for implementations of other refinement
 * operators. These utility methods may be completed and carefully
 * integrated back later. 
 * 
 * @author Jens Lehmann
 *
 */
@ComponentAnn(name = "rho refinement operator", shortName = "rho", version = 0.8)
public class RhoDRDown extends RefinementOperatorAdapter implements Component, CustomHierarchyRefinementOperator, CustomStartRefinementOperator, ReasoningBasedRefinementOperator {

	private static Logger logger = LoggerFactory.getLogger(RhoDRDown.class);
	
	private static final OWLClass OWL_THING = new OWLClassImpl(
            OWLRDFVocabulary.OWL_THING.getIRI());
	
	private AbstractReasonerComponent reasoner;
	
	// hierarchies
	private ClassHierarchy subHierarchy;
	private ObjectPropertyHierarchy objectPropertyHierarchy;
	private DatatypePropertyHierarchy dataPropertyHierarchy;
	
	// domains and ranges
	private Map<OWLObjectProperty,OWLClassExpression> opDomains = new TreeMap<OWLObjectProperty,OWLClassExpression>();
	private Map<OWLDataProperty,OWLClassExpression> dpDomains = new TreeMap<OWLDataProperty,OWLClassExpression>();
	private Map<OWLObjectProperty,OWLClassExpression> opRanges = new TreeMap<OWLObjectProperty,OWLClassExpression>();
	
	// maximum number of fillers for each role
	private Map<OWLObjectProperty,Integer> maxNrOfFillers = new TreeMap<OWLObjectProperty,Integer>();
	// limit for cardinality restrictions (this makes sense if we e.g. have compounds with up to
	// more than 200 atoms but we are only interested in atoms with certain characteristics and do
	// not want something like e.g. >= 204 hasAtom.NOT Carbon-87; which blows up the search space
	private int cardinalityLimit = 5;
	
	// start concept (can be used to start from an arbitrary concept, needs
	// to be Thing or NamedClass), note that when you use e.g. Compound as 
	// start class, then the algorithm should start the search with class
	// Compound (and not with Thing), because otherwise concepts like
	// NOT Carbon-87 will be returned which itself is not a subclass of Compound
	private OWLClassExpression startClass = OWL_THING;
	
	// the length of concepts of top refinements, the first values is
	// for refinements of \rho_\top(\top), the second one for \rho_A(\top)
	private int topRefinementsLength = 0;
	private Map<OWLClassExpression, Integer> topARefinementsLength = new TreeMap<OWLClassExpression, Integer>();
	// M is finite and this value is the maximum length of any value in M
	private static int mMaxLength = 4;
	
	// the sets M_\top and M_A
	private Map<Integer,SortedSet<OWLClassExpression>> m = new TreeMap<Integer,SortedSet<OWLClassExpression>>();
	private Map<OWLClassExpression,Map<Integer,SortedSet<OWLClassExpression>>> mA = new TreeMap<OWLClassExpression,Map<Integer,SortedSet<OWLClassExpression>>>();
	
	// @see MathOperations.getCombos
	private Map<Integer, List<List<Integer>>> combos = new HashMap<Integer, List<List<Integer>>>();

	// refinements of the top concept ordered by length
	private Map<Integer, SortedSet<OWLClassExpression>> topRefinements = new TreeMap<Integer, SortedSet<OWLClassExpression>>();
	private Map<OWLClassExpression,Map<Integer, SortedSet<OWLClassExpression>>> topARefinements = new TreeMap<OWLClassExpression,Map<Integer, SortedSet<OWLClassExpression>>>();
	
	// cumulated refinements of top (all from length one to the specified length)
	private Map<Integer, TreeSet<OWLClassExpression>> topRefinementsCumulative = new HashMap<Integer, TreeSet<OWLClassExpression>>();
	private Map<OWLClassExpression,Map<Integer, TreeSet<OWLClassExpression>>> topARefinementsCumulative = new TreeMap<OWLClassExpression,Map<Integer, TreeSet<OWLClassExpression>>>();
	
	// app_A set of applicable properties for a given class (separate for
	// object properties, boolean datatypes, and double datatypes)
	private Map<OWLClassExpression, Set<OWLObjectProperty>> appOP = new TreeMap<OWLClassExpression, Set<OWLObjectProperty>>();
	private Map<OWLClassExpression, Set<OWLDataProperty>> appBD = new TreeMap<OWLClassExpression, Set<OWLDataProperty>>();
	private Map<OWLClassExpression, Set<OWLDataProperty>> appNumeric = new TreeMap<OWLClassExpression, Set<OWLDataProperty>>();
	private Map<OWLClassExpression, Set<OWLDataProperty>> appSD = new TreeMap<OWLClassExpression, Set<OWLDataProperty>>();
	
	// most general applicable properties
	private Map<OWLClassExpression,Set<OWLObjectProperty>> mgr = new TreeMap<OWLClassExpression,Set<OWLObjectProperty>>();
	private Map<OWLClassExpression,Set<OWLDataProperty>> mgbd = new TreeMap<OWLClassExpression,Set<OWLDataProperty>>();
	private Map<OWLClassExpression,Set<OWLDataProperty>> mgNumeric = new TreeMap<OWLClassExpression,Set<OWLDataProperty>>();
	private Map<OWLClassExpression,Set<OWLDataProperty>> mgsd = new TreeMap<OWLClassExpression,Set<OWLDataProperty>>();
	
	// splits for double datatype properties in ascending order
	private Map<OWLDataProperty,List<Double>> splits = new TreeMap<OWLDataProperty,List<Double>>();
	
	private Map<OWLDataProperty,List<Byte>> splitsByte = new TreeMap<OWLDataProperty,List<Byte>>();
	private Map<OWLDataProperty,List<Short>> splitsShort = new TreeMap<OWLDataProperty,List<Short>>();
	private Map<OWLDataProperty,List<Integer>> splitsInt = new TreeMap<OWLDataProperty,List<Integer>>();
	private Map<OWLDataProperty,List<Long>> splitsLong = new TreeMap<OWLDataProperty,List<Long>>();
	private Map<OWLDataProperty,List<Float>> splitsFloat = new TreeMap<OWLDataProperty,List<Float>>();
	private Map<OWLDataProperty,List<Double>> splitsDouble = new TreeMap<OWLDataProperty,List<Double>>();
	
	private Map<OWLDataProperty,List<? extends Number>> splitsNumber = new TreeMap<OWLDataProperty,List<? extends Number>>();
	private int maxNrOfSplits = 10;
	
	// data structure for a simple frequent pattern matching preprocessing phase
	private int frequencyThreshold = CommonConfigOptions.valueFrequencyThresholdDefault;
	private Map<OWLObjectProperty, Map<OWLIndividual, Integer>> valueFrequency = new HashMap<OWLObjectProperty, Map<OWLIndividual, Integer>>();
	// data structure with identified frequent values
	private Map<OWLObjectProperty, Set<OWLIndividual>> frequentValues = new HashMap<OWLObjectProperty, Set<OWLIndividual>>();	
	// frequent data values
	private Map<OWLDataProperty, Set<OWLLiteral>> frequentDataValues = new HashMap<OWLDataProperty, Set<OWLLiteral>>();	
	private Map<OWLDataProperty, Map<OWLLiteral, Integer>> dataValueFrequency = new HashMap<OWLDataProperty, Map<OWLLiteral, Integer>>();		
	private boolean useDataHasValueConstructor = false;
	
	// statistics
	public long mComputationTimeNs = 0;
	public long topComputationTimeNs = 0;
	
	@ConfigOption(name = "applyAllFilter", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean applyAllFilter = true;
	
	@ConfigOption(name = "applyExistsFilter", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean applyExistsFilter = true;
	
	@ConfigOption(name = "useAllConstructor", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useAllConstructor = true;
	
	@ConfigOption(name = "useExistsConstructor", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useExistsConstructor = true;
	
	@ConfigOption(name = "useHasValueConstructor", defaultValue="false", propertyEditorClass = BooleanEditor.class)
	private boolean useHasValueConstructor = false;
	
	@ConfigOption(name = "useCardinalityRestrictions", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useCardinalityRestrictions = true;
	
	@ConfigOption(name = "useNegation", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useNegation = true;
	
	@ConfigOption(name = "useBooleanDatatypes", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useBooleanDatatypes = true;
	
	@ConfigOption(name = "useDoubleDatatypes", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useDoubleDatatypes = true;
	
	@ConfigOption(name = "useIntDatatypes", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useIntDatatypes = true;
	
	@ConfigOption(name = "useIntDatatypes", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean useNumericDatatypes = true;
	
	@ConfigOption(name = "useStringDatatypes", defaultValue="false", propertyEditorClass = BooleanEditor.class)
	private boolean useStringDatatypes = false;
	
	@ConfigOption(name = "disjointChecks", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean disjointChecks = true;
	
	@ConfigOption(name = "instanceBasedDisjoints", defaultValue="true", propertyEditorClass = BooleanEditor.class)
	private boolean instanceBasedDisjoints = true;
	
	@ConfigOption(name = "dropDisjuncts", defaultValue="false", propertyEditorClass = BooleanEditor.class)
	private boolean dropDisjuncts = false;

	// caches for reasoner queries
	private Map<OWLClassExpression,Map<OWLClassExpression,Boolean>> cachedDisjoints = new TreeMap<OWLClassExpression,Map<OWLClassExpression,Boolean>>();

//	private Map<OWLClass,Map<OWLClass,Boolean>> abDisjoint = new TreeMap<OWLClass,Map<OWLClass,Boolean>>();
//	private Map<OWLClass,Map<OWLClass,Boolean>> notABDisjoint = new TreeMap<OWLClass,Map<OWLClass,Boolean>>();
//	private Map<OWLClass,Map<OWLClass,Boolean>> notABMeaningful = new TreeMap<OWLClass,Map<OWLClass,Boolean>>();
	
	private boolean isInitialised = false;

	private boolean useObjectValueNegation = false;
	
	private OWLDataFactory df = new OWLDataFactoryImpl();
	
	public RhoDRDown() {}
	
	public RhoDRDown(RhoDRDown op) {
		setApplyAllFilter(op.applyAllFilter);
		setCardinalityLimit(op.cardinalityLimit);
		setClassHierarchy(op.subHierarchy);
		setDataPropertyHierarchy(op.dataPropertyHierarchy);
		setDropDisjuncts(op.dropDisjuncts);
		setFrequencyThreshold(op.frequencyThreshold);
		setInstanceBasedDisjoints(op.instanceBasedDisjoints);
		setObjectPropertyHierarchy(op.objectPropertyHierarchy);
		setReasoner(op.reasoner);
		setStartClass(op.startClass);
		setSubHierarchy(op.subHierarchy);
		setUseAllConstructor(op.useAllConstructor);
		setUseBooleanDatatypes(op.useBooleanDatatypes);
		setUseCardinalityRestrictions(op.useCardinalityRestrictions);
		setUseDataHasValueConstructor(op.useDataHasValueConstructor);
		setUseDoubleDatatypes(op.useDoubleDatatypes);
		setUseExistsConstructor(op.useExistsConstructor);
		setUseHasValueConstructor(op.useHasValueConstructor);
		setUseIntDatatypes(op.useIntDatatypes);
		setUseNegation(op.useNegation);
		setUseObjectValueNegation(op.useObjectValueNegation);
		setUseStringDatatypes(op.useStringDatatypes);
		isInitialised = false;
	}
	
	public void init() throws ComponentInitException {
		if(isInitialised) {
			throw new ComponentInitException("Refinement operator cannot be initialised twice.");
		}
//		System.out.println("subHierarchy: " + subHierarchy);
//		System.out.println("object properties: " + reasoner.getObjectProperties());
		
		// query reasoner for domains and ranges
		// (because they are used often in the operator)
		for(OWLObjectProperty op : reasoner.getObjectProperties()) {
			opDomains.put(op, reasoner.getDomain(op));
			opRanges.put(op, reasoner.getRange(op));
			
			if(useHasValueConstructor) {
				// init
				Map<OWLIndividual, Integer> opMap = new TreeMap<OWLIndividual, Integer>();
				valueFrequency.put(op, opMap);
				
				// sets ordered by corresponding individual (which we ignore)
				Collection<SortedSet<OWLIndividual>> fillerSets = reasoner.getPropertyMembers(op).values();
				for(SortedSet<OWLIndividual> fillerSet : fillerSets) {
					for(OWLIndividual i : fillerSet) {
//						System.out.println("op " + op + " i " + i);
						Integer value = opMap.get(i);
						
						if(value != null) {
							opMap.put(i, value+1);
						} else {
							opMap.put(i, 1);
						}
					}
				}
				
				// keep only frequent patterns
				Set<OWLIndividual> frequentInds = new TreeSet<OWLIndividual>();
				for(OWLIndividual i : opMap.keySet()) {
					if(opMap.get(i) >= frequencyThreshold) {
						frequentInds.add(i);
//						break;
					}
				}
				frequentValues.put(op, frequentInds);

			}
			
		}
		
		for(OWLDataProperty dp : reasoner.getDatatypeProperties()) {
			dpDomains.put(dp, reasoner.getDomain(dp));
			
			if(useDataHasValueConstructor) {
				Map<OWLLiteral, Integer> dpMap = new TreeMap<OWLLiteral, Integer>();
				dataValueFrequency.put(dp, dpMap);
				
				// sets ordered by corresponding individual (which we ignore)
				Collection<SortedSet<OWLLiteral>> fillerSets = reasoner.getDatatypeMembers(dp).values();
				for(SortedSet<OWLLiteral> fillerSet : fillerSets) {
					for(OWLLiteral i : fillerSet) {
//						System.out.println("op " + op + " i " + i);
						Integer value = dpMap.get(i);
						
						if(value != null) {
							dpMap.put(i, value+1);
						} else {
							dpMap.put(i, 1);
						}
					}
				}
				
				// keep only frequent patterns
				Set<OWLLiteral> frequentInds = new TreeSet<OWLLiteral>();
				for(OWLLiteral i : dpMap.keySet()) {
					if(dpMap.get(i) >= frequencyThreshold) {
						logger.trace("adding value "+i+", because "+dpMap.get(i) +">="+frequencyThreshold);
						frequentInds.add(i);
					}
				}
				frequentDataValues.put(dp, frequentInds);				
			}
		}
		
		// we do not need the temporary set anymore and let the
		// garbage collector take care of it
		valueFrequency = null;
		dataValueFrequency.clear();// = null;
		
//		System.out.println("freqDataValues: " + frequentDataValues);
		
		// compute splits for numeric data properties
		for (OWLDataProperty dp : reasoner.getNumericDataProperties()) {
			computeSplits2(dp);
		}
		
		// compute splits for double datatype properties
		for(OWLDataProperty dp : reasoner.getDoubleDatatypeProperties()) {
			computeSplits(dp);
		}
		
		// determine the maximum number of fillers for each role
		// (up to a specified cardinality maximum)
		if(useCardinalityRestrictions) {
			for(OWLObjectProperty op : reasoner.getObjectProperties()) {
				int maxFillers = 0;
				Map<OWLIndividual,SortedSet<OWLIndividual>> opMembers = reasoner.getPropertyMembers(op);
				for(SortedSet<OWLIndividual> inds : opMembers.values()) {
					if(inds.size()>maxFillers)
						maxFillers = inds.size();
					if(maxFillers >= cardinalityLimit) {
						maxFillers = cardinalityLimit;
						break;
					}	
				}
				maxNrOfFillers.put(op, maxFillers);
			}
		}
		
		if(startClass == null) {
			startClass = df.getOWLThing();
		}
		
		isInitialised = true;
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.algorithms.refinement.RefinementOperator#refine(org.dllearner.core.owl.Description)
	 */
	@Override
	public Set<OWLClassExpression> refine(OWLClassExpression concept) {
		throw new RuntimeException();
	}

	@Override
	public Set<OWLClassExpression> refine(OWLClassExpression description, int maxLength) {
		// check that maxLength is valid
		if(maxLength < OWLClassExpressionUtils.getLength(description)) {
			throw new Error("length has to be at least class expression length (class expression: " + description + ", max length: " + maxLength + ")");
		}
		return refine(description, maxLength, null, startClass);
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.algorithms.refinement.RefinementOperator#refine(org.dllearner.core.owl.Description, int, java.util.List)
	 */
	@Override
	public Set<OWLClassExpression> refine(OWLClassExpression description, int maxLength,
			List<OWLClassExpression> knownRefinements) {
		return refine(description, maxLength, knownRefinements, startClass);
	}

	@SuppressWarnings({"unchecked"})
	public Set<OWLClassExpression> refine(OWLClassExpression description, int maxLength,
			List<OWLClassExpression> knownRefinements, OWLClassExpression currDomain) {
		
//		System.out.println("|- " + OWLClassExpression + " " + currDomain + " " + maxLength);
		
		// actions needing to be performed if this is the first time the
		// current domain is used
		if(!currDomain.isOWLThing() && !topARefinementsLength.containsKey(currDomain)){
			topARefinementsLength.put(currDomain, 0);
		}
		
		// check whether using list or set makes more sense 
		// here; and whether HashSet or TreeSet should be used
		// => TreeSet because duplicates are possible
		Set<OWLClassExpression> refinements = new TreeSet<OWLClassExpression>();
		
		// used as temporary variable
		Set<OWLClassExpression> tmp = new HashSet<OWLClassExpression>();
		
		if(description.isOWLThing()) {
			// extends top refinements if necessary
			if(currDomain.isOWLThing()) {
				if(maxLength>topRefinementsLength)
					computeTopRefinements(maxLength);
				refinements = (TreeSet<OWLClassExpression>) topRefinementsCumulative.get(maxLength).clone();
			} else {
				if(maxLength>topARefinementsLength.get(currDomain)) {
					computeTopRefinements(maxLength, currDomain);
				}
				refinements = (TreeSet<OWLClassExpression>) topARefinementsCumulative.get(currDomain).get(maxLength).clone();
			}
//			refinements.addAll(subHierarchy.getMoreSpecialConcepts(description));
		} else if(description.isOWLNothing()) {
			// cannot be further refined
		} else if(!description.isAnonymous()) {
			refinements.addAll(subHierarchy.getSubClasses(description));
			refinements.remove(df.getOWLNothing());
		} else if (description instanceof OWLObjectComplementOf) {
			OWLClassExpression operand = ((OWLObjectComplementOf) description).getOperand();
			if(!operand.isAnonymous()){
				tmp = subHierarchy.getSuperClasses(operand);
				
				for(OWLClassExpression c : tmp) {
					if(!c.isOWLThing()){
						refinements.add(df.getOWLObjectComplementOf(c));
					}
				}
			}
		} else if (description instanceof OWLObjectIntersectionOf) {
			List<OWLClassExpression> operands = ((OWLObjectIntersectionOf) description).getOperandsAsList();
			// refine one of the elements
			for(OWLClassExpression child : operands) {
				// refine the child; the new max length is the current max length minus
				// the currently considered concept plus the length of the child
				// TODO: add better explanation
				tmp = refine(child, maxLength - OWLClassExpressionUtils.getLength(description) + OWLClassExpressionUtils.getLength(child),null,currDomain);

				// create new intersection
				for(OWLClassExpression c : tmp) {
					List<OWLClassExpression> newChildren = new ArrayList<OWLClassExpression>(operands);
					newChildren.add(c);
					newChildren.remove(child);
					Collections.sort(newChildren);
					OWLClassExpression mc = new OWLObjectIntersectionOfImplExt(newChildren);
					
					// clean concept and transform it to ordered negation normal form
					// (non-recursive variant because only depth 1 was modified)
					mc = ConceptTransformation.cleanConceptNonRecursive(mc);
					ConceptTransformation.transformToOrderedNegationNormalFormNonRecursive(mc);
					
					// check whether the intersection is OK (sanity checks), then add it
					if(checkIntersection((OWLObjectIntersectionOf) mc))
						refinements.add(mc);
				}
				
			}
				
		} else if (description instanceof OWLObjectUnionOf) {
			// refine one of the elements
			List<OWLClassExpression> operands = ((OWLObjectUnionOf) description).getOperandsAsList();
			for(OWLClassExpression child : operands) {
//				System.out.println("union child: " + child + " " + maxLength + " " + description.getLength() + " " + child.getLength());
				
				// refine child
				tmp = refine(child, maxLength - OWLClassExpressionUtils.getLength(description)+OWLClassExpressionUtils.getLength(child),null,currDomain);
				
				// construct union (see above)
				for(OWLClassExpression c : tmp) {
					List<OWLClassExpression> newChildren = new ArrayList<OWLClassExpression>(operands);
					newChildren.remove(child);						
					newChildren.add(c);
					Collections.sort(newChildren);
					OWLObjectUnionOf md = new OWLObjectUnionOfImplExt(newChildren);
						
					// transform to ordered negation normal form
					ConceptTransformation.transformToOrderedNegationNormalFormNonRecursive(md);
					// note that we do not have to call clean here because a disjunction will
					// never be nested in another disjunction in this operator
					
					refinements.add(md);	
				}
				
			}
			
			// if enabled, we can remove elements of the disjunction
			if(dropDisjuncts) {
				// A1 OR A2 => {A1,A2}
				if(operands.size() == 2) {
					refinements.add(operands.get(0));
					refinements.add(operands.get(1));
				} else {
					// copy children list and remove a different element in each turn
					for(int i=0; i<operands.size(); i++) {
						List<OWLClassExpression> newChildren = new LinkedList<OWLClassExpression>(operands);
						newChildren.remove(i);						
						OWLObjectUnionOf md = new OWLObjectUnionOfImplExt(newChildren);
						refinements.add(md);
					}
				}
			}
			
		} else if (description instanceof OWLObjectSomeValuesFrom) {
			OWLObjectPropertyExpression role = ((OWLObjectSomeValuesFrom) description).getProperty();
			OWLClassExpression filler = ((OWLObjectSomeValuesFrom) description).getFiller();
			OWLClassExpression range = opRanges.get(role);
			
			// rule 1: EXISTS r.D => EXISTS r.E
			tmp = refine(filler, maxLength-2, null, range);

			for(OWLClassExpression c : tmp){
				refinements.add(df.getOWLObjectSomeValuesFrom(role, c));
			}
			
			// rule 2: EXISTS r.D => EXISTS s.D or EXISTS r^-1.D => EXISTS s^-1.D
			// currently inverse roles are not supported
			// remove reasoner calls
			if(!role.isAnonymous()){
				Set<OWLObjectProperty> moreSpecialRoles = reasoner.getSubProperties(role.asOWLObjectProperty());
//				Set<OWLObjectProperty> moreSpecialRoles = objectPropertyHierarchy.getMoreSpecialRoles(ar);
				for(OWLObjectProperty moreSpecialRole : moreSpecialRoles){
					refinements.add(df.getOWLObjectSomeValuesFrom(moreSpecialRole, filler));
				}
			}
			

			// rule 3: EXISTS r.D => >= 2 r.D
			// (length increases by 1 so we have to check whether max length is sufficient)
			if(useCardinalityRestrictions) {
				if(maxLength > OWLClassExpressionUtils.getLength(description) && maxNrOfFillers.get(role)>1) {
					OWLObjectMinCardinality min = df.getOWLObjectMinCardinality(2,role,filler);
					refinements.add(min);
				}
			}
			
			// rule 4: EXISTS r.TOP => EXISTS r.{value}
			if(useHasValueConstructor && filler.isOWLThing()) {
				// watch out for frequent patterns
				Set<OWLIndividual> frequentInds = frequentValues.get(role);
				if(frequentInds != null) {
					for(OWLIndividual ind : frequentInds) {
						OWLObjectHasValue ovr = df.getOWLObjectHasValue(role, ind);
						refinements.add(ovr);
						if(useObjectValueNegation ){
							refinements.add(df.getOWLObjectComplementOf(ovr));
						}
						
					}			
				}
			}
			
		} else if (description instanceof OWLObjectAllValuesFrom) {
			OWLObjectPropertyExpression role = ((OWLObjectAllValuesFrom) description).getProperty();
			OWLClassExpression filler = ((OWLObjectAllValuesFrom) description).getFiller();
			OWLClassExpression range = opRanges.get(role);
			
			// rule 1: ALL r.D => ALL r.E
			tmp = refine(filler, maxLength-2, null, range);

			for(OWLClassExpression c : tmp) {
				refinements.add(df.getOWLObjectAllValuesFrom(role, c));
			}		
			
			// rule 2: ALL r.D => ALL r.BOTTOM if D is a most specific atomic concept
			if(!filler.isOWLNothing() && !filler.isAnonymous() && tmp.size()==0) {
				refinements.add(df.getOWLObjectAllValuesFrom(role, df.getOWLNothing()));
			}
			
			// rule 3: ALL r.D => ALL s.D or ALL r^-1.D => ALL s^-1.D
			// currently inverse roles are not supported
			if(!role.isAnonymous()){
				Set<OWLObjectProperty> subProperties = reasoner.getSubProperties(role.asOWLObjectProperty());
//				Set<OWLObjectProperty> moreSpecialRoles = objectPropertyHierarchy.getMoreSpecialRoles(ar);
				for(OWLObjectProperty subProperty : subProperties) {
					refinements.add(df.getOWLObjectAllValuesFrom(subProperty, filler));
				}
			}
			
			
			// rule 4: ALL r.D => <= (maxFillers-1) r.D
			// (length increases by 1 so we have to check whether max length is sufficient)
			// => commented out because this is actually not a downward refinement
//			if(useCardinalityRestrictions) {
//				if(maxLength > description.getLength() && maxNrOfFillers.get(ar)>1) {
//					ObjectMaxCardinalityRestriction max = new ObjectMaxCardinalityRestriction(maxNrOfFillers.get(ar)-1,role,description.getChild(0));
//					refinements.add(max);
//				}
//			}
		} else if (description instanceof OWLObjectCardinalityRestriction) {
			OWLObjectPropertyExpression role = ((OWLObjectCardinalityRestriction) description).getProperty();
			OWLClassExpression filler = ((OWLObjectCardinalityRestriction) description).getFiller();
			OWLClassExpression range = opRanges.get(role);	
			int cardinality = ((OWLObjectCardinalityRestriction) description).getCardinality();
			if(description instanceof OWLObjectMaxCardinality) {
				// rule 1: <= x r.C =>  <= x r.D
				if(useNegation || cardinality > 0){
					tmp = refine(filler, maxLength-3, null, range);
	
					for(OWLClassExpression d : tmp) {
						refinements.add(df.getOWLObjectMaxCardinality(cardinality,role,d));
					}	
				}
				
				// rule 2: <= x r.C  =>  <= (x-1) r.C
//				int number = max.getNumber();
				if((useNegation && cardinality > 1) || (!useNegation && cardinality > 2)){
					refinements.add(df.getOWLObjectMaxCardinality(cardinality-1,role,filler));
				}
				
			} else if(description instanceof OWLObjectMinCardinality) {
				tmp = refine(filler, maxLength-3, null, range);

				for(OWLClassExpression d : tmp) {
					refinements.add(df.getOWLObjectMinCardinality(cardinality,role,d));
				}
				
				// >= x r.C  =>  >= (x+1) r.C
//				int number = min.getNumber();
				if(cardinality < maxNrOfFillers.get(role)){
					refinements.add(df.getOWLObjectMinCardinality(cardinality+1,role,filler));	
				}
			}
		} else if (description instanceof OWLDataSomeValuesFrom) {
			OWLDataPropertyExpression dp = ((OWLDataSomeValuesFrom) description).getProperty();
			OWLDataRange dr = ((OWLDataSomeValuesFrom) description).getFiller();
			if(dr instanceof OWLDatatypeRestriction){
				OWLDatatype datatype = ((OWLDatatypeRestriction) dr).getDatatype();
				Set<OWLFacetRestriction> facetRestrictions = ((OWLDatatypeRestriction) dr).getFacetRestrictions();
				OWLDatatypeRestriction newDatatypeRestriction = null;
				if(datatype.isDouble()){
					for (OWLFacetRestriction facetRestriction : facetRestrictions) {
						OWLFacet facet = facetRestriction.getFacet();
						double value = facetRestriction.getFacetValue().parseDouble();
						if(facet == OWLFacet.MAX_INCLUSIVE){
							// find out which split value was used
							int splitIndex = splits.get(dp).lastIndexOf(value);
							if(splitIndex == -1)
								throw new Error("split error");
							int newSplitIndex = splitIndex - 1;
							if(newSplitIndex >= 0) {
								double newValue = splits.get(dp).get(newSplitIndex);
								newDatatypeRestriction = df.getOWLDatatypeMaxInclusiveRestriction(newValue);
							}
						} else if(facet == OWLFacet.MIN_INCLUSIVE){
							// find out which split value was used
							int splitIndex = splits.get(dp).lastIndexOf(value);
							if(splitIndex == -1)
								throw new Error("split error");
							int newSplitIndex = splitIndex + 1;
							if(newSplitIndex < splits.get(dp).size()) {
								double newValue = splits.get(dp).get(newSplitIndex);
								newDatatypeRestriction = df.getOWLDatatypeMinInclusiveRestriction(newValue);
							}
						}
					}
				} else if(datatype.isInteger()){
					for (OWLFacetRestriction facetRestriction : facetRestrictions) {
						OWLFacet facet = facetRestriction.getFacet();
						int value = facetRestriction.getFacetValue().parseInteger();
						if(facet == OWLFacet.MAX_INCLUSIVE){
							// find out which split value was used
							int splitIndex = splitsInt.get(dp).lastIndexOf(value);
							if(splitIndex == -1)
								throw new Error("split error");
							int newSplitIndex = splitIndex - 1;
							if(newSplitIndex >= 0) {
								int newValue = splitsInt.get(dp).get(newSplitIndex);
								newDatatypeRestriction = df.getOWLDatatypeMaxInclusiveRestriction(newValue);
							}
						} else if(facet == OWLFacet.MIN_INCLUSIVE){
							// find out which split value was used
							int splitIndex = splitsInt.get(dp).lastIndexOf(value);
							if(splitIndex == -1)
								throw new Error("split error");
							int newSplitIndex = splitIndex + 1;
							if(newSplitIndex < splitsInt.get(dp).size()) {
								int newValue = splitsInt.get(dp).get(newSplitIndex);
								newDatatypeRestriction = df.getOWLDatatypeMinInclusiveRestriction(newValue);
							}
						}
					}
				}
				if(newDatatypeRestriction != null){
					refinements.add(df.getOWLDataSomeValuesFrom(dp, newDatatypeRestriction));
				}
			}
			
		} else if (description instanceof OWLDataHasValue) {
			OWLDataPropertyExpression dp = ((OWLDataHasValue) description).getProperty();
			OWLLiteral value = ((OWLDataHasValue) description).getValue();
			if(!dp.isAnonymous()){
				Set<OWLDataProperty> subDPs = reasoner.getSubProperties(dp.asOWLDataProperty());
				for(OWLDataProperty subDP : subDPs) {
					refinements.add(df.getOWLDataHasValue(subDP, value));
				}
			}
		}
		
		// if a refinement is not Bottom, Top, ALL r.Bottom a refinement of top can be appended
		if(!description.isOWLThing() && !description.isOWLNothing() 
				&& !(description instanceof OWLObjectAllValuesFrom && ((OWLObjectAllValuesFrom)description).getFiller().isOWLNothing())) {
			// -1 because of the AND symbol which is appended
			int topRefLength = maxLength - OWLClassExpressionUtils.getLength(description) - 1; 
			
			// maybe we have to compute new top refinements here
			if(currDomain.isOWLThing()) {
				if(topRefLength > topRefinementsLength)
					computeTopRefinements(topRefLength);
			} else if(topRefLength > topARefinementsLength.get(currDomain))
				computeTopRefinements(topRefLength,(OWLClass)currDomain);
			
			if(topRefLength>0) {
				Set<OWLClassExpression> topRefs;
				if(currDomain.isOWLThing())
					topRefs = topRefinementsCumulative.get(topRefLength);
				else
					topRefs = topARefinementsCumulative.get(currDomain).get(topRefLength);
				
				for(OWLClassExpression c : topRefs) {
					// true if refinement should be skipped due to filters,
					// false otherwise
					boolean skip = false;
					
					// if a refinement of of the form ALL r, we check whether ALL r
					// does not occur already
					if(applyAllFilter) {
						if(c instanceof OWLObjectAllValuesFrom) {
							if(description instanceof OWLNaryBooleanClassExpression){
								for(OWLClassExpression child : ((OWLNaryBooleanClassExpression) description).getOperands()) {
									if(child instanceof OWLObjectAllValuesFrom) {
										OWLObjectPropertyExpression r1 = ((OWLObjectAllValuesFrom) c).getProperty();
										OWLObjectPropertyExpression r2 = ((OWLObjectAllValuesFrom) child).getProperty();
										if(r1.equals(r2)){
											skip = true;
											break;
										}
									}
								}
							}
						}
					}
					
					// check for double datatype properties
					/*
					if(c instanceof DatatypeSomeRestriction && 
							description instanceof DatatypeSomeRestriction) {
						DataRange dr = ((DatatypeSomeRestriction)c).getDataRange();
						DataRange dr2 = ((DatatypeSomeRestriction)description).getDataRange();
						// it does not make sense to have statements like height >= 1.8 AND height >= 1.7
						if((dr instanceof DoubleMaxValue && dr2 instanceof DoubleMaxValue)
							||(dr instanceof DoubleMinValue && dr2 instanceof DoubleMinValue))
							skip = true;
					}*/
					
					// perform a disjointness check when named classes are added;
					// this can avoid a lot of superfluous computation in the algorithm e.g.
					// when A1 looks good, so many refinements of the form (A1 OR (A2 AND A3))
					// are generated which are all equal to A1 due to disjointness of A2 and A3
					if(disjointChecks && !c.isAnonymous() && !description.isAnonymous() && isDisjoint(description, c)) {
						skip = true;
//						System.out.println(c + " ignored when refining " + description);
					}	
					
					if(!skip) {
						List<OWLClassExpression> operands = Lists.newArrayList(description, c);
						Collections.sort(operands);
						OWLObjectIntersectionOf mc = new OWLObjectIntersectionOfImplExt(operands);
						
						// clean and transform to ordered negation normal form
						mc = (OWLObjectIntersectionOf) ConceptTransformation.cleanConceptNonRecursive(mc);
						ConceptTransformation.transformToOrderedNegationNormalFormNonRecursive(mc);
						
						// last check before intersection is added
						if(checkIntersection(mc))
							refinements.add(mc);
					}
				}
			}
		}
		
//		for(OWLClassExpression refinement : refinements) {
//			if((refinement instanceof Intersection || refinement instanceof Union) && refinement.getChildren().size()<2) {
//				System.out.println(OWLClassExpression + " " + refinement + " " + currDomain + " " + maxLength);
//				System.exit(0);
//			}
//		}
//		System.out.println("++++++++\nREFINING: " + description + "   maxLength:" + maxLength);
//		System.out.println(refinements);
		return refinements;		
	}
	
	// when a child of an intersection is refined and reintegrated into the
	// intersection, we can perform some sanity checks;
	// method returns true if everything is OK and false otherwise
	// TODO: can be implemented more efficiently if the newly added child
	// is given as parameter
	public static boolean checkIntersection(OWLObjectIntersectionOf intersection) {
		// rule 1: max. restrictions at most once
		boolean maxDoubleOccurence = false;
		// rule 2: min restrictions at most once
		boolean minDoubleOccurence = false;
		// rule 3: no double occurences of boolean datatypes
		TreeSet<OWLDataProperty> occuredDP = new TreeSet<OWLDataProperty>();
		// rule 4: no double occurences of hasValue restrictions
		TreeSet<OWLObjectProperty> occuredVR = new TreeSet<OWLObjectProperty>();
		// rule 5: max. restrictions at most once
				boolean maxIntOccurence = false;
				// rule 6: min restrictions at most once
				boolean minIntOccurence = false;
		
		for(OWLClassExpression child : intersection.getOperands()) {
			if(child instanceof OWLDataSomeValuesFrom) {
				OWLDataRange dr = ((OWLDataSomeValuesFrom)child).getFiller();
				if(dr instanceof OWLDatatypeRestriction){
					OWLDatatype datatype = ((OWLDatatypeRestriction) dr).getDatatype();
					for (OWLFacetRestriction facetRestriction : ((OWLDatatypeRestriction) dr).getFacetRestrictions()) {
						OWLFacet facet = facetRestriction.getFacet();
						if (facet == OWLFacet.MIN_INCLUSIVE) {
							if (datatype.isDouble()) {
								if (minDoubleOccurence) {
									return false;
								} else {
									minDoubleOccurence = true;
								}
							} else if (datatype.isInteger()) {
								if (minIntOccurence) {
									return false;
								} else {
									minIntOccurence = true;
								}
							}
						} else if (facet == OWLFacet.MAX_INCLUSIVE) {
							if (datatype.isDouble()) {
								if (maxDoubleOccurence) {
									return false;
								} else {
									maxDoubleOccurence = true;
								}
							} else if (datatype.isInteger()) {
								if (maxIntOccurence) {
									return false;
								} else {
									maxIntOccurence = true;
								}
							}
						}
					}
				}
			} else if(child instanceof OWLDataHasValue) {
				OWLDataProperty dp = ((OWLDataHasValue) child).getProperty().asOWLDataProperty();
//				System.out.println("dp: " + dp);
				// return false if the boolean property exists already
				if(!occuredDP.add(dp))
					return false;
			} else if(child instanceof OWLObjectHasValue) {
				OWLObjectProperty op = ((OWLObjectHasValue) child).getProperty().asOWLObjectProperty();
				if(!occuredVR.add(op))
					return false;
			}
//			System.out.println(child.getClass());
		}
		return true;
	}
	
	/**
	 * By default, the operator does not specialize e.g. (A or B) to A, because
	 * it only guarantees weak completeness. Under certain circumstances, e.g.
	 * refinement of a fixed given concept, it can be useful to allow such
	 * refinements, which can be done by passing the parameter true to this method. 
	 * @param dropDisjuncts Whether to remove disjuncts in refinement process.
	 */
	public void setDropDisjuncts(boolean dropDisjuncts) {
		this.dropDisjuncts = dropDisjuncts;
	}	
	
	private void computeTopRefinements(int maxLength) {
		computeTopRefinements(maxLength, null);
	}
	
	private void computeTopRefinements(int maxLength, OWLClassExpression domain) {
		long topComputationTimeStartNs = System.nanoTime();
//		System.out.println("computing top refinements for " + domain + " up to length " + maxLength);		
		
		if(domain == null && m.size() == 0)
			computeM();
		
		if(domain != null && !mA.containsKey(domain))
			computeM(domain);
		
		int refinementsLength;
		
		if(domain == null) {
			refinementsLength = topRefinementsLength;
		} else {
			if(!topARefinementsLength.containsKey(domain))
				topARefinementsLength.put(domain,0);

			refinementsLength = topARefinementsLength.get(domain);
		}

		// compute all possible combinations of the disjunction
		for(int i = refinementsLength+1; i <= maxLength; i++) {
			combos.put(i,MathOperations.getCombos(i, mMaxLength));

			// initialise the refinements with empty sets
			if(domain == null) {
				topRefinements.put(i, new TreeSet<OWLClassExpression>());
			} else {
				if(!topARefinements.containsKey(domain))
					topARefinements.put(domain, new TreeMap<Integer,SortedSet<OWLClassExpression>>());
				topARefinements.get(domain).put(i, new TreeSet<OWLClassExpression>());
			}
				
			for(List<Integer> combo : combos.get(i)) {
				
				// combination is a single number => try to use M
				if(combo.size()==1) {
					// note we cannot use "put" instead of "addAll" because there
					// can be several combos for one length
					if(domain == null)
						topRefinements.get(i).addAll(m.get(i));
					else
						topARefinements.get(domain).get(i).addAll(mA.get(domain).get(i));
				// combinations has several numbers => generate disjunct
				} else {
					
					// check whether the combination makes sense, i.e. whether
					// all lengths mentioned in it have corresponding elements
					// e.g. when negation is deactivated there won't be elements of
					// length 2 in M
					boolean validCombo = true;
					for(Integer j : combo) {
						if((domain == null && m.get(j).size()==0) || 
								(domain != null && mA.get(domain).get(j).size()==0))
							validCombo = false;
					}
					
					if(validCombo) {
						
						SortedSet<OWLObjectUnionOf> baseSet = new TreeSet<OWLObjectUnionOf>();
						for(Integer j : combo) {
							if(domain == null)
								baseSet = MathOperations.incCrossProduct(baseSet, m.get(j));
							else
								baseSet = MathOperations.incCrossProduct(baseSet, mA.get(domain).get(j));
						}
						
						// convert all concepts in ordered negation normal form
						for(OWLClassExpression concept : baseSet) {
							ConceptTransformation.transformToOrderedForm(concept);
						}
						
						// apply the exists filter (throwing out all refinements with
						// double \exists r for any r)
						// TODO: similar filtering can be done for boolean datatype
						// properties
						if(applyExistsFilter) {
							Iterator<OWLObjectUnionOf> it = baseSet.iterator();
							while(it.hasNext()) {
								if(MathOperations.containsDoubleObjectSomeRestriction(it.next()))
									it.remove();							
							}
						}
							
						// add computed refinements
						if(domain == null)
							topRefinements.get(i).addAll(baseSet);
						else
							topARefinements.get(domain).get(i).addAll(baseSet);
						
					}
				}
			}
			
			// create cumulative versions of refinements such that they can
			// be accessed easily
			TreeSet<OWLClassExpression> cumulativeRefinements = new TreeSet<OWLClassExpression>();
			for(int j=1; j<=i; j++) {
				if(domain == null) {
					cumulativeRefinements.addAll(topRefinements.get(j));
				} else {
					cumulativeRefinements.addAll(topARefinements.get(domain).get(j));
				}
			}	
			
			if(domain == null) {
				topRefinementsCumulative.put(i, cumulativeRefinements);
			} else {
				if(!topARefinementsCumulative.containsKey(domain))
					topARefinementsCumulative.put(domain, new TreeMap<Integer, TreeSet<OWLClassExpression>>());
				topARefinementsCumulative.get(domain).put(i, cumulativeRefinements);
			}
		}
		
		// register new top refinements length
		if(domain == null)
			topRefinementsLength = maxLength;
		else
			topARefinementsLength.put(domain,maxLength);
		
		topComputationTimeNs += System.nanoTime() - topComputationTimeStartNs;
		
//		if(domain == null) {
//			System.out.println("computed top refinements up to length " + topRefinementsLength + ": " + topRefinementsCumulative.get(maxLength));
//		} else {
//			System.out.println("computed top refinements up to length " + topARefinementsLength + ": (domain: "+domain+"): " + topARefinementsCumulative.get(domain).get(maxLength));
//		}		
	}
	
	// compute M_\top
	private void computeM() {
		long mComputationTimeStartNs = System.nanoTime();

		// initialise all possible lengths (1 to 3)
		for(int i=1; i<=mMaxLength; i++) {
			m.put(i, new TreeSet<OWLClassExpression>());
		}
		
		SortedSet<OWLClassExpression> m1 = subHierarchy.getSubClasses(df.getOWLThing()); 
		m.put(1,m1);		
		
		SortedSet<OWLClassExpression> m2 = new TreeSet<OWLClassExpression>();
		if(useNegation) {
			Set<OWLClassExpression> m2tmp = subHierarchy.getSuperClasses(df.getOWLNothing());
			for(OWLClassExpression c : m2tmp) {
				if(!c.isOWLThing()) {
					m2.add(df.getOWLObjectComplementOf(c));	
				}
			}
		}
		
		// boolean datatypes, e.g. testPositive = true
		if(useBooleanDatatypes) {
			Set<OWLDataProperty> booleanDPs = reasoner.getBooleanDatatypeProperties();
			for(OWLDataProperty dp : booleanDPs) {
				m2.add(df.getOWLDataHasValue(dp, df.getOWLLiteral(true)));
				m2.add(df.getOWLDataHasValue(dp, df.getOWLLiteral(false)));
			}
		}		
		m.put(2,m2);
			
		SortedSet<OWLClassExpression> m3 = new TreeSet<OWLClassExpression>();
		if(useExistsConstructor) {
			for(OWLObjectProperty r : reasoner.getMostGeneralProperties()) {
				m3.add(df.getOWLObjectSomeValuesFrom(r, df.getOWLThing()));
			}				
		}
		
		if(useAllConstructor) {
			// we allow \forall r.\top here because otherwise the operator
			// becomes too difficult to manage due to dependencies between
			// M_A and M_A' where A'=ran(r)
			for(OWLObjectProperty r : reasoner.getMostGeneralProperties()) {
				m3.add(df.getOWLObjectAllValuesFrom(r, df.getOWLThing()));
			}				
		}		
		
		if(useNumericDatatypes) {
			Set<OWLDataProperty> doubleDPs = reasoner.getDoubleDatatypeProperties();
			
			for(OWLDataProperty dp : doubleDPs) {
				if(splits.get(dp).size() > 0) {
					double min = splits.get(dp).get(0);
					double max = splits.get(dp).get(splits.get(dp).size()-1);
					m3.add(df.getOWLDataSomeValuesFrom(dp, df.getOWLDatatypeMinInclusiveRestriction(min)));
					m3.add(df.getOWLDataSomeValuesFrom(dp, df.getOWLDatatypeMaxInclusiveRestriction(max)));
				}
			}
		}
		
		if(useDataHasValueConstructor) {
			Set<OWLDataProperty> stringDPs = reasoner.getStringDatatypeProperties();
			for(OWLDataProperty dp : stringDPs) {
				// loop over frequent values
				Set<OWLLiteral> freqValues = frequentDataValues.get(dp);
				for(OWLLiteral lit : freqValues) {
					m3.add(df.getOWLDataHasValue(dp, lit));
				}
			}			
		}		
		
		m.put(3,m3);
		
		SortedSet<OWLClassExpression> m4 = new TreeSet<OWLClassExpression>();
		if(useCardinalityRestrictions) {
			for(OWLObjectProperty r : reasoner.getMostGeneralProperties()) {
				int maxFillers = maxNrOfFillers.get(r);
				// zero fillers: <= -1 r.C does not make sense
				// one filler: <= 0 r.C is equivalent to NOT EXISTS r.C,
				// but we still keep it, because ALL r.NOT C may be difficult to reach
				if((useNegation && maxFillers > 0) || (!useNegation && maxFillers > 1))		
					m4.add(df.getOWLObjectMaxCardinality(maxFillers-1, r, df.getOWLThing()));
			}
		}
		m.put(4,m4);
		
//		System.out.println("m: " + m);
		
		mComputationTimeNs += System.nanoTime() - mComputationTimeStartNs;
	}
	
	// computation of the set M_A
	// a major difference compared to the ILP 2007 \rho operator is that
	// M is finite and contains elements of length (currently) at most 3
	private void computeM(OWLClassExpression nc) {
		long mComputationTimeStartNs = System.nanoTime();

//		System.out.println(nc);
		
		mA.put(nc, new TreeMap<Integer,SortedSet<OWLClassExpression>>());
		// initialise all possible lengths (1 to 3)
		for(int i=1; i<=mMaxLength; i++) {
			mA.get(nc).put(i, new TreeSet<OWLClassExpression>());
		}
		
		// incomplete, prior implementation
//		SortedSet<Description> m1 = subHierarchy.getSubClasses(nc); 
//		mA.get(nc).put(1,m1);
		
		// most general classes, which are not disjoint with nc and provide real refinement
		SortedSet<OWLClassExpression> m1 = getClassCandidates(nc);
		mA.get(nc).put(1,m1);
		
		// most specific negated classes, which are not disjoint with nc
		SortedSet<OWLClassExpression> m2 = new TreeSet<OWLClassExpression>();
		if(useNegation) {
			m2 = getNegClassCandidates(nc);
			mA.get(nc).put(2,m2);
		}
		
//		System.out.println("m1 " + "(" + nc + "): " + m1);
//		System.out.println("m2 " + "(" + nc + "): " + m2);
		
		/*
		SortedSet<Description> m2 = new TreeSet<Description>(conceptComparator);
		if(useNegation) {
			// the definition in the paper is more complex, but actually
			// we only have to insert the most specific concepts satisfying
			// the mentioned restrictions; there is no need to implement a
			// recursive method because for A subClassOf A' we have not A'
			// subClassOf A and thus: if A and B are disjoint then also A'
			// and B; if not A AND B = B then also not A' AND B = B
			// 2010/03: the latter is not correct => a recursive method is needed
			SortedSet<Description> m2tmp = subHierarchy.getSuperClasses(new Nothing());
			
			for(OWLClassExpression c : m2tmp) {
//				if(c instanceof Thing)
//					m2.add(c);
//				else {
				// we obviously do not add \top (\top refines \top does not make sense)
				if(!(c instanceof Thing)) {
					NamedClass a = (OWLClass) c;
					if(!isNotADisjoint(a, nc) && isNotAMeaningful(a, nc))
						m2.add(df.getOWLObjectComplementOf(a));
				}
			}	
		}
		*/
		
		// compute applicable properties
		computeMg(nc);		
		
		// boolean datatypes, e.g. testPositive = true
		if(useBooleanDatatypes) {
			Set<OWLDataProperty> booleanDPs = mgbd.get(nc);
			for(OWLDataProperty dp : booleanDPs) {
				m2.add(df.getOWLDataHasValue(dp, df.getOWLLiteral(true)));
				m2.add(df.getOWLDataHasValue(dp, df.getOWLLiteral(false)));
			}
		}
		
		mA.get(nc).put(2,m2);
			
		SortedSet<OWLClassExpression> m3 = new TreeSet<OWLClassExpression>();
		if(useExistsConstructor) {
			for(OWLObjectProperty r : mgr.get(nc)) {
				m3.add(df.getOWLObjectSomeValuesFrom(r, df.getOWLThing()));
			}				
		}
		
		if(useAllConstructor) {
			// we allow \forall r.\top here because otherwise the operator
			// becomes too difficult to manage due to dependencies between
			// M_A and M_A' where A'=ran(r)
			for(OWLObjectProperty r : mgr.get(nc)) {
				m3.add(df.getOWLObjectAllValuesFrom(r, df.getOWLThing()));
			}				
		}		
		
		if(useNumericDatatypes) {
			Set<OWLDataProperty> numericDPs = mgNumeric.get(nc);
			
			for(OWLDataProperty dp : numericDPs) {
				if(splits.get(dp).size() > 0) {
					double min = splits.get(dp).get(0);
					double max = splits.get(dp).get(splits.get(dp).size()-1);
					m3.add(df.getOWLDataSomeValuesFrom(dp, df.getOWLDatatypeMinInclusiveRestriction(min)));
					m3.add(df.getOWLDataSomeValuesFrom(dp, df.getOWLDatatypeMaxInclusiveRestriction(max)));
				}
			}
		}
		
		if(useDataHasValueConstructor) {
			Set<OWLDataProperty> stringDPs = mgsd.get(nc);
			for(OWLDataProperty dp : stringDPs) {
				// loop over frequent values
				Set<OWLLiteral> freqValues = frequentDataValues.get(dp);
				for(OWLLiteral lit : freqValues) {
					m3.add(df.getOWLDataHasValue(dp, lit));
				}
			}			
		}		
		
		mA.get(nc).put(3,m3);
		
		SortedSet<OWLClassExpression> m4 = new TreeSet<OWLClassExpression>();
		if(useCardinalityRestrictions) {
			for(OWLObjectProperty r : mgr.get(nc)) {
				int maxFillers = maxNrOfFillers.get(r);
				// zero fillers: <= -1 r.C does not make sense
				// one filler: <= 0 r.C is equivalent to NOT EXISTS r.C,
				// but we still keep it, because ALL r.NOT C may be difficult to reach
				if((useNegation && maxFillers > 0) || (!useNegation && maxFillers > 1))		
					m4.add(df.getOWLObjectMaxCardinality(maxFillers-1, r, df.getOWLThing()));
			}
		}
		mA.get(nc).put(4,m4);
		
//		System.out.println("m for " + nc + ": " + mA.get(nc));
		
		mComputationTimeNs += System.nanoTime() - mComputationTimeStartNs;
	}
	
	// get candidates for a refinement of \top restricted to a class B
	public SortedSet<OWLClassExpression> getClassCandidates(OWLClassExpression index) {
		return getClassCandidatesRecursive(index, df.getOWLThing());
	}
	
	private SortedSet<OWLClassExpression> getClassCandidatesRecursive(OWLClassExpression index, OWLClassExpression upperClass) {
		SortedSet<OWLClassExpression> candidates = new TreeSet<OWLClassExpression>();
//		System.out.println("index " + index + " upper class " + upperClass);
		
		// we descend the subsumption hierarchy to ensure that we get
		// the most general concepts satisfying the criteria
		for(OWLClassExpression candidate :  subHierarchy.getSubClasses(upperClass)) {
//				System.out.println("testing " + candidate + " ... ");
							
//				NamedClass candidate = (OWLClass) d;
				// check disjointness with index (if not no further traversal downwards is necessary)
				if(!isDisjoint(candidate,index)) {
//					System.out.println( " passed disjointness test ... ");
					// check whether the class is meaningful, i.e. adds something to the index
					// to do this, we need to make sure that the class is not a superclass of the
					// index (otherwise we get nothing new) - for instance based disjoints, we 
					// make sure that there is at least one individual, which is not already in the
					// upper class
					boolean meaningful;
					if(instanceBasedDisjoints) {
						// bug: tests should be performed against the index, not the upper class
//						SortedSet<OWLIndividual> tmp = rs.getIndividuals(upperClass);
						SortedSet<OWLIndividual> tmp = reasoner.getIndividuals(index);
						tmp.removeAll(reasoner.getIndividuals(candidate));
//						System.out.println("  instances of " + index + " and not " + candidate + ": " + tmp.size());
						meaningful = tmp.size() != 0;
					} else {
						meaningful = !isDisjoint(df.getOWLObjectComplementOf(candidate),index);
					}
					
					if(meaningful) {
						// candidate went successfully through all checks
						candidates.add(candidate);
//						System.out.println(" real refinement");
					} else {
						// descend subsumption hierarchy to find candidates
//						System.out.println(" enter recursion");
						candidates.addAll(getClassCandidatesRecursive(index, candidate));
					}
				} 
//				else {
//					System.out.println(" ruled out, because it is disjoint");
//				}
		}
//		System.out.println("cc method exit");
		return candidates;
	}	
	
	// get candidates for a refinement of \top restricted to a class B
	public SortedSet<OWLClassExpression> getNegClassCandidates(OWLClassExpression index) {
		return getNegClassCandidatesRecursive(index, df.getOWLNothing());
	}
	
	private SortedSet<OWLClassExpression> getNegClassCandidatesRecursive(OWLClassExpression index, OWLClassExpression lowerClass) {
		SortedSet<OWLClassExpression> candidates = new TreeSet<OWLClassExpression>();
//		System.out.println("index " + index + " lower class " + lowerClass);
		
		for(OWLClassExpression candidate :  subHierarchy.getSuperClasses(lowerClass)) {
			if(!candidate.isOWLThing()) {
//				System.out.println("candidate: " + candidate);
				// check disjointness with index/range (should not be disjoint otherwise not useful)
				if(!isDisjoint(df.getOWLObjectComplementOf(candidate),index)) {
					boolean meaningful;
//					System.out.println("not disjoint");
					if(instanceBasedDisjoints) {
						SortedSet<OWLIndividual> tmp = reasoner.getIndividuals(index);
						tmp.removeAll(reasoner.getIndividuals(df.getOWLObjectComplementOf(candidate)));
						meaningful = tmp.size() != 0;
//						System.out.println("instances " + tmp.size());
					} else {
						meaningful = !isDisjoint(candidate,index);
					}
					
					if(meaningful) {
						candidates.add(df.getOWLObjectComplementOf(candidate));
					} else {
						candidates.addAll(getNegClassCandidatesRecursive(index, candidate));
					}
				} 
			}
		}
		return candidates;
	}	
	
	private void computeMg(OWLClassExpression domain) {
		// compute the applicable properties if this has not been done yet
		if(appOP.get(domain) == null)
			computeApp(domain);	
		
		// initialise mgr, mgbd, mgdd, mgsd
		mgr.put(domain, new TreeSet<OWLObjectProperty>());
		mgbd.put(domain, new TreeSet<OWLDataProperty>());
		mgNumeric.put(domain, new TreeSet<OWLDataProperty>());
		mgsd.put(domain, new TreeSet<OWLDataProperty>());
		
		SortedSet<OWLObjectProperty> mostGeneral = reasoner.getMostGeneralProperties();
		computeMgrRecursive(domain, mostGeneral, mgr.get(domain));
		SortedSet<OWLDataProperty> mostGeneralDP = reasoner.getMostGeneralDatatypeProperties();
		// we make the (reasonable) assumption here that all sub and super
		// datatype properties have the same type (e.g. boolean, integer, double)
		Set<OWLDataProperty> mostGeneralBDP = Helper.intersection(mostGeneralDP, reasoner.getBooleanDatatypeProperties());
		Set<OWLDataProperty> mostGeneralNumericDPs = Helper.intersection(mostGeneralDP, reasoner.getNumericDataProperties());
		Set<OWLDataProperty> mostGeneralStringDPs = Helper.intersection(mostGeneralDP, reasoner.getStringDatatypeProperties());
		computeMgbdRecursive(domain, mostGeneralBDP, mgbd.get(domain));	
		computeMostGeneralNumericDPRecursive(domain, mostGeneralNumericDPs, mgNumeric.get(domain));
		computeMostGeneralStringDPRecursive(domain, mostGeneralStringDPs, mgsd.get(domain));
	}
	
	private void computeMgrRecursive(OWLClassExpression domain, Set<OWLObjectProperty> currProperties, Set<OWLObjectProperty> mgrTmp) {
		for(OWLObjectProperty prop : currProperties) {
			if(appOP.get(domain).contains(prop))
				mgrTmp.add(prop);
			else
				computeMgrRecursive(domain, reasoner.getSubProperties(prop), mgrTmp);
		}
	}
	
	private void computeMgbdRecursive(OWLClassExpression domain, Set<OWLDataProperty> currProperties, Set<OWLDataProperty> mgbdTmp) {
		for(OWLDataProperty prop : currProperties) {
			if(appBD.get(domain).contains(prop))
				mgbdTmp.add(prop);
			else
				computeMgbdRecursive(domain, reasoner.getSubProperties(prop), mgbdTmp);
		}
	}	
	
	
	private void computeMostGeneralNumericDPRecursive(OWLClassExpression domain, Set<OWLDataProperty> currProperties, Set<OWLDataProperty> mgddTmp) {
		for(OWLDataProperty prop : currProperties) {
			if(appNumeric.get(domain).contains(prop))
				mgddTmp.add(prop);
			else
				computeMostGeneralNumericDPRecursive(domain, reasoner.getSubProperties(prop), mgddTmp);
		}
	}
	private void computeMostGeneralStringDPRecursive(OWLClassExpression domain, Set<OWLDataProperty> currProperties, Set<OWLDataProperty> mgddTmp) {
		for(OWLDataProperty prop : currProperties) {
			if(appSD.get(domain).contains(prop))
				mgddTmp.add(prop);
			else
				computeMostGeneralStringDPRecursive(domain, reasoner.getSubProperties(prop), mgddTmp);
		}
	}
	
	// computes the set of applicable properties for a given class
	private void computeApp(OWLClassExpression domain) {
		SortedSet<OWLIndividual> individuals1 = reasoner.getIndividuals(domain);
		// object properties
		Set<OWLObjectProperty> mostGeneral = reasoner.getObjectProperties();
		Set<OWLObjectProperty> applicableRoles = new TreeSet<OWLObjectProperty>();
		for(OWLObjectProperty role : mostGeneral) {
			// TODO: currently we just rely on named classes as roles,
			// instead of computing dom(r) and ran(r)
			OWLClassExpression d = opDomains.get(role);
			
			Set<OWLIndividual> individuals2 = new HashSet<OWLIndividual>();
			for (Entry<OWLIndividual, SortedSet<OWLIndividual>> entry : reasoner.getPropertyMembers(role).entrySet()) {
				OWLIndividual ind = entry.getKey();
				if(!entry.getValue().isEmpty()){
					individuals2.add(ind);
				}
			}
			
			boolean disjoint = Sets.intersection(individuals1, individuals2).isEmpty();
//			if(!isDisjoint(domain,d))
				if(!disjoint){
					applicableRoles.add(role);
				}
				
		}
		appOP.put(domain, applicableRoles);
		
		// boolean datatype properties
		Set<OWLDataProperty> mostGeneralBDPs = reasoner.getBooleanDatatypeProperties();
		Set<OWLDataProperty> applicableBDPs = new TreeSet<OWLDataProperty>();
		for(OWLDataProperty role : mostGeneralBDPs) {
//			Description d = (OWLClass) rs.getDomain(role);
			OWLClassExpression d = dpDomains.get(role);
			if(!isDisjoint(domain,d))
				applicableBDPs.add(role);
		}
		appBD.put(domain, applicableBDPs);	
		
		// numeric data properties
		Set<OWLDataProperty> mostGeneralNumericDPs = reasoner.getNumericDataProperties();
		Set<OWLDataProperty> applicableNumericDPs = new TreeSet<OWLDataProperty>();
		for(OWLDataProperty role : mostGeneralNumericDPs) {
			// get domain of property
			OWLClassExpression d = dpDomains.get(role);
			// check if it's not disjoint with current class expression
			if(!isDisjoint(domain,d))
				applicableNumericDPs.add(role);
		}
		appNumeric.put(domain, applicableNumericDPs);
		
		// string datatype properties
		Set<OWLDataProperty> mostGeneralSDPs = reasoner.getStringDatatypeProperties();
		Set<OWLDataProperty> applicableSDPs = new TreeSet<OWLDataProperty>();
		for(OWLDataProperty role : mostGeneralSDPs) {
//			Description d = (OWLClass) rs.getDomain(role);
			OWLClassExpression d = dpDomains.get(role);
//			System.out.println("domain: " + d);
			if(!isDisjoint(domain,d))
				applicableSDPs.add(role);
		}
		appSD.put(domain, applicableSDPs);	
		
	}
	
	// returns true if the intersection contains elements disjoint
	// to the given OWLClassExpression (if true adding the OWLClassExpression to
	// the intersection results in a OWLClassExpression equivalent to bottom)
	// e.g. OldPerson AND YoungPerson; Nitrogen-34 AND Tin-113
	// Note: currently we only check named classes in the intersection,
	// it would be interesting to see whether it makes sense to extend this
	// (advantage: less refinements, drawback: operator will need infinitely many
	// reasoner queries in the long run)
	@SuppressWarnings({"unused"})
	private boolean containsDisjoints(OWLObjectIntersectionOf intersection, OWLClassExpression d) {
		List<OWLClassExpression> children = intersection.getOperandsAsList();
		for(OWLClassExpression child : children) {
			if(d.isOWLNothing())
				return true;
			else if(!child.isAnonymous()) {
				if(isDisjoint(child, d))
					return true;
			}
		}
		return false;
	}
	
	private boolean isDisjoint(OWLClassExpression d1, OWLClassExpression d2) {
		if(d1.isOWLThing() || d2.isOWLThing()) {
			return false;
		}
//		System.out.println("| " + d1 + " " + d2);
//		System.out.println("| " + cachedDisjoints);
		
		// check whether we have cached this query
		Map<OWLClassExpression,Boolean> tmp = cachedDisjoints.get(d1);
		Boolean tmp2 = null;
		if(tmp != null)
			tmp2 = tmp.get(d2);
		
//		System.out.println("| " + tmp + " " + tmp2);
		
		if(tmp2==null) {
			Boolean result;
			if(instanceBasedDisjoints) {
				result = isDisjointInstanceBased(d1,d2);
			} else {
				OWLClassExpression d = df.getOWLObjectIntersectionOf(d1, d2);
				result = reasoner.isSuperClassOf(df.getOWLNothing(), d);		
			}
			// add the result to the cache (we add it twice such that
			// the order of access does not matter)
			
//			System.out.println("| result: " + result);
			
			// create new entries if necessary
			Map<OWLClassExpression,Boolean> map1 = new TreeMap<OWLClassExpression,Boolean>();
			Map<OWLClassExpression,Boolean> map2 = new TreeMap<OWLClassExpression,Boolean>();
			if(tmp == null)
				cachedDisjoints.put(d1, map1);
			if(!cachedDisjoints.containsKey(d2))
				cachedDisjoints.put(d2, map2);
			
			// add result symmetrically in the OWLClassExpression matrix
			cachedDisjoints.get(d1).put(d2, result);
			cachedDisjoints.get(d2).put(d1, result);
//			System.out.println("---");
			return result;
		} else {
//			System.out.println("===");
			return tmp2;
		}
	}	
	
	private boolean isDisjointInstanceBased(OWLClassExpression d1, OWLClassExpression d2) {
		SortedSet<OWLIndividual> d1Instances = reasoner.getIndividuals(d1);
		SortedSet<OWLIndividual> d2Instances = reasoner.getIndividuals(d2);
//		System.out.println(d1 + " " + d2);
//		System.out.println(d1 + " " + d1Instances);
//		System.out.println(d2 + " " + d2Instances);
		for(OWLIndividual d1Instance : d1Instances) {
			if(d2Instances.contains(d1Instance))
				return false;
		}
		return true;
	}
	
	/*
	// computes whether two classes are disjoint; this should be computed
	// by the reasoner only ones and otherwise taken from a matrix
	private boolean isDisjoint(OWLClass a, OWLClassExpression d) {
		// we need to test whether A AND B is equivalent to BOTTOM
		Description d2 = new Intersection(a, d);
		return rs.subsumes(new Nothing(), d2);
	}*/
	
	// we need to test whether NOT A AND B is equivalent to BOTTOM
	@SuppressWarnings("unused")
	private boolean isNotADisjoint(OWLClass a, OWLClass b) {
//		Map<OWLClass,Boolean> tmp = notABDisjoint.get(a);
//		Boolean tmp2 = null;
//		if(tmp != null)
//			tmp2 = tmp.get(b);
//		
//		if(tmp2==null) {
		OWLClassExpression notA = df.getOWLObjectComplementOf(a);
		OWLClassExpression d = df.getOWLObjectIntersectionOf(notA, b);
			Boolean result = reasoner.isSuperClassOf(df.getOWLNothing(), d);
			// ... add to cache ...
			return result;
//		} else
//			return tmp2;
	}
	
	// we need to test whether NOT A AND B = B
	// (if not then NOT A is not meaningful in the sense that it does
	// not semantically add anything to B) 	
	@SuppressWarnings("unused")
	private boolean isNotAMeaningful(OWLClass a, OWLClass b) {
		OWLClassExpression notA = df.getOWLObjectComplementOf(a);
		OWLClassExpression d = df.getOWLObjectIntersectionOf(notA, b);
		// check b subClassOf b AND NOT A (if yes then it is not meaningful)
		return !reasoner.isSuperClassOf(d, b);
	}
	
	private void computeSplits(OWLDataProperty dp) {
		Set<Double> valuesSet = new TreeSet<Double>();
//		Set<OWLIndividual> individuals = rs.getIndividuals();
		Map<OWLIndividual,SortedSet<Double>> valueMap = reasoner.getDoubleDatatypeMembers(dp);
		// add all values to the set (duplicates will be remove automatically)
		for(Entry<OWLIndividual,SortedSet<Double>> e : valueMap.entrySet())
			valuesSet.addAll(e.getValue());
		// convert set to a list where values are sorted
		List<Double> values = new LinkedList<Double>(valuesSet);
		Collections.sort(values);
		
		int nrOfValues = values.size();
		// create split set
		List<Double> splitsDP = new LinkedList<Double>();
		for(int splitNr=0; splitNr < Math.min(maxNrOfSplits,nrOfValues-1); splitNr++) {
			int index;
			if(nrOfValues<=maxNrOfSplits)
				index = splitNr;
			else
				index = (int) Math.floor(splitNr * (double)nrOfValues/(maxNrOfSplits+1));
			
			double value = 0.5*(values.get(index)+values.get(index+1));
			splitsDP.add(value);
		}
		splits.put(dp, splitsDP);
		
//		System.out.println(values);
//		System.out.println(splits);
//		System.exit(0);
	}
	
	//TODO implement numerical splitting
	
	private <T extends Number & Comparable<Number>> void computeSplits2(OWLDataProperty dp) {
		Set<T> valuesSet = new TreeSet<T>();
//		Set<OWLIndividual> individuals = rs.getIndividuals();
		Map<OWLIndividual, SortedSet<T>> valueMap = reasoner.getNumericDatatypeMembers(dp);
		
		// add all values to the set (duplicates will be remove automatically)
		for(Entry<OWLIndividual, SortedSet<T>> e : valueMap.entrySet()){
			valuesSet.addAll(e.getValue());
		}
		
		// convert set to a list where values are sorted
		List<T> values = new LinkedList<T>(valuesSet);
		Collections.sort(values);
		
		int nrOfValues = values.size();
		
		// create split set
		List<T> splitsDP = new LinkedList<T>();
		for (int splitNr = 0; splitNr < Math.min(maxNrOfSplits, nrOfValues - 1); splitNr++) {
			int index;
			if (nrOfValues <= maxNrOfSplits) {
				index = splitNr;
			} else {
				index = (int) Math.floor(splitNr * (double) nrOfValues / (maxNrOfSplits + 1));
			}
			T number1 = values.get(index);
			T number2 = values.get(index + 1);
			
			T avg = avg(number1, number2);
			
			splitsDP.add(avg);
		}
		splitsNumber.put(dp, splitsDP);
	}
	
	private <T extends Number & Comparable<Number>> T avg(T number1, T number2){
		return number1;
//		T avg = null;
//		if((number1 instanceof Integer && number2 instanceof Integer) || 
//			(number1 instanceof Long && number2 instanceof Long) || 
//			(number1 instanceof Byte && number2 instanceof Byte)
//				) {
//			avg = number1;
//		} else if(number1 instanceof Double && number2 instanceof Double) {
//			avg = (T) Double.valueOf(
//					BigDecimal.valueOf(number1.doubleValue()).
//			add(BigDecimal.valueOf(number2.doubleValue()).divide(
//					BigDecimal.valueOf(0.5d))).doubleValue());
//		} else if(number1 instanceof Float && number2 instanceof Float) {
//			avg = (T) Float.valueOf(
//					BigDecimal.valueOf(number1.floatValue()).
//			add(BigDecimal.valueOf(number2.floatValue()).divide(
//					BigDecimal.valueOf(0.5d))).floatValue());
//		} 
//		return avg;
		
//		return (T) BigDecimal.valueOf(number1.doubleValue()).
//				add(BigDecimal.valueOf(number2.doubleValue()).divide(
//						BigDecimal.valueOf(0.5d)));
	}
	
	
	public int getFrequencyThreshold() {
		return frequencyThreshold;
	}

	public void setFrequencyThreshold(int frequencyThreshold) {
		this.frequencyThreshold = frequencyThreshold;
	}

	public boolean isUseDataHasValueConstructor() {
		return useDataHasValueConstructor;
	}

	public void setUseDataHasValueConstructor(boolean useDataHasValueConstructor) {
		this.useDataHasValueConstructor = useDataHasValueConstructor;
	}

	public boolean isApplyAllFilter() {
		return applyAllFilter;
	}

	public void setApplyAllFilter(boolean applyAllFilter) {
		this.applyAllFilter = applyAllFilter;
	}

	public boolean isUseAllConstructor() {
		return useAllConstructor;
	}

	public void setUseAllConstructor(boolean useAllConstructor) {
		this.useAllConstructor = useAllConstructor;
	}

	public boolean isUseExistsConstructor() {
		return useExistsConstructor;
	}

	public void setUseExistsConstructor(boolean useExistsConstructor) {
		this.useExistsConstructor = useExistsConstructor;
	}

	public boolean isUseHasValueConstructor() {
		return useHasValueConstructor;
	}

	public void setUseHasValueConstructor(boolean useHasValueConstructor) {
		this.useHasValueConstructor = useHasValueConstructor;
	}

	public boolean isUseCardinalityRestrictions() {
		return useCardinalityRestrictions;
	}

	public void setUseCardinalityRestrictions(boolean useCardinalityRestrictions) {
		this.useCardinalityRestrictions = useCardinalityRestrictions;
	}

	public boolean isUseNegation() {
		return useNegation;
	}

	public void setUseNegation(boolean useNegation) {
		this.useNegation = useNegation;
	}

	public boolean isUseBooleanDatatypes() {
		return useBooleanDatatypes;
	}

	public void setUseBooleanDatatypes(boolean useBooleanDatatypes) {
		this.useBooleanDatatypes = useBooleanDatatypes;
	}

	public boolean isUseDoubleDatatypes() {
		return useDoubleDatatypes;
	}

	public void setUseDoubleDatatypes(boolean useDoubleDatatypes) {
		this.useDoubleDatatypes = useDoubleDatatypes;
	}

	public boolean isUseStringDatatypes() {
		return useStringDatatypes;
	}

	public void setUseStringDatatypes(boolean useStringDatatypes) {
		this.useStringDatatypes = useStringDatatypes;
	}
	
	/**
	 * @param useIntDatatypes the useIntDatatypes to set
	 */
	public void setUseIntDatatypes(boolean useIntDatatypes) {
		this.useIntDatatypes = useIntDatatypes;
	}
	
	/**
	 * @return the useIntDatatypes
	 */
	public boolean isUseIntDatatypes() {
		return useIntDatatypes;
	}

	public boolean isInstanceBasedDisjoints() {
		return instanceBasedDisjoints;
	}

	public void setInstanceBasedDisjoints(boolean instanceBasedDisjoints) {
		this.instanceBasedDisjoints = instanceBasedDisjoints;
	}

	public AbstractReasonerComponent getReasoner() {
		return reasoner;
	}

    @Autowired
	public void setReasoner(AbstractReasonerComponent reasoner) {
		this.reasoner = reasoner;
	}

	public ClassHierarchy getSubHierarchy() {
		return subHierarchy;
	}

	public void setSubHierarchy(ClassHierarchy subHierarchy) {
		this.subHierarchy = subHierarchy;
	}

	public OWLClassExpression getStartClass() {
		return startClass;
	}

	public void setStartClass(OWLClassExpression startClass) {
		this.startClass = startClass;
	}

	public int getCardinalityLimit() {
		return cardinalityLimit;
	}

	public void setCardinalityLimit(int cardinalityLimit) {
		this.cardinalityLimit = cardinalityLimit;
	}

	public ObjectPropertyHierarchy getObjectPropertyHierarchy() {
		return objectPropertyHierarchy;
	}

	public void setObjectPropertyHierarchy(ObjectPropertyHierarchy objectPropertyHierarchy) {
		this.objectPropertyHierarchy = objectPropertyHierarchy;
	}

	public DatatypePropertyHierarchy getDataPropertyHierarchy() {
		return dataPropertyHierarchy;
	}

	public void setDataPropertyHierarchy(DatatypePropertyHierarchy dataPropertyHierarchy) {
		this.dataPropertyHierarchy = dataPropertyHierarchy;
	}

	@Override
	public void setReasoner(Reasoner reasoner) {
		this.reasoner = (AbstractReasonerComponent) reasoner;
	}

	@Override
	public void setClassHierarchy(ClassHierarchy classHierarchy) {
		subHierarchy = classHierarchy;
	}
	
	/**
	 * @param useObjectValueNegation the useObjectValueNegation to set
	 */
	public void setUseObjectValueNegation(boolean useObjectValueNegation) {
		this.useObjectValueNegation = useObjectValueNegation;
	}
}