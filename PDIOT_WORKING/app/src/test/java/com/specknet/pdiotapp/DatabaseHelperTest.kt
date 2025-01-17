import com.specknet.pdiotapp.utils.DatabaseHelper
import org.junit.Test
import org.junit.Assert.*

class DatabaseHelperTest {

    @Test
    fun addAndRetrieveRecord() {
        val dbHelper = DatabaseHelper()
        val sensorData = SensorData("2025-01-15T10:00:00", 1.0, 2.0, 3.0)

        dbHelper.addSensorData(sensorData)
        val retrievedData = dbHelper.getSensorData("2025-01-15T10:00:00")

        assertNotNull(retrievedData)
        assertEquals(sensorData, retrievedData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun handleDuplicateRecord() {
        val dbHelper = DatabaseHelper()
        val sensorData = SensorData("2025-01-15T10:00:00", 1.0, 2.0, 3.0)

        dbHelper.addSensorData(sensorData)
        dbHelper.addSensorData(sensorData) // Adding duplicate should throw an exception
    }
}
