/*******************************************************************************
 * Copyright (c) 2009, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *     Dawid Pakuła [459462]
 *******************************************************************************/
package org.eclipse.php.internal.core.format;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.php.internal.core.ast.util.Util;
import org.eclipse.php.internal.core.documentModel.parser.regions.IPhpScriptRegion;
import org.eclipse.php.internal.core.documentModel.parser.regions.PHPRegionTypes;
import org.eclipse.php.internal.core.documentModel.partitioner.PHPPartitionTypes;
import org.eclipse.php.internal.core.util.text.PHPTextSequenceUtilities;
import org.eclipse.php.internal.core.util.text.TextSequence;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionContainer;

public class DefaultIndentationStrategy implements IIndentationStrategy {

	private static final String BLANK = ""; //$NON-NLS-1$
	private static boolean pairArrayParen;
	private static int pairArrayOffset;

	private IndentationObject indentationObject;

	public DefaultIndentationStrategy() {
	}

	/**
	 * 
	 * @param indentationObject
	 *            basic indentation preferences, can be null
	 */
	public DefaultIndentationStrategy(IndentationObject indentationObject) {
		this.indentationObject = indentationObject;
	}

	public void setIndentationObject(IndentationObject indentationObject) {
		this.indentationObject = indentationObject;
	}

	// go backward and look for any region except comment region or white space
	// region
	// in the given line
	private static ITextRegion getLastTokenRegion(
			final IStructuredDocument document, final IRegion line,
			final int forOffset) throws BadLocationException {
		int offset = forOffset;
		int lineStartOffset = line.getOffset();
		IStructuredDocumentRegion sdRegion = document
				.getRegionAtCharacterOffset(offset);
		if (sdRegion == null) {
			return null;
		}

		ITextRegion tRegion = sdRegion.getRegionAtCharacterOffset(offset);
		if (tRegion == null && offset == document.getLength()) {
			offset -= 1;
			tRegion = sdRegion.getRegionAtCharacterOffset(offset);
		}
		int regionStart = sdRegion.getStartOffset(tRegion);

		// in case of container we have the extract the PhpScriptRegion
		if (tRegion instanceof ITextRegionContainer) {
			ITextRegionContainer container = (ITextRegionContainer) tRegion;
			tRegion = container.getRegionAtCharacterOffset(offset);
			regionStart += tRegion.getStart();
		}

		if (tRegion instanceof IPhpScriptRegion) {
			IPhpScriptRegion scriptRegion = (IPhpScriptRegion) tRegion;
			tRegion = scriptRegion.getPhpToken(offset - regionStart);

			if (tRegion == null)
				return null;

			// go backward over the region to find a region (not comment nor
			// whitespace)
			// in the same line
			do {
				String token = tRegion.getType();
				if (regionStart + tRegion.getStart() >= forOffset) {
					// making sure the region found is not after the caret
					// (https://bugs.eclipse.org/bugs/show_bug.cgi?id=222019 -
					// caret before '{')
				} else if (!PHPPartitionTypes.isPHPCommentState(token)
						&& token != PHPRegionTypes.WHITESPACE) {
					// not comment nor white space
					return tRegion;
				}
				if (tRegion.getStart() >= 1) {
					tRegion = scriptRegion.getPhpToken(tRegion.getStart() - 1);
				} else {
					tRegion = null;
				}
			} while (tRegion != null
					&& tRegion.getStart() + regionStart > lineStartOffset);
		}

		return null;
	}

	public void placeMatchingBlanks(final IStructuredDocument document,
			final StringBuffer result, final int lineNumber, final int forOffset)
			throws BadLocationException {
		placeMatchingBlanksForStructuredDocument(document, result, lineNumber,
				forOffset, getCommandText());
	}

	public void placeMatchingBlanksForStructuredDocument(
			final IStructuredDocument document, final StringBuffer result,
			final int lineNumber, final int forOffset)
			throws BadLocationException {
		placeMatchingBlanksForStructuredDocument(document, result, lineNumber,
				forOffset, BLANK);
	}

