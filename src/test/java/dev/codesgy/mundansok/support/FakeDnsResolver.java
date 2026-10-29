package dev.codesgy.mundansok.support;

import dev.codesgy.mundansok.net.DnsResolver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeDnsResolver implements DnsResolver {

    private final Map<String, List<String>> records = new ConcurrentHashMap<>();
    private final Map<String, List<String>> caaRecords = new ConcurrentHashMap<>();
    private volatile boolean failing;

    public FakeDnsResolver txt(String name, String... values) {
        records.put(name, List.of(values));
        return this;
    }

    public FakeDnsResolver caa(String name, String... values) {
        caaRecords.put(name, List.of(values));
        return this;
    }

    public FakeDnsResolver failing(boolean failing) {
        this.failing = failing;
        return this;
    }

    public void clear() {
        records.clear();
        caaRecords.clear();
        failing = false;
    }

    @Override
    public List<String> txt(String name) {
        if (failing) throw new IllegalStateException("DNS 조회 실패: " + name);
        return records.getOrDefault(name, List.of());
    }

    @Override
    public List<String> caa(String name) {
        if (failing) throw new IllegalStateException("DNS 조회 실패: " + name);
        return caaRecords.getOrDefault(name, List.of());
    }
}
