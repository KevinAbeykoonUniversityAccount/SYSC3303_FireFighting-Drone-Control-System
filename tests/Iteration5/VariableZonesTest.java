import org.junit.*;
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

    // ── Scheduler CSV loading tests ───────────────────────────────────────────

    /** 4. A well-formed zone file with multiple zones loads without errors. */
    @Test
    public void validZoneFileLoadsWithNoErrors() throws Exception {
        File f = csv(
            "ZoneID,xMin,xMax,yMin,yMax",
            "1,0,14,0,14",
            "2,15,29,0,14",
            "3,0,14,15,29",
            "4,15,29,15,29"
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

    /** 5. A zone narrower than 3 cells in either dimension is rejected. */
    @Test
    public void zoneSmallerThan3x3IsRejected() throws Exception {
        // Width = xMax - xMin + 1 = 2  (too small)
        File f = csv(
            "ZoneID,xMin,xMax,yMin,yMax",
            "1,0,1,0,10"
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
            "ZoneID,xMin,xMax,yMin,yMax",
            "1,0,14,0,14",
            "1,15,29,0,14"   // same ID
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
        File f = csv("ZoneID,xMin,xMax,yMin,yMax");

        Scheduler s = new Scheduler();
        try {
            List<String> errors = s.loadZonesFromFile(f.getAbsolutePath());
            assertFalse("Expected an error for empty zone file", errors.isEmpty());
        } finally {
            s.stop();
        }
    }
}
