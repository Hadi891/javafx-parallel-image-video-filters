# FilterFX

**Parallel Image & Video Filter Processing**

A JavaFX desktop application that applies three convolution filters—Mean Blur, Laplacian-of-Gaussian (LoG), and Difference-of-Gaussian (DoG)—to still images and video frames in parallel using Java’s Fork/Join framework. Includes timing, CPU-utilization sampling, and memory-overhead reporting.

---

## Features

- **Image & Video Tabs**  
  Load any standard image (JPEG, PNG) or video (MP4, AVI).

- **Three Filters**  
  Blur, LoG, DoG—selectable at runtime.

- **Sequential vs. Parallel**  
  Toggle between single-threaded and N-threaded Fork/Join execution.

- **Live Preview & Metrics**  
  View original and filtered media side-by-side, with real-time timing, speed-up, and CPU load.

- **CSV Export**  
  Save benchmark data (thread count, durations, memory use) for offline analysis.

---

## Requirements

- **Java 11** (or newer) JDK  
- **JavaFX SDK** (matching your JDK version)  
- **OpenCV Java** (native library + JAR)  
- **JCodec** (JAR)  
- A machine with at least 4 CPU cores recommended

All third-party JARs are provided in the `lib/` folder.

---

## Usage

1. **Launch** the app.  
2. **Image Tab**  
   - Click **Choose Image**, pick a file.  
   - Select a filter and thread count.  
   - Click **Run**, then view original vs. filtered and timing labels.  
3. **Video Tab**  
   - Click **Choose Video**, pick a file.  
   - Choose filter, thread count, then **Run**.  
   - Observe three video panes (original, sequential, parallel), CPU-usage label and speed-up.  

---

## Testing

- **JUnit 5**  
  - `FilterProcessorTest.java` ensures bit-identical outputs (sequential vs. parallel).  
  - `VideoProcessorTest.java` checks frame counts and basic encoding.  
- **VisualVM**  
  Attach to a running parallel session to inspect CPU hotspots and GC behavior.

---

## Results & Benchmarks

See the slides or report for:

- **Speed-Up vs. Threads** (e.g., up to 13.5× on 24 threads)  
- **Speed-Up vs. Image Size** (from 2.5× on small images to ~6× on 4K)  
- **CPU Utilization** (sustained ≥ 88%)  
- **Memory Overhead** (≤ 1.5×)

---

## Contributing

1. Fork the repo  
2. Create a feature branch (`git checkout -b feature/YourFeature`)  
3. Commit your changes (`git commit -am 'Add some feature'`)  
4. Push to the branch (`git push origin feature/YourFeature`)  
5. Open a Pull Request

---

## License

This project is released under the [MIT License](LICENSE).

---

## Authors

- **Hadi Jaber**
- **Mohammad Hammoud**
