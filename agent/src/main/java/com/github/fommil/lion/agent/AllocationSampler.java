package com.github.fommil.lion.agent;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.monitoring.runtime.instrumentation.Sampler;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.common.base.Preconditions.checkNotNull;

public class AllocationSampler implements Sampler {

    private final Map<String, Long> trackSources;
    private final AtomicLongMap<String> totalBytes = AtomicLongMap.create();

    private final AtomicLongMap<String> lastSampledSize = AtomicLongMap.create();
    private final Map<String, ConcurrentLinkedQueue<StackTraceElement[]>> traces = Maps.newConcurrentMap();
    private final Map<String, AtomicLongMap<Integer>> arrayLengths = Maps.newConcurrentMap();

    public AllocationSampler(Map<String, Long> trackSources,
                             Set<String> trackLengths) {
        checkNotNull(trackSources, "trackSources");
        checkNotNull(trackLengths, "trackLengths");

        this.trackSources = trackSources;
        for (Map.Entry<String, Long> track : trackSources.entrySet())
            traces.put(track.getKey(), Queues.<StackTraceElement[]>newConcurrentLinkedQueue());
        for (String name : trackLengths)
            arrayLengths.put(name, AtomicLongMap.<Integer>create());
    }

    // not atomic, so some information may be lost
    // and we may over- or under- sample
    public void clear() {
        totalBytes.clear();
        lastSampledSize.clear();
        for (Queue<?> el : traces.values())
            el.clear();
        for (AtomicLongMap<?> el : arrayLengths.values())
            el.clear();
    }

    @Override
    public void sampleAllocation(int count, String name, Object instance, long sizeBytes) {
        if (count != -1) {
            AtomicLongMap<Integer> lengths = arrayLengths.get(name);
            if (lengths != null)
                lengths.getAndIncrement(count);
        }

        Long threshold = trackSources.get(name);
        Long now = totalBytes.addAndGet(name, sizeBytes);

        if (threshold != null) {
            // if this whole lookup/check/put could be made atomic, that'd be awesome
            if (now - threshold >= lastSampledSize.get(name)) {
                lastSampledSize.put(name, now);
                // eek, object creation getting the trace
                StackTraceElement[] trace = AllocationEfficientStacktrace.stack(2, 50);
                traces.get(name).offer(trace);
            }
        }
    }

    ////////////////////////////////////////////////////////
    // the following methods are not part of the sample path
    ////////////////////////////////////////////////////////

    public Map<String, Long> snapshotTotalBytes() {
        return Maps.newHashMap(totalBytes.asMap());
    }

    public Map<String, List<StackTraceElement[]>> snapshotTraces() {
        Map<String, List<StackTraceElement[]>> snapshot = Maps.newHashMap();
        for (Map.Entry<String, ConcurrentLinkedQueue<StackTraceElement[]>> entry : traces.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            List<StackTraceElement[]> samples = Lists.newArrayList(entry.getValue());
            snapshot.put(entry.getKey(), samples);
        }
        return snapshot;
    }

    public Map<String, Map<Integer, Long>> snapshotArrayLengths() {
        Map<String, Map<Integer, Long>> snapshot = Maps.newHashMap();
        for (Map.Entry<String, AtomicLongMap<Integer>> entry : arrayLengths.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            Map<Integer, Long> lengths = Maps.newHashMap(entry.getValue().asMap());
            snapshot.put(entry.getKey(), lengths);
        }
        return snapshot;
    }
}
