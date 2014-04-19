package com.github.fommil.lion.agent;

import com.google.common.io.Closeables;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.Map.Entry;

@RequiredArgsConstructor
public class AllocationPrinter implements Runnable {

    private final AllocationSampler sampler;
    private final File out;

    private volatile Date from = new Date();

    @Override
    public synchronized void run() {
        Date to = new Date();
        Map<String, List<StackTraceElement[]>> traces = sampler.snapshotTraces();
        Map<String, Map<Integer, Long>> lengths = sampler.snapshotArrayLengths();
        Map<String, Long> sizes = sampler.snapshotTotalBytes();
        sampler.clear();

        try {
            Writer writer = null;
            try {
                writer = new FileWriter(out, true);
                writeSizes(writer, sizes, to);
                if (!traces.isEmpty())
                    writeStackTraces(writer, traces, to);
                if (!lengths.isEmpty())
                    writeLengths(writer, lengths, to);
                writer.flush();
            } finally {
                from = to;
                Closeables.close(writer, true);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeSizes(Writer writer,
                            Map<String, Long> sizes,
                            Date to) throws IOException {
        writeHeader(writer, "sizes", to);

        writer.append("\"sizes\":{");
        Iterator<Entry<String, Long>> it = sizes.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Long> entry = it.next();
            writeKeyValue(writer, entry.getKey(), entry.getValue());
            if (it.hasNext())
                writer.append(",");
        }
        writer.append("}");

        writeFooter(writer);
    }

    private void writeLengths(Writer writer,
                              Map<String, Map<Integer, Long>> lengths,
                              Date to) throws IOException {
        writeHeader(writer, "lengths", to);

        writer.append("\"lengths\":{");
        Iterator<Entry<String, Map<Integer, Long>>> it = lengths.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Map<Integer, Long>> entry = it.next();
            writer.append("\"").append(entry.getKey()).append("\":");

            writer.append("[");
            Iterator<Entry<Integer, Long>> inner = entry.getValue().entrySet().iterator();
            while (inner.hasNext()) {
                Entry<Integer, Long> sample = inner.next();
                writer.append("{");
                writeKeyValue(writer, "length", sample.getKey());
                writer.append(",");
                writeKeyValue(writer, "count", sample.getValue());
                writer.append("}");
                if (inner.hasNext())
                    writer.append(",");
            }
            writer.append("]");

            if (it.hasNext())
                writer.append(",");
        }
        writer.append("}");

        writeFooter(writer);
    }

    private void writeStackTraces(Writer writer,
                                  Map<String, List<StackTraceElement[]>> traces,
                                  Date to) throws IOException {
        writeHeader(writer, "traces", to);

        writer.append("\"traces\":{");
        Iterator<Entry<String, List<StackTraceElement[]>>> it = traces.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, List<StackTraceElement[]>> entry = it.next();
            writer.append("\"").append(entry.getKey()).append("\":");

            writer.append("[");
            Iterator<StackTraceElement[]> inner = entry.getValue().iterator();
            while (inner.hasNext()) {
                StackTraceElement[] trace = inner.next();
                writeTrace(writer, trace);
                if (inner.hasNext())
                    writer.append(",");
            }
            writer.append("]");

            if (it.hasNext())
                writer.append(",");
        }
        writer.append("}");

        writeFooter(writer);
    }

    private void writeHeader(Writer writer, String typeHint, Date to) throws IOException {
        writer.append("{");
        writeKeyValue(writer, "typeHint", typeHint);
        writer.append(",");
        writer.append("\"interval\":{\"from\":{");
        writeKeyValue(writer, "instant", from.getTime());
        writer.append("},\"to\":{");
        writeKeyValue(writer, "instant", to.getTime());
        writer.append("}}");
        writer.append(",");
    }

    private void writeFooter(Writer writer) throws IOException {
        writer.append("}\n");
    }

    private void writeTrace(Writer writer, StackTraceElement[] trace) throws IOException {
        writer.append("[");

        for (int i = 0; i < trace.length; i++) {
            writer.append("{");

            StackTraceElement frame = trace[i];
            writeKeyValue(writer, "clazz", frame.getClassName());
            writer.append(",");
            writeKeyValue(writer, "method", frame.getMethodName());
            if (frame.getFileName() != null) {
                writer.append(",");
                writeKeyValue(writer, "file", frame.getFileName());
            }
            if (frame.getLineNumber() >= 0) {
                writer.append(",");
                writeKeyValue(writer, "line", frame.getLineNumber());
            }

            writer.append("}");
            if (i < trace.length - 1)
                writer.append(",");
        }

        writer.append("]");
    }

    private void writeKeyValue(Writer writer, String key, String value) throws IOException {
        writer.append("\"").append(key).append("\":").append("\"").append(value).append("\"");
    }

    private void writeKeyValue(Writer writer, String key, long value) throws IOException {
        writer.append("\"").append(key).append("\":").append(Long.toString(value));
    }
}
