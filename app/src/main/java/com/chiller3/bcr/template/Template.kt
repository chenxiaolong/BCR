package com.chiller3.bcr.template

import com.copperleaf.kudzu.node.chars.CharNode
import com.copperleaf.kudzu.node.choice.Choice3Node
import com.copperleaf.kudzu.node.mapped.ValueNode
import com.copperleaf.kudzu.node.text.TextNode
import com.copperleaf.kudzu.parser.ParserContext
import com.copperleaf.kudzu.parser.chars.CharInParser
import com.copperleaf.kudzu.parser.chars.CharNotInParser
import com.copperleaf.kudzu.parser.chars.EndOfInputParser
import com.copperleaf.kudzu.parser.choice.ExactChoiceParser
import com.copperleaf.kudzu.parser.lazy.LazyParser
import com.copperleaf.kudzu.parser.many.AtLeastParser
import com.copperleaf.kudzu.parser.many.ManyParser
import com.copperleaf.kudzu.parser.mapped.FlatMappedParser
import com.copperleaf.kudzu.parser.mapped.MappedParser
import com.copperleaf.kudzu.parser.maybe.MaybeParser
import com.copperleaf.kudzu.parser.sequence.SequenceParser
import com.copperleaf.kudzu.parser.text.IdentifierTokenParser

