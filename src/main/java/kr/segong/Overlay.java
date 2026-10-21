package kr.segong;

import kr.segong.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

/** 추천 결과를 화면에 그리는 3가지 방식 (A 카드형 / B 한 줄형 / C 슬롯 배지형). */
final class Overlay {
    static final int VIO = 0xFFB06CF0, OPA = 0xFF5FD0E8, ADA = 0xFFCFD6E4;
    static final int GREEN = 0xFF7BE495, GRAY = 0xFF9AA3B8, YEL = 0xFFFFD76A, WHITE = 0xFFFFFFFF;
    static final int[] COL = {VIO, OPA, ADA};
    static final String[] NAME = {"바이올렛", "오팔", "아다만티움"};
    static final String[] LABEL = {"순도", "광도", "강도"};

    // 마지막으로 그린 카드의 위치(화면 좌표)와 끌기 상태
    private static int hitX, hitY, hitW, hitH;
    private static boolean dragging, prevDown;
    private static int grabDX, grabDY;
    // "오늘 세공 횟수" 알약을 카드와 따로 띄울 때: 알약의 위치와 끌기 상태 (카드와 별개)
    private static int tHitX, tHitY, tHitW, tHitH;
    private static boolean tDragging, tPrevDown;
    private static int tGrabDX, tGrabDY;
    private static int cardBottom;      // 마지막으로 그린 카드의 아래쪽 y (한 줄형일 때 알약 기본 위치용)

    private Overlay() {}

    /** 그리기 도구. on=false 이면 크기만 재고 실제로는 그리지 않는다. */
    private static final class Pen {
        final TextRenderer tr; final DrawContext ctx; final boolean on;
        Pen(TextRenderer tr, DrawContext ctx, boolean on) { this.tr = tr; this.ctx = ctx; this.on = on; }
        void rect(int x1, int y1, int x2, int y2, int c) { if (on) ctx.fill(x1, y1, x2, y2, c); }
        int text(String s, int x, int y, int c) { if (on) ctx.drawText(tr, s, x, y, c, true); return tr.getWidth(s); }
        void textRight(String s, int right, int y, int c) { text(s, right - tr.getWidth(s), y, c); }
        /** 그림자 없는 글자 (밝은 바탕용) */
        int plain(String s, int x, int y, int c) { if (on) ctx.drawText(tr, s, x, y, c, false); return tr.getWidth(s); }
        void plainCenter(String s, int cx, int y, int c) { plain(s, cx - tr.getWidth(s) / 2, y, c); }
        /** 크게 키운 글자 (왼쪽 x 기준) */
        void scaled(String s, int x, int y, int c, float k) {
            if (!on) return;
            MatrixStack m = ctx.getMatrices();
            m.push();
            try {
                m.translate((float) x, (float) y, 0f);
                m.scale(k, k, 1f);
                ctx.drawText(tr, s, 0, 0, c, false);
            } finally {
                m.pop();
            }
        }
        void bigRight(String s, int right, int y, int c) {
            if (!on) return;
            int w = tr.getWidth(s) * 2;
            MatrixStack m = ctx.getMatrices();
            m.push();
            try {
                m.translate((float) (right - w), (float) y, 0f);
                m.scale(2f, 2f, 1f);
                ctx.drawText(tr, s, 0, 0, c, true);
            } finally {
                m.pop();
            }
        }
        /** 아이템 그림 (가운데 cx, cy 기준, 16칸 그림을 k배로 줄여 그린다) */
        void item(ItemStack st, int cx, int cy, float k) {
            if (!on) return;
            MatrixStack m = ctx.getMatrices();
            m.push();
            try {
                m.translate(cx - 8f * k, cy - 8f * k, 0f);
                m.scale(k, k, 1f);
                ctx.drawItem(st, 0, 0);
            } catch (Throwable e) {
                SegongClient.iconFailed(e);     // 아이콘만 끄고 나머지는 계속 그린다
            } finally {
                m.pop();
            }
        }
        void frame(int w, int h) {
            rect(0, 0, w, h, 0xE0101420);
            rect(0, 0, w, 1, 0x40FFFFFF); rect(0, h - 1, w, h, 0x40FFFFFF);
            rect(0, 0, 1, h, 0x40FFFFFF); rect(w - 1, 0, w, h, 0x40FFFFFF);
        }
    }

    static String gradeLabel(int lim) {
        return switch (lim) {
            case 200 -> "COMMON 200";
            case 212 -> "NORMAL 212";
            case 236 -> "RARE 236";
            default -> String.valueOf(lim);
        };
    }

    static void clearHit() { hitW = 0; hitH = 0; tHitW = 0; tHitH = 0; }

    /** 광채 그림 (설정이 꺼져 있거나 인벤토리에서 못 봤으면 null) */
    private static ItemStack icon(int k) {
        return SegongClient.iconFor(k);
    }

    /** 오른쪽 아래 문구: 보유가 모자라면 부족 개수, 쓸 광채가 없으면 안내 */
    static String usedLabel(SegongClient.View v) {
        if (v.r.missing > 0) return "광채 " + v.r.missing + "개 부족";
        return v.r.n == 0 ? "추가 필요 없음" : "광채 " + v.r.n + "개";
    }

    /** 오른쪽 아래 문구 색: 모자라면 빨간색 */
    static int usedCol(SegongClient.View v, int normal, int red) {
        return v.r.missing > 0 ? red : normal;
    }

