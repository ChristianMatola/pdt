package org.eclipse.php.ui.editor.contentassist;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.php.internal.core.documentModel.DOMModelForPHP;

public interface IContentAssistSupport {

	public ICompletionProposal[] getCompletionOption(ITextViewer viewer, DOMModelForPHP phpDOMModel, int offset, boolean explicit) throws BadLocationException;

	public char[] getAutoactivationTriggers();

	public void handlePreferenceStoreChanged(PropertyChangeEvent event);
}