	private void placeMatchingBlanksForStructuredDocument(
			final IStructuredDocument document, final StringBuffer result,
			final int lineNumber, final int forOffset, String commandText)
			throws BadLocationException {
		if (forOffset == 0) {
			return;
		}
		if (indentationObject == null) {
			indentationObject = new IndentationObject(document);
		}
		boolean enterKeyPressed = document.getLineDelimiter().equals(
				result.toString());
		int lineOfOffset = document.getLineOfOffset(forOffset);
		IRegion lineInformationOfOffset = document
				.getLineInformation(lineOfOffset);
		final String lineText = document.get(
				lineInformationOfOffset.getOffset(),
				lineInformationOfOffset.getLength());

		int lastNonEmptyLineIndex;
		final int indentationBaseLineIndex;
		final int newForOffset;

		// code for not formatting comments
		if (lineText.trim().startsWith("//") && enterKeyPressed) { //$NON-NLS-1$
			lastNonEmptyLineIndex = lineOfOffset;
			indentationBaseLineIndex = lineOfOffset;
			int i = lineInformationOfOffset.getOffset();
			for (; i < lineInformationOfOffset.getOffset()
					+ lineInformationOfOffset.getLength()
					&& document.getChar(i) != '/'; i++)
				;
			newForOffset = (i < forOffset) ? i : forOffset;

		}
		// end
		else {
			newForOffset = forOffset;
			IndentationBaseDetector indentationDetector = new IndentationBaseDetector(
					document);
			lastNonEmptyLineIndex = indentationDetector.getIndentationBaseLine(
					lineNumber, newForOffset, false);
			indentationBaseLineIndex = indentationDetector
					.getIndentationBaseLine(lineNumber, newForOffset, true);
		}

		final IRegion lastNonEmptyLine = document
				.getLineInformation(lastNonEmptyLineIndex);
		final IRegion indentationBaseLine = document
				.getLineInformation(indentationBaseLineIndex);
		final String blanks = FormatterUtils.getLineBlanks(document,
				indentationBaseLine);
		result.append(blanks);
		final int lastLineEndOffset = lastNonEmptyLine.getOffset()
				+ lastNonEmptyLine.getLength();
		int offset;
		int line;
		if (newForOffset < lastLineEndOffset) {
			offset = newForOffset;
			line = lineNumber;
		} else {
			offset = lastLineEndOffset;
			line = lastNonEmptyLineIndex;
		}
		if (shouldIndent(document, offset, line)) {
			indent(document, result, indentationObject.getIndentationChar(),
					indentationObject.getIndentationSize());
		} else {
			boolean intended = indentMultiLineCase(document, lineNumber,
					newForOffset, enterKeyPressed, result, blanks, commandText,
					indentationObject);
			if (!intended) {
				lastNonEmptyLineIndex = lineNumber;
				if (!enterKeyPressed && lastNonEmptyLineIndex > 0) {
					lastNonEmptyLineIndex--;
				}
				while (lastNonEmptyLineIndex >= 0) {
					IRegion lineInfo = document
							.getLineInformation(lastNonEmptyLineIndex);
					String content = document.get(lineInfo.getOffset(),
							lineInfo.getLength());
					if (content.trim().length() > 0) {
						break;
					}
					lastNonEmptyLineIndex--;
				}
				if (!isEndOfStatement(document, offset, lastNonEmptyLineIndex)) {
					if (indentationBaseLineIndex == lastNonEmptyLineIndex) {
						// this only deal with "$a = 'aaa'.|","|" is the
						// cursor
						// position when we press enter key
						placeStringIndentation(document, lastNonEmptyLineIndex,
								result, indentationObject);
					}
					// if (enterKeyPressed) {
					// this line is one of multi line statement
					// in multi line statement,when user press enter
					// key,
					// we use the same indentation of the last non-empty
					// line.
					boolean shouldNotChangeIndent = false;
					if (newForOffset != document.getLength()) {
						final IRegion lineInfo = document
								.getLineInformation(lineNumber);
						int nonEmptyOffset = newForOffset;
						if (!enterKeyPressed) {
							if (nonEmptyOffset == lineInfo.getOffset()) {
								nonEmptyOffset = IndentationUtils
										.moveLineStartToNonBlankChar(document,
												nonEmptyOffset, lineNumber,
												false);
							}
						}
						char lineStartChar = document.getChar(nonEmptyOffset);
						if (lineStartChar == PHPHeuristicScanner.RBRACE
						// || lineStartChar == PHPHeuristicScanner.RBRACKET
								|| lineStartChar == PHPHeuristicScanner.RPAREN) {

							PHPHeuristicScanner scanner = PHPHeuristicScanner
									.createHeuristicScanner(document,
											nonEmptyOffset, true);
							if (lineStartChar == PHPHeuristicScanner.RBRACE) {
								int peer = scanner.findOpeningPeer(
										nonEmptyOffset - 1,
										PHPHeuristicScanner.UNBOUND,
										PHPHeuristicScanner.LBRACE,
										PHPHeuristicScanner.RBRACE);
								if (peer != PHPHeuristicScanner.NOT_FOUND) {
									shouldNotChangeIndent = true;
								}
							} else if (lineStartChar == PHPHeuristicScanner.RBRACKET) {
								int peer = scanner.findOpeningPeer(
										nonEmptyOffset - 1,
										PHPHeuristicScanner.UNBOUND,
										PHPHeuristicScanner.LBRACKET,
										PHPHeuristicScanner.RBRACKET);
								if (peer != PHPHeuristicScanner.NOT_FOUND) {
									shouldNotChangeIndent = true;
								}
							} else if (lineStartChar == PHPHeuristicScanner.RPAREN) {
								int peer = scanner.findOpeningPeer(
										nonEmptyOffset - 1,
										PHPHeuristicScanner.UNBOUND,
										PHPHeuristicScanner.LPAREN,
										PHPHeuristicScanner.RPAREN);
								if (peer != PHPHeuristicScanner.NOT_FOUND) {
									shouldNotChangeIndent = true;
								}
							}
						}
					}

					if (!shouldNotChangeIndent) {
						result.setLength(result.length() - blanks.length());
						IRegion lineInfo = document
								.getLineInformation(lastNonEmptyLineIndex);
						result.append(FormatterUtils.getLineBlanks(document,
								lineInfo));
					}

					// }
				} else {// current is a new statement,check if we should indent
						// it based on indentationBaseLine
					if (result.length() == blanks.length()) {

						final int baseLineEndOffset = indentationBaseLine
								.getOffset() + indentationBaseLine.getLength();
						offset = baseLineEndOffset;
						line = indentationBaseLineIndex;
						// check if after braceless

						if (shouldIndent(document, offset, line)) {
							indent(document, result,
									indentationObject.getIndentationChar(),
									indentationObject.getIndentationSize());
						}
					}
				}
			}

		}
	}

