package dev.codesgy.mundansok.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JndiDnsResolverTest {

    @Test
    void CAA_RDATA를_사람이_읽는_형식으로_바꾼다() {
        ByteArrayOutputStream rdata = new ByteArrayOutputStream();
        rdata.write(0);
        rdata.write(5);
        rdata.writeBytes("issue".getBytes(StandardCharsets.US_ASCII));
        rdata.writeBytes("letsencrypt.org".getBytes(StandardCharsets.US_ASCII));

        assertThat(JndiDnsResolver.caaText(rdata.toByteArray())).isEqualTo("0 issue \"letsencrypt.org\"");
    }

    @Test
    void 나뉜_TXT_값을_하나로_합친다() {
        assertThat(JndiDnsResolver.unquote("\"v=spf1 include:a\" \" -all\"")).isEqualTo("v=spf1 include:a -all");
    }
}
