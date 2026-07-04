package com.rama.blelab.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FormulaImporterTest {

    @Test
    fun parseJsonMacroDefinition_extractsNameCommandAndFormulas() {
        val json = """
            {
              "name": "Temp",
              "command": "GET_TEMP",
              "formulas": [
                {
                  "name": "Temp_Float",
                  "pattern": "01 04 04",
                  "offset": 3,
                  "length": 4,
                  "dataType": "FLOAT_32_BE",
                  "multiplier": 1.0,
                  "unit": "°C"
                }
              ]
            }
        """.trimIndent()

        val result = FormulaImporter.parseJsonMacroDefinition(json)

        assertNotNull(result)
        assertEquals("Temp", result.name)
        assertEquals("GET_TEMP", result.command)
        assertEquals(1, result.formulas.size)
        assertEquals("Temp_Float", result.formulas.first().name)
        assertEquals("01 04 04", result.formulas.first().pattern)
    }

    @Test
    fun parseJsonMacroDefinitions_extractsMultipleMacrosWithOwnFormulas() {
        val json = """
            {
              "macros": [
                {
                  "name": "Read Temp",
                  "command": "01 04 00 00",
                  "formulas": [
                    {
                      "name": "Temperature",
                      "offset": 3,
                      "length": 2,
                      "dataType": "INT16BE",
                      "divisor": 10,
                      "unit": "C"
                    }
                  ]
                },
                {
                  "name": "Read Humidity",
                  "command": "01 04 00 01",
                  "formulas": [
                    {
                      "name": "Humidity",
                      "offset": 3,
                      "length": 2,
                      "dataType": "UINT16BE",
                      "multiplier": 0.1,
                      "unit": "%"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = FormulaImporter.parseJsonMacroDefinitions(json)

        assertEquals(2, result.size)
        assertEquals("Read Temp", result[0].name)
        assertEquals("01 04 00 00", result[0].command)
        assertEquals(1, result[0].formulas.size)
        assertEquals("Temperature", result[0].formulas.first().name)
        assertEquals("Read Humidity", result[1].name)
        assertEquals("01 04 00 01", result[1].command)
        assertEquals(1, result[1].formulas.size)
        assertEquals("Humidity", result[1].formulas.first().name)
    }
}
