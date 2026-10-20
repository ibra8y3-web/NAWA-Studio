package com.nawa.studio.core

/** Safe source edits for the currently supported NawaScript screen block. */
object NawaSourceEditor {
    private val screenDeclaration = Regex("\\bscreen\\s+([A-Za-z_][A-Za-z0-9_]*)")

    /** Inserts a single NawaScript element before the matching screen closing brace. */
    fun insertElement(source: String, element: String, screenName: String? = null): String {
        val cleanElement = element.trim()
        require(cleanElement.isNotEmpty()) { "العنصر الجديد فارغ." }
        require(cleanElement.none { it == '\u0000' }) { "العنصر يحتوي محرفًا غير صالح." }
        val structural = maskCommentsAndStrings(source)
        val declaration = screenDeclaration.findAll(structural)
            .firstOrNull { screenName == null || it.groupValues[1] == screenName }
            ?: throw IllegalArgumentException(if (screenName == null) "لم أجد تعريف screen لإضافة العنصر إليه." else "لم أجد الشاشة «$screenName» لإضافة العنصر إليها.")
        val openBrace = structural.indexOf('{', declaration.range.last + 1)
        require(openBrace >= 0) { "تعريف الشاشة لا يحتوي على قوس فتح." }

        var depth = 0
        var closeBrace = -1
        for (index in openBrace until structural.length) {
            when (structural[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        closeBrace = index
                        break
                    }
                }
            }
        }
        require(closeBrace >= 0) { "أقواس الشاشة غير مكتملة؛ أصلح الكود قبل إضافة عنصر." }

        val lineStart = source.lastIndexOf('\n', (closeBrace - 1).coerceAtLeast(0)) + 1
        val prefixOnClosingLine = source.substring(lineStart, closeBrace)
        val closingIndent = prefixOnClosingLine.takeWhile { it == ' ' || it == '\t' }
        val elementIndent = closingIndent + "    "
        val multilineElement = cleanElement.lines().joinToString("\n$elementIndent")

