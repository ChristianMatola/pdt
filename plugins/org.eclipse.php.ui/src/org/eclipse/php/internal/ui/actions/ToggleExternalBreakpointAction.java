/*******************************************************************************
 * Copyright (c) 2006 Zend Corporation and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zend and IBM - Initial implementation
 *******************************************************************************/
/**
 * 
 */
package org.eclipse.php.internal.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.sse.ui.internal.debug.ToggleBreakpointAction;

/**
 * Toggle breakpoint action that can handle external files.
 * 
 * @author shalom
 *
 */
public class ToggleExternalBreakpointAction extends ToggleBreakpointAction {

	/**
	 * @param editor
	 * @param rulerInfo
	 */
	public ToggleExternalBreakpointAction(ITextEditor editor, IVerticalRulerInfo rulerInfo) {
		super(editor, rulerInfo);
	}

	/**
	 * @param editor
	 * @param rulerInfo
	 * @param fallbackAction
	 */
	public ToggleExternalBreakpointAction(ITextEditor editor, IVerticalRulerInfo rulerInfo, IAction fallbackAction) {
		super(editor, rulerInfo, fallbackAction);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.wst.sse.ui.internal.debug.BreakpointRulerAction#hasMarkers()
	 */
	protected boolean hasMarkers() {
		return ExternalBreakpointActionHelper.hasMarkers(getResource(), getDocument(), getAnnotationModel(), getRulerInfo());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.wst.sse.ui.internal.debug.BreakpointRulerAction#getMarkers()
	 */
	protected IMarker[] getMarkers() {
		return ExternalBreakpointActionHelper.getMarkers(getResource(), getDocument(), getAnnotationModel(), getRulerInfo());
	}
}
