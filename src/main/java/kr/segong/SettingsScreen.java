package kr.segong;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 세공 도우미 설정 화면 (다크 약방 · 우드카드 · 크림 디자인). 게임 중 설정 키 또는 /segong settings 로 연다. */
class SettingsScreen extends Screen {
    // 색 (테마에 따라 applyTheme() 가 채운다)
    private int PANEL, EDGE_OUT, EDGE_IN, TITLE, TXT, SUB, LINE;
    private int BTN, BTN_HI, BTN_LO, BTN_BD, BTN_TX, SEL, SEL_HI, SEL_LO, SEL_TX, RED, RED_HI, RED_LO, TRACK, INFO;
    private boolean wood, apo;    // wood = 어두운 판(약방·우드카드), apo = 다크 약방

    private void applyTheme() {
        apo = SegongClient.theme == 0;
        wood = SegongClient.theme <= 1;
        if (apo) {    // 다크 약방
            PANEL = Apo.FILL; EDGE_OUT = Apo.OUT; EDGE_IN = Apo.BAND;
            TITLE = Apo.B_TXT; TXT = Apo.TXT; SUB = Apo.SUB; LINE = Apo.CURL;
            BTN = Apo.BTN; BTN_HI = Apo.BTN_HI; BTN_LO = Apo.P_LO; BTN_BD = Apo.S_OUT; BTN_TX = Apo.TXT;
            SEL = Apo.SEL; SEL_HI = Apo.SEL_HI; SEL_LO = Apo.P_LO; SEL_TX = Apo.SEL_TX;
            RED = Apo.RD; RED_HI = Apo.RD_HI; RED_LO = Apo.RD_LO;
            TRACK = Apo.TRACK; INFO = Apo.INFO;
        } else if (wood) {   // 우드카드
            PANEL = Overlay.W_BODY; EDGE_OUT = Overlay.W_OUT; EDGE_IN = Overlay.W_HI;
            TITLE = Overlay.W_CREAM; TXT = Overlay.W_CREAM; SUB = Overlay.W_SUB; LINE = Overlay.W_SH;
            BTN = 0xFFA8763E; BTN_HI = 0xFFD9A868; BTN_LO = 0xFF5A3A18; BTN_BD = Overlay.W_OUT; BTN_TX = Overlay.W_CREAM;
            SEL = Overlay.W_GOLD; SEL_HI = 0xFFFFF0B0; SEL_LO = 0xFFA87A28; SEL_TX = Overlay.W_OUT;
            RED = 0xFF9A3B2A; RED_HI = 0xFFD98A78; RED_LO = 0xFF5A2018;
            TRACK = Overlay.W_SH; INFO = 0xFFE3C4FF;
        } else {      // 크림
            PANEL = 0xFFF3E4CF; EDGE_OUT = 0xFF3B2412; EDGE_IN = 0xFF6B4423;
            TITLE = 0xFF6B3F17; TXT = 0xFF4A2C14; SUB = 0xFF8A6A45; LINE = 0xFFB9874F;
            BTN = 0xFFE3C9A2; BTN_HI = 0xFFFFF2D8; BTN_LO = 0xFFB48A55; BTN_BD = 0xFF4A2C14; BTN_TX = 0xFF4A2C14;
            SEL = 0xFFF2B631; SEL_HI = 0xFFFFE08A; SEL_LO = 0xFFB9801A; SEL_TX = 0xFF3B2412;
            RED = 0xFFB5533C; RED_HI = 0xFFF0A08C; RED_LO = 0xFF6D2A1B;
            TRACK = 0xFFD9BF96; INFO = 0xFF7A4DA8;
        }
    }

    private static final class Btn {
        int x, y, w, h;
        String label;
        BooleanSupplier selected;
        boolean red;
        Runnable act;
    }

    private static final int ROW_GAP = 19;        // 줄 간격
    private final Screen parent;
    private final List<Btn> btns = new ArrayList<>();
    private int pl, pt, pw, ph;          // 패널 위치/크기
    private int cx0;                     // 컨트롤 시작 x
    private int sliderX, sliderY, sliderW = 190, sliderH = 14;
    private boolean sliderDrag;
    private boolean confirmReset;     // "설정 기본값으로" 를 한 번 눌러 확인 대기 중
    private Btn resetBtn;
    private Btn tallyBtn;

    SettingsScreen(Screen parent) {
        super(Text.literal("세공 도우미 설정"));
        this.parent = parent;
    }

    private Btn add(int x, int y, String label, BooleanSupplier sel, Runnable act, boolean red) {
        Btn b = new Btn();
        b.x = x; b.y = y; b.label = label; b.selected = sel; b.act = act; b.red = red;
        b.w = textRenderer.getWidth(label) + 14; b.h = 18;
        btns.add(b);
        return b;
    }

