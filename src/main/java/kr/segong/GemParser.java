package kr.segong;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 아이템 설명(툴팁) 글자에서 순도/광도/강도와 등급을 읽는다. Minecraft 코드와 무관. */
public final class GemParser {
    private static final Pattern PURITY = Pattern.compile("순도\\s*[:：]\\s*(\\d{1,3})");
    private static final Pattern LUSTER = Pattern.compile("광도\\s*[:：]\\s*(\\d{1,3})");
    private static final Pattern STRENGTH = Pattern.compile("강도\\s*[:：]\\s*(\\d{1,3})");

    private GemParser() {}

    /** 세 값이 모두 있으면 {순도, 광도, 강도}, 하나라도 없으면 null (보석이 아닌 아이템) */
    public static int[] parseStats(List<String> lines) {
        Integer p = null, l = null, s = null;
        for (String line : lines) {
            if (line == null) continue;
            if (p == null) p = find(PURITY, line);
            if (l == null) l = find(LUSTER, line);
            if (s == null) s = find(STRENGTH, line);
        }
        if (p == null || l == null || s == null) return null;
        return new int[]{p, l, s};
    }

    /** 위쪽 3줄에서 COMMON/NORMAL/RARE를 찾아 한도(200/212/236)를 반환. 못 찾으면 0 */
    public static int parseGradeLimit(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, lines.size()); i++) sb.append(lines.get(i)).append(' ');
        String up = sb.toString().toUpperCase();
        if (up.contains("COMMON")) return 200;
        if (up.contains("NORMAL")) return 212;
        if (up.contains("RARE")) return 236;
        return 0;
    }

    private static Integer find(Pattern pt, String line) {
        Matcher m = pt.matcher(line);
        if (!m.find()) return null;
        return Integer.parseInt(m.group(1));
    }
}
