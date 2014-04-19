package com.github.fommil.lion.agent;

import com.google.common.collect.Maps;
import com.google.monitoring.runtime.instrumentation.AllocationInstrumenter;
import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.SECONDS;

public class AllocationAgent {
    private static final ThreadFactory daemon = new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        }
    };

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(daemon);

    public static void premain(String agentArgs, Instrumentation inst) {
        String[] args = agentArgs.split(" ");

        String filename = args[0];
        File outFile = new File(filename);
        if (outFile.delete())
            out.println("[AGENT] Deleted an existing " + outFile.getAbsolutePath());

        Long period = Long.parseLong(args[1]);

        out.println("[AGENT] Writing allocation data to " + outFile.getAbsolutePath());
        out.println("[AGENT] Taking snapshots every " + period + " seconds");

        Map<String, Long> rates = Maps.newHashMap();
        if (args.length > 2)
            for (String arg : args[2].split(",")) {
                String[] parts = arg.split(":");
                Long sampleRate = Long.parseLong(parts[1]);
                out.println("[AGENT] " + parts[0] + " will be sampled every " + sampleRate + " bytes");
                rates.put(parts[0], sampleRate);
            }

        AllocationInstrumenter.premain(agentArgs, inst);
        AllocationSampler sampler = new AllocationSampler(rates, rates.keySet());
        AllocationRecorder.addSampler(sampler);

        AllocationPrinter collector = new AllocationPrinter(sampler, outFile);
        executor.scheduleWithFixedDelay(collector, period, period, SECONDS);
    }

}
