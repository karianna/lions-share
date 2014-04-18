package com.github.fommil.lion.agent;

import com.google.common.io.Closeables;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class AllocationPrinter implements Runnable {

    private final AllocationSampler sampler;
    private final File out;

    private volatile Date last = new Date();

    public AllocationPrinter(AllocationSampler sampler, File out) {
        this.sampler = sampler;
        this.out = out;
    }

    @Override
    public void run() {
        Map<String, List<StackTraceElement[]>> traces = sampler.snapshotTraces();
        Map<String, Map<Integer, Long>> lengths = sampler.snapshotArrayLengths();
        Map<String, Long> sizes = sampler.snapshotTotalBytes();
        sampler.clear();

        try {
            Writer writer = null;
            try {
                writer = new FileWriter(out);
                writeSizes(writer, sizes);
                if (!traces.isEmpty())
                    writeStackTraces(writer, traces);
                if (!lengths.isEmpty())
                    writeLengths(writer, lengths);
            } finally {
                last = new Date();
                Closeables.close(writer, true);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeSizes(Writer writer, Map<String, Long> sizes) {
        // TODO
        System.out.println("writing sizes for " + sizes.size() + " objects");
    }

    private void writeLengths(Writer writer, Map<String, Map<Integer, Long>> lengths) {
        // TODO
        System.out.println("writing array lengths for " + lengths.size() + " objects");
    }

    private void writeStackTraces(Writer writer, Map<String, List<StackTraceElement[]>> traces) {
        // TODO
        System.out.println("writing stack traces for " + traces.size() + " objects");
    }
}
