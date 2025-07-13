package com;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * FilterProcessor
 *   - Applies a filter ("blur", "log", or "dog") to a BufferedImage
 *     in parallel via a single ForkJoin recursive split over pixels.
 *   - No per‐tile allocations; reads src into an int[] once, writes
 *     straight into dst’s backing int[].
 *
 *   Now nulls out the big kernel buffers right after the pool finishes.
 */
public class FilterProcessor {

    /**
     * Applies the specified filter to the entire image using numThreads.
     */
    public static BufferedImage applyFilterParallel(
            BufferedImage src,
            String filterType,
            int numThreads
    ) {
        numThreads = Math.max(1, numThreads);
        int w = src.getWidth(), h = src.getHeight();
        String ft = filterType.toLowerCase();

        // 1) Read source pixels once
        int[] srcPixels = src.getRGB(0, 0, w, h, null, 0, w);

        // 2) Prepare destination and its raw buffer
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        DataBufferInt db = (DataBufferInt) dst.getRaster().getDataBuffer();
        int[] dstPixels = db.getData();

        // 3) Precompute kernels (once per call)
        double[][] mK   = makeMeanKernel(23);    // 15x15 mean filter instead of Gaussian
        double[][] logK = makeLoGKernel(4, 1.5);
        double[][] dK1  = makeGaussianKernel(7,  1.0);
        double[][] dK2  = makeGaussianKernel(15, 3.0);

        // 4) Invoke recursive filter job over all pixels
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        pool.invoke(new FilterJob(
                srcPixels, dstPixels, w, h,
                0, w * h,
                ft, mK, logK, dK1, dK2
        ));
        pool.shutdown();

        // 5) Null‐out large buffers so they can be GC’d right away
        mK = null;
        logK = null;
        dK1 = null;
        dK2 = null;

        return dst;
    }

    private static class FilterJob extends RecursiveAction {
        private static final int THRESHOLD = 10_000; // pixels per leaf

        final int[] src, dst;
        final int w, h;
        final int startIdx, endIdx;
        final String filterType;
        final double[][] gK, logK, dK1, dK2;

        FilterJob(
                int[] src, int[] dst, int w, int h,
                int startIdx, int endIdx,
                String filterType,
                double[][] gK, double[][] logK,
                double[][] dK1, double[][] dK2
        ) {
            this.src = src;
            this.dst = dst;
            this.w = w;
            this.h = h;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.filterType = filterType;
            this.gK = gK;
            this.logK = logK;
            this.dK1 = dK1;
            this.dK2 = dK2;
        }

        @Override
        protected void compute() {
            int len = endIdx - startIdx;
            if (len <= THRESHOLD) {
                // leaf: process each pixel
                for (int idx = startIdx; idx < endIdx; idx++) {
                    int y = idx / w, x = idx % w;
                    switch (filterType) {
                        case "blur":
                            dst[idx] = convolvePixel(src, w, h, x, y, gK);
                            break;
                        case "log":
                            dst[idx] = logPixel(src, w, h, x, y, logK);
                            break;
                        case "dog":
                            dst[idx] = dogPixel(src, w, h, x, y, dK1, dK2);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown filter: " + filterType);
                    }
                }
            } else {
                // split range
                int mid = startIdx + len / 2;
                invokeAll(
                        new FilterJob(src, dst, w, h, startIdx, mid, filterType, gK, logK, dK1, dK2),
                        new FilterJob(src, dst, w, h, mid,      endIdx, filterType, gK, logK, dK1, dK2)
                );
            }
        }
    }

    //───────────────────────────────────────────────────────────────────────────