	private static void indent(final IStructuredDocument document,
			final StringBuffer result, int indentationChar, int indentationSize) {
		// final int indentationSize = FormatPreferencesSupport.getInstance()
		// .getIndentationSize(document);
		// final char indentationChar = FormatPreferencesSupport.getInstance()
		// .getIndentationChar(document);
		for (int i = 0; i < indentationSize; i++)
			result.append((char) indentationChar);
	}

	private static boolean indentMultiLineCase(IStructuredDocument document,
			int lineNumber, int offset, boolean enterKeyPressed,
			StringBuffer result, String blanks, String commandText,
			IndentationObject indentationObject) {
		// LineState lineState = new LineState();
		// StringBuffer sb = new StringBuffer();
		try {
			IRegion region = document.getLineInformationOfOffset(offset);
			String content = document.get(offset,
					region.getOffset() + region.getLength() - offset);
			PHPHeuristicScanner scanner = PHPHeuristicScanner
					.createHeuristicScanner(document, offset, true);
			if (IndentationUtils.inBracelessBlock(scanner, document, offset)) {
				// lineState.inBracelessBlock = true;
				if (!"{".equals(commandText)) { //$NON-NLS-1$
					indent(document, result,
							indentationObject.getIndentationChar(),
							indentationObject.getIndentationSize());
				}
				return true;
			} else if (content.trim().startsWith(
					BLANK + PHPHeuristicScanner.LBRACE)) {
				// lineState.inBracelessBlock = true;
				int token = scanner.previousToken(offset - 1,
						PHPHeuristicScanner.UNBOUND);
				if (token == PHPHeuristicScanner.TokenRPAREN) {

					int peer = scanner.findOpeningPeer(scanner.getPosition(),
							PHPHeuristicScanner.UNBOUND,
							PHPHeuristicScanner.LPAREN,
							PHPHeuristicScanner.RPAREN);
					if (peer != PHPHeuristicScanner.NOT_FOUND) {

						String newblanks = FormatterUtils.getLineBlanks(
								document,
								document.getLineInformationOfOffset(peer));
						StringBuffer newBuffer = new StringBuffer(newblanks);
						// IRegion region = document
						// .getLineInformationOfOffset(offset);

						result.setLength(result.length() - blanks.length());
						result.append(newBuffer.toString());
						return true;
					}
				}

			} else if (inMultiLine(scanner, document, lineNumber, offset)) {
				// lineState.inBracelessBlock = true;
				int parenPeer = scanner.findOpeningPeer(offset - 1,
						PHPHeuristicScanner.UNBOUND,
						PHPHeuristicScanner.LPAREN, PHPHeuristicScanner.RPAREN);
				int bound = parenPeer != -1 ? parenPeer
						: PHPHeuristicScanner.UNBOUND;

				int bracketPeer = scanner.findOpeningPeer(offset - 1, bound,
						PHPHeuristicScanner.LBRACKET,
						PHPHeuristicScanner.RBRACKET);

				int peer = Math.max(parenPeer, bracketPeer);

				if (peer != PHPHeuristicScanner.NOT_FOUND) {

					// search for assignment (i.e. "=>")
					int position = peer - 1;
					int token = scanner.previousToken(position,
							PHPHeuristicScanner.UNBOUND);
					// scan tokens backwards until reaching a PHP token
					while (token > 100
							|| token == PHPHeuristicScanner.TokenOTHER) {
						position--;
						token = scanner.previousToken(position,
								PHPHeuristicScanner.UNBOUND);
					}

					position--;
					boolean isAssignment = scanner.previousToken(position,
							PHPHeuristicScanner.UNBOUND) == PHPHeuristicScanner.TokenGREATERTHAN
							&& scanner.previousToken(position - 1,
									PHPHeuristicScanner.UNBOUND) == PHPHeuristicScanner.TokenEQUAL;

					token = scanner.previousToken(peer - 1,
							PHPHeuristicScanner.UNBOUND);

					boolean isArray = token == Symbols.TokenARRAY
							|| peer == bracketPeer;

					String newblanks = FormatterUtils.getLineBlanks(document,
							document.getLineInformationOfOffset(peer));
					StringBuffer newBuffer = new StringBuffer(newblanks);
					pairArrayParen = false;

					String trimed = document.get(offset,
							region.getOffset() + region.getLength() - offset)
							.trim();
					if (enterKeyPressed
							|| !(trimed.startsWith(BLANK
									+ PHPHeuristicScanner.RPAREN) || trimed
										.startsWith(BLANK
												+ PHPHeuristicScanner.RBRACKET))) {
						if (isArray) {
							region = document
									.getLineInformationOfOffset(offset);
							int arrayBracket = scanner.nextToken(offset,
									region.getOffset() + region.getLength());
							if (arrayBracket == PHPHeuristicScanner.TokenRPAREN
									|| arrayBracket == PHPHeuristicScanner.TokenRBRACKET) {
								int prev = scanner.previousToken(offset - 1,
										PHPHeuristicScanner.UNBOUND);
								if ((isAssignment
										&& arrayBracket == PHPHeuristicScanner.TokenRPAREN && prev != PHPHeuristicScanner.TokenLPAREN)
										|| (isAssignment
												&& arrayBracket == PHPHeuristicScanner.TokenRBRACKET && prev != PHPHeuristicScanner.TokenLBRACKET)) {
									indent(document, newBuffer, 0,
											indentationObject
													.getIndentationChar(),
											indentationObject
													.getIndentationSize());
								} else {
									indent(document,
											newBuffer,
											indentationObject
													.getIndentationArrayInitSize(),
											indentationObject
													.getIndentationChar(),
											indentationObject
													.getIndentationSize());
									pairArrayParen = true;
								}
							} else {
								indent(document, newBuffer,
										indentationObject
												.getIndentationArrayInitSize(),
										indentationObject.getIndentationChar(),
										indentationObject.getIndentationSize());
							}
						} else {
							indent(document, newBuffer,
									indentationObject
											.getIndentationWrappedLineSize(),
									indentationObject.getIndentationChar(),
									indentationObject.getIndentationSize());
						}
					}

					result.setLength(result.length() - blanks.length());
					result.append(newBuffer.toString());
					if (pairArrayParen) {
						pairArrayOffset = offset + result.length();
						result.append(Util.getLineSeparator(null, null));
						result.append(blanks);

					}
					return true;
				}
			} else {
				int baseLine = inMultiLineString(document, offset, lineNumber,
						enterKeyPressed);
				if (baseLine >= 0) {
					String newblanks = FormatterUtils.getLineBlanks(document,
							document.getLineInformation(baseLine));
					StringBuffer newBuffer = new StringBuffer(newblanks);
					indent(document, newBuffer,
							indentationObject.getIndentationWrappedLineSize(),
							indentationObject.getIndentationChar(),
							indentationObject.getIndentationSize());
					result.setLength(result.length() - blanks.length());
					result.append(newBuffer.toString());
					return true;
				}
			}
		} catch (final BadLocationException e) {
		}
		return false;
	}

