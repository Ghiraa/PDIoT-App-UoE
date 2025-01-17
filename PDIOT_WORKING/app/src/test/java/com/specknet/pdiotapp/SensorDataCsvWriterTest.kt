import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class SensorDataCsvWriterTest {

    @Test
    fun writeSensorDataToCsv() {
        val sensorData = listOf(
            SensorData("2025-01-15T10:00:00", 1.0, 2.0, 3.0),
            SensorData("2025-01-15T10:05:00", 4.0, 5.0, 6.0)
        )
        val outputFile = File("test_output.csv")
        SensorDataCsvWriter.writeCsv(sensorData, outputFile)

        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("2025-01-15T10:00:00,1.0,2.0,3.0"))
        assertTrue(content.contains("2025-01-15T10:05:00,4.0,5.0,6.0"))

        outputFile.delete() // Clean up
    }
}