    /** 오늘 세공 횟수 문구 (v.tally 가 0 이상일 때만 표시) */
    static String tallyLabel(SegongClient.View v) {
        return "오늘 세공 " + v.tally + "회";
    }

    /** 가운데 cx, 위 y 에 놓는 "오늘 세공 N회" 알약 (높이 14). 다크 약방은 등급 알약과 같은 금색. */
    private static void tallyChip(Pen p, SegongClient.View v, int cx, int y) {
        chipAt(p, tallyLabel(v), cx, y);
    }

    private static void chipAt(Pen p, String s, int cx, int y) {
        int w = p.tr.getWidth(s) + 14, x = cx - w / 2;
        if (SegongClient.theme == 0) {
            if (p.on) Apo.pill(p.ctx, x, y, w, 14, Apo.P_FILL, Apo.OUT, Apo.P_HI, Apo.P_LO, 1);
            p.plain(s, x + 7, y + 3, Apo.P_TXT);
        } else {
            p.rect(x, y, x + w, y + 14, SegongClient.theme == 1 ? W_OUT : T_EDGE_OUT);
            p.rect(x + 1, y + 1, x + w - 1, y + 13, T_GOLD);
            p.plain(s, x + 7, y + 3, T_EDGE_OUT);
        }
    }

    /** 추천 개수가 가진 개수와 같거나 많으면(= 다 쓰는 경우) 보유 글자를 주황색으로 */
    static int stockCol(SegongClient.View v, int k, int normal) {
        if (v.r == null) return normal;
        int c = k == 0 ? v.r.a : k == 1 ? v.r.b : v.r.c;
        if (c <= 0 || c < v.stock[k]) return normal;
        boolean light = SegongClient.theme == 2 && SegongClient.style == 0;    // 밝은 바탕(크림 타일형)
        // 광채가 모자라서 한도까지 못 채우는데, 이 광채가 더 있었다면 더 쓸 수 있는 경우는 빨간색
        if (v.r.missing > 0 && v.stats[k] + c * 10 < 100) return light ? T_RED : 0xFFFF9A8A;
        return light ? 0xFFB86A00 : 0xFFFFC060;
    }

    static boolean hit(double mx, double my) {
        return (hitW > 0 && mx >= hitX && mx < hitX + hitW && my >= hitY && my < hitY + hitH) || tHit(mx, my);
    }

    private static boolean tHit(double mx, double my) {
        return tHitW > 0 && mx >= tHitX && mx < tHitX + tHitW && my >= tHitY && my < tHitY + tHitH;
    }

