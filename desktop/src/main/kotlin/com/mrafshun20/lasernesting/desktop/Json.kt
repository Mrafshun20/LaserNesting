package com.mrafshun20.lasernesting.desktop

/**
 * Minimal JSON value model, parser and writer — just enough for the
 * desktop project file (parts + settings). No external dependency.
 * The parser is a small recursive-descent parser; it is tolerant of
 * whitespace and reports failure as null instead of throwing.
 */
sealed interface JsonValue

data class JsonObject(val map: Map<String, JsonValue>) : JsonValue {
    fun string(key: String): String? = (map[key] as? JsonString)?.value
    fun double(key: String): Double? = (map[key] as? JsonNumber)?.value
    fun int(key: String): Int? = double(key)?.toInt()
    fun bool(key: String): Boolean? = (map[key] as? JsonBool)?.value
    fun obj(key: String): JsonObject? = map[key] as? JsonObject
    fun array(key: String): JsonArray? = map[key] as? JsonArray
}

data class JsonArray(val items: List<JsonValue>) : JsonValue
data class JsonString(val value: String) : JsonValue
data class JsonNumber(val value: Double) : JsonValue
data class JsonBool(val value: Boolean) : JsonValue
object JsonNull : JsonValue

object Json {

    fun parse(text: String): JsonValue? {
        val parser = Parser(text)
        val value = parser.parseValue() ?: return null
        parser.skipWhitespace()
        if (!parser.atEnd()) return null
        return value
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean {
            skipWhitespace()
            return pos >= text.length
        }

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue? {
            skipWhitespace()
            if (pos >= text.length) return null
            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()?.let { JsonString(it) }
                't' -> if (text.startsWith("true", pos)) {
                    pos += 4; JsonBool(true)
                } else null
                'f' -> if (text.startsWith("false", pos)) {
                    pos += 5; JsonBool(false)
                } else null
                'n' -> if (text.startsWith("null", pos)) {
                    pos += 4; JsonNull
                } else null
                '-', in '0'..'9' -> parseNumber()
                else -> null
            }
        }

        private fun parseObject(): JsonObject? {
            pos++ // {
            val map = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return JsonObject(map)
            }
            while (true) {
                skipWhitespace()
                val key = parseString() ?: return null
                skipWhitespace()
                if (pos >= text.length || text[pos] != ':') return null
                pos++
                val value = parseValue() ?: return null
                map[key] = value
                skipWhitespace()
                if (pos >= text.length) return null
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonObject(map)
                    }
                    else -> return null
                }
            }
        }

        private fun parseArray(): JsonArray? {
            pos++ // [
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return JsonArray(items)
            }
            while (true) {
                val value = parseValue() ?: return null
                items.add(value)
                skipWhitespace()
                if (pos >= text.length) return null
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonArray(items)
                    }
                    else -> return null
                }
            }
        }

        private fun parseString(): String? {
            if (pos >= text.length || text[pos] != '"') return null
            pos++
            val sb = StringBuilder()
            while (pos < text.length) {
                val c = text[pos]
                when (c) {
                    '"' -> {
                        pos++
                        return sb.toString()
                    }
                    '\\' -> {
                        pos++
                        if (pos >= text.length) return null
                        when (val e = text[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 >= text.length) return null
                                val hex = text.substring(pos + 1, pos + 5)
                                val code = hex.toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> return null
                        }
                        pos++
                    }
                    else -> {
                        sb.append(c)
                        pos++
                    }
                }
            }
            return null
        }

        private fun parseNumber(): JsonNumber? {
            val start = pos
            if (pos < text.length && text[pos] == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' ||
                        text[pos] == 'e' || text[pos] == 'E' ||
                        text[pos] == '+' || text[pos] == '-')
            ) {
                pos++
            }
            val num = text.substring(start, pos).toDoubleOrNull() ?: return null
            return JsonNumber(num)
        }
    }

    // ---- Writer ----

    fun stringify(value: JsonValue): String {
        val sb = StringBuilder()
        write(value, sb)
        return sb.toString()
    }

    private fun write(value: JsonValue, sb: StringBuilder) {
        when (value) {
            is JsonObject -> {
                sb.append('{')
                value.map.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    write(JsonString(k), sb)
                    sb.append(':')
                    write(v, sb)
                }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    write(item, sb)
                }
                sb.append(']')
            }
            is JsonString -> {
                sb.append('"')
                for (c in value.value) {
                    when (c) {
                        '"' -> sb.append("\\\"")
                        '\\' -> sb.append("\\\\")
                        '\n' -> sb.append("\\n")
                        '\r' -> sb.append("\\r")
                        '\t' -> sb.append("\\t")
                        '\b' -> sb.append("\\b")
                        '' -> sb.append("\\f")
                        else -> {
                            if (c.code < 0x20) {
                                sb.append(String.format("\\u%04x", c.code))
                            } else {
                                sb.append(c)
                            }
                        }
                    }
                }
                sb.append('"')
            }
            is JsonNumber -> {
                val v = value.value
                if (v == v.toLong().toDouble()) sb.append(v.toLong()) else sb.append(v)
            }
            is JsonBool -> sb.append(value.value)
            is JsonNull -> sb.append("null")
        }
    }

    // ---- Convenience builders ----

    fun obj(vararg pairs: Pair<String, JsonValue>): JsonObject =
        JsonObject(linkedMapOf(*pairs))

    fun arr(vararg items: JsonValue): JsonArray = JsonArray(items.toList())

    fun str(value: String): JsonString = JsonString(value)

    fun num(value: Double): JsonNumber = JsonNumber(value)

    fun num(value: Int): JsonNumber = JsonNumber(value.toDouble())

    fun num(value: Long): JsonNumber = JsonNumber(value.toDouble())

    fun bool(value: Boolean): JsonBool = JsonBool(value)
}
