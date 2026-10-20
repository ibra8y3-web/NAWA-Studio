package com.nawa.studio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NawaSourceEditorTest {
    @Test
    fun insertsElementInsideTheScreenAndProducesParsableSource() {
        val source = """
            app "Example" {
                screen Home {
                    text("existing")
                }
            }
        """.trimIndent()
        val updated = NawaSourceEditor.insertElement(source, "text(${NawaSourceEditor.quote("added")})")
        val program = NawaParser.parse(updated)
        assertTrue(updated.indexOf("text(\"added\")") < updated.lastIndexOf("}"))
        assertEquals(2, program.elements.size)
        assertTrue(!program.hasErrors)
    }

    @Test
    fun ignoresBracesInStringsAndCommentsWhenFindingScreenEnd() {
        val source = """
            app "Example" { screen Home {
                text("braces { } and close } and open {") // } comment brace
            } }
        """.trimIndent()
        val updated = NawaSourceEditor.insertElement(source, "text(\"safe\")")
        val program = NawaParser.parse(updated)
        assertEquals(2, program.elements.size)
        assertTrue(!program.hasErrors)
    }

    @Test
    fun quotesSpecialCharactersForNawaString() {
        assertEquals("\"hello \\\"world\\\"\\nnext\\tend\\\\\"", NawaSourceEditor.quote("hello \"world\"\nnext\tend\\"))
    }

    @Test
    fun replacesAnElementUsingParserSourceOffsets() {
        val source = """
            app "Example" {
                screen Home {
                    state count: Int = 0
                    text("old")
                    button("Go") { count = count + 1 }
                }
            }
        """.trimIndent()
        val first = NawaParser.parse(source).elements.filterIsInstance<NawaElement.Text>().single()
        val updated = NawaSourceEditor.replaceRange(
            source,
            first.sourceStart,
            first.sourceEnd,
            "text(${NawaSourceEditor.quote("new text")})"
        )
        val parsed = NawaParser.parse(updated)
        assertEquals("new text", parsed.elements.filterIsInstance<NawaElement.Text>().single().value)
        assertTrue(!parsed.hasErrors)
    }

    @Test
    fun serializesControlAndPreservesPositionDuringSourceLinkedMove() {
        val source = """
            app "Form" {
                screen Home {
                    state title: String = "Hello"
                    input("Your name", title).at(10, 20)
                }
            }
        """.trimIndent()
        val control = NawaParser.parse(source).screens.single().elements.single() as NawaElement.Control
        val moved = NawaSourceEditor.replaceRange(
            source, control.sourceStart, control.sourceEnd,
            NawaSourceEditor.elementSource(control, 42, 84)
        )
        val reparsed = NawaParser.parse(moved)
        assertTrue(reparsed.diagnostics.toString(), !reparsed.hasErrors)
        val updated = reparsed.screens.single().elements.single() as NawaElement.Control
        assertEquals("title", updated.stateName)
        assertEquals(42, updated.x)
        assertEquals(84, updated.y)
    }

    @Test
    fun removesOnlySelectedElementAndKeepsOtherSourceValid() {
        val source = """
            app "Example" {
                screen Home {
                    text("keep")
                    text("remove")
                }
            }
        """.trimIndent()
        val second = NawaParser.parse(source).elements.filterIsInstance<NawaElement.Text>().last()
        val updated = NawaSourceEditor.removeElement(source, second.sourceStart, second.sourceEnd)
        val parsed = NawaParser.parse(updated)
        assertEquals(listOf("keep"), parsed.elements.filterIsInstance<NawaElement.Text>().map { it.value })
        assertTrue(!parsed.hasErrors)
    }

    @Test
    fun serializesSupportedActionsBackToParsableSource() {
        val actions = listOf(
            NawaAction.AdjustNumber("count", -2),
            NawaAction.SetNumber("count", -10),
            NawaAction.SetString("name", "brace } and quote \" ok"),
            NawaAction.SetBoolean("enabled", false),
            NawaAction.ToggleBoolean("enabled"),
            NawaAction.ArithmeticNumber("count", "*", 3),
            NawaAction.Repeat(3, NawaAction.AdjustNumber("count", 1)),
            NawaAction.While(NawaCondition.Compare("count", "<", NawaValue.NumberValue(5)), NawaAction.AdjustNumber("count", 1))
        )
        val expected = listOf(
            "count = count - 2",
            "count = -10",
            "name = \"brace } and quote " + "\\\"" + " ok\"",
            "enabled = false",
            "enabled = !enabled",
            "count = count * 3",
            "repeat(3) {\n    count = count + 1\n}",
            "while (count < 5) {\n    count = count + 1\n}"
        )
        actions.zip(expected).forEach { (action, code) -> assertEquals(code, NawaSourceEditor.actionSource(action)) }
    }


    @Test
    fun serializesConditionalSequenceAndParsesItAgain() {
        val action = NawaAction.Sequence(listOf(
            NawaAction.Conditional(
                NawaCondition.Logical(
                    NawaCondition.Compare("count", "<", NawaValue.NumberValue(4)),
                    "&&",
                    NawaCondition.BooleanState("enabled")
                ),
                NawaAction.AdjustNumber("count", 1),
                NawaAction.SetNumber("count", 0)
            ),
            NawaAction.ToggleBoolean("enabled"),
            NawaAction.ArithmeticNumber("count", "*", 3),
            NawaAction.Repeat(3, NawaAction.AdjustNumber("count", 1)),
            NawaAction.While(NawaCondition.Compare("count", "<", NawaValue.NumberValue(5)), NawaAction.AdjustNumber("count", 1))
        ))
        val serialized = NawaSourceEditor.actionSource(action)
        val program = NawaParser.parse("""
            app "Roundtrip" { screen Home {
                state count: Int = 0
                state enabled: Bool = true
                text("{count}")
                button("Run") {
                    $serialized
                }
            } }
        """.trimIndent())
        assertTrue(program.diagnostics.toString(), !program.hasErrors)
        assertEquals(action, program.elements.filterIsInstance<NawaElement.Button>().single().action)
    }

    @Test
    fun insertsElementIntoNamedScreenInMultiScreenProject() {
        val source = """
            app "Example" {
                screen Home { text("home") }
                screen Settings { text("settings") }
            }
        """.trimIndent()
        val updated = NawaSourceEditor.insertElement(source, "text(${NawaSourceEditor.quote("extra")})", "Settings")
        val parsed = NawaParser.parse(updated)
        assertTrue(!parsed.hasErrors)
        assertEquals(listOf("home"), parsed.screens[0].elements.filterIsInstance<NawaElement.Text>().map { it.value })
        assertEquals(listOf("settings", "extra"), parsed.screens[1].elements.filterIsInstance<NawaElement.Text>().map { it.value })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenScreenRatherThanInjectingAtAnArbitraryOffset() {
        NawaSourceEditor.insertElement("app \"Broken\" { screen Home { text(\"x\")", "text(\"new\")")
    }
    @Test
    fun swapsVisualElementsWithoutChangingTheirIdentityOrInvalidatingSource() {
        val source = """
            app "Order" {
                screen Home {
                    text("first")
                    button("second") { count = count + 1 }
                    text("third")
                    state count: Int = 0
                }
            }
        """.trimIndent()
        val elements = NawaParser.parse(source).screens.single().elements
        val swapped = NawaSourceEditor.swapElements(source, elements[0].sourceStart, elements[0].sourceEnd, elements[1].sourceStart, elements[1].sourceEnd)
        val parsed = NawaParser.parse(swapped)
        assertTrue(parsed.diagnostics.toString(), !parsed.hasErrors)
        assertEquals(listOf("second", "first", "third"), parsed.screens.single().elements.map {
            when (it) { is NawaElement.Text -> it.value; is NawaElement.Button -> it.label; is NawaElement.Control -> it.label }
        })
    }

    @Test
    fun formatsNawaIndentationWithoutChangingStringsOrSingleLineComments() {
        val source = """app "Format" {
 screen Home {
 state message: String = "hello {x}  {"
 text("line {message}") // keep  spaces
 }
}"""
        val formatted = NawaSourceEditor.formatNawa(source)
        assertTrue(formatted.contains("state message: String = " + "\"hello {x}  {\""))
        assertTrue(formatted.contains("text(" + "\"line {message}\") // keep  spaces"))
        assertTrue(!NawaParser.parse(formatted).hasErrors)
    }

}
