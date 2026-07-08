package com.waj.tool.report;

import com.waj.tool.model.ApSnapshot;
import com.waj.tool.security.SecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportGeneratorTest {

    @Test
    void generatesAValidPdfUsingBizUdOrCjkFallbackForJapaneseText(@TempDir Path tempDir) throws Exception {
        // The PDF generator now prefers Windows BIZ UD fonts when available, and falls back to a
        // CJK-safe built-in base font otherwise. Verify structurally that a composite CJK-capable
        // font mapping is present and one of the expected font families is referenced.
        ApSnapshot ap = new ApSnapshot("日本語テストSSID", "AA:BB:CC:DD:EE:FF", 36, 5180000, "5GHz",
                -50, 90, "802.11ac", true, SecurityType.WPA2, null, Instant.now());
        ReportData data = new ReportData(Instant.now(), "テストインターフェース",
                List.of(ap), List.of(), null);

        File out = tempDir.resolve("report.pdf").toFile();
        PdfReportGenerator.generate(data, out);

        assertTrue(out.length() > 0, "PDF file should not be empty");

        // Read as Latin-1 (byte-transparent for any 8-bit value) so the object-name literals in
        // the PDF's own internal structure (font dictionaries, etc.) can be matched as plain text
        // regardless of surrounding compressed/binary stream data elsewhere in the file.
        String raw = Files.readString(out.toPath(), StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("/Type0"),
                "PDF should declare a Type0 (composite/CID-keyed) font for the CJK text");
        assertTrue(
                raw.contains("BIZUD") || raw.contains("HeiseiKakuGo-W5"),
                "PDF should reference either a BIZ UD font or the CJK fallback font");
    }
}
