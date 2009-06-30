/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *******************************************************************************/
package org.eclipse.php.internal.ui.editor.contentassist;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.dltk.ui.PreferenceConstants;
import org.eclipse.dltk.ui.text.completion.*;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.TemplateCompletionProcessor;
import org.eclipse.php.internal.ui.editor.templates.PhpTemplateCompletionProcessor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class PHPCompletionProposalComputer extends ScriptCompletionProposalComputer {

	private String fErrorMessage;
	
	protected TemplateCompletionProcessor createTemplateProposalComputer(ScriptContentAssistInvocationContext context) {
		return new PhpTemplateCompletionProcessor(context);
	}

	protected ScriptCompletionProposalCollector createCollector(ScriptContentAssistInvocationContext context) {

		boolean explicit = false;
		if (context instanceof PHPContentAssistInvocationContext) {
			explicit = ((PHPContentAssistInvocationContext) context).isExplicit();
		}

		return new PHPCompletionProposalCollector(context.getDocument(), context.getSourceModule(), explicit);
	}

	protected int guessContextInformationPosition(ContentAssistInvocationContext context) {

		IDocument document = context.getDocument();
		int offset = context.getInvocationOffset();
		try {
			for (; offset > 0; --offset) {
				if (document.getChar(offset) == '(') {
					return offset;
				}
			}
		} catch (BadLocationException e) {
		}

		return super.guessContextInformationPosition(context);
	}

	/*
	 * The following method is overriden in order to allow running code assist from a non-UI thread.
	 */
	protected List computeScriptCompletionProposals(int offset, final ScriptContentAssistInvocationContext context, IProgressMonitor monitor) {
		
		// Source module getting
		ISourceModule sourceModule = context.getSourceModule();
		if (sourceModule == null) {
			return Collections.EMPTY_LIST;
		}

		// Create and configure collector
		final ScriptCompletionProposalCollector collector = createCollector(context);
		if (collector == null) {
			return Collections.EMPTY_LIST;
		}

		collector.setInvocationContext(context);

		Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			public void run() {
				Point selection = context.getViewer().getSelectedRange();
				if (selection.y > 0) {
					collector.setReplacementLength(selection.y);
				}
			}
		});

		// Filling collector with proposals
		try {
			sourceModule.codeComplete(offset, collector);
		} catch (ModelException e) {
			handleCodeCompletionException(e, context);
		}

		ICompletionProposal[] proposals = collector.getScriptCompletionProposals();

		// Checking proposals
		if (proposals.length == 0) {
			String error = collector.getErrorMessage();
			if (error.length() > 0) {
				fErrorMessage = error;
			}

			return Collections.EMPTY_LIST;
		}

		return Arrays.asList(proposals);
	}
	
	protected List computeTemplateCompletionProposals(final int offset,
			final ScriptContentAssistInvocationContext context,
			final IProgressMonitor monitor) {

		final List[] result = new List[1];
		Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			public void run() {
				result[0] = PHPCompletionProposalComputer.super.computeTemplateCompletionProposals(offset, context, monitor);
			}
		});
		return result[0];
	}

	private void handleCodeCompletionException(ModelException e, ScriptContentAssistInvocationContext context) {
		ISourceModule module = context.getSourceModule();
		Shell shell = context.getViewer().getTextWidget().getShell();
		if (e.isDoesNotExist() && !module.getScriptProject().isOnBuildpath(module)) {
			IPreferenceStore store = DLTKUIPlugin.getDefault().getPreferenceStore();
			boolean value = store.getBoolean(PreferenceConstants.NOTIFICATION_NOT_ON_BUILDPATH_MESSAGE);
			if (!value) {
				MessageDialog.openInformation(shell, ScriptTextMessages.CompletionProcessor_error_notOnBuildPath_title, ScriptTextMessages.CompletionProcessor_error_notOnBuildPath_message);
			}
			store.setValue(PreferenceConstants.NOTIFICATION_NOT_ON_BUILDPATH_MESSAGE, true);
		} else
			ErrorDialog.openError(shell, ScriptTextMessages.CompletionProcessor_error_accessing_title, ScriptTextMessages.CompletionProcessor_error_accessing_message, e.getStatus());
	}
	
	public String getErrorMessage() {
		return fErrorMessage;
	}

	public void sessionEnded() {
		super.sessionEnded();
		fErrorMessage = null;
	}
}
