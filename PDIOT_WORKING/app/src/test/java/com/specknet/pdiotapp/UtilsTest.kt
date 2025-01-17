import org.junit.Test
import org.junit.Assert.*
import java.util.*

class UtilsTest {

    @Test
    fun convertTimestampToFormattedString() {
        val timestamp = 1672531200000L // Example timestamp in milliseconds
        val formattedDate = Utils.formatTimestamp(timestamp)
        assertEquals("2025-01-15 10:00:00", formattedDate)
    }

    @Test
    fun handleInvalidTimestamp() {
        val invalidTimestamp = -1L
        val formattedDate = Utils.formatTimestamp(invalidTimestamp)
        assertNull(formattedDate)
    }
}