        return if (prefixOnClosingLine.isBlank()) {
            source.substring(0, lineStart) +
                elementIndent + multilineElement + "\n" +
                source.substring(lineStart)
        } else {
            // Handles compact one-line screens as well, preserving all existing source text.
            source.substring(0, closeBrace).trimEnd() + "\n" +
                elementIndent + multilineElement + "\n" +
                closingIndent + source.substring(closeBrace)
        }
    }


    /** Replaces a parsed source range without reformatting unrelated code. */
    fun replaceRange(source: String, start: Int, endExclusive: Int, replacement: String): String {
        require(start >= 0 && endExclusive > start && endExclusive <= source.length) {
            "موضع العنصر لم يعد صالحًا. حدّث المعاينة ثم أعد المحاولة."
        }
        require(replacement.isNotBlank() && replacement.none { it == '\u0000' }) { "النص البديل غير صالح." }
        return source.replaceRange(start, endExclusive, replacement)
    }

    /** Removes the containing line when it has no other source text, otherwise only the element. */
    fun removeElement(source: String, start: Int, endExclusive: Int): String {
        require(start >= 0 && endExclusive > start && endExclusive <= source.length) {
            "موضع العنصر لم يعد صالحًا. حدّث المعاينة ثم أعد المحاولة."
        }
        val lineStart = source.lastIndexOf('\n', (start - 1).coerceAtLeast(0)) + 1
        val nextNewline = source.indexOf('\n', endExclusive)
        val lineEnd = if (nextNewline < 0) source.length else nextNewline + 1
        val before = source.substring(lineStart, start)
        val after = source.substring(endExclusive, lineEnd)
        return if (before.isBlank() && after.isBlank()) {
            source.removeRange(lineStart, lineEnd)
        } else {
            source.removeRange(start, endExclusive)
        }
    }

    /** Swaps two parsed element expressions while preserving their surrounding whitespace. */
    fun swapElements(source: String, firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): String {
        require(firstStart >= 0 && firstEnd > firstStart && secondStart >= 0 && secondEnd > secondStart &&
            firstEnd <= source.length && secondEnd <= source.length) { "مواضع العناصر غير صالحة." }
        require(firstEnd <= secondStart || secondEnd <= firstStart) { "لا يمكن إعادة ترتيب عنصرين متداخلين." }
        val aStart = minOf(firstStart, secondStart)
        val aEnd = if (firstStart < secondStart) firstEnd else secondEnd
        val bStart = maxOf(firstStart, secondStart)
        val bEnd = if (firstStart < secondStart) secondEnd else firstEnd
        val firstText = source.substring(aStart, aEnd)
        val middle = source.substring(aEnd, bStart)
        val secondText = source.substring(bStart, bEnd)
        return source.substring(0, aStart) + secondText + middle + firstText + source.substring(bEnd)
    }

    /** Formats indentation for the NawaScript DSL only; it does not alter string or comment contents. */
    fun formatNawa(source: String, indentSize: Int = 4): String {
        require(indentSize in 2..8) { "حجم المسافة البادئة يجب أن يكون بين 2 و8." }
        val output = StringBuilder()
        var depth = 0
        var inBlockComment = false
        source.replace("\r\n", "\n").replace('\r', '\n').lines().forEachIndexed { lineIndex, raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                if (lineIndex != source.replace("\r\n", "\n").replace('\r', '\n').lines().lastIndex) output.append('\n')
                return@forEachIndexed
            }
            val closings = leadingStructuralClosings(trimmed, inBlockComment)
            val visibleDepth = (depth - closings).coerceAtLeast(0)
            output.append(" ".repeat(visibleDepth * indentSize)).append(trimmed.trimEnd()).append('\n')
            val counts = structuralBraceDelta(trimmed, inBlockComment)
            depth = (depth + counts.first - counts.second).coerceAtLeast(0)
            inBlockComment = counts.third
        }
        return output.toString().trimEnd() + "\n"
    }

    private fun leadingStructuralClosings(line: String, initialBlockComment: Boolean): Int {
        var inString = false; var escaped = false; var inLineComment = false; var inBlockComment = initialBlockComment
        var count = 0; var atStart = true; var index = 0
        while (index < line.length) {
            val c = line[index]; val next = line.getOrNull(index + 1)
            if (inLineComment) break
            if (inBlockComment) { if (c == '*' && next == '/') { inBlockComment = false; index += 2; continue }; index++; continue }
            if (inString) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false; atStart = false; index++; continue }
            when {
                c == '/' && next == '*' -> { inBlockComment = true; index += 2; continue }
                c == '/' && next == '/' -> break
                c == '"' -> { inString = true; atStart = false }
                c == '}' && atStart -> count++
                c.isWhitespace() && atStart -> Unit
                else -> atStart = false
            }
            index++
        }
        return count
    }

    private fun structuralBraceDelta(line: String, initialBlockComment: Boolean): Triple<Int, Int, Boolean> {
        var opens = 0; var closes = 0; var inString = false; var escaped = false
        var inLineComment = false; var inBlockComment = initialBlockComment; var index = 0
        while (index < line.length) {
            val c = line[index]; val next = line.getOrNull(index + 1)
            when {
                inLineComment -> break
                inBlockComment -> { if (c == '*' && next == '/') { inBlockComment = false; index += 2; continue } }
                inString -> { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false }
                c == '/' && next == '*' -> { inBlockComment = true; index += 2; continue }
                c == '/' && next == '/' -> { inLineComment = true }
                c == '"' -> inString = true
                c == '{' -> opens++
                c == '}' -> closes++
            }
            index++
        }
        return Triple(opens, closes, inBlockComment)
    }

    fun actionSource(action: NawaAction?): String = when (action) {
        null -> ""
        is NawaAction.AdjustNumber -> if (action.delta >= 0) {
            "${action.stateName} = ${action.stateName} + ${action.delta}"
        } else {
            "${action.stateName} = ${action.stateName} - ${-action.delta.toLong()}"
        }
        is NawaAction.SetNumber -> "${action.stateName} = ${action.value}"
        is NawaAction.SetString -> "${action.stateName} = ${quote(action.value)}"
        is NawaAction.SetBoolean -> "${action.stateName} = ${action.value}"
        is NawaAction.ToggleBoolean -> "${action.stateName} = !${action.stateName}"
        is NawaAction.ArithmeticNumber -> "${action.stateName} = ${action.stateName} ${action.operator} ${action.operand}"
        is NawaAction.Navigate -> "navigate(${quote(action.targetScreen)})"
        is NawaAction.Sequence -> action.actions.joinToString("\n") { actionSource(it) }
        is NawaAction.Conditional -> buildString {
            append("if (").append(conditionSource(action.condition)).append(") {\n")
            append(indent(actionSource(action.thenAction))).append("\n}")
            action.elseAction?.let { append(" else {\n").append(indent(actionSource(it))).append("\n}") }
        }
        is NawaAction.Repeat -> "repeat(${action.times}) {\n${indent(actionSource(action.action))}\n}"
        is NawaAction.While -> "while (${conditionSource(action.condition)}) {\n${indent(actionSource(action.action))}\n}"
    }

    /** Rewrites one parsed visual element while preserving its semantics and optional absolute position. */
    fun elementSource(element: NawaElement, x: Int? = element.x, y: Int? = element.y): String {
        require((x == null) == (y == null)) { "أدخل إحداثيي X وY معًا." }
        if (x != null && y != null) require(x in 0..2048 && y in 0..4096) {
            "النطاق المسموح: X من 0 إلى 2048 وY من 0 إلى 4096."
        }
        val suffix = if (x == null || y == null) "" else ".at($x, $y)"
        return when (element) {
            is NawaElement.Text -> "text(${quote(element.value)})$suffix"
            is NawaElement.Button -> {
                val action = actionSource(element.action).trim()
                if (action.isBlank()) "button(${quote(element.label)})$suffix"
                else "button(${quote(element.label)}) { $action }$suffix"
            }
            is NawaElement.Control -> {
                val range = if (element.kind == NawaControlKind.SLIDER) ", ${element.minimum}, ${element.maximum}" else ""
                "${element.kind.keyword}(${quote(element.label)}, ${element.stateName}$range)$suffix"
            }
        }
    }

    private fun conditionSource(condition: NawaCondition): String = when (condition) {
        is NawaCondition.BooleanState -> condition.stateName
        is NawaCondition.Not -> "!(${conditionSource(condition.condition)})"
        is NawaCondition.Compare -> "${condition.stateName} ${condition.operator} ${when (val v = condition.value) {
            is NawaValue.NumberValue -> v.value.toString()
            is NawaValue.StringValue -> quote(v.value)
            is NawaValue.BooleanValue -> v.value.toString()
        }}"
        is NawaCondition.Logical -> "(${conditionSource(condition.left)}) ${condition.operator} (${conditionSource(condition.right)})"
    }

    private fun indent(source: String): String = source.lines().joinToString("\n") { "    $it" }

    fun quote(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "\""

    private fun maskCommentsAndStrings(source: String): String {
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
                    if (current != '\n' && current != '\r') chars[index] = ' '
                    if (escaped) escaped = false
                    else if (current == '\\') escaped = true
                    else if (current == '"') inString = false
                }
                current == '"' -> {
                    chars[index] = ' '
                    inString = true
                }
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
}
