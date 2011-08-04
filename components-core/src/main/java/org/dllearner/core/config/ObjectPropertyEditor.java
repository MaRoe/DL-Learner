package org.dllearner.core.config;

import org.dllearner.core.owl.ObjectProperty;

import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyEditor;

/**
 * Created by IntelliJ IDEA.
 * User: Chris
 * Date: 7/26/11
 * Time: 9:42 PM
 * <p/>
 * Basic Property Editor for the Object Property DL-Learner class.  Doesn't have GUI support yet but we could add that later if we wanted.
 */
public class ObjectPropertyEditor implements PropertyEditor {


    private ObjectProperty value;

    @Override
    public void setValue(Object value) {
        this.value = (ObjectProperty) value;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public boolean isPaintable() {
        /** Not right now, we're doing non gui work */
        return false;
    }

    @Override
    public void paintValue(Graphics gfx, Rectangle box) {

    }

    @Override
    public String getJavaInitializationString() {
        /** This returns the value needed to reconstitute the object from a string */
        return value.getName();
    }

    @Override
    public String getAsText() {
        /** Get the text value of this object - for displaying in GUIS, etc */
        return value.getName();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        value = new ObjectProperty(text);
    }

    @Override
    public String[] getTags() {
        /** If there was a known set of values it had to have, we could add that list here */
        return new String[0];
    }

    @Override
    public Component getCustomEditor() {
        /** GUI stuff, if you wanted to edit it a custom way */
        return null;
    }

    @Override
    public boolean supportsCustomEditor() {
        /** We don't support this right now, but maybe later */
        return false;

    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        /** More gui stuff, we don't need this for our basic example */
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        /** More gui stuff, we don't need this for our basic example */
    }
}