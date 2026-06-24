package com.rama.blelab.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsingFormula(
    val name: String,
    val pattern: String? = null, // Optional HEX pattern to match (e.g., "01 04 04")
    val offset: Int = 0,
    val length: Int = 0,
    val dataType: FormulaDataType = FormulaDataType.HEX,
    val multiplier: Double = 1.0,
    val unit: String = ""
)

enum class FormulaDataType {
    INT_8, UINT_8, 
    INT_16_BE, INT_16_LE, UINT_16_BE, UINT_16_LE,
    INT_32_BE, INT_32_LE, UINT_32_BE, UINT_32_LE,
    FLOAT_32_BE, FLOAT_32_LE,
    STRING, HEX
}
