package org.dllearner.algorithms.isle.index;

import org.dllearner.algorithms.isle.textretrieval.EntityTextRetriever;
import org.dllearner.core.owl.Entity;
import org.dllearner.utilities.datastructures.PrefixTrie;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.*;

public class SimpleEntityCandidatesTrie implements EntityCandidatesTrie {

	PrefixTrie<Set<Entity>> trie;
	EntityTextRetriever entityTextRetriever;
	
	public SimpleEntityCandidatesTrie(EntityTextRetriever entityTextRetriever, OWLOntology ontology) {
		this.entityTextRetriever = entityTextRetriever;
		buildTrie(ontology);
	}
	
	public void buildTrie(OWLOntology ontology) {	
		this.trie = new PrefixTrie<Set<Entity>>();
		Map<Entity, Set<String>> relevantText = entityTextRetriever.getRelevantText(ontology);
		
		for (Entity entity : relevantText.keySet()) {
			for (String text : relevantText.get(entity)) {
				addEntry(text, entity);
				// Adds also composing words, e.g. for "has child", "has" and "child" are also added
				if (text.contains(" ")) {
					for (String subtext : text.split(" ")) {
						addEntry(subtext, entity);
						//System.out.println("trie.add("+subtext+","++")");
					}
				}
			}
		}
	}
	
	@Override
	public void addEntry(String s, Entity e) {
		Set<Entity> candidates;
		if (trie.contains(s)) 
			candidates = trie.get(s);
		else
			candidates = new HashSet<Entity>();
		
		candidates.add(e);
		
		trie.put(s, candidates);
	}

	@Override
	public Set<Entity> getCandidateEntities(String s) {
		return trie.get(s);
	}

	@Override
	public String getLongestMatch(String s) {
		CharSequence match = trie.getLongestMatch(s);
		return (match!=null) ? match.toString() : null;
	}
	
	public String toString() {
		String output = "";
		Map<String,Set<Entity>> trieMap = trie.toMap();
		List<String> termsList = new ArrayList(trieMap.keySet());
		Collections.sort(termsList);
		for (String key : termsList) {
			output += key + ":\n";
			for (Entity candidate: trieMap.get(key)) {
				output += "\t"+candidate+"\n";
			}
		}
		return output;
	}
	
	public void printTrie() {
		System.out.println(this.toString());
		
	}

}
