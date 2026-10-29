package dev.codesgy.mundansok.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Function;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

/** JDK 내장 JNDI DNS 공급자로 레코드를 읽는다 (추가 의존성 없음) */
@Component
public class JndiDnsResolver implements DnsResolver {

    // JNDI 는 CAA 라는 이름을 모른다. 타입 번호로 물으면 RDATA 를 바이트 그대로 준다
    private static final String CAA_TYPE = "257";

    @Override
    public List<String> txt(String name) {
        return query(name, "TXT", value -> unquote(String.valueOf(value)));
    }

    @Override
    public List<String> caa(String name) {
        return query(name, CAA_TYPE, value -> value instanceof byte[] bytes ? caaText(bytes) : String.valueOf(value));
    }

    private List<String> query(String name, String type, Function<Object, String> decode) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");
        try {
            InitialDirContext context = new InitialDirContext(env);
            try {
                Attributes attributes = context.getAttributes(name, new String[] {type});
                Attribute records = attributes.get(type);
                if (records == null) return List.of();
                List<String> values = new ArrayList<>();
                NamingEnumeration<?> all = records.getAll();
                while (all.hasMore()) values.add(decode.apply(all.next()));
                return values;
            } finally {
                context.close();
            }
        } catch (NameNotFoundException e) {
            return List.of();
        } catch (NamingException e) {
            throw new IllegalStateException("DNS 조회 실패: " + name, e);
        }
    }

    // TXT 값은 "조각1" "조각2"처럼 따옴표로 나뉘어 올 수 있어 하나로 합친다
    static String unquote(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("\"")) return trimmed;
        StringBuilder joined = new StringBuilder();
        for (String part : trimmed.split("\"\\s*\"")) joined.append(part.replace("\"", ""));
        return joined.toString();
    }

    // CAA RDATA: 플래그 1바이트, 태그 길이 1바이트, 태그, 나머지가 값 (RFC 8659)
    static String caaText(byte[] rdata) {
        int flags = rdata[0] & 0xff;
        int tagLength = rdata[1] & 0xff;
        String tag = new String(rdata, 2, tagLength, StandardCharsets.US_ASCII);
        String value = new String(rdata, 2 + tagLength, rdata.length - 2 - tagLength, StandardCharsets.US_ASCII);
        return flags + " " + tag + " \"" + value + "\"";
    }
}
