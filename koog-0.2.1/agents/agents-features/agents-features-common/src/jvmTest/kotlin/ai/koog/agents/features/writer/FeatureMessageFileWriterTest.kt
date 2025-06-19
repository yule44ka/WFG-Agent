package ai.koog.agents.features.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.common.writer.FeatureMessageFileWriter
import ai.koog.agents.utils.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.test.*

class FeatureMessageFileWriterTest {

    companion object {
        private fun createTempLogFile(tempDir: Path) = Files.createTempFile(tempDir, "agent-trace", ".log")

        private fun sinkOpener(path: Path): Sink {
            return SystemFileSystem.sink(path = kotlinx.io.files.Path(path.pathString)).buffered()
        }
    }

    class TestFeatureMessageFileWriter(root: Path) :
        FeatureMessageFileWriter<Path>(createTempLogFile(root), FeatureMessageFileWriterTest::sinkOpener) {

        override fun FeatureMessage.toFileString(): String {
            return when (this) {
                is TestFeatureEventMessage -> "[${this.messageType.value}] ${this.eventId}"
                is FeatureStringMessage -> "[${this.messageType.value}] ${this.message}"
                else -> "UNDEFINED"
            }
        }
    }

    //region Initialize

    @Test
    fun `test base state for non-initialized writer`(@TempDir tempDir: Path) = runBlocking {
        val writer = TestFeatureMessageFileWriter(tempDir)
        assertFalse(writer.isOpen)
    }

    @Test
    fun `test base state for initialized writer`(@TempDir tempDir: Path) = runBlocking {
       TestFeatureMessageFileWriter(tempDir).use { writer ->
           writer.initialize()
           assertTrue(writer.isOpen)
       }
    }

    @Test
    fun `test initialize twice from same thread`(@TempDir tempDir: Path) = runBlocking {
        TestFeatureMessageFileWriter(tempDir).use { writer ->
            writer.initialize()
            writer.initialize()
            assertTrue(writer.isOpen)
        }
    }

    @Test
    fun `test initialize from different threads`(@TempDir tempDir: Path) = runBlocking {

        val cs = this@runBlocking

        TestFeatureMessageFileWriter(tempDir).use { writer ->

            val jobCount = 10
            val jobs = List(jobCount) { number ->
                cs.launch(Dispatchers.Default) {
                    writer.initialize()
                    writer.processMessage(FeatureStringMessage("Test message $number"))
                }
            }

            jobs.joinAll()

            val expectedContent = List(jobCount) { number -> "[${FeatureMessage.Type.Message.value}] Test message $number" }
            val actualContent = tempDir.listDirectoryEntries().first().readLines().sorted()

            assertEquals(expectedContent.size, actualContent.size)
            assertContentEquals(expectedContent, actualContent)
        }
    }

    //endregion Initialize

    //region Write

    @Test
    fun `test write using non-initialized writer`(@TempDir tempDir: Path) = runBlocking {
        val writer = TestFeatureMessageFileWriter(tempDir)

        val throwable = assertThrows<IllegalStateException> {
            writer.processMessage(message = FeatureStringMessage("test-message"))
        }

        val expectedError = "Writer is not initialized. Please make sure you call method 'initialize()' before."
        assertEquals(expectedError, throwable.message)
        assertFalse(writer.isOpen)
    }

    @Test
    fun `test write messages to file`(@TempDir tempDir: Path) = runBlocking {
        TestFeatureMessageFileWriter(tempDir).use { writer ->
            writer.initialize()

            val stringMessage = FeatureStringMessage("Test message")
            val eventMessage = TestFeatureEventMessage("Test event")

            writer.processMessage(stringMessage)
            writer.processMessage(eventMessage)

            val expectedContent = listOf(
                "[${stringMessage.messageType.value}] ${stringMessage.message}",
                "[${eventMessage.messageType.value}] ${eventMessage.eventId}"
            )

            val actualContent = writer.targetPath.readLines()

            assertEquals(expectedContent.size, actualContent.size)
            assertContentEquals(expectedContent, actualContent)
        }
    }

    @Test
    fun `test write messages from multiple threads`(@TempDir tempDir: Path) = runBlocking {

        val cs = this@runBlocking

        TestFeatureMessageFileWriter(tempDir).use { writer ->
            writer.initialize()

            val threadsCount = 10
            val writeJobs = List(threadsCount) { number ->
                val message = FeatureStringMessage("Test message $number")

                cs.launch(Dispatchers.Default) {
                    writer.processMessage(message)
                }
            }

            writeJobs.joinAll()

            val expectedContent = List(threadsCount) { number -> "[${FeatureMessage.Type.Message.value}] Test message $number" }
            val actualContent = writer.targetPath.readLines().sorted()

            assertEquals(expectedContent.size, actualContent.size)
            assertContentEquals(expectedContent, actualContent)
        }
    }

    //endregion Write

    //region Close

    @Test
    fun `test close non-initialized writer`(@TempDir tempDir: Path) = runBlocking {
        val writer = TestFeatureMessageFileWriter(tempDir)
        writer.close()
        assertFalse(writer.isOpen)
    }

    @Test
    fun `test close initialized writer`(@TempDir tempDir: Path) = runBlocking {
        val writer = TestFeatureMessageFileWriter(tempDir)
        writer.initialize()
        assertTrue(writer.isOpen)

        writer.close()
        assertFalse(writer.isOpen)
    }

    @Test
    fun `test close already closed writer`(@TempDir tempDir: Path) = runBlocking {
        val writer = TestFeatureMessageFileWriter(tempDir)
        writer.initialize()
        assertTrue(writer.isOpen)

        writer.close()
        assertFalse(writer.isOpen)

        writer.close()
        assertFalse(writer.isOpen)
    }

    @Test
    fun `test write after close throws exception`(@TempDir tempDir: Path) = runBlocking {
        val writer = TestFeatureMessageFileWriter(tempDir)
        writer.initialize()
        assertTrue(writer.isOpen)

        writer.close()
        assertFalse(writer.isOpen)

        val throwable = assertThrows<IllegalStateException> {
            writer.processMessage(message = FeatureStringMessage("test-message"))
        }

        val expectedError = "Writer is not initialized. Please make sure you call method 'initialize()' before."
        assertEquals(expectedError, throwable.message)
    }

    //endregion Close
}
