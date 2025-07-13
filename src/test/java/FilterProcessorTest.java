import com.FilterProcessor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FilterProcessorTest {

    @Test
    public void testGaussianBlurSingleThread() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        BufferedImage output = FilterProcessor.applyFilterParallel(input, "blur", 1);
        assertNotNull(output);
        assertEquals(input.getWidth(), output.getWidth());
        assertEquals(input.getHeight(), output.getHeight());
    }

    @Test
    public void testGaussianBlurMultiThread() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        BufferedImage output = FilterProcessor.applyFilterParallel(input, "blur", 4);
        assertNotNull(output);
    }

    @Test
    public void testDoGFilter() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        BufferedImage output = FilterProcessor.applyFilterParallel(input, "dog", 4);
        assertNotNull(output);
    }

    @Test
    public void testLoGFilter() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        BufferedImage output = FilterProcessor.applyFilterParallel(input, "log", 2);
        assertNotNull(output);
    }

    @Test
    public void testUnknownFilterThrows() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            FilterProcessor.applyFilterParallel(input, "unknown", 2);
        });
        assertTrue(exception.getMessage().contains("Unknown filter"));
    }

    @Test
    public void testNullInputThrows() {
        assertThrows(NullPointerException.class, () -> {
            FilterProcessor.applyFilterParallel(null, "blur", 2);
        });
    }

    @Test
    public void testZeroThreadsDefaultsToOne() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        BufferedImage output = FilterProcessor.applyFilterParallel(input, "blur", 0);
        assertNotNull(output);
    }

    @Test
    public void testImageEqualityDimensionOnly() throws IOException {
        BufferedImage input = ImageIO.read(new File("src/test/resources/sample.jpg"));
        BufferedImage blur = FilterProcessor.applyFilterParallel(input, "blur", 1);
        BufferedImage dog = FilterProcessor.applyFilterParallel(input, "dog", 1);
        assertEquals(input.getWidth(), blur.getWidth());
        assertEquals(input.getHeight(), blur.getHeight());
        assertEquals(input.getWidth(), dog.getWidth());
        assertEquals(input.getHeight(), dog.getHeight());
    }
}
