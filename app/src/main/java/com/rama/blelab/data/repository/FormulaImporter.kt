package com.rama.blelab.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rama.blelab.domain.model.FormulaDataType
import com.rama.blelab.domain.model.ParsingFormula
import com.rama.blelab.domain.repository.Macro
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ParserImportResult(
    val formulas: List<ParsingFormula>,
    val parserName: String? = null,
    val commandHex: String? = null,
    val macroName: String? = null,
    val macroCommand: String? = null
)

data class MacroImportDefinition(
    val name: String? = null,
    val command: String? = null,
    val formulas: List<ParsingFormula> = emptyList()
)

class FormulaImporter(private val context: Context) {
    companion object {
        fun parseJsonMacroDefinition(jsonText: String): MacroImportDefinition {
            val trimmed = jsonText.trim()
            return try {
                if (trimmed.startsWith("{")) {
                    jsonObjectToMacroDefinition(JSONObject(trimmed))
                } else {
                    MacroImportDefinition(formulas = parseJsonFormulasStatic(trimmed))
                }
            } catch (e: Exception) {
                safeLogError("JSON macro definition parse error", e)
                MacroImportDefinition()
            }
        }

        fun parseJsonMacroDefinitions(jsonText: String): List<MacroImportDefinition> {
            val trimmed = jsonText.trim()
            return try {
                when {
                    trimmed.startsWith("[") -> jsonArrayToMacroDefinitions(JSONArray(trimmed))
                    trimmed.startsWith("{") -> {
                        val obj = JSONObject(trimmed)
                        val nested = firstArrayFromObject(obj, "macros", "micros", "quickCommands", "quick_commands", "commands")
                        if (nested != null) {
                            jsonArrayToMacroDefinitions(nested)
                        } else {
                            listOf(jsonObjectToMacroDefinition(obj))
                        }
                    }
                    else -> emptyList()
                }.filter { !it.name.isNullOrBlank() && !it.command.isNullOrBlank() }
            } catch (e: Exception) {
                safeLogError("JSON macro definitions parse error", e)
                emptyList()
            }
        }

        private fun jsonArrayToMacroDefinitions(array: JSONArray): List<MacroImportDefinition> {
            val list = mutableListOf<MacroImportDefinition>()
            for (i in 0 until array.length()) {
                when (val value = array.opt(i)) {
                    is JSONObject -> {
                        if (looksLikeMacroDefinition(value)) {
                            list.add(jsonObjectToMacroDefinition(value))
                        } else {
                            firstArrayFromObject(value, "macros", "micros", "quickCommands", "quick_commands", "commands")
                                ?.let { list.addAll(jsonArrayToMacroDefinitions(it)) }
                        }
                    }
                    is JSONArray -> list.addAll(jsonArrayToMacroDefinitions(value))
                }
            }
            return list
        }

        private fun jsonObjectToMacroDefinition(obj: JSONObject): MacroImportDefinition {
            val formulas = jsonObjectToFormulasStatic(obj)
            return MacroImportDefinition(
                name = firstStringFromObject(obj, "name", "macroName", "microName", "label", "title").ifBlank { null },
                command = firstStringFromObject(obj, "command", "commandHex", "sendCommandHex", "send_command_hex", "macroCommand", "value").ifBlank { null },
                formulas = formulas
            )
        }

        private fun looksLikeMacroDefinition(obj: JSONObject): Boolean {
            val hasName = firstStringFromObject(obj, "name", "macroName", "microName", "label", "title").isNotBlank()
            val hasCommand = firstStringFromObject(obj, "command", "commandHex", "sendCommandHex", "send_command_hex", "macroCommand", "value").isNotBlank()
            return hasName && hasCommand
        }

        private fun firstArrayFromObject(obj: JSONObject, vararg keys: String): JSONArray? {
            keys.forEach { key ->
                val value = obj.opt(key)
                if (value is JSONArray) return value
            }
            return null
        }

        private fun parseJsonFormulasStatic(jsonText: String): List<ParsingFormula> {
            val list = mutableListOf<ParsingFormula>()
            try {
                val trimmed = jsonText.trim()
                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    list.addAll(jsonArrayToFormulasStatic(array))
                } else {
                    val obj = JSONObject(trimmed)
                    list.addAll(jsonObjectToFormulasStatic(obj))
                }
            } catch (e: Exception) {
                safeLogError("JSON parse error", e)
            }
            return list
        }

