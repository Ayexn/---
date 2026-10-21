package kr.segong;

import net.minecraft.client.gui.DrawContext;

/**
 * 다크 약방 디자인 (갈색 판 + 주황 조각 액자 + 금 못 + 리본 제목 + 알약 버튼 + 둥근 메달).
 * 그림 파일 없이 사각형(fill)만으로 그린다.
 */
final class Apo {
    private Apo() {}

    // 액자
    static final int OUT = 0xFF3A1C0A, BAND = 0xFFC0702A, BAND_HI = 0xFFE8A050, BAND_SH = 0xFF7A3E12, OUT2 = 0xFF5A2E12;
    static final int CURL = 0xFFC0702A, GOLD = 0xFFF0C050, GOLD_HI = 0xFFFFF0A0;
    // 판
    static final int VIG = 0xFF3A2012, FILL = 0xFF4F2F1D;
    // 오목한 칸
    static final int S_FILL = 0xFF2E180C, S_OUT = 0xFF1A0C04, S_RIM = 0xFFB8641E, S_SHADOW = 0xFF1F0F06;
    // 리본 / 배지
    static final int B_FILL = 0xFFF6E6BC, B_TXT = 0xFF4A2C14;
    static final int P_FILL = 0xFFE0B060, P_HI = 0xFFFFF0B0, P_LO = 0xFFA87A28, P_TXT = 0xFF3A2410;
    // 글자
    static final int TXT = 0xFFFBE9C0, SUB = 0xFFD8B888, OK = 0xFFBFF08A, INFO = 0xFFF0C860, RED = 0xFFFFB0A0;
    // 막대
    static final int TRACK = 0xFF2E180C, SLIDER = 0xFFE08A2A;
    // 버튼
    static final int BTN = 0xFF6A4228, BTN_HI = 0xFF8A5C3A, BTN_TX = TXT;
    static final int SEL = 0xFFE0B060, SEL_HI = 0xFFFFF0B0, SEL_TX = 0xFF3A2410;
    static final int RD = 0xFF9A3B2A, RD_HI = 0xFFD98A78, RD_LO = 0xFF5A2018, RD_OUT = 0xFF3A1008;

    /** 모서리를 n 칸 깎은 사각형 (n = 1 또는 2) */
    static void notch(DrawContext c, int x, int y, int w, int h, int color, int n) {
        c.fill(x + n, y, x + w - n, y + h, color);
        if (n >= 2) c.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        c.fill(x, y + n, x + w, y + h - n, color);
    }

    /** 픽셀 원 (지름 d, 가운데 cx, cy) */
    static void circle(DrawContext c, int cx, int cy, int d, int color) {
        double rad = d / 2.0;
        for (int i = 0; i < d; i++) {
            double yc = i + 0.5 - rad;
            double half = Math.sqrt(Math.max(0.0, rad * rad - yc * yc));
            int w = (int) Math.round(2 * half);
            if ((w & 1) != (d & 1)) w++;
            if (w > d) w = d;
            int x = cx - d / 2 + (d - w) / 2;
            int y = cy - d / 2 + i;
            c.fill(x, y, x + w, y + 1, color);
        }
    }

    /** 둥근 메달 (바깥 테두리 + 주황 고리 + 안쪽 어두운 바탕 + 가운데 색) */
    static void medal(DrawContext c, int cx, int cy, int d, int color) {
        circle(c, cx, cy, d, OUT);
        circle(c, cx, cy, d - 2, S_RIM);
        circle(c, cx, cy, d - 4, S_OUT);
        circle(c, cx, cy, d - 6, color);
        c.fill(cx - 1, cy - 1, cx, cy, 0xAAFFFFFF);
    }

    /** 작은 메달 (통계 줄 앞) */
    static void dot(DrawContext c, int cx, int cy, int color) {
        circle(c, cx, cy, 7, OUT);
        circle(c, cx, cy, 5, S_RIM);
        circle(c, cx, cy, 3, color);
    }

    /** 작은 마름모 (금색) */
    static void diamond(DrawContext c, int cx, int cy) {
        c.fill(cx, cy - 2, cx + 1, cy - 1, GOLD);
        c.fill(cx - 1, cy - 1, cx + 2, cy, GOLD);
        c.fill(cx - 2, cy, cx + 3, cy + 1, GOLD);
        c.fill(cx - 1, cy + 1, cx + 2, cy + 2, GOLD);
        c.fill(cx, cy + 2, cx + 1, cy + 3, GOLD);
        c.fill(cx, cy, cx + 1, cy + 1, GOLD_HI);
    }