	private static void indent(IStructuredDocument document,
			StringBuffer indent, int times, int indentationChar,
			int indentationSize) {
		for (int i = 0; i < times; i++) {
			indent(document, indent, indentationChar, indentationSize);
		}
	}

	private static boolean inMultiLine(PHPHeuristicScanner scanner,
			IStructuredDocument document, int lineNumber, int offset) {
		int lineStart = offset;
		try {
			IRegion region = document.getLineInformation(lineNumber);
			char[] line = document.get(lineStart,
					region.getOffset() + region.getLength() - lineStart)
					.toCharArray();
			for (int i = 0; i < line.length; i++) {
				char c = line[i];
				if (Character.isWhitespace(c)) {
				} else {
					// move line start to first non blank char
					// and do + 1 to adjust offset of
					// PHPTextSequenceUtilities.getStatement(...)
					lineStart += i + 1;
					break;
				}
			}
		} catch (BadLocationException e) {
		}
		TextSequence textSequence = PHPTextSequenceUtilities
				.getStatement(lineStart,
						document.getRegionAtCharacterOffset(lineStart), true);
		if (textSequence.length() == 0) {
			return false;
		}
		String regionType = FormatterUtils.getRegionType(document,
				textSequence.getOriginalOffset(0));
		if (IndentationUtils.isRegionTypeAllowedMultiline(regionType)) {
			int statementStart = textSequence.getOriginalOffset(0);
			// we only search for opening pear in textSequence
			int bound = statementStart;
			int parenPeer = scanner.findOpeningPeer(offset - 1, bound,
					PHPHeuristicScanner.LPAREN, PHPHeuristicScanner.RPAREN);
			bound = parenPeer != -1 ? Math.max(parenPeer, bound) : bound;
			int bracketPeer = scanner.findOpeningPeer(offset - 1, bound,
					PHPHeuristicScanner.LBRACKET, PHPHeuristicScanner.RBRACKET);
			int peer = Math.max(parenPeer, bracketPeer);
			if (peer == PHPHeuristicScanner.NOT_FOUND) {
				return false;
			}
			if (statementStart < peer) {
				return true;
			}
		}
		return false;
	}