        private fun safeLogError(message: String, error: Throwable) {
            try {
                android.util.Log.e("FORMULA_IMPORT", message, error)
            } catch (_: RuntimeException) {
                println("FORMULA_IMPORT: $message - ${error.message}")
            }
        }

        private fun jsonArrayToFormulasStatic(array: JSONArray): List<ParsingFormula> {
            val list = mutableListOf<ParsingFormula>()
            for (i in 0 until array.length()) {
                when (val value = array.opt(i)) {
                    is JSONObject -> list.addAll(jsonObjectToFormulasStatic(value))
                    is JSONArray -> list.addAll(jsonArrayToFormulasStatic(value))
                }
            }
            return list
        }

        private fun jsonObjectToFormulasStatic(obj: JSONObject): List<ParsingFormula> {
            val nestedKeys = listOf(
                "formulas",
                "parsers",
                "responseParsers",
                "response_parsers",
                "response_parser",
                "fields",
                "values",
                "measurements",
                "items"
            )
            val nested = mutableListOf<ParsingFormula>()

            nestedKeys.forEach { key ->
                when (val value = obj.opt(key)) {
                    is JSONArray -> nested.addAll(jsonArrayToFormulasStatic(value))
                    is JSONObject -> nested.addAll(jsonObjectToFormulasStatic(value))
                }
            }

            return if (looksLikeFormulaStatic(obj)) nested + jsonObjectToFormulaStatic(obj) else nested
        }

        private fun looksLikeFormulaStatic(obj: JSONObject): Boolean {
            if (looksLikeMacroDefinition(obj)) return false

            val formulaKeys = listOf(
                "name",
                "label",
                "field",
                "pattern",
                "offset",
                "byteOffset",
                "byte_offset",
                "start",
                "length",
                "size",
                "bytes",
                "dataType",
                "data_type",
                "type",
                "multiplier",
                "scale",
                "unit",
                "units"
            )
            return formulaKeys.any { obj.has(it) }
        }

        private fun jsonObjectToFormulaStatic(obj: JSONObject): ParsingFormula {
            return ParsingFormula(
                name = firstStringFromObject(obj, "name", "label", "field", "key", "id").ifBlank { "Unknown" },
                pattern = if (obj.has("pattern") && !obj.isNull("pattern")) obj.getString("pattern") else null,
                offset = firstIntFromObject(obj, "offset", "byteOffset", "byte_offset", "start"),
                length = firstIntFromObject(obj, "length", "size", "bytes"),
                dataType = parseDataTypeStatic(firstStringFromObject(obj, "dataType", "data_type", "type").ifBlank { "HEX" }),
                multiplier = formulaMultiplierStatic(obj),
                unit = firstStringFromObject(obj, "unit", "units")
            )
        }

        private fun formulaMultiplierStatic(obj: JSONObject): Double {
            val explicitMultiplier = firstNullableDoubleFromObject(obj, "multiplier", "scale")
            if (explicitMultiplier != null) return explicitMultiplier

            val divisor = firstNullableDoubleFromObject(obj, "divisor", "divideBy", "divide_by")
            if (divisor != null && divisor != 0.0) return 1.0 / divisor

            val decimalPlaces = firstNullableIntFromObject(obj, "decimalPlaces", "decimal_places", "decimals")
            if (decimalPlaces != null && decimalPlaces > 0) {
                return 1.0 / Math.pow(10.0, decimalPlaces.toDouble())
            }

            return 1.0
        }

        private fun firstStringFromObject(obj: JSONObject, vararg keys: String): String {
            keys.forEach { key ->
                if (obj.has(key) && !obj.isNull(key)) return obj.optString(key, "")
            }
            return ""
        }

