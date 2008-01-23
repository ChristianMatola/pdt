package org.eclipse.php.internal.core.compiler.ast.parser;

import java.io.CharArrayReader;
import java.io.Reader;

import org.eclipse.dltk.ast.declarations.ModuleDeclaration;
import org.eclipse.dltk.ast.parser.AbstractSourceParser;
import org.eclipse.dltk.ast.parser.ISourceParser;
import org.eclipse.dltk.compiler.problem.IProblemReporter;
import org.eclipse.dltk.core.DLTKCore;

public abstract class AbstractPHPSourceParser extends AbstractSourceParser implements ISourceParser {

	public ModuleDeclaration parse(char[] fileName, char[] source, IProblemReporter reporter) {
		try {
			return parse(new CharArrayReader(source), reporter);

		} catch (Exception e) {
			if (DLTKCore.DEBUG) {
				e.printStackTrace();
			}
			throw new RuntimeException(e);
		}
	}

	public abstract ModuleDeclaration parse(Reader in, IProblemReporter reporter);
}
