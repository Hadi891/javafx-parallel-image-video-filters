// File: VideoProcessor.java
package com;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * VideoProcessor (JCodec version)
 *
 * Reads frames from an input video (OpenCV), applies a parallel filter
 * (via FilterProcessor), and writes them out as an H.264‐encoded MP4
 * using JCodec’s AWTSequenceEncoder.
 */
public class VideoProcessor {

    static {
        // Load the OpenCV native library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * Processes the input video by applying the specified filter,
     * then writes the result to an MP4 via JCodec.
     *
     * @param inputPath   path to the input video file (e.g. .mp4, .avi)
     * @param outputPath  desired output MP4 path (must end in .mp4)
     * @param filterType  one of "blur", "gaussian_highpass", "log"
     * @param numThreads  number of parallel threads to use in FilterProcessor
     */

    /**
     * Convert an OpenCV Mat into a BufferedImage (TYPE_3BYTE_BGR)
     * by encoding to JPEG in memory and decoding via ImageIO.
     */
    BufferedImage matToBufferedImage(Mat mat) {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, mob);
        try {
            BufferedImage buf = ImageIO.read(new ByteArrayInputStream(mob.toArray()));
            // Ensure TYPE_3BYTE_BGR
            if (buf.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                BufferedImage converted = new BufferedImage(
                        buf.getWidth(),
                        buf.getHeight(),
                        BufferedImage.TYPE_3BYTE_BGR
                );
                converted.getGraphics().drawImage(buf, 0, 0, null);
                return converted;
            }
            return buf;
        } catch (IOException e) {
            throw new RuntimeException("Cannot convert Mat to BufferedImage", e);
        }
    }
}
