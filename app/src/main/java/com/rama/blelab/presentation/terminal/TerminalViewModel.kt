package com.rama.blelab.presentation.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.blelab.data.repository.MacroDataStore
import com.rama.blelab.domain.model.FormulaDataType
import com.rama.blelab.domain.model.ParsingFormula
import com.rama.blelab.domain.repository.*
import com.rama.blelab.domain.usecase.BleUseCases
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TerminalViewModel(
    private val repository: BluetoothRepository,
    private val useCases: BleUseCases,
    private val macroDataStore: MacroDataStore
) : ViewModel() {
    private val oldResponseParserCommand = "__RESPONSE_PARSER_ONLY__"

    val connectionState = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.connectionState.value)

    private val _messages = MutableStateFlow<List<BleMessage>>(emptyList())
    val messages: StateFlow<List<BleMessage>> = _messages.asStateFlow()

    private val _isHexMode = MutableStateFlow(true)
    val isHexMode: StateFlow<Boolean> = _isHexMode.asStateFlow()

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private var activeResponseParserMacro: Macro? = null

    init {
        // Load macros from storage
        macroDataStore.macros.onEach { loadedMacros ->
            if (loadedMacros.isEmpty()) {
                val defaults = listOf(
                    Macro("Temp", "GET_TEMP", listOf(ParsingFormula("Temp_Float", "01 04 04", 3, 4, FormulaDataType.FLOAT_32_BE, 1.0, "°C"))),
                    Macro("On", "LED_ON"),
                    Macro("Off", "LED_OFF"),
                    Macro("Reset", "RESET"),
                    Macro("Status", "STATUS")
                )
                _macros.value = defaults
                macroDataStore.saveMacros(defaults)
            } else {
                val commandMacros = loadedMacros.filterNot { it.command == oldResponseParserCommand }
                _macros.value = commandMacros
                if (commandMacros.size != loadedMacros.size) {
                    macroDataStore.saveMacros(commandMacros)
                }
            }
        }.take(1).launchIn(viewModelScope)

        repository.messages.onEach { message ->
            var processedMessage = message
            
            // Apply formulas associated with any macro if pattern matches
            if (message.type == MessageType.RX) {
                val results = mutableListOf<String>()
                var bestParserName: String? = null
                val parserMacros = activeResponseParserMacro
                    ?.takeIf { it.formulas.isNotEmpty() }
                    ?.let { listOf(it) }
                    ?: _macros.value

                parserMacros.forEach { macro ->
                    macro.formulas.forEach { formula ->
                        val hexContent = message.content.replace(" ", "")
                        val patternMatch = formula.pattern?.replace(" ", "")?.let { 
                            hexContent.contains(it, ignoreCase = true) 
                        } ?: true

                        if (patternMatch) {
                            val parsed = applyFormula(formula, message.rawData)
                            if (parsed != null) {
                                results.add("${formula.name}: $parsed")
                                if (bestParserName == null) bestParserName = macro.name
                            }
                        }
                    }
                }
                
                if (results.isNotEmpty()) {
                    processedMessage = processedMessage.copy(
                        parsedContent = results.joinToString("\n"),
                        parserName = activeResponseParserMacro?.name
                            ?: if (results.size > 1) "Multi-Field" else bestParserName
                    )
                }
            }
            
            _messages.update { it + processedMessage }
        }.launchIn(viewModelScope)
    }

    private fun applyFormula(formula: ParsingFormula, data: ByteArray?): String? {
        if (data == null) return null
        return try {
            val offset = formula.offset
            val length = if (formula.length <= 0) 1 else formula.length
            
            if (offset < 0 || offset + length > data.size) return null
            
            val value: Double = when (formula.dataType) {
                FormulaDataType.INT_8 -> data[offset].toDouble()
                FormulaDataType.UINT_8 -> (data[offset].toInt() and 0xFF).toDouble()
                
                FormulaDataType.INT_16_BE -> {
                    ((data[offset].toInt() shl 8) or (data[offset + 1].toInt() and 0xFF)).toDouble()
                }
                FormulaDataType.INT_16_LE -> {
                    ((data[offset + 1].toInt() shl 8) or (data[offset].toInt() and 0xFF)).toDouble()
                }
                FormulaDataType.UINT_16_BE -> {
                    ((data[offset].toInt() and 0xFF shl 8) or (data[offset + 1].toInt() and 0xFF)).toDouble()
                }
                FormulaDataType.UINT_16_LE -> {
                    ((data[offset + 1].toInt() and 0xFF shl 8) or (data[offset].toInt() and 0xFF)).toDouble()
                }

                FormulaDataType.INT_32_BE -> {
                    java.nio.ByteBuffer.wrap(data, offset, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int.toDouble()
                }
                FormulaDataType.INT_32_LE -> {
                    java.nio.ByteBuffer.wrap(data, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toDouble()
                }
                FormulaDataType.UINT_32_BE -> {
                    java.nio.ByteBuffer.wrap(data, offset, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int.toLong().let { 
                        if (it < 0) it + 0x100000000L else it 
                    }.toDouble()
                }
                FormulaDataType.UINT_32_LE -> {
                    java.nio.ByteBuffer.wrap(data, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong().let { 
                        if (it < 0) it + 0x100000000L else it 
                    }.toDouble()
                }

                FormulaDataType.FLOAT_32_BE -> {
                    java.nio.ByteBuffer.wrap(data, offset, 4).order(java.nio.ByteOrder.BIG_ENDIAN).float.toDouble()
                }
                FormulaDataType.FLOAT_32_LE -> {
                    java.nio.ByteBuffer.wrap(data, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).float.toDouble()
                }
                
                FormulaDataType.STRING -> {
                    return String(data, offset, length)
                }
                FormulaDataType.HEX -> {
                    return data.sliceArray(offset until offset + length).joinToString("") { 
                        (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() 
                    }
                }
            }
            
            val result = value * formula.multiplier
            val decimals = decimalPlacesForMultiplier(formula.multiplier)
            val formatStr = "%.${decimals}f %s"
            formatStr.format(result, formula.unit).trim()
        } catch (e: Exception) {
            android.util.Log.e("TERMINAL_VM", "Error applying formula", e)
            null
        }
    }

    private fun decimalPlacesForMultiplier(multiplier: Double): Int {
        if (multiplier >= 1.0) return 2
        val text = java.math.BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString()
        return text.substringAfter(".", "").length.coerceIn(2, 6)
    }

    fun toggleHexMode() {
        _isHexMode.value = !_isHexMode.value
    }

    fun addMacro(name: String, command: String, formulas: List<ParsingFormula> = emptyList()) {
        if (name.isNotBlank() && command.isNotBlank()) {
            val newMacro = Macro(name, command, formulas)
            android.util.Log.d("MACRO_STORAGE", "Saving new Macro: $name with ${formulas.size} formulas")
            val newList = _macros.value + newMacro
            _macros.value = newList
            viewModelScope.launch { macroDataStore.saveMacros(newList) }
        }
    }

    fun importMacros(importedMacros: List<Macro>): Int {
        val validMacros = importedMacros.filter { it.name.isNotBlank() && it.command.isNotBlank() }
        if (validMacros.isEmpty()) return 0

        val merged = _macros.value.toMutableList()
        validMacros.forEach { imported ->
            val existingIndex = merged.indexOfFirst { it.name.equals(imported.name, ignoreCase = true) }
            if (existingIndex >= 0) {
                merged[existingIndex] = imported
            } else {
                merged.add(imported)
            }
        }

        _macros.value = merged
        viewModelScope.launch { macroDataStore.saveMacros(merged) }
        return validMacros.size
    }

    fun updateMacro(oldMacro: Macro, newName: String, newCommand: String, formulas: List<ParsingFormula>) {
        android.util.Log.d("MACRO_STORAGE", "Updating Macro: ${oldMacro.name} -> $newName with ${formulas.size} formulas")
        val newList = _macros.value.map { 
            if (it == oldMacro) Macro(newName, newCommand, formulas) else it 
        }
        _macros.value = newList
        viewModelScope.launch { macroDataStore.saveMacros(newList) }
    }

    fun removeMacro(macro: Macro) {
        android.util.Log.d("MACRO_STORAGE", "Removing Macro: ${macro.name}")
        val newList = _macros.value - macro
        _macros.value = newList
        viewModelScope.launch { macroDataStore.saveMacros(newList) }
    }

    fun sendMessage(content: String) {
        val isHex = _isHexMode.value
        android.util.Log.d("BLE_COMMAND", "Sending command: $content (Hex: $isHex)")
        
        // Check if this command matches any macro to log context
        val matchingMacro = _macros.value.find { it.command.equals(content, ignoreCase = true) }
        matchingMacro?.let { 
            android.util.Log.d("BLE_COMMAND", "Command corresponds to Macro: ${it.name}. Expecting response for ${it.formulas.size} formulas.")
        }
        activeResponseParserMacro = matchingMacro?.takeIf { it.formulas.isNotEmpty() }

        useCases.sendMessage(content, isHex)
    }

    fun disconnect() {
        useCases.disconnect()
        _messages.value = emptyList()
    }

    fun clearLogs() {
        _messages.value = emptyList()
    }
}