	private static int inMultiLineString(IStructuredDocument document,
			int offset, int lineNumber, boolean enterKeyPressed) {

		try {
			IRegion lineInfo = document.getLineInformation(lineNumber);
			ITextRegion token = getLastTokenRegion(document, lineInfo, offset);
			if (token == null)
				return -1;
			String tokenType = token.getType();

			if (tokenType == PHPRegionTypes.PHP_CONSTANT_ENCAPSED_STRING) {
				int startLine = document.getLineOfOffset(token.getStart());
				if (enterKeyPressed && startLine <= lineNumber
						|| !enterKeyPressed && startLine < lineNumber) {
					return startLine;
				}
			}
		} catch (BadLocationException e) {
		}

		// Program program = null;
		// try {
		// final Reader reader = new StringReader(document.get());
		// program = ASTParser.newParser(reader, PHPVersion.PHP5_4, true)
		// .createAST(new NullProgressMonitor());
		// ASTNode node = NodeFinder.perform(program, offset, 0);
		// if (node != null && node.getType() == ASTNode.SCALAR
		// && ((Scalar) node).getScalarType() == Scalar.TYPE_STRING
		// && document.getLineOfOffset(node.getStart()) < lineNumber) {
		// return document.getLineOfOffset(node.getStart());
		// }
		// } catch (Exception e) {
		// }

		return -1;
	}

