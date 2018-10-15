/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package dulinglai.android.ate.entryPointCreators;

import java.util.Collection;
import java.util.List;

import soot.SootField;
import soot.SootMethod;

public interface IEntryPointCreator {

	/**
	 * Generates a dummy main method that calls all methods in the given list
	 * 
	 * @return The generated main method
	 */
	public SootMethod createDummyMain();

	/**
	 * with this option enabled the EntryPointCreator tries to find suitable
	 * subclasses of abstract classes and implementers of interfaces
	 * 
	 * @param b
	 *            sets substitution of call parameters
	 */
	public void setSubstituteCallParams(boolean b);

	/**
	 * set classes that are allowed to substitute (otherwise constructor loops are
	 * very likely)
	 * 
	 * @param l
	 */
	public void setSubstituteClasses(List<String> l);

	/**
	 * Gets the set of classes used in this dummy main method. These classes will be
	 * loaded by the FlowDroid engine.
	 * 
	 * @return The list of required classes in this dummy main method
	 */
	public Collection<String> getRequiredClasses();

	/**
	 * Gets the set of additional methods that are used by the entry point and that
	 * were generated by this entry point creator, although they are not directly
	 * entry points on their own.
	 * 
	 * @return The set of additional helper methods used by the generated entry
	 *         point
	 */
	public Collection<SootMethod> getAdditionalMethods();

	/**
	 * Gets the set of additional fields that are used by the entry point and that
	 * were generated by this entry point creator.
	 * 
	 * @return The set of additional helper fields used by the generated entry point
	 */
	public Collection<SootField> getAdditionalFields();

	/**
	 * Gets the last dummy main method that was generated by this creator
	 * 
	 * @return The last dummy main method that was generated by this creator
	 */
	public SootMethod getGeneratedMainMethod();

}