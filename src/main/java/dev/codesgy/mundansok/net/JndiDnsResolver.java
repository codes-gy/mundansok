package dev.codesgy.mundansok.net;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

/** JDK 내장 JNDI DNS 공급자로 TXT 레코드를 읽는다 (추가 의존성 없음) */
@Component
public class JndiDnsResolver implements DnsResolver {

    @Override
    public List<String> txt(String name) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");
        try {
            InitialDirContext context = new InitialDirContext(env);
            try {
                Attributes attributes = context.getAttributes(name, new String[] {"TXT"});
                Attribute txt = attributes.get("TXT");
                if (txt == null) return List.of();
                List<String> values = new ArrayList<>();
                NamingEnumeration<?> all = txt.getAll();
                while (all.hasMore()) values.add(unquote(String.valueOf(all.next())));
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
}