class Template(template: String) {
    companion object {
        private fun escape(input: String): String =
            input.replace("([\\\\{}\\[\\]|])".toRegex(), "\\\\$1")

        /**
         * Create a parser that parses as many characters as possible so long as they are not `\\`
         * or any character in [requireEscape] unless escaped with a backslash. The input must not
         * be empty.
         */
        private fun createLiteralParser(vararg requireEscape: Char) =
            FlatMappedParser(
                SequenceParser(
                    AtLeastParser(
                        ExactChoiceParser(
                            CharNotInParser('\\', *requireEscape),
                            FlatMappedParser(
                                SequenceParser(
                                    CharInParser('\\'),
                                    CharInParser('\\', *requireEscape),
                                )
                            ) { (nodeContext, _, charNode) ->
                                CharNode(charNode.char, nodeContext)
                            },
                        ),
                        1,
                    ),
                ),
            ) {
                TextNode(it.text, it.context)
            }

        /** Parse a literal string, including escaped special characters. */
        internal val stringLiteralParser = MappedParser(
            createLiteralParser('{', '}', '[', ']', '|'),
        ) {
            StringLiteral(it.text)
        }

        /** Parse a variable reference. */
        internal val variableRefParser = MappedParser(
            SequenceParser(
                CharInParser('{'),
                IdentifierTokenParser(),
                MaybeParser(
                    SequenceParser(
                        CharInParser(':'),
                        MaybeParser(
                            stringLiteralParser,
                        ),
                    ),
                ),
                CharInParser('}'),
            )
        ) {
            val identNode = it.node2
            val argNode = it.node3.node?.node2
            val arg = if (argNode != null) {
                // The colon was specified, but the argument value may be empty
                argNode.node?.value?.value ?: ""
            } else {
                // No argument
                null
            }

            VariableRef(identNode.text, arg)
        }

        /** Parse a clause sequence containing literals, variables, and fallbacks. */
        internal val templateParser = LazyParser<ValueNode<TemplateString>>()

        /** Parse a fallback clause. */
        internal val fallbackParser = MappedParser(
            SequenceParser(
                CharInParser('['),
                MaybeParser(
                    SequenceParser(
                        // This MaybeParser is here in order to handle the first choice being an
                        // empty string: eg. [|]. The top-level MaybeParser's predict() ultimately
                        // ends up calling BaseCharParser's predict(), which calls ParserContext's
                        // validateNextChar(), which returns false for empty strings. Thus, even
                        // though templateParser can match empty strings, predict() returning false
                        // prevents the top-level MaybeParser from trying to parse this whole
                        // sequence entirely.
                        MaybeParser(
                            templateParser,
                        ),
                        ManyParser(
                            SequenceParser(
                                CharInParser('|'),
                                templateParser,
                            )
                        ),
                        MaybeParser(
                            CharInParser('|'),
                        ),
                    ),
                ),
                CharInParser(']'),
            ),
        ) {
            val choices = mutableListOf<TemplateString>()
            val choicesNode = it.node2.node

            if (choicesNode != null) {
                // First argument
                val firstArgNode = choicesNode.node1.node
                if (firstArgNode != null) {
                    // Non-empty value
                    choices.add(firstArgNode.value)
                } else {
                    // Maybe empty; check if followed by a separator
                    if (choicesNode.node2.nodeList.isNotEmpty() || choicesNode.node3.node != null) {
                        choices.add(TemplateString(emptyList()))
                    }
                }

                // Remaining arguments
                for (separatorAndArgNode in choicesNode.node2.nodeList) {
                    choices.add(separatorAndArgNode.node2.value)
                }
            }

            if (choices.isEmpty()) {
                // [] should be treated like a single empty string choice
                choices.add(TemplateString(emptyList()))
            }

            Fallback(choices)
        }

        init {
            templateParser uses MappedParser(
                ManyParser(
                    ExactChoiceParser(
                        stringLiteralParser,
                        variableRefParser,
                        fallbackParser,
                    ),
                ),
            ) {
                TemplateString(it.nodeList.map { choiceNode ->
                    when (choiceNode) {
                        is Choice3Node.Option1 -> choiceNode.node.value
                        is Choice3Node.Option2 -> choiceNode.node.value
                        is Choice3Node.Option3 -> choiceNode.node.value
                    }
                })
            }
        }

        internal val parser = MappedParser(
            SequenceParser(
                templateParser,
                EndOfInputParser(),
            ),
        ) {
            it.node1.value
        }

        /**
         * Find the first instance of a variable reference for {name} and return every literal
         * prefix that can possibly be used to find it.
         */
        internal fun findVariableRefInternal(
            template: TemplateString,
            name: String,
        ): Pair<VariableRef?, Set<Prefix>> {
            val prefixes = hashSetOf(Prefix("", true))

            for (clause in template.clauses) {
                when (clause) {
                    is StringLiteral -> {
                        val newPrefixes = prefixes.map {
                            it.copy(literal = it.literal + clause.value)
                        }
                        prefixes.clear()
                        prefixes.addAll(newPrefixes)
                    }
                    is VariableRef -> {
                        if (clause.name == name) {
                            return Pair(clause, prefixes)
                        } else {
                            prefixes.clear()
                            prefixes.add(Prefix("", false))
                        }
                    }
                    is Fallback -> {
                        val newPrefixes = hashSetOf<Prefix>()
                        var haveLiteralChoice = false

                        for (choice in clause.choices) {
                            if (haveLiteralChoice) {
                                break
                            }

                            val (varRef, choicePrefixes) = findVariableRefInternal(choice, name)
                            if (varRef != null) {
                                // Variable found in this fallback choice.
                                return Pair(varRef, choicePrefixes)
                            } else {
                                // Variable not found in this fallback choice, but it may contain
                                // prefixes for further clauses.

                                if (choicePrefixes.any { it.atStart }) {
                                    // This fallback clause is a complete string literal. Any
                                    // further string literal choices (eg. `b` in `[a|b]`) should be
                                    // ignored as they would never be evaluated anyway. This also
                                    // helps mitigate an explosion in prefix possibilities (eg.
                                    // `[a|b][c|d][e|f]{var}`).
                                    haveLiteralChoice = true
                                }

                                for (prefix in choicePrefixes) {
                                    if (prefix.atStart) {
                                        // The subtemplate consists entirely of a string literal.
                                        // Extend our current set of prefixes.
                                        for (oldPrefix in prefixes) {
                                            newPrefixes.add(oldPrefix.copy(
                                                literal = oldPrefix.literal + prefix.literal,
                                            ))
                                        }
                                    } else {
                                        // The fallback choice ends in a string literal. It can only
                                        // serve as a new prefix for later clauses.
                                        newPrefixes.add(prefix)
                                    }
                                }
                            }
                        }

                        prefixes.clear()
                        prefixes.addAll(newPrefixes)
                    }
                }
            }

            return Pair(null, prefixes)
        }
    }

    /** Base type for AST nodes. */
    sealed interface AstNode {
        fun toTemplate(): String
    }

    /** Base type for string clauses. */
    sealed interface Clause : AstNode

    /**
     * AST node representing a literal string with embedded special characters already
     * unescaped.
     */
    data class StringLiteral(val value: String) : Clause {
        override fun toTemplate(): String = escape(value)
    }

    /** AST node representing a variable reference. */
    data class VariableRef(val name: String, val arg: String?) : Clause {
        override fun toTemplate(): String = buildString {
            append('{')
            append(name)
            if (arg != null) {
                append(':')
                append(escape(arg))
            }
            append('}')
        }
    }

