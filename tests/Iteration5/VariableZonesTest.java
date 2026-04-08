import org.junit.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Tests for variable zone loading (Scheduler.loadZonesFromFile) and
 * Zone coordinate helpers.
 *
 * Each test that needs a Scheduler creates one on a dedicated port to avoid
 * conflicts, then stops it immediately so the OS releases the socket.
 */
public class VariableZonesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // Writes CSV lines to a temp file
    private File csv(String... lines) throws IOException {
        File f = tmp.newFile("zones_test.csv");
        try (PrintWriter pw = new PrintWriter(f)) {
            for (String line : lines) pw.println(line);
        }
        return f;
    }

    /** 1. getCenterX returns the midpoint of xMin and xMax. */
    @Test
    public void zoneCenterXCalculatedCorrectly() {
        Zone z = new Zone(1, 0, 10, 0, 10);
        assertEquals(5, z.getCenterX());
    }

    /** 2. getCenterY returns the midpoint of yMin and yMax. */
    @Test
    public void zoneCenterYCalculatedCorrectly() {
        Zone z = new Zone(1, 0, 10, 0, 20);
        assertEquals(10, z.getCenterY());
    }

    /** 3. Coordinate getters return the exact values passed to the constructor. */
    @Test
    public void zoneBoundGettersReturnCorrectValues() {
        Zone z = new Zone(3, 2, 12, 5, 15);
        assertEquals(2,  z.getXMin());
        assertEquals(12, z.getXMax());
        assertEquals(5,  z.getYMin());
        assertEquals(15, z.getYMax());
    }

    // ==== Scheduler CSV loading tests ====
    /** 4. A well-formed zone file with multiple zones loads without errors. */
    @Test
    public void validZoneFileLoadsWithNoErrors() throws Exception {
        File f = csv(
            "ZoneID, ZoneStart, ZoneEnd",
            "1, (0, 0), (450, 450)",
            "2, (450, 0), (900, 450)",
            "3, (0, 450), (450, 900)",
            "4, (450, 450), (900, 900)"
        );

        Scheduler s = new Scheduler();
        try {
            List<String> errors = s.loadZonesFromFile(f.getAbsolutePath());
            assertTrue("Expected no errors, got: " + errors, errors.isEmpty());
            assertEquals(4, s.getZones().size());
        } finally {
            s.stop();
        }

    }

    /** 5. A zone smaller than 90 m (3 grid cells) in either dimension is rejected. */
    @Test
    public void zoneSmallerThan3CellsIsRejected() throws Exception {
        // Width = 60 m < 90 m minimum
        File f = csv(
            "ZoneID, ZoneStart, ZoneEnd",
            "1, (0, 0), (60, 450)"
        );

        Scheduler s = new Scheduler();
        try {
            List<String> errors = s.loadZonesFromFile(f.getAbsolutePath());
            assertFalse("Expected a size-validation error", errors.isEmpty());
        } finally {
            s.stop();
        }
    }

    /** 6. A file containing a duplicate zone ID is rejected entirely. */
    @Test
    public void duplicateZoneIdIsRejected() throws Exception {
        File f = csv(
            "ZoneID, ZoneStart, ZoneEnd",
            "1, (0, 0), (450, 450)",
            "1, (450, 0), (900, 450)"   // same ID
        );

        Scheduler s = new Scheduler();
        try {
            List<String> errors = s.loadZonesFromFile(f.getAbsolutePath());
            assertFalse("Expected a duplicate-ID error", errors.isEmpty());
        } finally {
            s.stop();
        }
    }

    /** 7. A file with only the header row (no data) returns an error. */
    @Test
    public void emptyZoneFileReturnsError() throws Exception {
        File f = csv("ZoneID, ZoneStart, ZoneEnd");

        Scheduler s = new Scheduler();
        try {
            List<String> errors = s.loadZonesFromFile(f.getAbsolutePath());
            assertFalse("Expected an error for empty zone file", errors.isEmpty());
        } finally {
            s.stop();
        }
    }
}
