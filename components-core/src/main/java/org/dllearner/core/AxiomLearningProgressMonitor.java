/**
 * 
 */
package org.dllearner.core;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;

/**
 * The AxiomLearningProgressMonitor interface should be implemented by objects that
 * wish to monitor the progress of an axiom learning algorithm. The learning algorithm whose progress is
 * being monitored will call the methods on this interface. The progress monitor
 * is designed to monitor long running learning algorithm tasks such as loading,
 * preprocessing and learning. <br>
 * Tasks are executed sequentially. Nested tasks are not supported. <br>
 * The general contract is that the learning algorithm will call
 * {@link #learningStarted(String)}, then call either
 * {@link #learningTaskBusy()} or {@link #learningProgressChanged(int, int)}
 * any number of times and finally call {@link #learningStopped()} when the
 * task ends or has been interrupted. This cycle may then be repeated.
 * 
 * 
 * @author Lorenz Buehmann
 *
 */
public interface AxiomLearningProgressMonitor {
	/**
     * A standard name for the task of loading a reasoner with axioms. Note that
     * there is no guarantee that the reasoner will use this name for loading.
     */
    String LOADING = "Loading";
    /**
     * A standard name for the task of computing the class hierarchy. Note that
     * there is no guarantee that the reasoner will use this name for the task
     * of computing the class hierarchy.
     */
    String PREPROCESSING = "Preprocessing";
    /**
     * A standard name for the task of computing the types of individual. Note
     * that there is no guarantee that the reasoner will use this name for the
     * task of realising.
     */
    String LEARNING = "Learning";
   

    /**
     * Indicates that some learning algorithm has started. When the learning algorithm has
     * finished the {@link #learningStopped()} method will be called. Once
     * this method has been called it will not be called again unless the
     * {@link #learningStopped()} method has been called. <br>
     * Note that this method may be called from a thread that is not the event
     * dispatch thread.
     * 
     * @param axiomType
     *        The type of axiom
     */
    void learningStarted(AxiomType<? extends OWLAxiom> axiomType);

    /**
     * Indicates that a previously started learning algorithm has now stopped. This method will
     * only be called after the {@link #learningStarted(String)} method has
     * been called.<br>
     * Note that this method may be called from a thread that is not the event
     * dispatch thread.
     */
    void learningStopped(AxiomType<? extends OWLAxiom> axiomType);

    /**
     * Indicates that the learning algorithm is part way through its task. This method
     * will only be called after the {@link #learningStarted(String)} method
     * has been called. It will not be called after the
     * {@link #learningStopped()} method has been called. <br>
     * Note that this method may be called from a thread that is not the event
     * dispatch thread.
     * 
     * @param value
     *        The value or portion of the learning algorithm task completed
     * @param max
     *        The total size of the learning algorithm task
     */
    void learningProgressChanged(AxiomType<? extends OWLAxiom> axiomType, int value, int max);

    /**
     * Indicates that the learning algorithm is busy performing a task whose size cannot
     * be determined. This method will only be called after the
     * {@link #learningStarted(String)} method has been called. It will not
     * be called after the {@link #learningStopped()} method has been
     * called. <br>
     * Note that this method may be called from a thread that is not the event
     * dispatch thread.
     */
    void learningTaskBusy(AxiomType<? extends OWLAxiom> axiomType);
    
    void learningFailed(AxiomType<? extends OWLAxiom> axiomType);
}
