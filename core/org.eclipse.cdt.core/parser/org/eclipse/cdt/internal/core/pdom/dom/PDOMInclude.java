/*******************************************************************************
 * Copyright (c) 2006, 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 * Markus Schorn (Wind River Systems)
 *******************************************************************************/

package org.eclipse.cdt.internal.core.pdom.dom;

import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorIncludeStatement;
import org.eclipse.cdt.core.index.IIndexFile;
import org.eclipse.cdt.core.index.IIndexFileLocation;
import org.eclipse.cdt.internal.core.index.IIndexFragment;
import org.eclipse.cdt.internal.core.index.IIndexFragmentFile;
import org.eclipse.cdt.internal.core.index.IIndexFragmentInclude;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.cdt.internal.core.pdom.db.IString;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Doug Schaefer
 *
 */
public class PDOMInclude implements IIndexFragmentInclude {

	private final PDOM pdom;
	private final int record;
	
	private final int INCLUDES_FILE_OR_NAME = 0;
	private final int INCLUDED_BY = 4;
	private final int INCLUDES_NEXT = 8;
	private final int INCLUDED_BY_NEXT = 12;
	private final int INCLUDED_BY_PREV = 16;
	private static final int NODE_OFFSET_OFFSET  = 20;
	private static final int NODE_LENGTH_OFFSET  = 24;
	private static final int FLAG_OFFSET 				 = 26;

	private static final int FLAG_SYSTEM_INCLUDE = 1;
	private static final int FLAG_INACTIVE_INCLUDE = 2;
	private static final int FLAG_UNRESOLVED_INCLUDE = 4;
	
	private final int RECORD_SIZE = 27;
	
	// cached fields
	private String fName= null;

	public PDOMInclude(PDOM pdom, int record) {
		this.pdom = pdom;
		this.record = record;
	}
	
	public PDOMInclude(PDOM pdom, IASTPreprocessorIncludeStatement include, PDOMFile containerFile, PDOMFile targetFile) throws CoreException {
		this.pdom = pdom;
		this.record = pdom.getDB().malloc(RECORD_SIZE);
		IASTName name= include.getName();
		IASTFileLocation loc= name.getFileLocation();
		// includes generated by -include or -macro don't have a location
		if (loc != null) {
			setNameOffsetAndLength(loc.getNodeOffset(), (short) loc.getNodeLength());
		}
		
		setFlag(encodeFlags(include, targetFile == null));
		setIncludedBy(containerFile);
		setIncludes(targetFile, name.toCharArray());
	}
	
	private byte encodeFlags(IASTPreprocessorIncludeStatement include, boolean unresolved) {
		byte flags= 0;
		if (include.isSystemInclude()) {
			flags |= FLAG_SYSTEM_INCLUDE;
		}
		if (!include.isActive()) {
			flags |= FLAG_INACTIVE_INCLUDE;
		}
		if (unresolved) {
			flags |= FLAG_UNRESOLVED_INCLUDE;
		}
		return flags;
	}

	public int getRecord() {
		return record;
	}
	
	public void delete() throws CoreException {
		if (isResolved()) {
			// Remove us from the includedBy chain
			removeThisFromIncludedByChain();
		}
		else {
			getNameForUnresolved().delete();
		}
		
		// Delete our record
		pdom.getDB().free(record);
	}

	private void removeThisFromIncludedByChain() throws CoreException {
		PDOMInclude prevInclude = getPrevInIncludedBy();
		PDOMInclude nextInclude = getNextInIncludedBy();
		if (prevInclude != null)
			prevInclude.setNextInIncludedBy(nextInclude);
		else
			((PDOMFile) getIncludes()).setFirstIncludedBy(nextInclude);

		if (nextInclude != null)
			nextInclude.setPrevInIncludedBy(prevInclude);
	}
	
	private IString getNameForUnresolved() throws CoreException {
		if (isResolved()) {
			return null;
		}
		final Database db = pdom.getDB();
		return db.getString(db.getInt(record + INCLUDES_FILE_OR_NAME));
	}
		
	public IIndexFragmentFile getIncludes() throws CoreException {
		if (!isResolved()) {
			return null;
		}
		int rec = pdom.getDB().getInt(record + INCLUDES_FILE_OR_NAME);
		return rec != 0 ? new PDOMFile(pdom, rec) : null;
	}
	