    static boolean isDragging() { return dragging || tDragging; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static void render(MinecraftClient mc, DrawContext ctx, SegongClient.View v, int mx, int my, boolean interactive) {
        float s = SegongClient.scale;
        int style = SegongClient.style;
        int[] sz = layout(new Pen(mc.textRenderer, ctx, false), v, style, null);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int pw = Math.round(sz[0] * s), ph = Math.round(sz[1] * s);
        int[] xy;
        if (SegongClient.customX >= 0 && SegongClient.customY >= 0) {
            xy = new int[]{clamp(Math.round(SegongClient.customX * sw), 0, Math.max(0, sw - pw)),
                           clamp(Math.round(SegongClient.customY * sh), 0, Math.max(0, sh - ph))};
        } else {
            xy = anchor(0, style, pw, ph, sw, sh);
        }
        boolean hover = false;
        if (interactive) {
            boolean down = false;
            try {
                down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            } catch (Throwable e) {
                // 읽지 못하면 끌기 없이 표시만
            }
            boolean inside = mx >= xy[0] && mx < xy[0] + pw && my >= xy[1] && my < xy[1] + ph;
            if (down && !prevDown && inside && !tHit(mx, my)) { dragging = true; grabDX = mx - xy[0]; grabDY = my - xy[1]; }   // 알약이 위에 있으면 알약이 먼저
            boolean wasDragging = dragging;
            if (!down) dragging = false;
            if (dragging) {
                int nx = clamp(mx - grabDX, 0, Math.max(0, sw - pw)), ny = clamp(my - grabDY, 0, Math.max(0, sh - ph));
                xy = new int[]{nx, ny};
                SegongClient.customX = (float) nx / sw;
                SegongClient.customY = (float) ny / sh;
            } else if (wasDragging) {
                SegongClient.save();
            }
            prevDown = down;
            hover = inside || dragging;
            hitX = xy[0]; hitY = xy[1]; hitW = pw; hitH = ph;
        }
        cardBottom = xy[1] + ph;
        MatrixStack m = ctx.getMatrices();
        m.push();
        try {
            m.translate((float) xy[0], (float) xy[1], 0f);
            m.scale(s, s, 1f);
            layout(new Pen(mc.textRenderer, ctx, true), v, style, sz);
        } finally {
            m.pop();      // 그리다 오류가 나도 행렬은 원래대로 돌려놓는다
        }
        if (hover) {
            int g = 0xFFFFD76A;
            ctx.fill(xy[0] - 1, xy[1] - 1, xy[0] + pw + 1, xy[1], g);
            ctx.fill(xy[0] - 1, xy[1] + ph, xy[0] + pw + 1, xy[1] + ph + 1, g);
            ctx.fill(xy[0] - 1, xy[1] - 1, xy[0], xy[1] + ph + 1, g);
            ctx.fill(xy[0] + pw, xy[1] - 1, xy[0] + pw + 1, xy[1] + ph + 1, g);
            if (xy[1] >= 11) ctx.drawText(mc.textRenderer, "끌어서 이동", xy[0], xy[1] - 10, 0xFFFFD76A, true);
        }
        if (style == 3) badges(mc, ctx, v);
    }

    /**
     * "오늘 세공 N회" 알약만 카드와 따로 그린다 (설정에서 '따로 띄우기'를 켰을 때).
     * 위치는 알약 혼자 끌어서 옮기고(tallyX/tallyY), 정하지 않았으면 화면 위 가운데
     * (한 줄형이면 카드 바로 아래)에 놓는다.
     */
    static void renderTally(MinecraftClient mc, DrawContext ctx, int count, int mx, int my, boolean interactive) {
        float s = SegongClient.scale;
        String label = "오늘 세공 " + count + "회";
        int cw = mc.textRenderer.getWidth(label) + 14;
        int pw = Math.round(cw * s), ph = Math.round(14 * s);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int[] xy;
        if (SegongClient.tallyX >= 0 && SegongClient.tallyY >= 0) {
            xy = new int[]{clamp(Math.round(SegongClient.tallyX * sw), 0, Math.max(0, sw - pw)),
                           clamp(Math.round(SegongClient.tallyY * sh), 0, Math.max(0, sh - ph))};
        } else {
            int y = (SegongClient.style == 2 && cardBottom > 0) ? cardBottom + 4 : 6;
            xy = new int[]{(sw - pw) / 2, clamp(y, 0, Math.max(0, sh - ph))};
        }
        boolean hover = false;
        if (interactive) {
            boolean down = false;
            try {
                down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            } catch (Throwable e) {
                // 읽지 못하면 끌기 없이 표시만
            }
            boolean inside = mx >= xy[0] && mx < xy[0] + pw && my >= xy[1] && my < xy[1] + ph;
            if (down && !tPrevDown && inside && !dragging) { tDragging = true; tGrabDX = mx - xy[0]; tGrabDY = my - xy[1]; }
            boolean was = tDragging;
            if (!down) tDragging = false;
            if (tDragging) {
                int nx = clamp(mx - tGrabDX, 0, Math.max(0, sw - pw)), ny = clamp(my - tGrabDY, 0, Math.max(0, sh - ph));
                xy = new int[]{nx, ny};
                SegongClient.tallyX = (float) nx / sw;
                SegongClient.tallyY = (float) ny / sh;
            } else if (was) {
                SegongClient.save();
            }
            tPrevDown = down;
            hover = inside || tDragging;
            tHitX = xy[0]; tHitY = xy[1]; tHitW = pw; tHitH = ph;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        try {
            m.translate((float) xy[0], (float) xy[1], 0f);
            m.scale(s, s, 1f);
            chipAt(new Pen(mc.textRenderer, ctx, true), label, cw / 2, 0);
        } finally {
            m.pop();
        }
        if (hover) {
            int g = 0xFFFFD76A;
            ctx.fill(xy[0] - 1, xy[1] - 1, xy[0] + pw + 1, xy[1], g);
            ctx.fill(xy[0] - 1, xy[1] + ph, xy[0] + pw + 1, xy[1] + ph + 1, g);
            ctx.fill(xy[0] - 1, xy[1] - 1, xy[0], xy[1] + ph + 1, g);
            ctx.fill(xy[0] + pw, xy[1] - 1, xy[0] + pw + 1, xy[1] + ph + 1, g);
            ctx.drawText(mc.textRenderer, "끌어서 이동", xy[0], xy[1] >= 11 ? xy[1] - 10 : xy[1] + ph + 2, 0xFFFFD76A, true);
        }
    }

    private static int[] anchor(int pos, int style, int w, int h, int sw, int sh) {
        if (pos == 0) pos = style <= 1 ? 2 : (style == 2 ? 4 : 5);
        switch (pos) {
            case 1: return new int[]{6, 6};
            case 2: return new int[]{6, (sh - h) / 2};
            case 3: return new int[]{sw - w - 6, 6};
            case 4: return new int[]{(sw - w) / 2, 6};
            default: return new int[]{(sw - w) / 2, sh - h - 6};
        }
    }

    private static int[] layout(Pen p, SegongClient.View v, int style, int[] known) {
        return switch (style) {
            case 1 -> layoutA(p, v, known);   // 카드형
            case 2 -> layoutB(p, v, known);   // 한 줄형
            case 3 -> layoutC(p, v, known);   // 슬롯 배지형
            default -> layoutT(p, v, known);  // 타일형
        };
    }


    // ---------------------------------------------------------------- 우드카드 (나무판 + 못) 공통 그리기
    static final int W_OUT = 0xFF2A1608, W_HI = 0xFFC8955A, W_SH = 0xFF4A2C12, W_BODY = 0xFF8A5A2B, W_GROOVE = 0xFF6A4220;
    static final int W_PLANK = 0xFF7A4F26, W_NAIL = 0xFFBFC4CC, W_NAIL_D = 0xFF3A3F48;
    static final int W_CREAM = 0xFFFFF3D6, W_SUB = 0xFFE0C99A, W_OK = 0xFFFFE08A, W_RED = 0xFFFFB0A0, W_GOLD = 0xFFE0B060;
    static final int[] W_COL = {0xFFE3C4FF, 0xFFB5F2FF, 0xFFFFFFFF};

    /** 나무판 틀: 모서리를 살짝 깎은 바깥선 + 밝은/어두운 테두리 + 판자 줄 + 모서리 못 */
    static void woodFrame(DrawContext c, int w, int h) {
        c.fill(1, 0, w - 1, h, W_OUT);
        c.fill(0, 1, w, h - 1, W_OUT);
        c.fill(1, 1, w - 1, 2, W_HI);
        c.fill(1, 1, 2, h - 1, W_HI);
        c.fill(1, h - 2, w - 1, h - 1, W_SH);
        c.fill(w - 2, 1, w - 1, h - 1, W_SH);
        c.fill(2, 2, w - 2, h - 2, W_BODY);
        c.fill(3, 3, w - 3, h - 3, W_GROOVE);
        c.fill(4, 4, w - 4, h - 4, W_BODY);
        for (int y = 14; y < h - 5; y += 14) c.fill(4, y, w - 4, y + 1, W_PLANK);
        nail(c, 2, 2); nail(c, w - 6, 2); nail(c, 2, h - 6); nail(c, w - 6, h - 6);
    }

    private static void nail(DrawContext c, int x, int y) {
        c.fill(x, y, x + 4, y + 4, W_NAIL_D);
        c.fill(x + 1, y + 1, x + 3, y + 3, W_NAIL);
    }

    /** 오목하게 들어간 칸 */
    static void woodSlot(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, W_OUT);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFA8763E);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 2, W_GROOVE);
        c.fill(x + 1, y + 1, x + w - 2, y + 2, W_SH);
        c.fill(x + 1, y + 1, x + 2, y + h - 2, W_SH);
    }

    /** 나무 버튼 (선택되면 금색) */
    static void woodButton(DrawContext c, int x, int y, int w, int h, boolean sel, boolean red) {
        int fill = red ? 0xFF9A3B2A : sel ? W_GOLD : 0xFFA8763E;
        int hi = red ? 0xFFD98A78 : sel ? 0xFFFFF0B0 : 0xFFD9A868;
        int lo = red ? 0xFF5A2018 : sel ? 0xFFA87A28 : 0xFF5A3A18;
        c.fill(x, y, x + w, y + h, W_OUT);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        c.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
        c.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
    }

    // ---------------------------------------------------------------- 타일형 (우드카드 / 크림)
    private static final int T_PANEL = 0xFFF3E4CF, T_EDGE_OUT = 0xFF3B2412, T_EDGE_IN = 0xFF6B4423;
    private static final int T_TITLE = 0xFF6B3F17, T_TXT = 0xFF4A2C14, T_SUB = 0xFF8A6A45, T_OK = 0xFF2F8A4A, T_RED = 0xFFB5533C;
    private static final int T_TILE = 0xFFFFF6E6, T_TRACK = 0xFFD9BF96, T_GOLD = 0xFFF2B631;
    private static final int[] T_COL = {0xFF8A3FC9, 0xFF1F8FA8, 0xFF6B7386};      // 글자색
    private static final int[] T_BAR = {0xFFB06CF0, 0xFF5FD0E8, 0xFFCFD6E4};      // 띠 색

    private static int[] layoutT(Pen p, SegongClient.View v, int[] known) {
        final int th = SegongClient.theme;                 // 0 다크 약방 / 1 우드카드 / 2 크림
        final boolean apo = th == 0, wood = th <= 1;       // wood = 두꺼운 틀 디자인 (약방·우드카드)
        final int cTitle = wood ? W_CREAM : T_TITLE;
        final int cTxt = apo ? Apo.TXT : wood ? W_CREAM : T_TXT;
        final int cSub = apo ? Apo.SUB : wood ? W_SUB : T_SUB;
        final int cOk = apo ? Apo.OK : wood ? W_OK : T_OK;
        final int cRed = wood ? W_RED : T_RED;
        final int cEdge = apo ? Apo.S_OUT : wood ? W_OUT : T_EDGE_OUT;
        final int[] cCol = wood ? W_COL : T_COL;
        int w = wood ? 214 : 206;
        int m = wood ? 10 : 8;                 // 안쪽 여백
        int bm = apo ? 18 : m;                 // 맨 아래 줄 여백 (모서리 장식 피하기)
        int[] st = v.stats;
        if (known != null) {
            int kw = known[0], kh = known[1];
            if (apo) {
                if (p.on) Apo.frame(p.ctx, kw, kh, false);
            } else if (wood) {
                if (p.on) woodFrame(p.ctx, kw, kh);
            } else {
                p.rect(0, 0, kw, kh, T_EDGE_OUT);
                p.rect(1, 1, kw - 1, kh - 1, T_EDGE_IN);
                p.rect(3, 3, kw - 3, kh - 3, T_PANEL);
            }
        }
        int top = wood ? 3 : 0;
        if (apo) {
            if (p.on) Apo.banner(p.ctx, m - 1, 4, 96, 15);
            p.plainCenter("세공 도우미", m - 1 + 48, 8, Apo.B_TXT);
        } else if (wood) {
            p.text("세공 도우미", m, 8 + top, cTitle);
        } else {
            p.plain("세공 도우미", m, 8, cTitle);
        }
        if (v.lim > 0) {
            String g = gradeLabel(v.lim);
            int tw = p.tr.getWidth(g);
            if (apo) {
                int bx = w - m - tw - 12;
                if (p.on) Apo.pill(p.ctx, bx, 5, tw + 12, 14, Apo.P_FILL, Apo.OUT, Apo.P_HI, Apo.P_LO, 1);
                p.plain(g, bx + 6, 8, Apo.P_TXT);
            } else {
                p.rect(w - m - tw - 8, 5 + top, w - m, 18 + top, cEdge);
                p.rect(w - m - tw - 7, 6 + top, w - m - 1, 17 + top, T_GOLD);
                p.plain(g, w - m - tw - 3, 8 + top, T_EDGE_OUT);
            }
        }
        if (v.r == null) {
            int y = 24 + top;
            p.plain("현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), m, y, cTxt); y += 12;
            p.plain("등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", m, y, cRed); y += 12;
            if (!v.sig.isEmpty()) { p.plain("표시 " + v.sig, m, y, cSub); y += 12; }
            return new int[]{w, y + (apo ? 12 : 8)};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        int tileW = (w - 2 * m - 8) / 3, tileH = apo ? 64 : 56, ty = 22 + top;
        for (int k = 0; k < 3; k++) {
            int tx = m + k * (tileW + 4);
            if (apo) {
                if (p.on) Apo.slot(p.ctx, tx, ty, tileW, tileH);
                ItemStack ic = icon(k);
                if (ic != null) p.item(ic, tx + tileW / 2, ty + 10, 0.75f);
                else if (p.on) Apo.dot(p.ctx, tx + tileW / 2, ty + 9, COL[k]);
            } else if (wood) {
                if (p.on) woodSlot(p.ctx, tx, ty, tileW, tileH);
                p.rect(tx + 2, ty + 3, tx + tileW - 2, ty + 6, T_BAR[k]);
            } else {
                p.rect(tx, ty, tx + tileW, ty + tileH, T_EDGE_OUT);
                p.rect(tx + 1, ty + 1, tx + tileW - 1, ty + tileH - 1, T_TILE);
                p.rect(tx + 1, ty + 1, tx + tileW - 1, ty + 4, T_BAR[k]);
            }
            int nameY = apo ? 17 : 8, numY = apo ? 26 : 19;
            if (wood) p.text(NAME[k], tx + (tileW - p.tr.getWidth(NAME[k])) / 2, ty + nameY, cCol[k]);
            else p.plainCenter(NAME[k], tx + tileW / 2, ty + 7, cCol[k]);
            String num = String.valueOf(cnt[k]);
            int unit = p.tr.getWidth("개");
            int total = p.tr.getWidth(num) * 3 + 2 + unit;
            int nx = tx + (tileW - total) / 2;
            p.scaled(num, nx, ty + numY, cCol[k], 3f);
            p.plain("개", nx + p.tr.getWidth(num) * 3 + 2, ty + numY + 12, cCol[k]);
            p.plainCenter("보유 " + v.stock[k], tx + tileW / 2, ty + tileH - 11, stockCol(v, k, cSub));
        }
        int y = ty + tileH + 6;
        if (wood) p.text("합 " + v.r.sum + " / " + v.lim, m, y, cOk); else p.plain("합 " + v.r.sum + " / " + v.lim, m, y, cOk);
        String right = usedLabel(v);
        p.plain(right, w - m - p.tr.getWidth(right), y, usedCol(v, cSub, cRed));
        y += 12;
        p.rect(m, y, w - m, y + 8, cEdge);
        p.rect(m + 1, y + 1, w - m - 1, y + 7, apo ? Apo.TRACK : wood ? W_SH : T_TRACK);
        int fillW = Math.round((w - 2 * m - 2) * Math.min(1f, (float) v.r.sum / v.lim));
        p.rect(m + 1, y + 1, m + 1 + fillW, y + 7, cOk);
        y += 12;
        int[] after = new int[3];
        for (int k = 0; k < 3; k++) after[k] = Math.min(100, st[k] + cnt[k] * 10);
        p.plain(LABEL[0] + " " + after[0], bm, y, cTxt);
        p.plainCenter(LABEL[1] + " " + after[1], w / 2, y, cTxt);
        String last = LABEL[2] + " " + after[2];
        p.plain(last, w - bm - p.tr.getWidth(last), y, cTxt);
        int endY = y + (apo ? 20 : wood ? 19 : 16);
        if (v.tally >= 0) {
            int cy = y + (apo ? 14 : wood ? 12 : 11);
            tallyChip(p, v, w / 2, cy);
            endY = cy + 14 + (apo ? 9 : wood ? 8 : 6);
        }
        return new int[]{w, endY};
    }

    // ---------------------------------------------------------------- A 카드형 (다크 약방)
    private static int[] layoutAP(Pen p, SegongClient.View v, int[] known) {
        int w = 184;
        int[] st = v.stats;
        if (known != null && p.on) Apo.frame(p.ctx, known[0], known[1], false);
        if (p.on) Apo.banner(p.ctx, 9, 4, 96, 15);
        p.plainCenter("세공 도우미", 57, 8, Apo.B_TXT);
        if (v.lim > 0) {
            String g = gradeLabel(v.lim);
            int pw2 = p.tr.getWidth(g) + 12, bx = w - 8 - pw2;
            if (p.on) Apo.pill(p.ctx, bx, 5, pw2, 14, Apo.P_FILL, Apo.OUT, Apo.P_HI, Apo.P_LO, 1);
            p.plain(g, bx + 6, 8, Apo.P_TXT);
        }
        if (v.r == null) {
            int y = 26;
            p.text("현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), 12, y, Apo.TXT); y += 12;
            p.text("등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", 12, y, Apo.RED); y += 12;
            if (!v.sig.isEmpty()) { p.text("표시 " + v.sig, 12, y, Apo.SUB); y += 12; }
            return new int[]{w, y + 14};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            int y0 = 24 + 20 * k;
            if (p.on) Apo.slot(p.ctx, 10, y0, 164, 18);
            ItemStack ic = icon(k);
            if (ic != null) p.item(ic, 20, y0 + 9, 0.75f);
            else if (p.on) Apo.medal(p.ctx, 21, y0 + 9, 11, COL[k]);
            p.text(NAME[k], 31, y0 + 5, W_COL[k]);
            p.text("보유 " + v.stock[k], 94, y0 + 5, stockCol(v, k, Apo.SUB));
            p.bigRight(String.valueOf(cnt[k]), 170, y0 + 1, W_COL[k]);
        }
        // 장식 구분선
        p.rect(12, 86, 82, 87, Apo.CURL);
        p.rect(102, 86, 172, 87, Apo.CURL);
        if (p.on) Apo.diamond(p.ctx, 92, 86);
        for (int k = 0; k < 3; k++) {
            int y = 94 + 12 * k;
            int cur = st[k], after = Math.min(100, cur + cnt[k] * 10);
            ItemStack ic2 = icon(k);
            if (ic2 != null) p.item(ic2, 15, y + 4, 0.65f);
            else if (p.on) Apo.dot(p.ctx, 15, y + 4, COL[k]);
            p.text(LABEL[k], 24, y, Apo.SUB);
            p.rect(41, y + 1, 123, y + 8, Apo.S_OUT);
            p.rect(42, y + 2, 122, y + 7, Apo.TRACK);
            p.rect(42, y + 2, 42 + 80 * after / 100, y + 7, (0x88 << 24) | (COL[k] & 0xFFFFFF));
            p.rect(42, y + 2, 42 + 80 * cur / 100, y + 7, COL[k]);
            p.rect(124, y + 2, 125, y + 7, Apo.S_RIM);
            p.rect(125, y + 3, 126, y + 6, Apo.S_RIM);
            p.rect(126, y + 4, 127, y + 5, Apo.S_RIM);
            p.textRight(cur + " → " + after, w - 14, y, Apo.TXT);
        }
        p.text("합 " + v.r.sum + " / " + v.lim, 18, 137, Apo.OK);
        p.textRight(usedLabel(v), w - 18, 137, usedCol(v, Apo.SUB, Apo.RED));
        if (v.tally >= 0) {
            tallyChip(p, v, w / 2, 150);
            return new int[]{w, 174};
        }
        return new int[]{w, 156};
    }

    // ---------------------------------------------------------------- A 카드형 (우드카드)
    private static int[] layoutAW(Pen p, SegongClient.View v, int[] known) {
        int w = 184;
        int[] st = v.stats;
        if (known != null && p.on) woodFrame(p.ctx, known[0], known[1]);
        p.text("세공 도우미", 10, 9, W_CREAM);
        if (v.lim > 0) {
            String g = gradeLabel(v.lim);
            int bx = w - 8 - (p.tr.getWidth(g) + 8);
            p.rect(bx, 7, w - 8, 20, W_OUT);
            p.rect(bx + 1, 8, w - 9, 19, T_GOLD);
            p.plain(g, bx + 4, 10, W_OUT);
        }
        if (v.r == null) {
            int y = 26;
            p.text("현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), 10, y, W_CREAM); y += 12;
            p.text("등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", 10, y, W_RED); y += 12;
            if (!v.sig.isEmpty()) { p.text("표시 " + v.sig, 10, y, W_SUB); y += 12; }
            return new int[]{w, y + 8};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            int y0 = 24 + 20 * k;
            if (p.on) woodSlot(p.ctx, 8, y0, 168, 17);
            p.rect(13, y0 + 5, 19, y0 + 11, T_BAR[k]);
            p.text(NAME[k], 23, y0 + 5, W_COL[k]);
            p.text("보유 " + v.stock[k], 92, y0 + 5, stockCol(v, k, W_SUB));
            p.bigRight(String.valueOf(cnt[k]), 170, y0 + 1, W_COL[k]);
        }
        p.rect(8, 86, 176, 87, W_SH);
        p.rect(8, 87, 176, 88, 0xFFA8763E);
        for (int k = 0; k < 3; k++) {
            int y = 92 + 12 * k;
            int cur = st[k], after = Math.min(100, cur + cnt[k] * 10);
            p.text(LABEL[k], 10, y, W_SUB);
            p.rect(36, y + 3, 116, y + 7, W_OUT);
            p.rect(36, y + 3, 36 + 80 * after / 100, y + 7, (0x66 << 24) | (T_BAR[k] & 0xFFFFFF));
            p.rect(36, y + 3, 36 + 80 * cur / 100, y + 7, W_COL[k]);
            p.textRight(cur + " → " + after, w - 10, y, W_CREAM);
        }
        p.text("합 " + v.r.sum + " / " + v.lim, 10, 130, W_OK);
        p.textRight(usedLabel(v), w - 10, 130, usedCol(v, W_SUB, W_RED));
        if (v.tally >= 0) {
            tallyChip(p, v, w / 2, 145);
            return new int[]{w, 166};
        }
        return new int[]{w, 146};
    }

    // ---------------------------------------------------------------- A 카드형
    private static int[] layoutA(Pen p, SegongClient.View v, int[] known) {
        if (SegongClient.theme == 0) return layoutAP(p, v, known);
        if (SegongClient.theme == 1) return layoutAW(p, v, known);
        int w = 176, y = 4;
        if (known != null) p.frame(known[0], known[1]);
        p.text("§e세공 도우미", 6, y + 2, WHITE);
        if (v.lim > 0) p.textRight("§6" + gradeLabel(v.lim) + " §8" + v.how, w - 6, y + 2, WHITE);
        y += 14;
        int[] st = v.stats;
        if (v.r == null) {
            p.text("§f현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]), 6, y, WHITE); y += 11;
            p.text("§c등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택", 6, y, WHITE); y += 11;
            if (!v.sig.isEmpty()) { p.text("§8표시 " + v.sig, 6, y, WHITE); y += 11; }
            return new int[]{w, y + 3};
        }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        for (int k = 0; k < 3; k++) {
            int y0 = y;
            p.rect(6, y0 + 6, 12, y0 + 12, COL[k]);
            p.text(NAME[k], 16, y0 + 6, COL[k]);
            p.text("보유 " + v.stock[k], 84, y0 + 7, stockCol(v, k, GRAY));
            p.bigRight(String.valueOf(cnt[k]), w - 6, y0 + 2, COL[k]);
            y += 19;
        }
        p.rect(6, y + 1, w - 6, y + 2, 0x22FFFFFF);
        y += 4;
        for (int k = 0; k < 3; k++) {
            int cur = st[k], after = Math.min(100, cur + cnt[k] * 10);
            p.text(LABEL[k], 6, y + 1, GRAY);
            p.rect(32, y + 3, 112, y + 7, 0x33FFFFFF);
            p.rect(32, y + 3, 32 + 80 * after / 100, y + 7, (0x80 << 24) | (COL[k] & 0xFFFFFF));
            p.rect(32, y + 3, 32 + 80 * cur / 100, y + 7, COL[k]);
            p.textRight(cur + " → " + after, w - 6, y + 1, WHITE);
            y += 11;
        }
        y += 3;
        p.text("합 " + v.r.sum + " / " + v.lim, 6, y, GREEN);
        p.textRight(usedLabel(v), w - 6, y, usedCol(v, GRAY, 0xFFFF8F8F));
        if (v.tally >= 0) {
            tallyChip(p, v, w / 2, y + 13);
            return new int[]{w, y + 32};
        }
        return new int[]{w, y + 14};
    }

    // ---------------------------------------------------------------- B 한 줄형
    /** 한 줄 내용을 x, y 에서 그리고 차지한 폭을 돌려준다 (Pen 이 꺼져 있으면 폭만 잰다). 광채 아이콘이 있으면 이름 대신 그림. */
    private static int rowB(Pen p, SegongClient.View v, int x, int y) {
        int x0 = x;
        if (v.r == null) {
            x += p.text("§f현재 " + v.stats[0] + "/" + v.stats[1] + "/" + v.stats[2] + " §7· §c등급 못 읽음 (" + SegongClient.keyName(SegongClient.gradeKey) + ")", x, y, WHITE);
            return x - x0;
        }
        final String[] pre = {"§d", "§b", "§7"};
        final int[] cnt = {v.r.a, v.r.b, v.r.c};
        final int G = 4;                       // 글자 사이 간격 (띄어쓰기 한 칸 폭)
        x += p.text("§6" + gradeLabel(v.lim), x, y, WHITE) + G;
        x += p.text("§7│", x, y, WHITE) + G;
        for (int k = 0; k < 3; k++) {
            ItemStack ic = icon(k);
            if (ic != null) {
                p.item(ic, x + 5, y + 4, 0.6f);
                x += 12;
            } else {
                x += p.text(pre[k] + NAME[k], x, y, WHITE) + G;
            }
            x += p.text("§f§l" + cnt[k], x, y, WHITE) + G + 2;
        }
        x += p.text("§7│", x, y, WHITE) + G;
        x += p.text("§a합 " + v.r.sum, x, y, WHITE);
        if (v.r.missing > 0) {
            x += G;
            x += p.text("§7·", x, y, WHITE) + G;
            x += p.text("§c" + v.r.missing + "개 부족", x, y, WHITE);
        }
        if (v.tally >= 0) {
            x += G;
            x += p.text("§7│", x, y, WHITE) + G;
            x += p.text("§e오늘 " + v.tally + "회", x, y, WHITE);
        }
        return x - x0;
    }

    private static int[] layoutB(Pen p, SegongClient.View v, int[] known) {
        final int th = SegongClient.theme;      // 0 약방 / 1 우드 / 2 크림
        boolean wood = th == 1, apo = th == 0;
        int padL = apo ? 12 : wood ? 11 : 8, padT = apo || wood ? 6 : 5;
        int tw = rowB(new Pen(p.tr, p.ctx, false), v, 0, 0);
        int w = tw + (apo ? 24 : wood ? 22 : 16), h = apo ? 21 : wood ? 19 : 17;
        if (known != null) {
            if (apo) { if (p.on) Apo.frame(p.ctx, known[0], known[1], true); }
            else if (wood) { if (p.on) woodFrame(p.ctx, known[0], known[1]); }
            else p.frame(known[0], known[1]);
        }
        rowB(p, v, padL, padT);
        return new int[]{w, h};
    }

    // ---------------------------------------------------------------- C 슬롯 배지형 (요약 상자 + 슬롯 배지)
    /** 둘째 줄 (순도 · 광도 · 강도 결과). 광채 아이콘이 있으면 각 항목 앞에 그림. 폭을 돌려준다. */
    private static int rowC2(Pen p, SegongClient.View v, int x, int y) {
        int x0 = x;
        if (v.r == null) {
            x += p.text("§7현재 " + v.stats[0] + " / " + v.stats[1] + " / " + v.stats[2], x, y, WHITE);
            return x - x0;
        }
        final int[] cnt = {v.r.a, v.r.b, v.r.c};
        final int G = 4;
        for (int k = 0; k < 3; k++) {
            if (k > 0) {
                x += G;
                x += p.text("§7·", x, y, WHITE) + G;
            }
            ItemStack ic = icon(k);
            if (ic != null) {
                p.item(ic, x + 5, y + 4, 0.6f);
                x += 12;
            }
            int after = Math.min(100, v.stats[k] + cnt[k] * 10);
            x += p.text("§7" + LABEL[k] + " " + after, x, y, WHITE);
        }
        return x - x0;
    }

    private static int[] layoutC(Pen p, SegongClient.View v, int[] known) {
        String l1;
        if (v.r == null) {
            l1 = "§c등급을 못 읽음 → " + SegongClient.keyName(SegongClient.gradeKey) + " 키로 선택";
        } else {
            l1 = "§a합 " + v.r.sum + " / " + v.lim + " §7· §6" + gradeLabel(v.lim);
            if (v.r.missing > 0) l1 += " §7· §c" + v.r.missing + "개 부족";
            if (v.tally >= 0) l1 += " §7· §e오늘 " + v.tally + "회";
        }
        final int th = SegongClient.theme;      // 0 약방 / 1 우드 / 2 크림
        boolean wood = th <= 1, apo = th == 0;
        int w1 = p.tr.getWidth(l1);
        int w2 = rowC2(new Pen(p.tr, p.ctx, false), v, 0, 0);
        int w = Math.max(w1, w2) + (wood ? 24 : 16), h = wood ? 34 : 30;
        if (known != null) {
            if (apo) { if (p.on) Apo.frame(p.ctx, known[0], known[1], true); }
            else if (wood) { if (p.on) woodFrame(p.ctx, known[0], known[1]); }
            else p.frame(known[0], known[1]);
        }
        int off = wood ? 3 : 0;
        p.text(l1, (w - w1) / 2, 5 + off, WHITE);
        rowC2(p, v, (w - w2) / 2, 17 + off);
        return new int[]{w, h};
    }

    /** 쓸 광채 종류마다, 내 인벤토리 첫 칸에 초록 테두리와 "N개" 배지를 그린다. */
    private static void badges(MinecraftClient mc, DrawContext ctx, SegongClient.View v) {
        if (v.r == null || mc.player == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> hs)) return;
        Object o = hs;
        if (!(o instanceof HandledScreenAccessor acc)) return;   // 통로가 없으면 배지는 생략
        int ox, oy;
        try { ox = acc.segong_getX(); oy = acc.segong_getY(); } catch (Throwable e) { return; }
        int[] cnt = {v.r.a, v.r.b, v.r.c};
        if (cnt[0] <= 0 && cnt[1] <= 0 && cnt[2] <= 0) return;
        boolean[] done = new boolean[3];
        for (Slot sl : hs.getScreenHandler().slots) {      // 슬롯을 한 번만 훑는다 (종류마다 첫 칸에만 배지)
            if (sl.inventory != mc.player.getInventory()) continue;
            ItemStack st = sl.getStack();
            if (st.isEmpty()) continue;
            String name = st.getName().getString();
            if (!name.contains("광채")) continue;
            for (int k = 0; k < 3; k++) {
                if (done[k] || cnt[k] <= 0 || !name.contains(NAME[k])) continue;
                done[k] = true;
                int sx = ox + sl.x, sy = oy + sl.y;
                int g = 0xFF18E07A;
                ctx.fill(sx - 1, sy - 1, sx + 17, sy, g);
                ctx.fill(sx - 1, sy + 16, sx + 17, sy + 17, g);
                ctx.fill(sx - 1, sy - 1, sx, sy + 17, g);
                ctx.fill(sx + 16, sy - 1, sx + 17, sy + 17, g);
                String t = cnt[k] + "개";
                int tw = mc.textRenderer.getWidth(t);
                int bx = sx + 8 - (tw + 6) / 2, by = sy - 13;
                ctx.fill(bx, by, bx + tw + 6, by + 11, 0xFF18C06A);
                ctx.drawText(mc.textRenderer, t, bx + 3, by + 2, WHITE, true);
                break;
            }
        }
    }
}