    /**
     * AST node representing a fallback clause. This does not include the implicit final empty
     * string choice.
     */
    data class Fallback(val choices: List<TemplateString>) : Clause {
        override fun toTemplate(): String = buildString {
            append('[')
            for ((i, choice) in choices.withIndex()) {
                if (i > 0) {
                    append('|')
                }
                append(choice.toTemplate())
            }
            append(']')
        }
    }

    /**
     * AST node representing a complete string template, which can include string literals,
     * variable references, and fallback clauses.
     */
    data class TemplateString(val clauses: List<Clause>) : AstNode {
        override fun toTemplate(): String = buildString {
            for (clause in clauses) {
                append(clause.toTemplate())
            }
        }
    }

    sealed interface EvalResult {
        data class Success(val value: String) : EvalResult
        data class MissingVariable(var name: String) : EvalResult
    }

    class MissingVariableException(val name: String, cause: Throwable? = null)
        : Exception("Unknown variable: $name", cause)

    private val parsed = parser.parse(ParserContext.fromString(template))

    val ast: TemplateString
        get() = parsed.first.value

    override fun toString(): String = ast.toTemplate()

    fun evaluate(getVar: (String, String?) -> String?): String {
        val varCache = hashMapOf<Pair<String, String?>, String?>()
        val getVarCached = { name: String, arg: String? ->
            varCache.getOrPut(Pair(name, arg)) {
                getVar(name, arg)
            }
        }

        fun recurse(node: AstNode): EvalResult {
            when (node) {
                is StringLiteral -> return EvalResult.Success(node.value)
                is VariableRef -> {
                    val value = getVarCached(node.name, node.arg)
                    return if (value != null) {
                        EvalResult.Success(value)
                    } else {
                        EvalResult.MissingVariable(node.name)
                    }
                }
                is Fallback -> {
                    lateinit var lastResult: EvalResult

                    for (choice in node.choices) {
                        lastResult = recurse(choice)

                        when (lastResult) {
                            is EvalResult.Success -> return lastResult
                            is EvalResult.MissingVariable -> continue
                        }
                    }

                    // Return last error
                    return lastResult
                }
                is TemplateString -> {
                    return EvalResult.Success(buildString {
                        for (clause in node.clauses) {
                            when (val result = recurse(clause)) {
                                is EvalResult.Success -> append(result.value)
                                is EvalResult.MissingVariable -> return result
                            }
                        }
                    })
                }
            }
        }

        when (val result = recurse(ast)) {
            is EvalResult.Success -> return result.value
            is EvalResult.MissingVariable -> throw MissingVariableException(result.name)
        }
    }

    internal data class Prefix(
        val literal: String,
        val atStart: Boolean,
    )

    sealed interface VariableRefLocation {
        /**
         * The location of the variable reference is after a string literal. If [atStart] is true,
         * then the literal exists at the very start of the template.
         */
        data class AfterPrefix(val literal: String, val atStart: Boolean) : VariableRefLocation

        /** The location of the variable reference is arbitrary (dependent on other variables). */
        object Arbitrary : VariableRefLocation
    }

    /**
     * Find the first reference to the specified variable and the set of string literal prefixes
     * that can be used to potentially find it inside a string produced by the template.
     *
     * @return the variable name, argument, and the locations where its value could start in a
     * output string.
     */
    fun findVariableRef(name: String): Pair<VariableRef, Set<VariableRefLocation>>? {
        val (varRef, prefixes) = findVariableRefInternal(ast, name)
        if (varRef == null) {
            return null
        }

        val locations = prefixes
            .asSequence()
            .map {
                if (it.literal.isEmpty() && !it.atStart) {
                    VariableRefLocation.Arbitrary
                } else {
                    VariableRefLocation.AfterPrefix(it.literal, it.atStart)
                }
            }
            .toHashSet()

        return Pair(varRef, locations)
    }

    /**
     * Find every variable reference in the template. This includes ones that may not be evaluated
     * by [evaluate] due to being in later fallback choices.
     */
    fun findAllVariableRefs(): List<VariableRef> {
        val refs = mutableListOf<VariableRef>()

        fun recurse(node: AstNode) {
            when (node) {
                is StringLiteral -> {}
                is VariableRef -> refs.add(node)
                is Fallback -> {
                    for (choice in node.choices) {
                        recurse(choice)
                    }
                }
                is TemplateString -> {
                    for (clause in node.clauses) {
                        recurse(clause)
                    }
                }
            }
        }

        recurse(ast)

        return refs
    }
}