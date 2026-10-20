package com.nawa.studio.core

/** Typed values implemented by the current NawaScript interpreter. */
sealed interface NawaValue {
    data class NumberValue(val value: Int) : NawaValue
    data class StringValue(val value: String) : NawaValue
    data class BooleanValue(val value: Boolean) : NawaValue

    fun display(): String = when (this) {
        is NumberValue -> value.toString()
        is StringValue -> value
        is BooleanValue -> if (value) "true" else "false"
    }
}

data class NawaState(val name: String, val initialValue: NawaValue, val line: Int)

sealed interface NawaCondition {
    data class BooleanState(val stateName: String) : NawaCondition
    data class Not(val condition: NawaCondition) : NawaCondition
    data class Compare(val stateName: String, val operator: String, val value: NawaValue) : NawaCondition
    data class Logical(val left: NawaCondition, val operator: String, val right: NawaCondition) : NawaCondition
}

sealed interface NawaAction {
    data class AdjustNumber(val stateName: String, val delta: Int) : NawaAction
    data class SetNumber(val stateName: String, val value: Int) : NawaAction
    data class SetString(val stateName: String, val value: String) : NawaAction
    data class SetBoolean(val stateName: String, val value: Boolean) : NawaAction
    data class ToggleBoolean(val stateName: String) : NawaAction
    /** Integer arithmetic with a compile-time integer operand; division/modulo by zero fail safely. */
    data class ArithmeticNumber(val stateName: String, val operator: String, val operand: Int) : NawaAction
    data class Navigate(val targetScreen: String) : NawaAction
    data class Sequence(val actions: List<NawaAction>) : NawaAction
    data class Conditional(val condition: NawaCondition, val thenAction: NawaAction, val elseAction: NawaAction?) : NawaAction
    data class Repeat(val times: Int, val action: NawaAction) : NawaAction
    /** Condition-controlled loop with an interpreter/compiler hard cap of 1000 iterations. */
    data class While(val condition: NawaCondition, val action: NawaAction) : NawaAction
}

enum class NawaControlKind(val keyword: String) {
    INPUT("input"), SWITCH("switch"), CHECKBOX("checkbox"), SLIDER("slider")
}

sealed interface NawaElement {
    val line: Int
    /** Inclusive start / exclusive end offsets in the source; used by the source-linked designer. */
    val sourceStart: Int
    val sourceEnd: Int
    /** Optional absolute dp position from the screen's top-left corner. */
    val x: Int?
    val y: Int?

    data class Text(
        val value: String,
        override val line: Int,
        override val sourceStart: Int = -1,
        override val sourceEnd: Int = -1,
        /** Optional absolute position in dp; null means the normal vertical-flow layout. */
        override val x: Int? = null,
        override val y: Int? = null
    ) : NawaElement

    data class Button(
        val label: String,
        val action: NawaAction?,
        override val line: Int,
        override val sourceStart: Int = -1,
        override val sourceEnd: Int = -1,
        /** Optional absolute position in dp; null means the normal vertical-flow layout. */
        override val x: Int? = null,
        override val y: Int? = null
    ) : NawaElement {
        val isExecutable: Boolean get() = action != null
    }

    data class Control(
        val kind: NawaControlKind,
        val label: String,
        val stateName: String,
        val minimum: Int? = null,
        val maximum: Int? = null,
        override val line: Int,
        override val sourceStart: Int = -1,
        override val sourceEnd: Int = -1,
        override val x: Int? = null,
        override val y: Int? = null
    ) : NawaElement
}

data class NawaTraceEvent(
    val step: Int,
    val kind: String,
    val detail: String,
    val stateSnapshot: Map<String, NawaValue>
)

data class NawaExecutionResult(
    val success: Boolean,
    val navigationTarget: String? = null,
    val events: List<NawaTraceEvent> = emptyList(),
    val warning: String? = null
)

data class NawaDiagnostic(
    val line: Int,
    val severity: Severity,
    val message: String
) {
    enum class Severity { ERROR, WARNING, INFO }
    override fun toString(): String = "السطر $line: $message"
}

data class NawaScreen(
    val name: String,
    val states: List<NawaState>,
    val elements: List<NawaElement>,
    val sourceStart: Int,
    val sourceEnd: Int,
    val diagnostics: List<NawaDiagnostic> = emptyList()
)

data class NawaProgram(
    val appName: String,
    val screenName: String,
    val states: List<NawaState>,
    val elements: List<NawaElement>,
    val diagnostics: List<NawaDiagnostic>,
    val hasAppDeclaration: Boolean,
    val hasScreenDeclaration: Boolean,
    val screens: List<NawaScreen> = emptyList()
) {
    val initialState: Map<String, NawaValue> get() = states.associate { it.name to it.initialValue }
    val hasErrors: Boolean get() = diagnostics.any { it.severity == NawaDiagnostic.Severity.ERROR }
    val canPreview: Boolean get() = hasAppDeclaration && hasScreenDeclaration &&
        (if (screens.isNotEmpty()) screens.any { it.elements.isNotEmpty() } else elements.isNotEmpty()) && !hasErrors
}

/**
 * Parser and small deterministic interpreter for the documented NawaScript subset.
 * This is deliberately not eval: source text is parsed into typed actions and arbitrary code is never executed.
 */
