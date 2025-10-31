package br.com.zaya.flow.events;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinhaDigitavelExtractor {

    public static String extraiCodBarras(String boletoTexto) throws Exception {
        String txt = sanitize(boletoTexto);

        Pattern p1 = Pattern.compile(
                "\\b\\d{5}[\\.\\- ]?\\d{5}(?:[\\.\\- ]?\\d{5}[\\.\\- ]?\\d{6}){2}[\\.\\- ]?\\d[\\.\\- ]?\\d{14}\\b"
        );
        String c = findAndClean(p1, txt);
        if (c != null) return c;

        Pattern p2 = Pattern.compile(
                "\\b(?:(?:\\d{11}[\\s\\-]?\\d)[\\s\\-]?){4}\\b"
        );
        c = findAndClean(p2, txt);
        if (c != null) return c;

        Pattern p3 = Pattern.compile("[\\d\\.\\-\\s]{44,100}");
        Matcher m3 = p3.matcher(txt);
        while (m3.find()) {
            String only = m3.group().replaceAll("\\D+", "");
            if (only.length()==44 || only.length()==48) {
                return only;
            }
        }

        throw new Exception("N�o foi poss�vel ler o código de barras do texto.");
    }

    private static String sanitize(String s) {
        return s == null ? "" :
                s.replace("\r", " ")
                        .replace("\n", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private static String findAndClean(Pattern p, String txt) {
        Matcher m = p.matcher(txt);
        if (m.find()) {
            return m.group().replaceAll("\\D+", "");
        }
        return null;
    }
}