        private fun firstIntFromObject(obj: JSONObject, vararg keys: String): Int {
            return firstNullableIntFromObject(obj, *keys) ?: 0
        }

        private fun firstNullableIntFromObject(obj: JSONObject, vararg keys: String): Int? {
            keys.forEach { key ->
                if (obj.has(key) && !obj.isNull(key)) {
                    when (val value = obj.opt(key)) {
                        is Number -> return value.toInt()
                        is String -> value.toIntOrNull()?.let { return it }
                    }
                }
            }
            return null
        }

        private fun firstNullableDoubleFromObject(obj: JSONObject, vararg keys: String): Double? {
            keys.forEach { key ->
                if (obj.has(key) && !obj.isNull(key)) {
                    when (val value = obj.opt(key)) {
                        is Number -> return value.toDouble()
                        is String -> value.toDoubleOrNull()?.let { return it }
                    }
                }
            }
            return null
        }

        private fun parseDataTypeStatic(typeStr: String): FormulaDataType {
            val normalized = typeStr.uppercase().replace(" ", "").replace("_", "")
            return when (normalized) {
                "INT8" -> FormulaDataType.INT_8
                "UINT8" -> FormulaDataType.UINT_8
                "INT16", "INT16BE" -> FormulaDataType.INT_16_BE
                "INT16LE" -> FormulaDataType.INT_16_LE
                "UINT16", "UINT16BE" -> FormulaDataType.UINT_16_BE
                "UINT16LE" -> FormulaDataType.UINT_16_LE
                "INT32", "INT32BE" -> FormulaDataType.INT_32_BE
                "INT32LE" -> FormulaDataType.INT_32_LE
                "UINT32", "UINT32BE" -> FormulaDataType.UINT_32_BE
                "UINT32LE" -> FormulaDataType.UINT_32_LE
                "FLOAT", "FLOAT32", "FLOAT32BE" -> FormulaDataType.FLOAT_32_BE
                "FLOAT32LE" -> FormulaDataType.FLOAT_32_LE
                "STRING" -> FormulaDataType.STRING
                "HEX" -> FormulaDataType.HEX
                else -> {
                    try { FormulaDataType.valueOf(typeStr.uppercase()) } catch (_: Exception) { FormulaDataType.HEX }
                }
            }
        }
    }

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) { }
    }

    suspend fun importFromUri(uri: Uri, mimeType: String?): List<ParsingFormula> = withContext(Dispatchers.IO) {
        val textContent = StringBuilder()
        android.util.Log.d("FORMULA_IMPORT", "Starting import from URI: $uri")

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getDisplayName(uri).lowercase().ifBlank {
                    uri.path?.lowercase() ?: ""
                }
                val actualMime = (mimeType ?: context.contentResolver.getType(uri)).orEmpty().lowercase()
                android.util.Log.d("FORMULA_IMPORT", "File name: $fileName, MimeType: $actualMime")

                when {
                    isJsonFile(fileName, actualMime) -> {
                        val jsonText = BufferedReader(InputStreamReader(inputStream)).readText()
                        android.util.Log.d("FORMULA_IMPORT", "Parsing as JSON")
                        return@withContext parseJsonFormulas(jsonText)
                    }
                    isCsvFile(fileName, actualMime) -> {
                        android.util.Log.d("FORMULA_IMPORT", "Parsing as CSV")
                        return@withContext parseCsvFormulas(BufferedReader(InputStreamReader(inputStream)).readText())
                    }
                    isPdfFile(fileName, actualMime) -> {
                        android.util.Log.d("FORMULA_IMPORT", "Extracting text from PDF")
                        val document = PDDocument.load(inputStream)
                        val stripper = PDFTextStripper()
                        textContent.append(stripper.getText(document))
                        document.close()
                    }
                    isDocxFile(fileName, actualMime) -> {
                        android.util.Log.d("FORMULA_IMPORT", "Extracting text from DOCX")
                        textContent.append(extractDocxText(inputStream))
                    }
                    isImageFile(fileName, actualMime) -> {
                        android.util.Log.d("FORMULA_IMPORT", "Extracting text from Image via ML Kit")
                        val image = InputImage.fromFilePath(context, uri)
                        val result = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await()
                        textContent.append(result.text)
                    }
                    isTextFile(fileName, actualMime) -> {
                        android.util.Log.d("FORMULA_IMPORT", "Parsing as TXT")
                        return@withContext parseTextFormulas(BufferedReader(InputStreamReader(inputStream)).readText())
                    }
                    else -> {
                        android.util.Log.d("FORMULA_IMPORT", "Attempting generic text read")
                        val genericText = BufferedReader(InputStreamReader(inputStream)).readText()
                        return@withContext parseTextFormulas(genericText)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FORMULA_IMPORT", "Import error", e)
        }

        val extracted = parseTextFormulas(textContent.toString())
        android.util.Log.d("FORMULA_IMPORT", "Extracted ${extracted.size} formulas from text content")
        return@withContext extracted
    }

    suspend fun importJsonParserFromUri(uri: Uri, mimeType: String?): ParserImportResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getDisplayName(uri).lowercase().ifBlank {
                    uri.path?.lowercase() ?: ""
                }
                val actualMime = (mimeType ?: context.contentResolver.getType(uri)).orEmpty().lowercase()

                if (!isJsonFile(fileName, actualMime)) {
                    return@withContext ParserImportResult(emptyList())
                }

                return@withContext parseJsonParserImport(
                    BufferedReader(InputStreamReader(inputStream)).readText()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FORMULA_IMPORT", "JSON parser import error", e)
        }

        ParserImportResult(emptyList())
    }

    suspend fun importJsonMacrosFromUri(uri: Uri, mimeType: String?): List<Macro> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getDisplayName(uri).lowercase().ifBlank {
                    uri.path?.lowercase() ?: ""
                }
                val actualMime = (mimeType ?: context.contentResolver.getType(uri)).orEmpty().lowercase()

                if (!isJsonFile(fileName, actualMime)) {
                    return@withContext emptyList()
                }

                return@withContext parseJsonMacroDefinitions(
                    BufferedReader(InputStreamReader(inputStream)).readText()
                ).mapNotNull { definition ->
                    val name = definition.name?.trim().orEmpty()
                    val command = definition.command?.trim().orEmpty()
                    if (name.isBlank() || command.isBlank()) {
                        null
                    } else {
                        Macro(name = name, command = command, formulas = definition.formulas)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FORMULA_IMPORT", "JSON macros import error", e)
        }

        emptyList()
    }

    private fun isJsonFile(fileName: String, mimeType: String): Boolean {
        return mimeType == "application/json" ||
            mimeType == "text/json" ||
            fileName.endsWith(".json")
    }

    private fun isCsvFile(fileName: String, mimeType: String): Boolean {
        return mimeType == "text/csv" ||
            mimeType == "application/csv" ||
            mimeType == "application/vnd.ms-excel" ||
            fileName.endsWith(".csv")
    }

    private fun isPdfFile(fileName: String, mimeType: String): Boolean {
        return mimeType == "application/pdf" || fileName.endsWith(".pdf")
    }

    private fun isDocxFile(fileName: String, mimeType: String): Boolean {
        return mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            mimeType.contains("word") ||
            fileName.endsWith(".docx")
    }

    private fun isImageFile(fileName: String, mimeType: String): Boolean {
        return mimeType.startsWith("image/") ||
            listOf(".png", ".jpg", ".jpeg", ".webp", ".bmp").any { fileName.endsWith(it) }
    }

    private fun isTextFile(fileName: String, mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
            mimeType == "application/octet-stream" && fileName.endsWith(".txt") ||
            fileName.endsWith(".txt")
    }

    private fun getDisplayName(uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex).orEmpty() else ""
            }
            .orEmpty()
    }

    private fun extractDocxText(inputStream: java.io.InputStream): String {
        val output = StringBuilder()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader().readText()
                    return docxXmlToText(xml)
                }
                entry = zip.nextEntry
            }
        }
        return output.toString()
    }

    private fun docxXmlToText(xml: String): String {
        val normalized = xml
            .replace(Regex("<w:tab\\b[^>]*/>"), "\t")
            .replace(Regex("<w:br\\b[^>]*/>"), "\n")
            .replace(Regex("</w:tc>"), "\t")
            .replace(Regex("</w:p>"), "\n")

        return Regex("<w:t\\b[^>]*>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(normalized)
            .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    fun parseJsonFormulas(jsonText: String): List<ParsingFormula> {
        val list = mutableListOf<ParsingFormula>()
        try {
            val trimmed = jsonText.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                list.addAll(jsonArrayToFormulas(array))
            } else {
                val obj = JSONObject(trimmed)
                list.addAll(jsonObjectToFormulas(obj))
            }
            android.util.Log.d("FORMULA_IMPORT", "Successfully parsed ${list.size} formulas from JSON")
        } catch (e: Exception) {
            android.util.Log.e("FORMULA_IMPORT", "JSON parse error", e)
        }
        return list
    }

    fun parseJsonParserImport(jsonText: String): ParserImportResult {
        return try {
            val trimmed = jsonText.trim()
            if (trimmed.startsWith("{")) {
                val macroDefinition = parseJsonMacroDefinition(trimmed)
                ParserImportResult(
                    formulas = macroDefinition.formulas,
                    parserName = macroDefinition.name ?: firstString(JSONObject(trimmed), "parserName", "parser_name", "name").ifBlank { null },
                    commandHex = macroDefinition.command ?: firstString(JSONObject(trimmed), "sendCommandHex", "commandHex", "command", "send_command_hex").ifBlank { null },
                    macroName = macroDefinition.name,
                    macroCommand = macroDefinition.command
                )
            } else {
                ParserImportResult(parseJsonFormulas(trimmed))
            }
        } catch (e: Exception) {
            android.util.Log.e("FORMULA_IMPORT", "JSON parser metadata parse error", e)
            ParserImportResult(emptyList())
        }
    }

    private fun parseStructuredText(text: String): List<ParsingFormula>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJsonFormulas(trimmed)
        }

        val firstLine = trimmed.lineSequence().firstOrNull().orEmpty().lowercase()
        val looksLikeCsv = firstLine.contains(",") &&
            ("name" in firstLine || "offset" in firstLine || "length" in firstLine || "type" in firstLine)

        return if (looksLikeCsv) parseCsvFormulas(trimmed) else null
    }

    fun parseTextFormulas(text: String): List<ParsingFormula> {
        parseStructuredText(text)?.let { return it }
        return extractFormulasFromText(text)
    }

    private fun parseDataType(typeStr: String): FormulaDataType {
        val normalized = typeStr.uppercase().replace(" ", "").replace("_", "")
        return when (normalized) {
            "INT8" -> FormulaDataType.INT_8
            "UINT8" -> FormulaDataType.UINT_8
            "INT16", "INT16BE" -> FormulaDataType.INT_16_BE
            "INT16LE" -> FormulaDataType.INT_16_LE
            "UINT16", "UINT16BE" -> FormulaDataType.UINT_16_BE
            "UINT16LE" -> FormulaDataType.UINT_16_LE
            "INT32", "INT32BE" -> FormulaDataType.INT_32_BE
            "INT32LE" -> FormulaDataType.INT_32_LE
            "UINT32", "UINT32BE" -> FormulaDataType.UINT_32_BE
            "UINT32LE" -> FormulaDataType.UINT_32_LE
            "FLOAT", "FLOAT32", "FLOAT32BE" -> FormulaDataType.FLOAT_32_BE
            "FLOAT32LE" -> FormulaDataType.FLOAT_32_LE
            "STRING" -> FormulaDataType.STRING
            "HEX" -> FormulaDataType.HEX
            else -> {
                try { FormulaDataType.valueOf(typeStr.uppercase()) } catch (_: Exception) { FormulaDataType.HEX }
            }
        }
    }

    private fun jsonArrayToFormulas(array: JSONArray): List<ParsingFormula> {
        val list = mutableListOf<ParsingFormula>()
        for (i in 0 until array.length()) {
            when (val value = array.opt(i)) {
                is JSONObject -> list.addAll(jsonObjectToFormulas(value))
                is JSONArray -> list.addAll(jsonArrayToFormulas(value))
            }
        }
        return list
    }

    private fun jsonObjectToFormulas(obj: JSONObject): List<ParsingFormula> {
        val nestedKeys = listOf(
            "formulas",
            "parsers",
            "responseParsers",
            "response_parsers",
            "response_parser",
            "fields",
            "values",
            "measurements",
            "items"
        )
        val nested = mutableListOf<ParsingFormula>()

        nestedKeys.forEach { key ->
            when (val value = obj.opt(key)) {
                is JSONArray -> nested.addAll(jsonArrayToFormulas(value))
                is JSONObject -> nested.addAll(jsonObjectToFormulas(value))
            }
        }

        return if (looksLikeFormula(obj)) nested + jsonObjectToFormula(obj) else nested
    }

    private fun looksLikeFormula(obj: JSONObject): Boolean {
        if (firstString(obj, "command", "commandHex", "sendCommandHex", "send_command_hex", "macroCommand", "value").isNotBlank()) {
            return false
        }

        val formulaKeys = listOf(
            "name",
            "label",
            "field",
            "pattern",
            "offset",
            "byteOffset",
            "byte_offset",
            "start",
            "length",
            "size",
            "bytes",
            "dataType",
            "data_type",
            "type",
            "multiplier",
            "scale",
            "unit",
            "units"
        )
        return formulaKeys.any { obj.has(it) }
    }

    private fun jsonObjectToFormula(obj: JSONObject): ParsingFormula {
        return ParsingFormula(
            name = firstString(obj, "name", "label", "field", "key", "id").ifBlank { "Unknown" },
            pattern = if (obj.has("pattern") && !obj.isNull("pattern")) obj.getString("pattern") else null,
            offset = firstInt(obj, "offset", "byteOffset", "byte_offset", "start"),
            length = firstInt(obj, "length", "size", "bytes"),
            dataType = parseDataType(firstString(obj, "dataType", "data_type", "type").ifBlank { "HEX" }),
            multiplier = formulaMultiplier(obj),
            unit = firstString(obj, "unit", "units")
        )
    }

    private fun formulaMultiplier(obj: JSONObject): Double {
        val explicitMultiplier = firstNullableDouble(obj, "multiplier", "scale")
        if (explicitMultiplier != null) return explicitMultiplier

        val divisor = firstNullableDouble(obj, "divisor", "divideBy", "divide_by")
        if (divisor != null && divisor != 0.0) return 1.0 / divisor

        val decimalPlaces = firstNullableInt(obj, "decimalPlaces", "decimal_places", "decimals")
        if (decimalPlaces != null && decimalPlaces > 0) {
            return 1.0 / Math.pow(10.0, decimalPlaces.toDouble())
        }

        return 1.0
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) return obj.optString(key, "")
        }
        return ""
    }

    private fun firstInt(obj: JSONObject, vararg keys: String): Int {
        return firstNullableInt(obj, *keys) ?: 0
    }

    private fun firstNullableInt(obj: JSONObject, vararg keys: String): Int? {
        keys.forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) {
                when (val value = obj.opt(key)) {
                    is Number -> return value.toInt()
                    is String -> value.toIntOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    private fun firstDouble(obj: JSONObject, vararg keys: String, default: Double): Double {
        return firstNullableDouble(obj, *keys) ?: default
    }

    private fun firstNullableDouble(obj: JSONObject, vararg keys: String): Double? {
        keys.forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) {
                when (val value = obj.opt(key)) {
                    is Number -> return value.toDouble()
                    is String -> value.toDoubleOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    fun parseCsvFormulas(csvText: String): List<ParsingFormula> {
        val list = mutableListOf<ParsingFormula>()
        val rows = csvText.lines()
            .map { parseCsvLine(it) }
            .filter { row -> row.any { it.isNotBlank() } }

        if (rows.isEmpty()) return emptyList()

        val firstRow = rows.first().map { it.lowercase() }
        val hasHeader = firstRow.any { it in listOf("name", "label", "field", "offset", "byteoffset", "length", "type", "datatype") }
        val headers = if (hasHeader) firstRow else emptyList()
        val dataRows = if (hasHeader) rows.drop(1) else rows

        dataRows.forEach { tokens ->
            if (tokens.size >= 4) {
                list.add(
                    ParsingFormula(
                        name = csvValue(tokens, headers, 0, "name", "label", "field", "key", "id").ifBlank { "Unknown" },
                        pattern = csvValue(tokens, headers, 1, "pattern").ifBlank { null },
                        offset = csvValue(tokens, headers, 2, "offset", "byteoffset", "byte_offset", "start").toIntOrNull() ?: 0,
                        length = csvValue(tokens, headers, 3, "length", "size", "bytes").toIntOrNull() ?: 0,
                        dataType = parseDataType(csvValue(tokens, headers, 4, "datatype", "data_type", "type").ifBlank { "HEX" }),
                        multiplier = csvValue(tokens, headers, 5, "multiplier", "scale").toDoubleOrNull() ?: 1.0,
                        unit = csvValue(tokens, headers, 6, "unit", "units")
                    )
                )
            }
        }
        return list
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values.add(current.toString().trim())
        return values
    }

    private fun csvValue(tokens: List<String>, headers: List<String>, fallbackIndex: Int, vararg names: String): String {
        names.forEach { name ->
            val index = headers.indexOf(name.lowercase())
            if (index >= 0) return tokens.getOrNull(index).orEmpty().trim()
        }
        return tokens.getOrNull(fallbackIndex).orEmpty().trim()
    }

    fun extractFormulasFromText(text: String): List<ParsingFormula> {
        val list = mutableListOf<ParsingFormula>()
        // Split by lines or patterns
        val lines = text.lines()
        var currentName: String? = null
        var currentPattern: String? = null
        var currentOffset: Int = 0
        var currentLength: Int = 1
        var currentType = FormulaDataType.HEX
        var currentUnit = ""
        var currentMultiplier = 1.0

        for (line in lines) {
            val lower = line.lowercase()
            
            if (lower.contains("name:")) {
                // If we already had a formula being built, add it before starting a new one
                if (currentName != null) {
                    list.add(ParsingFormula(
                        name = currentName,
                        pattern = if (currentPattern?.isBlank() == true) null else currentPattern,
                        offset = currentOffset,
                        length = currentLength,
                        dataType = currentType,
                        multiplier = currentMultiplier,
                        unit = currentUnit
                    ))
                    // Reset fields
                    currentPattern = null
                    currentOffset = 0
                    currentLength = 1
                    currentType = FormulaDataType.HEX
                    currentUnit = ""
                    currentMultiplier = 1.0
                }
                currentName = line.substringAfter(":").trim()
            } else {
                when {
                    lower.contains("pattern:") -> currentPattern = line.substringAfter(":").trim()
                    lower.contains("offset:") -> currentOffset = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    lower.contains("length:") -> currentLength = line.substringAfter(":").trim().toIntOrNull() ?: 1
                    lower.contains("unit:") -> currentUnit = line.substringAfter(":").trim()
                    lower.contains("type:") -> {
                        currentType = parseDataType(line.substringAfter(":").trim())
                    }
                    lower.contains("multiplier:") -> currentMultiplier = line.substringAfter(":").trim().toDoubleOrNull() ?: 1.0
                }
            }
        }
        // ... (rest of the function is the same, but wait I need to keep the part after the loop)

        
        // Add the last one if it exists
        if (currentName != null) {
            list.add(ParsingFormula(
                name = currentName,
                pattern = if (currentPattern?.isBlank() == true) null else currentPattern,
                offset = currentOffset,
                length = currentLength,
                dataType = currentType,
                multiplier = currentMultiplier,
                unit = currentUnit
            ))
        }
        
        return list
    }
}