object NawaParser {
    const val MAX_WHILE_STEPS: Int = 1_000
    private val appPattern = Regex("\\bapp\\s+\"((?:\\\\.|[^\"\\\\])*)\"")
    private val screenPattern = Regex("""\bscreen\s+([A-Za-z_][A-Za-z0-9_]*)""")
    private val statePattern = Regex("""(?m)^[ \t]*state[ \t]+([A-Za-z_][A-Za-z0-9_]*)[ \t]*:[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=[ \t]*("(?:\\.|[^"\\])*"|-?\d+|true|false)[ \t]*(?://.*)?$""")
    private val anyStateLinePattern = Regex("""(?m)^[ \t]*state[ \t]+([A-Za-z_][A-Za-z0-9_]*)[ \t]*:[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=([^\r\n]*)$""")
    private val textPattern = Regex("""\btext\s*\(\s*"((?:\\.|[^"\\])*)"\s*\)(?:\s*\.at\s*\(\s*(\d+)\s*,\s*(\d+)\s*\))?""")
    private val controlPattern = Regex("""\b(input|switch|checkbox|slider)\s*\(\s*"((?:\\.|[^"\\])*)"\s*,\s*([A-Za-z_][A-Za-z0-9_]*)(?:\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*)?\)(?:\s*\.at\s*\(\s*(\d+)\s*,\s*(\d+)\s*\))?""")
    // Screen-local, no-argument action functions are safely parsed and inlined into event actions.
    private val functionPattern = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)\s*\{((?:[^{}"]|"(?:\\.|[^"\\])*"|\{(?:[^{}"]|"(?:\\.|[^"\\])*")*\})*)\}""")
    private val functionCallPattern = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*\)$""")
    // One nested block level is supported for if/else and repeat action bodies.
    private val buttonPattern = Regex("""\bbutton\s*\(\s*"((?:\\.|[^"\\])*)"\s*\)\s*(?:\{((?:[^{}"]|"(?:\\.|[^"\\])*"|\{(?:[^{}"]|"(?:\\.|[^"\\])*")*\})*)\})?(?:\s*\.at\s*\(\s*(\d+)\s*,\s*(\d+)\s*\))?""")
    private val adjustPattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*([+-])\s*(\d+)""")
    private val arithmeticPattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*([*/%])\s*(-?\d+)""")
    private val numberSetPattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(-?\d+)""")
    private val stringSetPattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"((?:\\.|[^"\\])*)"""")
    private val booleanSetPattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(true|false)""")
    private val togglePattern = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*!\s*([A-Za-z_][A-Za-z0-9_]*)""")
    private val navigatePattern = Regex("""navigate\s*\(\s*"((?:\\.|[^"\\])*)"\s*\)""")
    private val repeatHeaderPattern = Regex("""^repeat\s*\(\s*(\d+)\s*\)""")
    private val interpolationPattern = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)}""")

    /**
     * Parses a complete NawaScript app, including multiple screens and typed navigation actions.
     * Each screen is parsed independently so state names remain scoped to their own screen.
     */
    fun parse(source: String): NawaProgram {
        val code = maskComments(source)
        val inString = stringInteriorMask(code)
        val appMatches = appPattern.findAll(code).filter { !inString[it.range.first] }.toList()
        val screenMatches = screenPattern.findAll(code).filter { !inString[it.range.first] }.toList()
        if (screenMatches.size <= 1) {
            val single = parseSingleScreen(source)
            val screens = if (single.hasScreenDeclaration && single.screenName != "Home" || screenMatches.isNotEmpty()) {
                val match = screenMatches.firstOrNull()
                val start = match?.range?.first ?: 0
                val open = match?.let { findNextBrace(code, it.range.last + 1, inString) } ?: -1
                val close = if (open >= 0) findMatchingCloseBrace(code, open, inString) else -1
                listOf(NawaScreen(single.screenName, single.states, single.elements, start, if (close >= 0) close + 1 else source.length, single.diagnostics))
            } else emptyList()
            val diagnostics = (single.diagnostics + validateNavigationTargets(screens)).distinct()
            return single.copy(diagnostics = diagnostics, screens = screens)
        }

        val diagnostics = mutableListOf<NawaDiagnostic>()
        val appMatch = appMatches.firstOrNull()
        val appName = appMatch?.groupValues?.get(1)?.let(::unescape) ?: "مشروع بدون اسم"
        if (appMatch == null) diagnostics += errorAt(1, "لم أجد تعريف app \"اسم التطبيق\".")
        if (appMatches.size > 1) diagnostics += errorAt(lineAt(source, appMatches[1].range.first), "يوجد أكثر من تعريف app؛ يجب أن يحتوي الملف على تطبيق واحد.")
        diagnostics += braceDiagnostics(code, inString)

        val appOpen = appMatch?.let { findNextBrace(code, it.range.last + 1, inString) } ?: -1
        val appClose = if (appOpen >= 0) findMatchingCloseBrace(code, appOpen, inString) else -1
        if (appMatch != null && appOpen < 0) diagnostics += errorAt(lineAt(source, appMatch.range.first), "تعريف app لا يحتوي على كتلة بين قوسين { }.")
        if (appOpen >= 0 && appClose < 0) diagnostics += errorAt(lineAt(source, appOpen), "كتلة app غير مغلقة.")

        val blocks = screenMatches.mapNotNull { match ->
            val open = findNextBrace(code, match.range.last + 1, inString)
            val close = if (open >= 0) findMatchingCloseBrace(code, open, inString) else -1
            if (open < 0 || close < 0) {
                diagnostics += errorAt(lineAt(source, match.range.first), "تعريف screen لا يحتوي على كتلة كاملة بين قوسين { }.")
                null
            } else {
                if (appOpen < 0 || appClose < 0 || match.range.first <= appOpen || close >= appClose) {
                    diagnostics += errorAt(lineAt(source, match.range.first), "كل تعريف screen يجب أن يكون داخل كتلة app.")
                }
                Triple(match.range.first, close + 1, match.groupValues[1])
            }
        }
        if (blocks.isEmpty()) diagnostics += errorAt(1, "أضف شاشة واحدة على الأقل بصيغة screen Home { ... }.")
        val duplicateNames = blocks.groupBy { it.third }.filterValues { it.size > 1 }.keys
        duplicateNames.forEach { name ->
            val duplicate = blocks.filter { it.third == name }.drop(1)
            duplicate.forEach { block -> diagnostics += errorAt(lineAt(source, block.first), "اسم الشاشة «$name» مكرر.") }
        }
        val elementCalls = Regex("\\b(text|button|input|switch|checkbox|slider)\\s*\\(").findAll(code).filter { !inString[it.range.first] }.toList()
        elementCalls.filter { call -> blocks.none { call.range.first > it.first && call.range.first < it.second } }
            .forEach { call -> diagnostics += errorAt(lineAt(source, call.range.first), "عناصر الواجهة يجب أن تكون داخل كتلة screen.") }
        Regex("(?m)^[ \t]*state\\b[^\\r\\n]*").findAll(code)
            .filter { !inString[it.range.first] && blocks.none { block -> it.range.first > block.first && it.range.first < block.second } }
            .forEach { state -> diagnostics += errorAt(lineAt(source, state.range.first), "تعريف state يجب أن يكون داخل كتلة screen.") }

        val parsedScreens = blocks.map { (start, end, screenName) ->
            val prefix = "app ${quoteNawa(appName)} {\n"
            val wrapped = prefix + source.substring(start, end) + "\n}"
            val part = parseSingleScreen(wrapped)
            val lineShift = lineAt(source, start) - 2
            val adjustedStates = part.states.map { it.copy(line = it.line + lineShift) }
            val adjustedElements = part.elements.map { element ->
                val shiftedStart = if (element.sourceStart >= 0) start + element.sourceStart - prefix.length else element.sourceStart
                val shiftedEnd = if (element.sourceEnd >= 0) start + element.sourceEnd - prefix.length else element.sourceEnd
                when (element) {
                    is NawaElement.Text -> element.copy(
                        line = element.line + lineShift, sourceStart = shiftedStart, sourceEnd = shiftedEnd
                    )
                    is NawaElement.Button -> element.copy(
                        line = element.line + lineShift, sourceStart = shiftedStart, sourceEnd = shiftedEnd
                    )
                    is NawaElement.Control -> element.copy(
                        line = element.line + lineShift, sourceStart = shiftedStart, sourceEnd = shiftedEnd
                    )
                }
            }
            val adjustedDiagnostics = part.diagnostics
                .filterNot { it.message.contains("يوجد أكثر من تعريف screen") }
                .map { it.copy(line = (it.line + lineShift).coerceAtLeast(1)) }
            NawaScreen(screenName, adjustedStates, adjustedElements, start, end, adjustedDiagnostics)
        }
        diagnostics += parsedScreens.flatMap { it.diagnostics }
        diagnostics += validateNavigationTargets(parsedScreens)

        val first = parsedScreens.firstOrNull()
        return NawaProgram(
            appName = appName,
            screenName = first?.name ?: "Home",
            states = first?.states.orEmpty(),
            elements = first?.elements.orEmpty(),
            diagnostics = diagnostics.distinct(),
            hasAppDeclaration = appMatch != null,
            hasScreenDeclaration = parsedScreens.isNotEmpty(),
            screens = parsedScreens
        )
    }

    private fun validateNavigationTargets(screens: List<NawaScreen>): List<NawaDiagnostic> {
        if (screens.isEmpty()) return emptyList()
        val names = screens.map { it.name }.toSet()
        return screens.flatMap { screen ->
            screen.elements.flatMap elementLoop@ { element ->
                val action = (element as? NawaElement.Button)?.action ?: return@elementLoop emptyList()
                navigationTargets(action).filterNot { it in names }.map { target ->
                    errorAt(element.line, "زر التنقل يشير إلى شاشة غير معرّفة: «$target».")
                }
            }
        }
    }

    private fun navigationTargets(action: NawaAction): List<String> = when (action) {
        is NawaAction.Navigate -> listOf(action.targetScreen)
        is NawaAction.Sequence -> action.actions.flatMap(::navigationTargets)
        is NawaAction.Conditional -> navigationTargets(action.thenAction) +
            (action.elseAction?.let(::navigationTargets) ?: emptyList())
        is NawaAction.Repeat -> navigationTargets(action.action)
        is NawaAction.While -> navigationTargets(action.action)
        else -> emptyList()
    }

    private fun quoteNawa(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "\""

    private fun parseSingleScreen(source: String): NawaProgram {
        val diagnostics = mutableListOf<NawaDiagnostic>()
        // Mask comments while preserving strings and character offsets, so examples in comments
        // are not accidentally parsed as executable declarations.
        val code = maskComments(source)
        val inString = stringInteriorMask(code)
        // Regex patterns operate on one source string, so explicitly discard any match which starts
        // inside another string literal; otherwise text such as `text("app \"fake\"")` could
        // accidentally create a fake declaration or button in the parsed program.
        val appMatches = appPattern.findAll(code).filter { !inString[it.range.first] }.toList()
        val screenMatches = screenPattern.findAll(code).filter { !inString[it.range.first] }.toList()
        val appMatch = appMatches.firstOrNull()
        val screenMatch = screenMatches.firstOrNull()
        val appName = appMatch?.groupValues?.get(1)?.let(::unescape) ?: "مشروع بدون اسم"
        val screenName = screenMatch?.groupValues?.get(1) ?: "Home"

        if (appMatch == null) diagnostics += errorAt(1, "لم أجد تعريف app \"اسم التطبيق\".")
        if (screenMatch == null) diagnostics += errorAt(1, "لم أجد تعريف screen؛ أضف شاشة واحدة على الأقل.")
        if (appMatches.size > 1) diagnostics += errorAt(lineAt(code, appMatches[1].range.first), "يوجد أكثر من تعريف app؛ يجب أن يحتوي الملف على تطبيق واحد.")
        if (screenMatches.size > 1) diagnostics += errorAt(lineAt(code, screenMatches[1].range.first), "المعاينة الحالية تدعم شاشة واحدة في كل ملف. احذف تعريف screen الزائد أو افصل الشاشات إلى ملفات أخرى.")

        braceDiagnostics(code, inString).forEach { diagnostics += it }
        val appOpen = appMatch?.let { findNextBrace(code, it.range.last + 1, inString) } ?: -1
        val appClose = if (appOpen >= 0) findMatchingCloseBrace(code, appOpen, inString) else -1
        val screenOpen = screenMatch?.let { findNextBrace(code, it.range.last + 1, inString) } ?: -1
        val screenClose = if (screenOpen >= 0) findMatchingCloseBrace(code, screenOpen, inString) else -1
        if (appMatch != null && appOpen < 0) diagnostics += errorAt(lineAt(code, appMatch.range.first), "تعريف app لا يحتوي على كتلة بين قوسين { }.")
        if (screenMatch != null && screenOpen < 0) diagnostics += errorAt(lineAt(code, screenMatch.range.first), "تعريف screen لا يحتوي على كتلة بين قوسين { }.")
        if (screenOpen >= 0 && appOpen >= 0 && appClose >= 0 && (screenOpen <= appOpen || screenOpen >= appClose)) {
            diagnostics += errorAt(lineAt(code, screenMatch!!.range.first), "يجب أن يكون تعريف screen داخل كتلة app.")
        }
        val validScreenBounds = screenOpen >= 0 && screenClose > screenOpen && appOpen >= 0 && appClose > appOpen &&
            screenOpen > appOpen && screenOpen < appClose
        fun insideScreen(offset: Int): Boolean = validScreenBounds && offset > screenOpen && offset < screenClose

        val states = mutableListOf<NawaState>()
        val stateByName = linkedMapOf<String, NawaState>()
        val declaredStateNames = mutableSetOf<String>()
        val allStateLines = anyStateLinePattern.findAll(code).filter { !inString[it.range.first] }.toList()
        allStateLines.filter { !insideScreen(it.range.first) }.forEach { outside ->
            diagnostics += errorAt(lineAt(code, outside.range.first), "تعريف state يجب أن يكون داخل كتلة screen.")
        }
        Regex("""(?m)^[ \t]*state\b[^\r\n]*""").findAll(code)
            .filter { !inString[it.range.first] && !insideScreen(it.range.first) && allStateLines.none { state -> state.range.first == it.range.first } }
            .forEach { outside ->
                diagnostics += errorAt(lineAt(code, outside.range.first), "تعريف state يجب أن يكون داخل كتلة screen وبصيغة name: Type = value.")
            }
        val matchedStateOffsets = statePattern.findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) }.map { it.range.first }.toSet()
        val stateLines = allStateLines.filter { insideScreen(it.range.first) }
        val parsedStateLineOffsets = stateLines.map { it.range.first }.toSet()
        Regex("""(?m)^[ \t]*state\b[^\r\n]*""").findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) && it.range.first !in parsedStateLineOffsets }
            .forEach { malformed ->
                diagnostics += errorAt(lineAt(source, malformed.range.first), "صيغة تعريف الحالة غير مكتملة. استخدم: state name: Type = value.")
            }
        stateLines.forEach { match ->
            val offset = match.range.first
            val line = lineAt(source, offset)
            val name = match.groupValues[1]
            val type = match.groupValues[2]
            val raw = match.groupValues[3].trim().removeSuffix(";")
            if (!declaredStateNames.add(name)) {
                diagnostics += errorAt(line, "تعريف الحالة «$name» مكرر.")
                return@forEach
            }
            if (offset !in matchedStateOffsets) {
                diagnostics += errorAt(line, "القيمة «$raw» لا تتوافق مع النوع $type في الحالة «$name». الأنواع المدعومة: Int وString وBool، ويجب أن تكون القيمة كاملة في السطر.")
                return@forEach
            }
            val rawMatch = statePattern.find(code, offset) ?: return@forEach
            val rawValue = rawMatch.groupValues[3]
            val value = when (type) {
                "Int" -> rawValue.toIntOrNull()?.let(NawaValue::NumberValue)
                "String" -> if (rawValue.startsWith('"') && rawValue.endsWith('"')) {
                    NawaValue.StringValue(unescape(rawValue.substring(1, rawValue.length - 1)))
                } else null
                "Bool", "Boolean" -> rawValue.toBooleanStrictOrNull()?.let(NawaValue::BooleanValue)
                else -> null
            }
            if (value == null) {
                diagnostics += errorAt(line, "القيمة «$rawValue» لا تتوافق مع النوع $type في الحالة «$name».")
            } else {
                val state = NawaState(name, value, line)
                states += state
                stateByName[name] = state
            }
        }

        val functionMatches = functionPattern.findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) }.toList()
        val allFunctionKeywords = Regex("\\bfun\\b").findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) }.toList()
        val parsedFunctionOffsets = functionMatches.map { it.range.first }.toSet()
        allFunctionKeywords.filter { it.range.first !in parsedFunctionOffsets }.forEach { malformed ->
            diagnostics += errorAt(lineAt(source, malformed.range.first), "صيغة تعريف الدالة غير مدعومة؛ استخدم fun name() { action } داخل الشاشة.")
        }
        Regex("\\bfun\\b").findAll(code)
            .filter { !inString[it.range.first] && !insideScreen(it.range.first) }
            .forEach { outside -> diagnostics += errorAt(lineAt(code, outside.range.first), "تعريف fun يجب أن يكون داخل كتلة screen.") }
        val functionsGrouped = functionMatches.groupBy { it.groupValues[1] }
        functionsGrouped.filterValues { it.size > 1 }.forEach { (name, duplicates) ->
            duplicates.drop(1).forEach { duplicate ->
                diagnostics += errorAt(lineAt(source, duplicate.range.first), "اسم الدالة «$name» مكرر داخل الشاشة.")
            }
        }
        val functionBodies = functionsGrouped.mapValues { (_, matches) -> matches.first().groupValues[2] }
        functionMatches.forEach { function ->
            val name = function.groupValues[1]
            val body = function.groupValues[2]
            val line = lineAt(source, function.range.first)
            if (Regex("\\b(text|button|input|switch|checkbox|slider)\\s*\\(").containsMatchIn(body)) {
                diagnostics += errorAt(line, "الدالة «$name» مخصصة للمنطق ولا يمكنها تعريف عناصر واجهة text/button.")
            } else if (parseAction(body, stateByName, functionBodies, setOf(name)) == null) {
                diagnostics += errorAt(line, "تعذر تحليل الدالة «$name»؛ تحقق من الإجراءات المدعومة وتجنب الاستدعاء الذاتي أو الدائري.")
            }
        }

        fun position(match: MatchResult, xGroup: Int, yGroup: Int, line: Int): Pair<Int?, Int?> {
            val xRaw = match.groups[xGroup]?.value
            val yRaw = match.groups[yGroup]?.value
            if (xRaw == null && yRaw == null) return null to null
            val x = xRaw?.toIntOrNull()
            val y = yRaw?.toIntOrNull()
            if (x == null || y == null || x !in 0..2048 || y !in 0..4096) {
                diagnostics += errorAt(line, "إحداثيات at(x, y) يجب أن تكون أعدادًا صحيحة: x من 0 إلى 2048 وy من 0 إلى 4096.")
                return null to null
            }
            return x to y
        }

        val located = mutableListOf<Pair<Int, NawaElement>>()
        val parsedTextMatches = textPattern.findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) }.toList()
        parsedTextMatches.forEach { match ->
            val line = lineAt(source, match.range.first)
            val (x, y) = position(match, 2, 3, line)
            located += match.range.first to NawaElement.Text(
                unescape(match.groupValues[1]), line, match.range.first, match.range.last + 1, x, y
            )
        }
        val parsedButtonMatches = buttonPattern.findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) }.toList()
        parsedButtonMatches.forEach { match ->
            val label = unescape(match.groupValues[1])
            val body = match.groups[2]?.value.orEmpty().trim()
            val line = lineAt(source, match.range.first)
            val action = parseAction(body, stateByName, functionBodies)
            val (x, y) = position(match, 3, 4, line)
            located += match.range.first to NawaElement.Button(label, action, line, match.range.first, match.range.last + 1, x, y)
            when {
                body.isBlank() -> diagnostics += warningAt(line, "الزر «$label» بلا إجراء؛ سيظهر معطّلًا في المعاينة.")
                action == null -> diagnostics += errorAt(line,
                    "إجراء الزر «$label» غير مدعوم أو لا يتوافق نوعه مع الحالة. راجع دليل NawaScript.")
            }
        }

        val parsedControlMatches = controlPattern.findAll(code)
            .filter { !inString[it.range.first] && insideScreen(it.range.first) }.toList()
        parsedControlMatches.forEach { match ->
            val kind = NawaControlKind.entries.first { it.keyword == match.groupValues[1] }
            val label = unescape(match.groupValues[2])
            val stateName = match.groupValues[3]
            val line = lineAt(source, match.range.first)
            val state = stateByName[stateName]
            val typeMatches = when (kind) {
                NawaControlKind.INPUT -> state?.initialValue is NawaValue.StringValue
                NawaControlKind.SWITCH, NawaControlKind.CHECKBOX -> state?.initialValue is NawaValue.BooleanValue
                NawaControlKind.SLIDER -> state?.initialValue is NawaValue.NumberValue
            }
            var minimum: Int? = null
            var maximum: Int? = null
            if (kind == NawaControlKind.SLIDER) {
                minimum = match.groupValues[4].toIntOrNull()
                maximum = match.groupValues[5].toIntOrNull()
                if (minimum == null || maximum == null || minimum >= maximum || minimum !in -1_000_000..1_000_000 || maximum !in -1_000_000..1_000_000) {
                    diagnostics += errorAt(line, "صيغة slider هي slider(\"تسمية\", intState, min, max) بنطاق صحيح لا يتجاوز ±1000000.")
                }
            } else if (match.groupValues[4].isNotEmpty() || match.groupValues[5].isNotEmpty()) {
                diagnostics += errorAt(line, "التحكم ${kind.keyword} لا يقبل حدّي min/max؛ استخدمهما مع slider فقط.")
            }
            if (state == null) {
                diagnostics += errorAt(line, "التحكم ${kind.keyword} يستخدم حالة غير معرّفة: «$stateName».")
            } else if (!typeMatches) {
                val expected = when (kind) {
                    NawaControlKind.INPUT -> "String"
                    NawaControlKind.SWITCH, NawaControlKind.CHECKBOX -> "Bool"
                    NawaControlKind.SLIDER -> "Int"
                }
                diagnostics += errorAt(line, "التحكم ${kind.keyword} يحتاج حالة من النوع $expected؛ الحالة «$stateName» لها نوع مختلف.")
            }
            val (x, y) = position(match, 6, 7, line)
            located += match.range.first to NawaElement.Control(
                kind, label, stateName, minimum, maximum, line, match.range.first, match.range.last + 1, x, y
            )
        }

        val elementCallPattern = Regex("\\b(text|button|input|switch|checkbox|slider)\\s*\\(")
        val allCallsEverywhere = elementCallPattern.findAll(code).filter { !inString[it.range.first] }.toList()
        allCallsEverywhere.filter { !insideScreen(it.range.first) }.forEach { outside ->
            diagnostics += errorAt(lineAt(code, outside.range.first), "عناصر الواجهة يجب أن تكون داخل كتلة screen.")
        }
        val allCalls = allCallsEverywhere.filter { insideScreen(it.range.first) }
        val matchedCallCount = parsedTextMatches.size + parsedButtonMatches.size + parsedControlMatches.size
        if (allCalls.size > matchedCallCount) {
            val parsedOffsets = (parsedTextMatches.map { it.range.first } + parsedButtonMatches.map { it.range.first } + parsedControlMatches.map { it.range.first }).toSet()
            allCalls.firstOrNull { it.range.first !in parsedOffsets }?.let { malformed ->
                diagnostics += errorAt(lineAt(code, malformed.range.first), "صيغة عنصر واجهة غير مكتملة أو غير مدعومة.")
            }
        }

        val knownStates = states.map { it.name }.toSet()
        parsedTextMatches.forEach { match ->
            interpolationPattern.findAll(match.groupValues[1]).forEach { interpolation ->
                val name = interpolation.groupValues[1]
                if (name !in knownStates) diagnostics += errorAt(lineAt(code, match.range.first), "النص يشير إلى حالة غير معرّفة: «$name».")
            }
        }

        val elements = located.sortedBy { it.first }.map { it.second }
        if (elements.isEmpty() && appMatch != null && screenMatch != null) {
            diagnostics += errorAt(lineAt(source, screenMatch.range.first), "الشاشة لا تحتوي على عناصر text أو button مدعومة.")
        }
        // Detect obvious state declarations that were skipped by the supported grammar.
        if (Regex("\\bstate\\s+").containsMatchIn(code) && states.isEmpty() && anyStateLinePattern.containsMatchIn(code).not()) {
            diagnostics += errorAt(1, "لم أستطع تحليل تعريف الحالة. تحقق من صيغة state name: Type = value.")
        }
        return NawaProgram(
            appName = appName,
            screenName = screenName,
            states = states,
            elements = elements,
            diagnostics = diagnostics.distinct(),
            hasAppDeclaration = appMatch != null,
            hasScreenDeclaration = screenMatch != null
        )
    }

    /** Executes a state-only action. Navigation is exposed separately for the preview router. */
    fun execute(action: NawaAction, values: MutableMap<String, NawaValue>): Boolean {
        return when (action) {
            is NawaAction.AdjustNumber -> {
                val old = values[action.stateName] as? NawaValue.NumberValue ?: return false
                val next = old.value.toLong() + action.delta.toLong()
                if (next !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return false
                values[action.stateName] = NawaValue.NumberValue(next.toInt())
                true
            }
            is NawaAction.SetNumber -> {
                if (values[action.stateName] !is NawaValue.NumberValue) return false
                values[action.stateName] = NawaValue.NumberValue(action.value)
                true
            }
            is NawaAction.SetString -> {
                if (values[action.stateName] !is NawaValue.StringValue) return false
                values[action.stateName] = NawaValue.StringValue(action.value)
                true
            }
            is NawaAction.SetBoolean -> {
                if (values[action.stateName] !is NawaValue.BooleanValue) return false
                values[action.stateName] = NawaValue.BooleanValue(action.value)
                true
            }
            is NawaAction.ToggleBoolean -> {
                val old = values[action.stateName] as? NawaValue.BooleanValue ?: return false
                values[action.stateName] = NawaValue.BooleanValue(!old.value)
                true
            }
            is NawaAction.ArithmeticNumber -> {
                val old = (values[action.stateName] as? NawaValue.NumberValue)?.value ?: return false
                if ((action.operator == "/" || action.operator == "%") && action.operand == 0) return false
                val result = when (action.operator) {
                    "*" -> old.toLong() * action.operand.toLong()
                    "/" -> old.toLong() / action.operand.toLong()
                    "%" -> old.toLong() % action.operand.toLong()
                    else -> return false
                }
                if (result !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return false
                values[action.stateName] = NawaValue.NumberValue(result.toInt())
                true
            }
            is NawaAction.Navigate -> false // Navigation is handled by the screen router.
            is NawaAction.Sequence -> action.actions.all { execute(it, values) }
            is NawaAction.Conditional -> {
                val branch = evaluateCondition(action.condition, values) ?: return false
                val selected = if (branch) action.thenAction else action.elseAction
                selected?.let { execute(it, values) } ?: true
            }
            is NawaAction.Repeat -> {
                repeat(action.times) { if (!execute(action.action, values)) return false }
                true
            }
            is NawaAction.While -> {
                var steps = 0
                while (steps < MAX_WHILE_STEPS && evaluateCondition(action.condition, values) == true) {
                    if (!execute(action.action, values)) return false
                    steps++
                }
                evaluateCondition(action.condition, values) == false
            }
        }
    }

    /** Runs an action and returns a destination if this action (or one of its branches) navigates. */
    fun executeWithNavigation(action: NawaAction, values: MutableMap<String, NawaValue>): String? {
        return when (action) {
            is NawaAction.Navigate -> action.targetScreen
            is NawaAction.Sequence -> {
                var destination: String? = null
                for (part in action.actions) {
                    destination = executeWithNavigation(part, values)
                    if (destination != null) break
                }
                destination
            }
            is NawaAction.Conditional -> {
                val condition = evaluateCondition(action.condition, values) ?: return null
                val branch = if (condition) action.thenAction else action.elseAction
                branch?.let { executeWithNavigation(it, values) }
            }
            is NawaAction.Repeat -> {
                repeat(action.times) {
                    val destination = executeWithNavigation(action.action, values)
                    if (destination != null) return destination
                }
                null
            }
            is NawaAction.While -> {
                var steps = 0
                while (steps < MAX_WHILE_STEPS && evaluateCondition(action.condition, values) == true) {
                    val destination = executeWithNavigation(action.action, values)
                    if (destination != null) return destination
                    steps++
                }
                null
            }
            else -> { execute(action, values); null }
        }
    }

    /**
     * Executes parsed actions while producing an inspectable local trace. The runner has a hard
     * total-node budget in addition to the bounded repeat/while semantics; source text is never eval'd.
     */
    fun executeTraced(
        action: NawaAction,
        values: MutableMap<String, NawaValue>,
        maxSteps: Int = 5_000
    ): NawaExecutionResult {
        require(maxSteps in 1..20_000) { "حد خطوات التصحيح يجب أن يكون بين 1 و20000." }
        val events = mutableListOf<NawaTraceEvent>()
        var visited = 0
        var success = true
        var warning: String? = null

        fun event(kind: String, detail: String) {
            events += NawaTraceEvent(events.size + 1, kind, detail.take(300), values.toMap())
        }
        fun run(node: NawaAction): String? {
            if (warning != null) return null
            visited++
            if (visited > maxSteps) {
                warning = "توقف التصحيح عند حد الخطوات الآمن ($maxSteps)."
                success = false
                return null
            }
            val before = values.toMap()
            when (node) {
                is NawaAction.Navigate -> {
                    event("تنقّل", "طلب الانتقال إلى ${node.targetScreen}")
                    return node.targetScreen
                }
                is NawaAction.Sequence -> {
                    for (part in node.actions) {
                        val destination = run(part)
                        if (destination != null || warning != null) return destination
                    }
                    event("تسلسل", "اكتمل تسلسل ${node.actions.size} إجراء")
                }
                is NawaAction.Conditional -> {
                    val condition = evaluateCondition(node.condition, values)
                    if (condition == null) {
                        success = false
                        warning = "تعذّر تقييم شرط أثناء التصحيح."
                        event("خطأ", warning!!)
                        return null
                    }
                    event("شرط", "نتيجة الشرط: $condition")
                    val branch = if (condition) node.thenAction else node.elseAction
                    if (branch != null) return run(branch)
                }
                is NawaAction.Repeat -> {
                    event("حلقة", "بدء repeat(${node.times})")
                    repeat(node.times) {
                        if (warning != null) return null
                        val destination = run(node.action)
                        if (destination != null) return destination
                    }
                    event("حلقة", "انتهى repeat(${node.times})")
                }
                is NawaAction.While -> {
                    var iterations = 0
                    event("حلقة", "بدء while (الحد ${MAX_WHILE_STEPS} تكرار)")
                    while (iterations < MAX_WHILE_STEPS && evaluateCondition(node.condition, values) == true) {
                        if (warning != null) return null
                        val destination = run(node.action)
                        if (destination != null) return destination
                        iterations++
                    }
                    if (evaluateCondition(node.condition, values) == true) {
                        warning = "توقف while عند الحد الآمن $MAX_WHILE_STEPS؛ الشرط ما زال صحيحًا."
                        success = false
                        event("تحذير", warning!!)
                    } else {
                        event("حلقة", "انتهى while بعد $iterations تكرار")
                    }
                }
                else -> {
                    val executed = execute(node, values)
                    if (!executed) {
                        success = false
                        warning = "فشل تنفيذ الإجراء ${node.javaClass.simpleName}: نوع الحالة أو العملية غير صالح."
                        event("خطأ", warning!!)
                        return null
                    }
                    val changed = values.filter { (key, value) -> before[key] != value }
                    val summary = if (changed.isEmpty()) "تم تنفيذ ${node.javaClass.simpleName} دون تغيير حالة" else
                        changed.entries.joinToString("، ") { (key, value) -> "${key}: ${before[key]?.display()} ← ${value.display()}" }
                    event("إجراء", summary)
                }
            }
            return null
        }

        val destination = run(action)
        return NawaExecutionResult(success && warning == null, destination, events.takeLast(250), warning)
    }

    /** Evaluates only typed, parsed conditions; it never invokes arbitrary source code. */
    fun evaluateCondition(condition: NawaCondition, values: Map<String, NawaValue>): Boolean? {
        return when (condition) {
            is NawaCondition.BooleanState -> (values[condition.stateName] as? NawaValue.BooleanValue)?.value
            is NawaCondition.Not -> evaluateCondition(condition.condition, values)?.not()
            is NawaCondition.Logical -> {
                val left = evaluateCondition(condition.left, values) ?: return null
                when (condition.operator) {
                    "&&" -> if (!left) false else evaluateCondition(condition.right, values)
                    "||" -> if (left) true else evaluateCondition(condition.right, values)
                    else -> null
                }
            }
            is NawaCondition.Compare -> {
                val left = values[condition.stateName] ?: return null
                val comparison = when {
                    left is NawaValue.NumberValue && condition.value is NawaValue.NumberValue -> left.value.compareTo(condition.value.value)
                    left is NawaValue.StringValue && condition.value is NawaValue.StringValue -> left.value.compareTo(condition.value.value)
                    left is NawaValue.BooleanValue && condition.value is NawaValue.BooleanValue -> left.value.compareTo(condition.value.value)
                    else -> return null
                }
                when (condition.operator) {
                    "==" -> comparison == 0
                    "!=" -> comparison != 0
                    ">" -> comparison > 0
                    ">=" -> comparison >= 0
                    "<" -> comparison < 0
                    "<=" -> comparison <= 0
                    else -> null
                }
            }
        }
    }

    fun interpolate(text: String, values: Map<String, NawaValue>): String =
        interpolationPattern.replace(text) { match -> values[match.groupValues[1]]?.display() ?: match.value }

    private fun maskComments(source: String): String {
        val chars = source.toCharArray()
        var index = 0
        var inString = false
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (index < chars.size) {
            val current = chars[index]
            val next = chars.getOrNull(index + 1)
            when {
                lineComment -> {
                    if (current == '\n') lineComment = false else if (current != '\r') chars[index] = ' '
                }
                blockComment -> {
                    if (current == '*' && next == '/') {
                        chars[index] = ' '; chars[index + 1] = ' '
                        index++
                        blockComment = false
                    } else if (current != '\n' && current != '\r') chars[index] = ' '
                }
                inString -> {
                    if (escaped) escaped = false
                    else if (current == '\\') escaped = true
                    else if (current == '"') inString = false
                }
                current == '"' -> inString = true
                current == '/' && next == '/' -> {
                    chars[index] = ' '; chars[index + 1] = ' '
                    index++
                    lineComment = true
                }
                current == '/' && next == '*' -> {
                    chars[index] = ' '; chars[index + 1] = ' '
                    index++
                    blockComment = true
                }
            }
            index++
        }
        return String(chars)
    }

    private fun braceDiagnostics(source: String, inString: BooleanArray): List<NawaDiagnostic> {
        val openBraces = mutableListOf<Int>()
        val found = mutableListOf<NawaDiagnostic>()
        source.forEachIndexed { index, char ->
            if (inString[index]) return@forEachIndexed
            when (char) {
                '{' -> openBraces += index
                '}' -> if (openBraces.isEmpty()) {
                    found += errorAt(lineAt(source, index), "يوجد قوس إغلاق } بدون قوس فتح مطابق.")
                } else {
                    openBraces.removeAt(openBraces.lastIndex)
                }
            }
        }
        openBraces.lastOrNull()?.let { offset ->
            found += errorAt(lineAt(source, offset), "يوجد قوس فتح { غير مغلق.")
        }
        return found
    }

    private fun findNextBrace(source: String, start: Int, inString: BooleanArray): Int {
        for (index in start.coerceAtLeast(0) until source.length) {
            if (inString[index]) return -1
            when {
                source[index] == '{' -> return index
                source[index].isWhitespace() -> Unit
                else -> return -1
            }
        }
        return -1
    }

    private fun findMatchingCloseBrace(source: String, open: Int, inString: BooleanArray): Int {
        var depth = 0
        for (index in open until source.length) {
            if (inString[index]) continue
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** True for source offsets inside (but not before) a quoted string literal. */
    private fun stringInteriorMask(source: String): BooleanArray {
        val mask = BooleanArray(source.length)
        var inString = false
        var escaped = false
        for (index in source.indices) {
            val current = source[index]
            if (inString) {
                mask[index] = true
                if (escaped) escaped = false
                else if (current == '\\') escaped = true
                else if (current == '"') inString = false
            } else if (current == '"') {
                inString = true
                mask[index] = true
            }
        }
        return mask
    }

    private fun parseAction(
        body: String,
        states: Map<String, NawaState>,
        functions: Map<String, String> = emptyMap(),
        resolvingFunctions: Set<String> = emptySet()
    ): NawaAction? {
        val clean = body.trim()
        if (clean.isBlank()) return null

        val statements = splitTopLevelStatements(clean)
        if (statements.size > 1) {
            val actions = statements.map { parseAction(it, states, functions, resolvingFunctions) ?: return null }
            return NawaAction.Sequence(actions)
        }
        if (Regex("^if\\b").containsMatchIn(clean)) return parseConditionalAction(clean, states, functions, resolvingFunctions)
        if (Regex("^repeat\\b").containsMatchIn(clean)) return parseRepeatAction(clean, states, functions, resolvingFunctions)
        if (Regex("^while\\b").containsMatchIn(clean)) return parseWhileAction(clean, states, functions, resolvingFunctions)
        functionCallPattern.matchEntire(clean)?.let { call ->
            val name = call.groupValues[1]
            if (name in resolvingFunctions) return null
            val functionBody = functions[name] ?: return null
            return parseAction(functionBody, states, functions, resolvingFunctions + name)
        }
        navigatePattern.matchEntire(clean)?.let { match ->
            val target = unescape(match.groupValues[1])
            return target.takeIf { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }?.let(NawaAction::Navigate)
        }
        adjustPattern.matchEntire(clean)?.let { match ->
            val lhs = match.groupValues[1]
            val rhs = match.groupValues[2]
            val state = states[lhs] ?: return null
            if (lhs != rhs || state.initialValue !is NawaValue.NumberValue) return null
            val raw = match.groupValues[4].toLongOrNull() ?: return null
            if (raw > Int.MAX_VALUE.toLong()) return null
            val delta = if (match.groupValues[3] == "+") raw else -raw
            if (delta !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            return NawaAction.AdjustNumber(lhs, delta.toInt())
        }
        arithmeticPattern.matchEntire(clean)?.let { match ->
            val lhs = match.groupValues[1]
            val rhs = match.groupValues[2]
            val state = states[lhs] ?: return null
            if (lhs != rhs || state.initialValue !is NawaValue.NumberValue) return null
            val operand = match.groupValues[4].toIntOrNull() ?: return null
            val operator = match.groupValues[3]
            if ((operator == "/" || operator == "%") && operand == 0) return null
            return NawaAction.ArithmeticNumber(lhs, operator, operand)
        }
        numberSetPattern.matchEntire(clean)?.let { match ->
            val name = match.groupValues[1]
            val state = states[name] ?: return null
            if (state.initialValue !is NawaValue.NumberValue) return null
            return NawaAction.SetNumber(name, match.groupValues[2].toIntOrNull() ?: return null)
        }
        stringSetPattern.matchEntire(clean)?.let { match ->
            val name = match.groupValues[1]
            val state = states[name] ?: return null
            if (state.initialValue !is NawaValue.StringValue) return null
            return NawaAction.SetString(name, unescape(match.groupValues[2]))
        }
        booleanSetPattern.matchEntire(clean)?.let { match ->
            val name = match.groupValues[1]
            val state = states[name] ?: return null
            if (state.initialValue !is NawaValue.BooleanValue) return null
            return NawaAction.SetBoolean(name, match.groupValues[2].toBoolean())
        }
        togglePattern.matchEntire(clean)?.let { match ->
            val lhs = match.groupValues[1]
            val rhs = match.groupValues[2]
            val state = states[lhs] ?: return null
            if (lhs != rhs || state.initialValue !is NawaValue.BooleanValue) return null
            return NawaAction.ToggleBoolean(lhs)
        }
        return null
    }

    private fun parseConditionalAction(
        body: String, states: Map<String, NawaState>, functions: Map<String, String>, resolvingFunctions: Set<String>
    ): NawaAction? {
        var cursor = 2 // after `if`
        while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        if (body.getOrNull(cursor) != '(') return null
        val conditionClose = findMatchingDelimiter(body, cursor, '(', ')') ?: return null
        val condition = parseCondition(body.substring(cursor + 1, conditionClose), states) ?: return null
        cursor = conditionClose + 1
        while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        if (body.getOrNull(cursor) != '{') return null
        val thenClose = findMatchingDelimiter(body, cursor, '{', '}') ?: return null
        val thenAction = parseAction(body.substring(cursor + 1, thenClose), states, functions, resolvingFunctions) ?: return null
        cursor = thenClose + 1
        while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        var elseAction: NawaAction? = null
        if (cursor < body.length) {
            if (!body.startsWith("else", cursor)) return null
            cursor += 4
            while (cursor < body.length && body[cursor].isWhitespace()) cursor++
            if (body.getOrNull(cursor) != '{') return null
            val elseClose = findMatchingDelimiter(body, cursor, '{', '}') ?: return null
            elseAction = parseAction(body.substring(cursor + 1, elseClose), states, functions, resolvingFunctions) ?: return null
            cursor = elseClose + 1
            while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        }
        if (cursor != body.length) return null
        return NawaAction.Conditional(condition, thenAction, elseAction)
    }

    private fun parseRepeatAction(
        body: String, states: Map<String, NawaState>, functions: Map<String, String>, resolvingFunctions: Set<String>
    ): NawaAction? {
        val header = repeatHeaderPattern.find(body) ?: return null
        val times = header.groupValues[1].toIntOrNull() ?: return null
        // Hard limit prevents accidental freezes in the on-device interpreter.
        if (times !in 0..1_000) return null
        var cursor = header.range.last + 1
        while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        if (body.getOrNull(cursor) != '{') return null
        val close = findMatchingDelimiter(body, cursor, '{', '}') ?: return null
        if (body.substring(close + 1).isNotBlank()) return null
        val child = parseAction(body.substring(cursor + 1, close), states, functions, resolvingFunctions) ?: return null
        return NawaAction.Repeat(times, child)
    }

    private fun parseWhileAction(
        body: String, states: Map<String, NawaState>, functions: Map<String, String>, resolvingFunctions: Set<String>
    ): NawaAction? {
        var cursor = "while".length
        while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        if (body.getOrNull(cursor) != '(') return null
        val conditionClose = findMatchingDelimiter(body, cursor, '(', ')') ?: return null
        val condition = parseCondition(body.substring(cursor + 1, conditionClose), states) ?: return null
        cursor = conditionClose + 1
        while (cursor < body.length && body[cursor].isWhitespace()) cursor++
        if (body.getOrNull(cursor) != '{') return null
        val close = findMatchingDelimiter(body, cursor, '{', '}') ?: return null
        if (body.substring(close + 1).isNotBlank()) return null
        val child = parseAction(body.substring(cursor + 1, close), states, functions, resolvingFunctions) ?: return null
        return NawaAction.While(condition, child)
    }

    private fun parseCondition(source: String, states: Map<String, NawaState>): NawaCondition? {
        var text = source.trim()
        if (text.isEmpty()) return null
        while (text.startsWith('(') && text.endsWith(')')) {
            val close = findMatchingDelimiter(text, 0, '(', ')') ?: break
            if (close != text.lastIndex) break
            text = text.substring(1, text.length - 1).trim()
        }
        findTopLevelOperator(text, "||")?.let { index ->
            val left = parseCondition(text.substring(0, index), states) ?: return null
            val right = parseCondition(text.substring(index + 2), states) ?: return null
            return NawaCondition.Logical(left, "||", right)
        }
        findTopLevelOperator(text, "&&")?.let { index ->
            val left = parseCondition(text.substring(0, index), states) ?: return null
            val right = parseCondition(text.substring(index + 2), states) ?: return null
            return NawaCondition.Logical(left, "&&", right)
        }
        if (text.startsWith('!') && !text.startsWith("!=")) {
            return parseCondition(text.substring(1), states)?.let(NawaCondition::Not)
        }
        val compare = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*(==|!=|<=|>=|<|>)\s*("(?:\\.|[^"\\])*"|-?\d+|true|false)$""").matchEntire(text)
        if (compare != null) {
            val name = compare.groupValues[1]
            val operator = compare.groupValues[2]
            val raw = compare.groupValues[3]
            val state = states[name] ?: return null
            val value = when (state.initialValue) {
                is NawaValue.NumberValue -> raw.toIntOrNull()?.let(NawaValue::NumberValue)
                is NawaValue.StringValue -> if (raw.startsWith('"') && raw.endsWith('"')) NawaValue.StringValue(unescape(raw.substring(1, raw.length - 1))) else null
                is NawaValue.BooleanValue -> raw.toBooleanStrictOrNull()?.let(NawaValue::BooleanValue)
            } ?: return null
            if (operator !in setOf("==", "!=") && state.initialValue !is NawaValue.NumberValue) return null
            return NawaCondition.Compare(name, operator, value)
        }
        val stateName = Regex("^[A-Za-z_][A-Za-z0-9_]*$").matchEntire(text)?.value ?: return null
        if (states[stateName]?.initialValue !is NawaValue.BooleanValue) return null
        return NawaCondition.BooleanState(stateName)
    }

    private fun splitTopLevelStatements(source: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var braces = 0
        var parens = 0
        var inString = false
        var escaped = false
        source.forEachIndexed { index, c ->
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                return@forEachIndexed
            }
            when (c) {
                '"' -> inString = true
                '{' -> braces++
                '}' -> braces--
                '(' -> parens++
                ')' -> parens--
                '\n', ';' -> if (braces == 0 && parens == 0) {
                    val part = source.substring(start, index).trim()
                    if (part.isNotEmpty()) result += part
                    start = index + 1
                }
            }
        }
        val tail = source.substring(start).trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }

    private fun findMatchingDelimiter(source: String, openIndex: Int, opening: Char, closing: Char): Int? {
        if (source.getOrNull(openIndex) != opening) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in openIndex until source.length) {
            val c = source[index]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                opening -> depth++
                closing -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun findTopLevelOperator(source: String, operator: String): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        var index = 0
        while (index <= source.length - operator.length) {
            val c = source[index]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                index++
                continue
            }
            when (c) {
                '"' -> inString = true
                '(' -> depth++
                ')' -> depth--
            }
            if (depth == 0 && source.startsWith(operator, index)) return index
            index++
        }
        return null
    }

    private fun errorAt(line: Int, message: String) =
        NawaDiagnostic(line, NawaDiagnostic.Severity.ERROR, message)

    private fun warningAt(line: Int, message: String) =
        NawaDiagnostic(line, NawaDiagnostic.Severity.WARNING, message)

    private fun lineAt(source: String, offset: Int): Int =
        source.take(offset.coerceAtLeast(0)).count { it == '\n' } + 1

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\r")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
