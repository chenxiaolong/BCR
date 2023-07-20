package com.chiller3.bcr.template

import com.chiller3.bcr.Preferences
import com.chiller3.bcr.output.OutputFilenameGenerator
import com.copperleaf.kudzu.node.mapped.ValueNode
import com.copperleaf.kudzu.parser.Parser
import com.copperleaf.kudzu.parser.ParserContext
import com.copperleaf.kudzu.parser.ParserException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TemplateTest {
    private fun <N: Template.AstNode> parse(
        parser: Parser<ValueNode<N>>,
        input: String,
        expectedTemplate: String = input,
    ): Template.AstNode {
        val node = parser.parse(ParserContext.fromString(input)).first.value
        assertEquals(expectedTemplate, node.toTemplate())
        return node
    }

    private fun evaluate(input: String, getVar: (String, String?) -> String?): String {
        val template = Template(input)
        assertEquals(input, template.toString())
        return template.evaluate(getVar)
    }

    @Test
    fun testStringLiteralParser() {
        assertEquals(
            Template.StringLiteral("foo"),
            parse(Template.stringLiteralParser, "foo"),
        )
        assertEquals(
            Template.StringLiteral("\\{}[]|"),
            parse(Template.stringLiteralParser, "\\\\\\{\\}\\[\\]\\|"),
        )
        for (s in arrayOf("", "\\a")) {
            assertThrows(ParserException::class.java) {
                parse(Template.stringLiteralParser, s)
            }
        }
    }

    @Test
    fun testVariableRefParser() {
        assertEquals(
            Template.VariableRef("foo", null),
            parse(Template.variableRefParser, "{foo}"),
        )
        assertEquals(
            Template.VariableRef("foo", null),
            parse(Template.variableRefParser, "{foo}"),
        )
        assertEquals(
            Template.VariableRef("foo", ""),
            parse(Template.variableRefParser, "{foo:}"),
        )
        assertEquals(
            Template.VariableRef("foo", " "),
            parse(Template.variableRefParser, "{foo: }"),
        )
        assertEquals(
            Template.VariableRef("foo", "bar"),
            parse(Template.variableRefParser, "{foo:bar}"),
        )
        assertEquals(
            Template.VariableRef("foo", "\\{}[]|"),
            parse(Template.variableRefParser, "{foo:\\\\\\{\\}\\[\\]\\|}"),
        )
        for (s in arrayOf("", "{foo", "foo}", "foo", "{0}", "{}")) {
            assertThrows(ParserException::class.java) {
                parse(Template.variableRefParser, s)
            }
        }
    }

    @Test
    fun testFallbackParser() {
        assertEquals(
            Template.Fallback(listOf(
                Template.TemplateString(emptyList()),
            )),
            parse(Template.fallbackParser, "[]"),
        )
        assertEquals(
            Template.Fallback(listOf(
                Template.TemplateString(emptyList()),
                Template.TemplateString(emptyList()),
            )),
            parse(Template.fallbackParser, "[|]"),
        )
        assertEquals(
            Template.Fallback(listOf(
                Template.TemplateString(emptyList()),
                Template.TemplateString(emptyList()),
                Template.TemplateString(emptyList()),
                Template.TemplateString(emptyList()),
                Template.TemplateString(emptyList()),
                Template.TemplateString(emptyList()),
            )),
            parse(Template.fallbackParser, "[|||||]"),
        )
        assertEquals(
            Template.Fallback(listOf(
                Template.TemplateString(listOf(
                    Template.StringLiteral("foo"),
                )),
            )),
            parse(Template.fallbackParser, "[foo]"),
        )
        assertEquals(
            Template.Fallback(listOf(
                Template.TemplateString(listOf(
                    Template.StringLiteral("foo"),
                )),
                Template.TemplateString(emptyList()),
            )),
            parse(Template.fallbackParser, "[foo|]"),
        )
        assertEquals(
            Template.Fallback(listOf(
                Template.TemplateString(listOf(
                    Template.StringLiteral("foo"),
                )),
                Template.TemplateString(listOf(
                    Template.VariableRef("var", null),
                )),
                Template.TemplateString(listOf(
                    Template.StringLiteral("|"),
                )),
            )),
            parse(Template.fallbackParser, "[foo|{var}|\\|]"),
        )
        for (s in arrayOf("", "[foo", "foo]")) {
            assertThrows(ParserException::class.java) {
                parse(Template.variableRefParser, s)
            }
        }
    }

    @Test
    fun testTemplateParser() {
        assertEquals(
            Template.TemplateString(emptyList()),
            parse(Template.templateParser, ""),
        )
        assertEquals(
            Template.TemplateString(listOf(
                Template.StringLiteral("a"),
                Template.Fallback(listOf(
                    Template.TemplateString(emptyList()),
                    Template.TemplateString(listOf(
                        Template.StringLiteral("b"),
                    )),
                    Template.TemplateString(listOf(
                        Template.VariableRef("c", null),
                    )),
                    Template.TemplateString(emptyList()),
                )),
                Template.StringLiteral("d"),
                // `|e` is ignored because templateParser doesn't require end-of-input
            )),
            parse(Template.templateParser, "a[|b|{c}|]d|e", "a[|b|{c}|]d"),
        )
    }

    @Test
    fun testParser() {
        assertEquals(
            Template.TemplateString(listOf(
                Template.StringLiteral("a"),
                Template.Fallback(listOf(
                    Template.TemplateString(listOf(
                        Template.VariableRef("b", null),
                    )),
                    Template.TemplateString(listOf(
                        Template.Fallback(listOf(
                            Template.TemplateString(listOf(
                                Template.VariableRef("c", null),
                            )),
                            Template.TemplateString(listOf(
                                Template.StringLiteral("d"),
                            )),
                        )),
                    )),
                )),
                Template.StringLiteral("e"),
            )),
            parse(Template.parser, "a[{b}|[{c}|d]]e"),
        )

        assertThrows(ParserException::class.java) {
            parse(Template.parser, "a[|b|{c}|]d|e")
        }
    }

    @Test
    fun testEvaluate() {
        fun getVar(name: String, arg: String?): String? {
            return when (name) {
                "arg" -> arg
                else -> null
            }
        }

        assertEquals(
            "",
            evaluate("", ::getVar),
        )
        assertEquals(
            "blah",
            evaluate("{arg:blah}", ::getVar),
        )
        assertEquals(
            "{}",
            evaluate("{arg:\\{\\}}", ::getVar),
        )
        assertEquals(
            "",
            evaluate("[{arg}|]", ::getVar),
        )
        assertEquals(
            "c",
            evaluate("[{a}|{b}|{arg:c}]", ::getVar),
        )
        assertEquals(
            "d",
            evaluate("[[[{a}|{b}]|{c}]|d]", ::getVar),
        )
        assertThrows(Template.MissingVariableException::class.java) {
            evaluate("{arg}", ::getVar)
        }
        assertThrows(Template.MissingVariableException::class.java) {
            evaluate("[{arg}]", ::getVar)
        }
    }

    @Test
    fun testFindVariableRefInternal() {
        val find = { input: String, name: String ->
            val template = parse(Template.parser, input)
            Template.findVariableRefInternal(template as Template.TemplateString, name)
        }

        // No variable
        assertEquals(
            Pair(null, setOf(Template.Prefix("", true))),
            find("", "var"),
        )
        // Unreachable variable
        assertEquals(
            Pair(null, setOf(Template.Prefix("", true))),
            find("[|{var}]", "var"),
        )
        // Variable at beginning of template
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.Prefix("", true)),
            ),
            find("{var}", "var"),
        )
        // Variable at beginning of subtemplate
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.Prefix("", true)),
            ),
            find("[{a}|{var}]", "var"),
        )
        // Variable after another variable
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.Prefix("", false)),
            ),
            find("{a}{var}", "var"),
        )
        // Variable after non-empty fallback
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(
                    Template.Prefix("b", false),
                    Template.Prefix("c", true),
                ),
            ),
            find("[{a}b|c]{var}", "var"),
        )
        // Variable after empty fallback
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(
                    Template.Prefix("c", false),
                    Template.Prefix("", false),
                ),
            ),
            find("{a}[{b}c|]{var}", "var"),
        )
        // Literals surrounding fallback
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(
                    Template.Prefix("ade", true),
                    Template.Prefix("ce", false),
                ),
            ),
            find("a[{b}c|d]e{var}", "var"),
        )
        // Unreachable fallback choices
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.Prefix("ace", true)),
            ),
            find("[a|b][c|d][e|f]{var}", "var"),
        )
        // Deeply nested variable
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.Prefix("", true)),
            ),
            find("[{a}|[{b}|[{c}|[{d}|[{e}|[{f}|{var}]]]]]]", "var"),
        )
    }

    @Test
    fun testFindVariableRef() {
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.VariableRefLocation.AfterPrefix("foo", true)),
            ),
            Template("foo{var}").findVariableRef("var"),
        )
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.VariableRefLocation.AfterPrefix("foo", true)),
            ),
            Template("[]foo{var}").findVariableRef("var"),
        )
        assertEquals(
            Pair(
                Template.VariableRef("var", null),
                setOf(Template.VariableRefLocation.AfterPrefix("foo", true)),
            ),
            Template("foo[]{var}").findVariableRef("var"),
        )
        assertEquals(
            Pair(
                Template.VariableRef("var", "arg"),
                setOf(Template.VariableRefLocation.AfterPrefix("", true)),
            ),
            Template("{var:arg}").findVariableRef("var"),
        )
        assertEquals(
            Pair(
                Template.VariableRef("date", null),
                setOf(Template.VariableRefLocation.AfterPrefix("", true)),
            ),
            Preferences.DEFAULT_FILENAME_TEMPLATE.findVariableRef("date"),
        )
    }

    @Test
    fun testAllFindVariableRef() {
        assertEquals(
            listOf(Template.VariableRef("var", null)),
            Template("{var}").findAllVariableRefs(),
        )
        assertEquals(
            listOf(
                Template.VariableRef("a", null),
                Template.VariableRef("b", null),
            ),
            Template("[{a}|{b}]").findAllVariableRefs(),
        )
        assertEquals(
            listOf(
                Template.VariableRef("a", null),
                Template.VariableRef("b", null),
                Template.VariableRef("c", null),
                Template.VariableRef("d", "arg"),
            ),
            Template("[{a}|{b}]{c}[[{d:arg}]]").findAllVariableRefs(),
        )
        assertEquals(
            OutputFilenameGenerator.KNOWN_VARS.map { Template.VariableRef(it, null) },
            Preferences.DEFAULT_FILENAME_TEMPLATE.findAllVariableRefs(),
        )
    }
}