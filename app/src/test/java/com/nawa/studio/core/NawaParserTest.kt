package com.nawa.studio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NawaParserTest {
    private val validSource = """
        app "Counter" {
            screen Home {
                state count: Int = 2
                text("Count: {count}")
                button("Increase") {
                    count = count + 1
                }
            }
        }
    """.trimIndent()

    @Test
    fun parsesAppStateTextAndExecutableButton() {
        val program = NawaParser.parse(validSource)
        assertEquals("Counter", program.appName)
        assertEquals("Home", program.screenName)
        assertEquals(NawaValue.NumberValue(2), program.initialState["count"])
        assertEquals(2, program.elements.size)
        val button = program.elements.filterIsInstance<NawaElement.Button>().single()
        assertTrue(button.isExecutable)
        assertEquals(NawaAction.AdjustNumber("count", 1), button.action)
        assertFalse(program.hasErrors)
    }

    @Test
    fun interpreterExecutesTypedActionsAndInterpolation() {
        val program = NawaParser.parse("""
            app "Types" { screen Home {
              state count: Int = 2
              state name: String = "Nawa"
              state enabled: Bool = true
              text("{name}: {count}; {enabled}")
              button("plus") { count = count + 1 }
              button("rename") { name = "Studio" }
              button("toggle") { enabled = !enabled }
            } }
        """.trimIndent())
        val values = program.initialState.toMutableMap()
        program.elements.filterIsInstance<NawaElement.Button>().forEach { button ->
            assertTrue(button.action != null)
            assertTrue(NawaParser.execute(button.action!!, values))
        }
        assertEquals(NawaValue.NumberValue(3), values["count"])
        assertEquals(NawaValue.StringValue("Studio"), values["name"])
        assertEquals(NawaValue.BooleanValue(false), values["enabled"])
        assertEquals("Studio: 3; false", NawaParser.interpolate("{name}: {count}; {enabled}", values))
    }

    @Test
    fun parsesConditionalBranchesAndMultipleStatementsWithTypedConditions() {
        val program = NawaParser.parse("""
            app "Flow" {
                screen Home {
                    state count: Int = 0
                    state enabled: Bool = true
                    state name: String = "before"
                    text("{count} {name} {enabled}")
                    button("conditional") {
                        if (count < 2 && enabled) {
                            count = count + 1
                            name = "inside"
                        } else {
                            count = 9
                        }
                        enabled = !enabled
                    }
                }
            }
        """.trimIndent())
        assertFalse(program.hasErrors)
        val button = program.elements.filterIsInstance<NawaElement.Button>().single()
        assertTrue(button.action is NawaAction.Sequence)
        val values = program.initialState.toMutableMap()
        assertTrue(NawaParser.execute(button.action!!, values))
        assertEquals(NawaValue.NumberValue(1), values["count"])
        assertEquals(NawaValue.StringValue("inside"), values["name"])
        assertEquals(NawaValue.BooleanValue(false), values["enabled"])

        assertTrue(NawaParser.execute(button.action!!, values))
        // After the first execution enabled is false, so the else branch runs.
        assertEquals(NawaValue.NumberValue(9), values["count"])
    }

    @Test
    fun supportsScreenLocalActionFunctionsAndBoundedRepeatLoops() {
        val program = NawaParser.parse("""
            app "Functions and repeat" {
                screen Home {
                    state count: Int = 0
                    fun increase() { count = count + 1 }
                    text("count {count}")
                    button("Add three") {
                        repeat(3) { increase() }
                    }
                }
            }
        """.trimIndent())
        assertTrue(!program.hasErrors)
        val action = program.elements.filterIsInstance<NawaElement.Button>().single().action!!
        val values = program.initialState.toMutableMap()
        assertTrue(NawaParser.execute(action, values))
        assertEquals(NawaValue.NumberValue(3), values["count"])

        val unsafe = NawaParser.parse("""
            app "Infinite-ish" { screen Home {
                state count: Int = 0
                text("{count}")
                button("too much") { repeat(1001) { count = count + 1 } }
            } }
        """.trimIndent())
        assertTrue(unsafe.hasErrors)
    }

    @Test
    fun malformedFunctionDeclarationIsNotSilentlyIgnored() {
        val program = NawaParser.parse("""
            app "Malformed" { screen Home {
                state count: Int = 0
                fun increase(value) { count = count + 1 }
                text("{count}")
                button("run") { count = count + 1 }
            } }
        """.trimIndent())
        assertTrue(program.hasErrors)
        assertTrue(program.diagnostics.any { it.message.contains("صيغة تعريف الدالة غير مدعومة") })
    }

    @Test
    fun recursiveActionFunctionsAreRejectedRatherThanExecuted() {
        val program = NawaParser.parse("""
            app "Recursive" { screen Home {
                state count: Int = 0
                fun first() { second() }
                fun second() { first() }
                text("{count}")
                button("run") { first() }
            } }
        """.trimIndent())
        assertTrue(program.hasErrors)
        assertTrue(program.diagnostics.any { it.message.contains("استدعاء الذاتي أو الدائري") })
    }

    @Test
    fun conditionalCanNavigateAndUnknownNestedRouteIsDiagnosed() {
        val source = """
            app "Routes" {
                screen Home {
                    state ready: Bool = true
                    text("home")
                    button("continue") {
                        if (ready) { navigate("Next") } else { ready = true }
                    }
                }
                screen Next { text("next") }
            }
        """.trimIndent()
        val program = NawaParser.parse(source)
        assertFalse(program.hasErrors)
        val action = program.screens.first().elements.filterIsInstance<NawaElement.Button>().single().action!!
        assertEquals("Next", NawaParser.executeWithNavigation(action, program.screens.first().states.associate { it.name to it.initialValue }.toMutableMap()))

        val bad = NawaParser.parse(source.replace("navigate(\"Next\")", "navigate(\"Missing\")"))
        assertTrue(bad.hasErrors)
        assertTrue(bad.diagnostics.any { it.message.contains("شاشة غير معرّفة") })
    }

    @Test
    fun rejectsConditionsWithUnknownStatesOrWrongTypes() {
        val program = NawaParser.parse("""
            app "Bad flow" { screen Home {
                state count: Int = 1
                text("hi")
                button("bad") { if (missing) { count = 2 } }
            } }
        """.trimIndent())
        assertTrue(program.hasErrors)
        assertFalse(program.elements.filterIsInstance<NawaElement.Button>().single().isExecutable)
    }

    @Test
    fun unsupportedButtonActionIsDisabledAndDiagnosed() {
        val program = NawaParser.parse("""
            app "Test" { screen Home {
              state count: Int = 0
              button("No-op") { print(count) }
            } }
        """.trimIndent())
        val button = program.elements.filterIsInstance<NawaElement.Button>().single()
        assertFalse(button.isExecutable)
        assertTrue(program.diagnostics.any { it.message.contains("غير مدعوم") })
        assertTrue(program.hasErrors)
    }

    @Test
    fun reportsMissingAppAndScreen() {
        val program = NawaParser.parse("text(\"Hello\")")
        assertTrue(program.diagnostics.any { it.message.contains("app") })
        assertTrue(program.diagnostics.any { it.message.contains("screen") })
    }

    @Test
    fun reportsInvalidStateTypesAndDuplicateNames() {
        val invalid = NawaParser.parse("""
            app "Bad" { screen Home {
              state count: Int = nope
              state ok: Strange = 0
              state count: Int = 3
              text("hello")
            } }
        """.trimIndent())
        assertTrue(invalid.hasErrors)
        assertTrue(invalid.diagnostics.any { it.message.contains("لا تتوافق") })
        assertTrue(invalid.diagnostics.any { it.message.contains("مكرر") })
    }

    @Test
    fun ignoresDeclarationsInsideCommentsAndReportsUnknownStateReferences() {
        val program = NawaParser.parse("""
            // app "Fake" { screen Wrong { text("not real") } }
            app "Real" {
                /* screen AlsoFake { text("ignore") } */
                screen Home {
                    text("Missing value: {unknown}")
                }
            }
        """.trimIndent())
        assertEquals("Real", program.appName)
        assertEquals("Home", program.screenName)
        assertEquals(1, program.elements.size)
        assertTrue(program.diagnostics.any { it.message.contains("غير معرّفة") })
    }

    @Test
    fun parsesMultipleScreensWithScopedStateAndNavigation() {
        val program = NawaParser.parse("""
            app "Two screens" {
                screen Home {
                    state count: Int = 1
                    text("home {count}")
                    button("settings") { navigate("Settings") }
                }
                screen Settings {
                    state count: Int = 99
                    text("settings {count}")
                    button("home") { navigate("Home") }
                }
            }
        """.trimIndent())
        assertFalse(program.hasErrors)
        assertEquals(listOf("Home", "Settings"), program.screens.map { it.name })
        assertEquals(NawaValue.NumberValue(1), program.screens[0].states.single().initialValue)
        assertEquals(NawaValue.NumberValue(99), program.screens[1].states.single().initialValue)
        assertEquals("home {count}", (program.screens[0].elements[0] as NawaElement.Text).value)
        assertEquals(NawaAction.Navigate("Settings"), (program.screens[0].elements[1] as NawaElement.Button).action)
        val secondText = program.screens[1].elements.filterIsInstance<NawaElement.Text>().single()
        assertEquals("text(\"settings {count}\")", programSourceLine("""
            app "Two screens" {
                screen Home {
                    state count: Int = 1
                    text("home {count}")
                    button("settings") { navigate("Settings") }
                }
                screen Settings {
                    state count: Int = 99
                    text("settings {count}")
                    button("home") { navigate("Home") }
                }
            }
        """.trimIndent(), secondText.line))
    }

    @Test
    fun diagnosesNavigationToMissingScreen() {
        val program = NawaParser.parse("""
            app "Bad route" {
                screen Home {
                    text("home")
                    button("missing") { navigate("Missing") }
                }
            }
        """.trimIndent())
        assertTrue(program.hasErrors)
        assertTrue(program.diagnostics.any { it.message.contains("شاشة غير معرّفة") })
    }

    private fun programSourceLine(source: String, line: Int): String = source.lines()[line - 1].trim()

    @Test
    fun ignoresKeywordsInsideStringLiteralsAndAllowsBracesInStringActions() {
        val program = NawaParser.parse("""
            app "Real app with app \"fake\" and screen Ghost" {
                screen Home {
                    state message: String = "initial"
                    text("literal button(\"fake\") and screen Wrong")
                    button("Set brace") { message = "}" }
                }
            }
        """.trimIndent())
        assertEquals("Real app with app \"fake\" and screen Ghost", program.appName)
        assertEquals("Home", program.screenName)
        assertEquals(2, program.elements.size)
        val button = program.elements.filterIsInstance<NawaElement.Button>().single()
        assertTrue(button.isExecutable)
        val state = mutableMapOf<String, NawaValue>("message" to NawaValue.StringValue("initial"))
        assertTrue(NawaParser.execute(button.action!!, state))
        assertEquals(NawaValue.StringValue("}"), state["message"])
        assertFalse(program.hasErrors)
    }

    @Test
    fun reportsIncompleteStateDeclarationsEvenWhenOtherStatesExist() {
        val program = NawaParser.parse("""
            app "Broken" {
                screen Home {
                    state count: Int = 1
                    state missingAssignment
                    text("hi")
                }
            }
        """.trimIndent())
        assertTrue(program.hasErrors)
        assertTrue(program.diagnostics.any { it.message.contains("صيغة تعريف الحالة غير مكتملة") })
    }

    @Test
    fun rejectsUnbalancedBracesAndDisablesPreview() {
        val program = NawaParser.parse("app \"Broken\" { screen Home { text(\"unfinished\") }")
        assertTrue(program.hasErrors)
        assertFalse(program.canPreview)
        assertTrue(program.diagnostics.any { it.message.contains("قوس") })
    }

    @Test
    fun rejectsElementsOutsideTheScreenAndMissingScreenBlock() {
        val outside = NawaParser.parse("""
            app "Scope" {
                text("not inside a screen")
                screen Home { text("inside") }
            }
        """.trimIndent())
        assertTrue(outside.hasErrors)
        assertTrue(outside.diagnostics.any { it.message.contains("داخل كتلة screen") })

        val missingBlock = NawaParser.parse("""
            app "Scope" {
                screen Home
                text("not valid")
            }
        """.trimIndent())
        assertTrue(missingBlock.hasErrors)
        assertTrue(missingBlock.diagnostics.any { it.message.contains("لا يحتوي على كتلة") })
    }

    @Test
    fun preventsIntegerOverflowInRuntime() {
        val values = mutableMapOf<String, NawaValue>("count" to NawaValue.NumberValue(Int.MAX_VALUE))
        assertFalse(NawaParser.execute(NawaAction.AdjustNumber("count", 1), values))
        assertEquals(NawaValue.NumberValue(Int.MAX_VALUE), values["count"])
    }
    @Test
    fun supportsGuardedWhileLoopsAndIntegerMultiplicationDivisionModulo() {
        val program = NawaParser.parse("""
            app "Arithmetic and loops" {
                screen Home {
                    state count: Int = 1
                    text("{count}")
                    button("Run") {
                        count = count * 3
                        count = count / 2
                        count = count % 4
                        while (count < 3) { count = count + 1 }
                    }
                }
            }
        """.trimIndent())
        assertTrue(program.diagnostics.toString(), !program.hasErrors)
        val action = program.elements.filterIsInstance<NawaElement.Button>().single().action!!
        val values = program.initialState.toMutableMap()
        assertTrue(NawaParser.execute(action, values))
        // 1*3=3, 3/2=1, 1%4=1, then loop to 3.
        assertEquals(NawaValue.NumberValue(3), values["count"])
    }

    @Test
    fun rejectsDivisionByZeroAndCapsNonTerminatingWhileLoop() {
        val division = NawaParser.parse("""
            app "Bad math" { screen Home {
                state count: Int = 1
                text("{count}")
                button("bad") { count = count / 0 }
            } }
        """.trimIndent())
        assertTrue(division.hasErrors)

        val looping = NawaParser.parse("""
            app "Capped loop" { screen Home {
                state count: Int = 0
                text("{count}")
                button("loop") { while (count >= 0) { count = count + 1 } }
            } }
        """.trimIndent())
        assertFalse("A syntactically supported loop is parsed", looping.hasErrors)
        val values = looping.initialState.toMutableMap()
        val action = looping.elements.filterIsInstance<NawaElement.Button>().single().action!!
        assertFalse("An always-true loop must stop at the safety cap and report failure", NawaParser.execute(action, values))
        assertEquals(NawaValue.NumberValue(NawaParser.MAX_WHILE_STEPS), values["count"])
    }

    @Test
    fun tracedExecutionRecordsStateChangesConditionsAndNavigation() {
        val program = NawaParser.parse("""
            app "Trace" {
                screen Home {
                    state count: Int = 0
                    text("{count}")
                    button("go") {
                        count = count + 1
                        if (count < 2) { count = count + 1 }
                        navigate("Settings")
                    }
                }
                screen Settings { text("settings") }
            }
        """.trimIndent())
        assertFalse(program.hasErrors)
        val values = program.screens.first().states.associate { it.name to it.initialValue }.toMutableMap()
        val action = program.screens.first().elements.filterIsInstance<NawaElement.Button>().single().action!!
        val result = NawaParser.executeTraced(action, values)
        assertTrue(result.success)
        assertEquals("Settings", result.navigationTarget)
        assertEquals(NawaValue.NumberValue(2), values["count"])
        assertTrue(result.events.any { it.kind == "شرط" })
        assertTrue(result.events.any { it.detail.contains("count") && it.detail.contains("←") })
        assertTrue(result.events.any { it.kind == "تنقّل" })
    }

    @Test
    fun parsesPositionedTextButtonsAndStateBoundControls() {
        val program = NawaParser.parse("""
            app "Controls" {
                screen Home {
                    state name: String = "Nawa"
                    state enabled: Bool = true
                    state checked: Bool = false
                    state volume: Int = 25
                    text("Hello {name}").at(12, 16)
                    button("Increase") { volume = volume + 1 }.at(24, 72)
                    input("Name", name).at(10, 140)
                    switch("Enabled", enabled)
                    checkbox("Remember", checked)
                    slider("Volume", volume, 0, 100).at(16, 260)
                }
            }
        """.trimIndent())

        assertFalse(program.diagnostics.joinToString("\n"), program.hasErrors)
        val screen = program.screens.single()
        val positionedText = screen.elements.filterIsInstance<NawaElement.Text>().single()
        assertEquals("Hello {name}", positionedText.value)
        assertEquals(12, positionedText.x)
        assertEquals(16, positionedText.y)
        val positionedButton = screen.elements.filterIsInstance<NawaElement.Button>().single()
        assertEquals(24, positionedButton.x)
        assertEquals(72, positionedButton.y)
        val controls = screen.elements.filterIsInstance<NawaElement.Control>()
        assertEquals(4, controls.size)
        assertEquals(setOf(NawaControlKind.INPUT, NawaControlKind.SWITCH, NawaControlKind.CHECKBOX, NawaControlKind.SLIDER), controls.map { it.kind }.toSet())
        assertEquals("name", controls.single { it.kind == NawaControlKind.INPUT }.stateName)
        assertEquals(0, controls.single { it.kind == NawaControlKind.SLIDER }.minimum)
        assertEquals(100, controls.single { it.kind == NawaControlKind.SLIDER }.maximum)
        assertEquals(10, controls.single { it.kind == NawaControlKind.INPUT }.x)
        assertEquals(140, controls.single { it.kind == NawaControlKind.INPUT }.y)
    }

    @Test
    fun rejectsControlsWithWrongStateTypesAndInvalidSliderRanges() {
        val program = NawaParser.parse("""
            app "Invalid controls" {
                screen Home {
                    state count: Int = 2
                    state enabled: Bool = false
                    input("Name", count)
                    slider("Range", count, 10, 2)
                    checkbox("Bad type", count)
                    switch("Missing", absent)
                }
            }
        """.trimIndent())

        assertTrue(program.hasErrors)
        assertTrue(program.diagnostics.any { it.message.contains("String") })
        assertTrue(program.diagnostics.any { it.message.contains("slider") })
        assertTrue(program.diagnostics.any { it.message.contains("Bool") })
        assertTrue(program.diagnostics.any { it.message.contains("غير معرّفة") })
    }

}