    @Override
    protected void init() {
        btns.clear();
        applyTheme();
        pw = Math.min(width - 16, 400);
        ph = Math.min(height - 16, 290);
        pl = (width - pw) / 2;
        pt = (height - ph) / 2;
        cx0 = pl + (apo ? 96 : 92);
        int y = pt + (apo ? 34 : 38), gap = ROW_GAP;

        // 동작
        int x = cx0;
        Btn b = add(x, y, "켜짐", () -> SegongClient.enabled, () -> { SegongClient.enabled = true; SegongClient.rearm(); SegongClient.save(); }, false);
        x += b.w + 4;
        add(x, y, "꺼짐", () -> !SegongClient.enabled, () -> { SegongClient.enabled = false; SegongClient.save(); }, false);

        // 표시 방식
        y += gap; x = cx0;
        for (int i = 0; i < SegongClient.STYLE_NAME.length; i++) {
            final int k = i;
            b = add(x, y, SegongClient.STYLE_NAME[i], () -> SegongClient.style == k, () -> { SegongClient.style = k; SegongClient.save(); }, false);
            x += b.w + 4;
        }

        // 디자인 (테마)
        y += gap; x = cx0;
        for (int i = 0; i < SegongClient.THEME_NAME.length; i++) {
            final int k = i;
            b = add(x, y, SegongClient.THEME_NAME[i], () -> SegongClient.theme == k, () -> { SegongClient.theme = k; SegongClient.save(); init(); }, false);
            x += b.w + 4;
        }

        // 아이콘 (광채 그림)
        y += gap; x = cx0;
        b = add(x, y, "켜짐", () -> SegongClient.icons, () -> { SegongClient.icons = true; SegongClient.save(); }, false);
        x += b.w + 4;
        add(x, y, "꺼짐", () -> !SegongClient.icons, () -> { SegongClient.icons = false; SegongClient.save(); }, false);

        // 추천 고정 (처음 정한 목표를 광채 넣는 동안 유지)
        y += gap; x = cx0;
        b = add(x, y, "켜짐", () -> SegongClient.lock, () -> { SegongClient.lock = true; Plan.clear(); SegongClient.save(); }, false);
        x += b.w + 4;
        add(x, y, "꺼짐", () -> !SegongClient.lock, () -> { SegongClient.lock = false; Plan.clear(); SegongClient.save(); }, false);

        // 세공 횟수 (오늘 몇 번 했는지 표시 / 고치기). 광채가 소모될 때마다 1번.
        y += gap; x = cx0;
        b = add(x, y, "켜짐", () -> Tally.show, () -> { Tally.show = true; SegongClient.save(); }, false);
        x += b.w + 4;
        b = add(x, y, "꺼짐", () -> !Tally.show, () -> { Tally.show = false; SegongClient.save(); }, false);
        x += b.w + 12;
        b = add(x, y, "-1", () -> false, () -> { Tally.add(-1); SegongClient.save(); }, false);
        x += b.w + 4;
        b = add(x, y, "+1", () -> false, () -> { Tally.add(1); SegongClient.save(); }, false);
        x += b.w + 4;
        tallyBtn = add(x, y, "0으로", () -> false, () -> { Tally.reset(); SegongClient.save(); }, false);

        // 횟수 표시 위치: 카드 안 / 따로 띄우기 (따로면 알약만 끌어서 옮길 수 있음)
        y += gap; x = cx0;
        b = add(x, y, "카드 안", () -> !SegongClient.tallySep, () -> { SegongClient.tallySep = false; SegongClient.save(); }, false);
        x += b.w + 4;
        b = add(x, y, "따로 띄우기", () -> SegongClient.tallySep, () -> { SegongClient.tallySep = true; SegongClient.save(); }, false);
        x += b.w + 12;
        add(x, y, "횟수 위치 초기화", () -> SegongClient.tallyX < 0, () -> SegongClient.resetTallyPos(), false);

        // 글자 크기 (슬라이더)
        y += gap;
        sliderX = cx0; sliderY = y + 2;

        // 위치
        y += gap; x = cx0;
        add(x, y, "위치 초기화 (자동)", () -> SegongClient.customX < 0, () -> SegongClient.resetPos(), false);

        // 등급
        y += gap; x = cx0;
        for (int i = 0; i < SegongClient.GRADE_NAME.length; i++) {
            final int k = i;
            String lab = i == 0 ? "자동" : SegongClient.GRADE_NAME[i].split(" ")[0];
            b = add(x, y, lab, () -> SegongClient.gradeMode == k, () -> { SegongClient.gradeMode = k; SegongClient.save(); }, false);
            x += b.w + 4;
        }

        // 아래 버튼
        int by = pt + ph - (apo ? 34 : 28), sideM = apo ? 18 : 14;
        Btn learn = add(pl + sideM, by, "학습한 등급 표시 지우기", () -> false, () -> SegongClient.resetLearned(), true);
        resetBtn = add(learn.x + learn.w + 6, by, confirmReset ? "한 번 더 누르면 초기화" : "설정 기본값으로", () -> false, () -> {
            if (!confirmReset) {
                confirmReset = true;
            } else {
                confirmReset = false;
                SegongClient.resetAll();
            }
            init();
        }, confirmReset);
        Btn close = add(0, by, "닫기", () -> false, this::close, false);
        close.w = 56;
        close.x = pl + pw - sideM - close.w;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private static boolean in(Btn b, double mx, double my) {
        return mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
    }

    private void setSlider(double mx) {
        double t = Math.max(0, Math.min(1, (mx - sliderX) / sliderW));
        float v = 0.7f + (float) t * 0.8f;
        SegongClient.scale = Math.round(v * 20f) / 20f;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Btn b : new ArrayList<>(btns)) {
                if (in(b, mx, my)) {
                    boolean was = confirmReset;
                    if (b != resetBtn) confirmReset = false;    // 다른 버튼을 누르면 확인 대기 해제
                    b.act.run();
                    if (was && b != resetBtn) init();
                    return true;
                }
            }
            if (mx >= sliderX - 2 && mx <= sliderX + sliderW + 2 && my >= sliderY - 3 && my <= sliderY + sliderH + 3) {
                sliderDrag = true; setSlider(mx); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (sliderDrag) { setSlider(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (sliderDrag) { sliderDrag = false; SegongClient.save(); return true; }
        return super.mouseReleased(mx, my, button);
    }

    // 입체감 있는 사각형 (위·왼쪽은 밝게, 아래·오른쪽은 어둡게)
    private static void bevel(DrawContext c, int x, int y, int w, int h, int fill, int hi, int lo, int bd) {
        c.fill(x, y, x + w, y + h, bd);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        c.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
        c.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);

        // 패널
        String title = "세공 도우미 설정";
        String ver = version();
        if (apo) {
            c.getMatrices().push();
            c.getMatrices().translate((float) pl, (float) pt, 0f);
            Apo.frame(c, pw, ph, false);
            c.getMatrices().pop();
            int bw = 150, bx = pl + (pw - bw) / 2;
            Apo.banner(c, bx, pt + 4, bw, 17);
            c.drawText(textRenderer, title, bx + (bw - textRenderer.getWidth(title)) / 2, pt + 8, TITLE, false);
            if (!ver.isEmpty()) c.drawText(textRenderer, "v" + ver, pl + pw - 24 - textRenderer.getWidth("v" + ver), pt + 10, SUB, true);
        } else {
            if (wood) {
                c.getMatrices().push();
                c.getMatrices().translate((float) pl, (float) pt, 0f);
                Overlay.woodFrame(c, pw, ph);
                c.getMatrices().pop();
            } else {
                c.fill(pl - 3, pt - 3, pl + pw + 3, pt + ph + 3, EDGE_OUT);
                c.fill(pl - 2, pt - 2, pl + pw + 2, pt + ph + 2, EDGE_IN);
                c.fill(pl, pt, pl + pw, pt + ph, PANEL);
            }
            c.drawText(textRenderer, title, pl + (pw - textRenderer.getWidth(title)) / 2, pt + 9, TITLE, wood);
            c.fill(pl + pw / 2 - 70, pt + 22, pl + pw / 2 + 70, pt + 23, LINE);
            if (!ver.isEmpty()) c.drawText(textRenderer, "v" + ver, pl + pw - 10 - textRenderer.getWidth("v" + ver), pt + 9, SUB, false);
        }

        // 줄 이름
        int gap = ROW_GAP, y = pt + (apo ? 34 : 38), lx = pl + (apo ? 18 : 14);
        String[] labels = {"동작", "표시 방식", "디자인", "아이콘", "추천 고정", "세공 횟수", "횟수 표시", "글자 크기", "위치", "등급"};
        for (int i = 0; i < labels.length; i++) {
            c.drawText(textRenderer, labels[i], lx, y + gap * i + 5, TXT, wood);
        }

        // 슬라이더
        float t = (SegongClient.scale - 0.7f) / 0.8f;
        int hx = sliderX + Math.round(t * sliderW);
        if (apo) {
            c.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, Apo.S_OUT);
            c.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, TRACK);
            c.fill(sliderX, sliderY, hx, sliderY + sliderH, Apo.SLIDER);
            c.fill(sliderX, sliderY, hx, sliderY + 1, 0x55FFFFFF);
            Apo.circle(c, hx, sliderY + sliderH / 2, 13, Apo.OUT);
            Apo.circle(c, hx, sliderY + sliderH / 2, 11, Apo.GOLD);
            Apo.circle(c, hx, sliderY + sliderH / 2, 7, Apo.GOLD_HI);
        } else {
            c.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, BTN_BD);
            c.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, TRACK);
            c.fill(sliderX, sliderY, hx, sliderY + sliderH, SEL);
            bevel(c, hx - 4, sliderY - 2, 8, sliderH + 4, BTN, BTN_HI, BTN_LO, BTN_BD);
        }
        c.drawText(textRenderer, String.format("%.2f배", SegongClient.scale), sliderX + sliderW + 8, sliderY + 3, TXT, false);

        // 버튼
        for (Btn b : btns) {
            boolean sel = b.selected.getAsBoolean();
            boolean hov = in(b, mx, my);
            int fill, hi, lo, tx;
            if (b.red) { fill = RED; hi = RED_HI; lo = RED_LO; tx = 0xFFFFFFFF; }
            else if (sel) { fill = SEL; hi = SEL_HI; lo = SEL_LO; tx = SEL_TX; }
            else { fill = BTN; hi = BTN_HI; lo = BTN_LO; tx = BTN_TX; }
            if (hov && !sel) { fill = lighten(fill); }
            if (apo) {
                int out = b.red ? Apo.RD_OUT : Apo.S_OUT;
                Apo.pill(c, b.x, b.y, b.w, b.h, fill, out, hi, lo, 2);
                c.drawText(textRenderer, b.label, b.x + (b.w - textRenderer.getWidth(b.label)) / 2, b.y + (sel ? 5 : 4), tx, !sel);
            } else {
                bevel(c, b.x, b.y, b.w, b.h, fill, hi, lo, BTN_BD);
                c.drawText(textRenderer, b.label, b.x + (b.w - textRenderer.getWidth(b.label)) / 2, b.y + 5, tx, wood && !sel);
            }
        }

        // 세공 횟수 줄 오른쪽에 지금 횟수
        if (tallyBtn != null) {
            c.drawText(textRenderer, "오늘 " + Tally.count + "회", tallyBtn.x + tallyBtn.w + 10, tallyBtn.y + 5, INFO, wood);
        }

        // 아래 안내 (광채 개수는 그림이 있으면 그림 + 숫자로)
        long[] n = SegongClient.countOrbs(client);
        int ly = pt + ph - (apo ? 60 : 52);
        int ix = lx;
        String head = "인벤토리 광채";
        c.drawText(textRenderer, head, ix, ly, INFO, wood);
        ix += textRenderer.getWidth(head) + 8;
        for (int k = 0; k < 3; k++) {
            ItemStack ic = SegongClient.iconFor(k);
            String cntText;
            if (ic != null) {
                MatrixStack m = c.getMatrices();
                m.push();
                try {
                    m.translate((float) (ix + 5 - 6), (float) (ly + 4 - 6), 0f);
                    m.scale(0.75f, 0.75f, 1f);
                    c.drawItem(ic, 0, 0);
                } catch (Throwable e) {
                    SegongClient.iconFailed(e);
                } finally {
                    m.pop();
                }
                ix += 13;
                cntText = String.valueOf(n[k]);
            } else {
                cntText = Overlay.NAME[k] + " " + n[k];
            }
            c.drawText(textRenderer, cntText, ix, ly, TXT, wood);
            ix += textRenderer.getWidth(cntText) + 10;
        }
        String l2 = SegongClient.keyName(SegongClient.toggleKey) + " 켜기/끄기 · " + SegongClient.keyName(SegongClient.gradeKey)
                + " 등급 · " + SegongClient.keyName(SegongClient.settingsKey) + " 설정 (키는 조작 설정에서 변경)";
        c.drawText(textRenderer, l2, lx, pt + ph - (apo ? 48 : 40), SUB, wood);

        // 실제 위치 미리보기
        try {
            Overlay.render(client, c, SegongClient.sampleView(), -1, -1, false);
            if (SegongClient.tallySep && Tally.show) Overlay.renderTally(client, c, Tally.count, -1, -1, false);
        } catch (Throwable e) {
            SegongClient.noteError(e);     // 미리보기가 실패해도 설정 화면은 계속 쓸 수 있게
        }
    }

    /** 설치된 모드 버전 (jar 이름과 같은 값) */
    private static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("segong")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        } catch (Throwable e) {
            return "";
        }
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 18), g = Math.min(255, ((c >> 8) & 0xFF) + 18), b = Math.min(255, (c & 0xFF) + 18);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
