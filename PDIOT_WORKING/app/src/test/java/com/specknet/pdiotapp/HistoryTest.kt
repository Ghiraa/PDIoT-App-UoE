import com.specknet.pdiotapp.history.HistoryActivity
import com.specknet.pdiotapp.utils.DatabaseHelper
import org.junit.Test
import org.mockito.Mockito.*
import org.junit.Assert.*

class HistoryActivityTest {

    @Test
    fun fetchHistoryForValidDate() {
        val mockDatabaseHelper = mock(DatabaseHelper::class.java)
        val historyActivity = HistoryActivity(mockDatabaseHelper)
        val sampleData = listOf(SensorData("2025-01-15T10:00:00", 1.0, 2.0, 3.0))

        `when`(mockDatabaseHelper.getSensorDataForDate("2025-01-15")).thenReturn(sampleData)

        val result = historyActivity.fetchHistory("2025-01-15")
        assertEquals(sampleData, result)
    }

    @Test
    fun fetchHistoryForEmptyDate() {
        val mockDatabaseHelper = mock(DatabaseHelper::class.java)
        val historyActivity = HistoryActivity(mockDatabaseHelper)

        `when`(mockDatabaseHelper.getSensorDataForDate("2025-01-15")).thenReturn(emptyList())

        val result = historyActivity.fetchHistory("2025-01-15")
        assertTrue(result.isEmpty())
    }
}