	private static boolean isEndOfStatement(IStructuredDocument document,
			int offset, int lineNumber) {
		try {
			IRegion lineInfo = document.getLineInformation(lineNumber);
			ITextRegion token = getLastTokenRegion(document, lineInfo,
					lineInfo.getOffset() + lineInfo.getLength());
			if (token == null)// comment
				return true;
			if (token.getType() == PHPRegionTypes.PHP_SEMICOLON
					|| token.getType() == PHPRegionTypes.PHP_CURLY_CLOSE) {
				return true;
			} else if (token.getType() == PHPRegionTypes.PHP_HEREDOC_TAG
					&& document.get(lineInfo.getOffset(), lineInfo.getLength())
							.trim().endsWith(";")) { //$NON-NLS-1$
				return true;
			}
		} catch (final BadLocationException e) {
		}
		return false;
	}

	private static void placeStringIndentation(
			final IStructuredDocument document, int lineNumber,
			StringBuffer result, IndentationObject indentationObject) {
		try {

			IRegion lineInfo = document.getLineInformation(lineNumber);
			int offset = lineInfo.getOffset() + lineInfo.getLength();
			final IStructuredDocumentRegion sdRegion = document
					.getRegionAtCharacterOffset(offset);
			ITextRegion token = getLastTokenRegion(document, lineInfo, offset);
			if (token == null)
				return;
			String tokenType = token.getType();

			if (tokenType == PHPRegionTypes.PHP_CURLY_OPEN)
				return;

			ITextRegion scriptRegion = sdRegion
					.getRegionAtCharacterOffset(offset);
			if (scriptRegion == null && offset == document.getLength()) {
				offset -= 1;
				scriptRegion = sdRegion.getRegionAtCharacterOffset(offset);
			}
			int regionStart = sdRegion.getStartOffset(scriptRegion);
			// in case of container we have the extract the PhpScriptRegion
			if (scriptRegion instanceof ITextRegionContainer) {
				ITextRegionContainer container = (ITextRegionContainer) scriptRegion;
				scriptRegion = container.getRegionAtCharacterOffset(offset);
				regionStart += scriptRegion.getStart();
			}
			if (scriptRegion instanceof IPhpScriptRegion) {
				if (tokenType == PHPRegionTypes.PHP_TOKEN
						&& document.getChar(regionStart + token.getStart()) == '.') {
					token = ((IPhpScriptRegion) scriptRegion).getPhpToken(token
							.getStart() - 1);
					if (token != null
							&& token.getType() == PHPRegionTypes.PHP_CONSTANT_ENCAPSED_STRING) {
						boolean isToken = true;
						int currentOffset = regionStart + token.getStart() - 1;
						while (currentOffset >= lineInfo.getOffset()) {
							token = ((IPhpScriptRegion) scriptRegion)
									.getPhpToken(token.getStart() - 1);
							tokenType = token.getType();
							if (isToken
									&& (tokenType == PHPRegionTypes.PHP_TOKEN && document
											.getChar(regionStart
													+ token.getStart()) == '.')
									|| !isToken
									&& tokenType == PHPRegionTypes.PHP_CONSTANT_ENCAPSED_STRING) {
								currentOffset = regionStart + token.getStart()
										- 1;
							} else {
								break;
							}
						}
						indent(document, result,
								indentationObject
										.getIndentationWrappedLineSize(),
								indentationObject.getIndentationChar(),
								indentationObject.getIndentationSize());
					}
				}
			}
		} catch (final BadLocationException e) {
		}
	}