    private static void stud(DrawContext c, int x, int y) {
        notch(c, x, y, 5, 5, OUT, 1);
        c.fill(x + 1, y + 1, x + 4, y + 4, GOLD);
        c.fill(x + 1, y + 1, x + 2, y + 2, GOLD_HI);
    }

    /**
     * 조각 액자 판. compact=true 면 얇은 틀(한 줄형·슬롯 배지형용),
     * false 면 두꺼운 틀 + 모서리 장식 + 가운데 못.
     */
    static void frame(DrawContext c, int w, int h, boolean compact) {
        notch(c, 0, 0, w, h, OUT, 1);
        notch(c, 1, 1, w - 2, h - 2, BAND_SH, 1);
        c.fill(1, 1, w - 2, h - 2, BAND_HI);
        c.fill(2, 2, w - 2, h - 2, BAND);
        if (compact) {
            c.fill(4, 4, w - 4, h - 4, OUT2);
            c.fill(5, 5, w - 5, h - 5, FILL);
            stud(c, 0, 0); stud(c, w - 5, 0); stud(c, 0, h - 5); stud(c, w - 5, h - 5);
            return;
        }
        c.fill(5, 5, w - 5, h - 5, OUT2);
        c.fill(6, 6, w - 6, h - 6, VIG);
        c.fill(8, 8, w - 8, h - 8, FILL);
        // 모서리 장식 (ㄱ자 두 줄)
        int[][] cs = {{0, 0, 1, 1}, {w - 1, 0, -1, 1}, {0, h - 1, 1, -1}, {w - 1, h - 1, -1, -1}};
        int[][] ls = {{10, 6}, {12, 3}};
        for (int[] k : cs) {
            for (int[] l : ls) {
                int x1 = k[0] + k[2] * l[0], y1 = k[1] + k[3] * l[0];
                int hx = k[0] + k[2] * (l[0] + l[1] - 1), vy = k[1] + k[3] * (l[0] + l[1] - 1);
                c.fill(Math.min(x1, hx), y1, Math.max(x1, hx) + 1, y1 + 1, CURL);
                c.fill(x1, Math.min(y1, vy), x1 + 1, Math.max(y1, vy) + 1, CURL);
            }
        }
        stud(c, 0, 0); stud(c, w - 5, 0); stud(c, 0, h - 5); stud(c, w - 5, h - 5);
        stud(c, w / 2 - 2, h - 5); stud(c, 0, h / 2 - 2); stud(c, w - 5, h / 2 - 2);
    }

    /** 양 끝이 V자로 파인 리본 (제목용) */
    static void banner(DrawContext c, int x, int y, int w, int h) {
        double mid = (h - 1) / 2.0;
        for (int i = 0; i < h; i++) {
            int d = 3 - (int) Math.round(Math.abs(i - mid) * 3.0 / mid);
            if (d < 0) d = 0;
            c.fill(x + d, y + i, x + w - d, y + i + 1, OUT);
            if (i >= 1 && i <= h - 2) c.fill(x + d + 1, y + i, x + w - d - 1, y + i + 1, BAND);
            if (i >= 2 && i <= h - 3) c.fill(x + d + 2, y + i, x + w - d - 2, y + i + 1, B_FILL);
        }
    }

    /** 알약 모양 (배지·버튼 공통) */
    static void pill(DrawContext c, int x, int y, int w, int h, int fill, int out, int hi, int lo, int n) {
        notch(c, x, y, w, h, out, n);
        notch(c, x + 1, y + 1, w - 2, h - 2, fill, n);
        c.fill(x + 1, y + 1, x + w - 2, y + 2, hi);
        if (lo != 0) c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
    }

    /** 오목하게 들어간 어두운 칸 (주황 테두리) */
    static void slot(DrawContext c, int x, int y, int w, int h) {
        notch(c, x, y, w, h, S_OUT, 1);
        notch(c, x + 1, y + 1, w - 2, h - 2, S_RIM, 1);
        notch(c, x + 2, y + 2, w - 4, h - 4, S_FILL, 1);
        c.fill(x + 2, y + 2, x + w - 2, y + 3, S_SHADOW);
        c.fill(x + 2, y + 2, x + 3, y + h - 2, S_SHADOW);
    }

    /** 막대 틀 (끝에 작은 화살표) */
    static void track(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, S_OUT);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, TRACK);
    }
}