    private static int convolvePixel(
            int[] src, int w, int h, int x, int y, double[][] K
    ) {
        int k = K.length, r = k/2;
        double sr=0, sg=0, sb=0;
        for (int dy=-r; dy<=r; dy++) {
            int yy = clamp(y+dy, 0, h-1);
            int off = yy * w;
            for (int dx=-r; dx<=r; dx++) {
                int xx = clamp(x+dx, 0, w-1);
                double wgt = K[dy+r][dx+r];
                int rgb = src[off + xx];
                sr += wgt * ((rgb>>16)&0xFF);
                sg += wgt * ((rgb>>8 )&0xFF);
                sb += wgt * ( rgb      &0xFF);
            }
        }
        int ir = clamp((int)Math.round(sr), 0, 255);
        int ig = clamp((int)Math.round(sg), 0, 255);
        int ib = clamp((int)Math.round(sb), 0, 255);
        return (ir<<16)|(ig<<8)|ib;
    }

    private static int logPixel(
            int[] src, int w, int h, int x, int y, double[][] K
    ) {
        int k = K.length, r = k/2;
        double acc = 0;
        for (int dy=-r; dy<=r; dy++) {
            int yy = clamp(y+dy, 0, h-1);
            int off = yy * w;
            for (int dx=-r; dx<=r; dx++) {
                int xx = clamp(x+dx, 0, w-1);
                double gray = toGray(src[off + xx]);
                acc += gray * K[dy+r][dx+r];
            }
        }
        int v = clamp((int)(Math.abs(acc)*15), 0, 255);
        return (v<<16)|(v<<8)|v;
    }

    private static int dogPixel(
            int[] src, int w, int h, int x, int y,
            double[][] K1, double[][] K2
    ) {
        int r1 = K1.length/2, r2 = K2.length/2;
        double b1=0, b2=0;
        for (int dy=-r1; dy<=r1; dy++) {
            int yy = clamp(y+dy, 0, h-1), off=yy*w;
            for (int dx=-r1; dx<=r1; dx++) {
                int xx = clamp(x+dx, 0, w-1);
                b1 += toGray(src[off + xx]) * K1[dy+r1][dx+r1];
            }
        }
        for (int dy=-r2; dy<=r2; dy++) {
            int yy = clamp(y+dy, 0, h-1), off=yy*w;
            for (int dx=-r2; dx<=r2; dx++) {
                int xx = clamp(x+dx, 0, w-1);
                b2 += toGray(src[off + xx]) * K2[dy+r2][dx+r2];
            }
        }
        int v = clamp((int)Math.round(Math.abs(b1 - b2)*8.0), 0, 255);
        return (v<<16)|(v<<8)|v;
    }

    private static double toGray(int rgb) {
        return 0.299*((rgb>>16)&0xFF)
                + 0.587*((rgb>>8 )&0xFF)
                + 0.114*( rgb      &0xFF);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Creates a k x k mean filter kernel (all weights = 1/(k*k)). */
    private static double[][] makeMeanKernel(int k) {
        double weight = 1.0 / (k * k);
        double[][] K = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                K[i][j] = weight;
            }
        }
        return K;
    }

    private static double[][] makeGaussianKernel(int k, double sigma) {
        int r = k/2;
        double[][] K = new double[k][k];
        double sum = 0, s2 = sigma*sigma;
        for (int i=-r; i<=r; i++) {
            for (int j=-r; j<=r; j++) {
                double w = Math.exp(-(i*i + j*j)/(2*s2));
                K[i+r][j+r] = w;
                sum += w;
            }
        }
        for (int i=0; i<k; i++) {
            for (int j=0; j<k; j++) {
                K[i][j] /= sum;
            }
        }
        return K;
    }

    private static double[][] makeLoGKernel(int r, double sigma) {
        int k = 2*r + 1;
        double[][] K = new double[k][k];
        double s2 = sigma*sigma, s4 = s2*s2;
        double factor = 1.0 / (Math.PI * s4);
        for (int y=-r; y<=r; y++) {
            for (int x=-r; x<=r; x++) {
                double r2 = x*x + y*y;
                double norm = (r2 - 2*s2) / s4;
                double gauss = Math.exp(-r2 / (2*s2));
                K[y+r][x+r] = factor * norm * gauss;
            }
        }
        return K;
    }
}