	private void setIncludes(PDOMFile includes, char[] name) throws CoreException {
		int rec= 0;
		if (includes == null) {
			rec= pdom.getDB().newString(name).getRecord();
		}
		else {
			rec= includes.getRecord();
		}
		pdom.getDB().putInt(record + INCLUDES_FILE_OR_NAME, rec);
	}
	
	public IIndexFile getIncludedBy() throws CoreException {
		int rec = pdom.getDB().getInt(record + INCLUDED_BY);
		return rec != 0 ? new PDOMFile(pdom, rec) : null;
	}
	
	private void setIncludedBy(PDOMFile includedBy) throws CoreException {
		int rec = includedBy != null ? includedBy.getRecord() : 0;
		pdom.getDB().putInt(record + INCLUDED_BY, rec);
	}
	
	public PDOMInclude getNextInIncludes() throws CoreException {
		int rec = pdom.getDB().getInt(record + INCLUDES_NEXT);
		return rec != 0 ? new PDOMInclude(pdom, rec) : null;
	}
	
	public void setNextInIncludes(PDOMInclude include) throws CoreException {
		int rec = include != null ? include.getRecord() : 0;
		pdom.getDB().putInt(record + INCLUDES_NEXT, rec);
	}
	
	public PDOMInclude getNextInIncludedBy() throws CoreException {
		int rec = pdom.getDB().getInt(record + INCLUDED_BY_NEXT);
		return rec != 0 ? new PDOMInclude(pdom, rec) : null;
	}
	
	public void setNextInIncludedBy(PDOMInclude include) throws CoreException {
		int rec = include != null ? include.getRecord() : 0;
		pdom.getDB().putInt(record + INCLUDED_BY_NEXT, rec);
	}
	
	public PDOMInclude getPrevInIncludedBy() throws CoreException {
		int rec = pdom.getDB().getInt(record + INCLUDED_BY_PREV);
		return rec != 0 ? new PDOMInclude(pdom, rec) : null;
	}
	
	public void setPrevInIncludedBy(PDOMInclude include) throws CoreException {
		int rec = include != null ? include.getRecord() : 0;
		pdom.getDB().putInt(record + INCLUDED_BY_PREV, rec);
	}

	public IIndexFileLocation getIncludedByLocation() throws CoreException {
		return getIncludedBy().getLocation();
	}

	public IIndexFileLocation getIncludesLocation() throws CoreException {
		if (!isResolved()) {
			return null;
		}
		return getIncludes().getLocation();
	}

	public IIndexFragment getFragment() {
		return pdom;
	}
	
	private void setNameOffsetAndLength(int offset, short length) throws CoreException {
		pdom.getDB().putInt(record + NODE_OFFSET_OFFSET, offset);
		pdom.getDB().putShort(record + NODE_LENGTH_OFFSET, length);
	}
	
	private void setFlag(byte flag) throws CoreException {
		pdom.getDB().putByte(record + FLAG_OFFSET, flag);
	}
	
	private int getFlag() throws CoreException {
		return pdom.getDB().getByte(record + FLAG_OFFSET);
	}

	public boolean isSystemInclude() throws CoreException {
		return (getFlag() & FLAG_SYSTEM_INCLUDE) != 0;
	}
	
	public boolean isActive() throws CoreException {
		return (getFlag() & FLAG_INACTIVE_INCLUDE) == 0;
	}
	
	public boolean isResolved() throws CoreException {
		return (getFlag() & FLAG_UNRESOLVED_INCLUDE) == 0;
	}
	
	public int getNameOffset() throws CoreException {
		return pdom.getDB().getInt(record + NODE_OFFSET_OFFSET);
	}
	
	public int getNameLength() throws CoreException {
		return pdom.getDB().getShort(record + NODE_LENGTH_OFFSET) & 0xffff;
	}	
	
	public String getName() throws CoreException {
		if (fName == null) {
			computeName();
		}
		return fName;
	}

	private void computeName() throws CoreException {
		if (isResolved()) {
			fName= getIncludes().getLocation().getURI().getPath();
			fName= fName.substring(fName.lastIndexOf('/')+1);
		}
		else {
			fName= getNameForUnresolved().getString();
		}
	}

	public void convertToUnresolved() throws CoreException {
		if (isResolved()) {
			setIncludes(null, getName().toCharArray());
			setFlag((byte) (getFlag() | FLAG_UNRESOLVED_INCLUDE));
		}
	}
}