	private static boolean shouldIndent(final IStructuredDocument document,
			int offset, final int lineNumber) {
		try {
			final IRegion lineInfo = document.getLineInformation(lineNumber);

			final IStructuredDocumentRegion sdRegion = document
					.getRegionAtCharacterOffset(offset);
			ITextRegion token = getLastTokenRegion(document, lineInfo, offset);
			if (token == null)
				return false;
			String tokenType = token.getType();

			if (tokenType == PHPRegionTypes.PHP_CURLY_OPEN)
				return true;

			ITextRegion scriptRegion = sdRegion
					.getRegionAtCharacterOffset(offset);
			if (scriptRegion == null && offset == document.getLength()) {
				offset -= 1;
				scriptRegion = sdRegion.getRegionAtCharacterOffset(offset);
			}
			int regionStart = sdRegion.getStartOffset(scriptRegion);
			// in case of container we have the extract the PhpScriptRegion
			if (scriptRegion instanceof ITextRegionContainer) {
				ITextRegionContainer container = (ITextRegionContainer) scriptRegion;
				scriptRegion = container.getRegionAtCharacterOffset(offset);
				regionStart += scriptRegion.getStart();
			}
			if (scriptRegion instanceof IPhpScriptRegion) {
				if (tokenType == PHPRegionTypes.PHP_TOKEN
						&& document.getChar(regionStart + token.getStart()) == ':') {
					// checking if the line starts with "case" or "default"
					int currentOffset = regionStart + token.getStart() - 1;
					while (currentOffset >= lineInfo.getOffset()) {
						token = ((IPhpScriptRegion) scriptRegion)
								.getPhpToken(token.getStart() - 1);
						tokenType = token.getType();
						if (tokenType == PHPRegionTypes.PHP_CASE
								|| tokenType == PHPRegionTypes.PHP_DEFAULT)
							return true;
						currentOffset = regionStart + token.getStart() - 1;
					}
				}
			}

		} catch (final BadLocationException e) {
		}
		return false;
	}

	protected String getCommandText() {
		return BLANK;
	}

	public static int getPairArrayOffset() {

		// TODO Auto-generated method stub
		if (pairArrayParen) {
			return pairArrayOffset;
		}
		return -1;
	}

	public static boolean getPairArrayParen() {
		// TODO Auto-generated method stub
		return pairArrayParen;
	}

	public static void unsetPairArrayParen() {
		// TODO Auto-generated method stub
		pairArrayParen = false;
	}

}
