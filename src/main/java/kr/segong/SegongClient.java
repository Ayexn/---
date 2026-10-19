package kr.segong;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * 60 세공 도우미 (클라이언트 전용, 보기만 하는 모드).
 *  - 세공 모듈 칸에 들어 있는 보석(또는 마우스를 올린 보석)의 순도/광도/강도를 읽고
 *  - 광채 개수는 내 인벤토리를 직접 세어서
 *  - 추천 개수를 화면에 표시한다.
 */
public class SegongClient implements ClientModInitializer {
    private static final String[] NAMES = {"바이올렛", "오팔", "아다만티움"};
    private static final int[] LIMITS = {0, 200, 212, 236};          // 0 = 자동
    private static final String[] GRADE_NAME = {"자동", "COMMON 200", "NORMAL 212", "RARE 236"};
    private static final long HOVER_MS = 300;                         // 화면이 없을 때 툴팁 표시 유지 시간
    private static final long FLASH_MS = 2500;                        // 알림 문구 표시 시간

    /** 읽어 낸 보석 한 개 */
    private static final class Gem {
        int[] stats;
        int gradeLimit;          // 글자로 읽은 등급 한도 (못 읽으면 0)
        String sig = "";         // 등급 표시 특수문자 (학습용)
        List<String> lines = new ArrayList<>();
        List<Text> texts = new ArrayList<>();
        String info = "";
    }

    private static KeyBinding toggleKey, gradeKey;
    private static boolean enabled = true;
    private static int gradeMode = 0;
    private static final Map<String, Integer> learned = new HashMap<>();   // 등급 표시 문자 → 한도

    private static boolean scanning = false;
    private static Gem hoverGem;          // 마우스를 올린 마지막 보석
    private static long hoverAt;
    private static Gem moduleGem;         // 열린 화면의 모듈 칸에서 찾은 보석
    private static long moduleScanAt;
    private static Gem currentGem;        // 지금 화면에 표시 중인 보석
    private static Gem lastAnyGem;        // 가장 최근에 본 보석 (/segong dump 용)

    private static String flash = "";
    private static long flashUntil;

    @Override
    public void onInitializeClient() {
        load();
        seedDefaults();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.segong"));
        gradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.grade", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.segong"));

        // 1) 마우스를 올린 아이템의 툴팁 읽기
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (scanning) return;
            Gem g = makeGem(stack, new ArrayList<>(lines));
            if (g == null) return;
            hoverGem = g;
            hoverAt = System.currentTimeMillis();
            lastAnyGem = g;
        });

        // 2) 화면이 없을 때(게임 중) 키 입력
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) doToggle();
            while (gradeKey.wasPressed()) doGrade();
        });

        // 3) 화면이 열려 있을 때 키 입력 + 그리기 (열린 화면에서는 일반 키 바인딩이 안 먹기 때문)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            hoverGem = null;
            moduleGem = null;
            moduleScanAt = 0;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, mods) -> onScreenKey(s, key, scancode));
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(client, ctx));
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.currentScreen == null) draw(c, ctx);
        });

        // 4) 명령어
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("segong")
                        .then(ClientCommandManager.literal("toggle").executes(c -> { doToggle(); return Command.SINGLE_SUCCESS; }))
                        .then(ClientCommandManager.literal("grade").executes(c -> { doGrade(); return Command.SINGLE_SUCCESS; }))
                        .then(ClientCommandManager.literal("forget").executes(c -> {
                            learned.clear(); gradeMode = 0; seedDefaults(); save();
                            c.getSource().sendFeedback(Text.literal("[세공] 학습한 등급 표시와 수동 등급을 모두 지웠습니다"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("inv").executes(c -> {
                            long[] n = countOrbs(MinecraftClient.getInstance());
                            c.getSource().sendFeedback(Text.literal("[세공] 인벤토리 광채: 바이올렛 " + n[0] + " / 오팔 " + n[1] + " / 아다만티움 " + n[2]));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("dump").executes(c -> {
                            Gem g = lastAnyGem;
                            if (g == null) {
                                c.getSource().sendFeedback(Text.literal("[세공] 아직 읽은 보석이 없습니다. 보석에 마우스를 올린 뒤 다시 입력하세요"));
                                return Command.SINGLE_SUCCESS;
                            }
                            c.getSource().sendFeedback(Text.literal("[세공] 마지막 보석 (" + g.lines.size() + "줄)"));
                            c.getSource().sendFeedback(Text.literal(" 아이템: " + g.info));
                            c.getSource().sendFeedback(Text.literal(" 등급표시 문자: " + (g.sig.isEmpty() ? "(없음)" : g.sig) + " / 글자로 읽은 한도: " + g.gradeLimit));
                            for (int i = 0; i < g.lines.size(); i++) {
                                c.getSource().sendFeedback(Text.literal("  " + (i + 1) + ") " + esc(g.lines.get(i))));
                                if (i < 2) c.getSource().sendFeedback(Text.literal("     조각: " + runs(g.texts.get(i))));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("slots").executes(c -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (!(mc.currentScreen instanceof HandledScreen<?> hs)) {
                                c.getSource().sendFeedback(Text.literal("[세공] 세공 화면을 연 상태에서 입력하세요"));
                                return Command.SINGLE_SUCCESS;
                            }
                            int cnt = 0;
                            for (Slot sl : hs.getScreenHandler().slots) {
                                if (sl.inventory == mc.player.getInventory()) continue;
                                ItemStack st = sl.getStack();
                                if (st.isEmpty()) continue;
                                cnt++;
                                boolean gem = makeGem(st, tooltipTexts(mc, st)) != null;
                                c.getSource().sendFeedback(Text.literal("  칸" + sl.id + ": " + st.getName().getString() + " x" + st.getCount() + (gem ? "  ← 보석" : "")));
                            }
                            if (cnt == 0) c.getSource().sendFeedback(Text.literal("[세공] 내 인벤토리가 아닌 칸에 아이템이 없습니다"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("items").executes(c -> {
                            PlayerInventory inv = MinecraftClient.getInstance().player.getInventory();
                            for (int i = 0; i < inv.size(); i++) {
                                ItemStack s = inv.getStack(i);
                                if (!s.isEmpty()) c.getSource().sendFeedback(Text.literal("  [" + i + "] " + s.getName().getString() + " x" + s.getCount()));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    // ---------------------------------------------------------------- 키 동작

    private static void onScreenKey(Screen screen, int key, int scancode) {
        if (screen instanceof ChatScreen) return;
        if (screen.getFocused() instanceof TextFieldWidget) return;   // 글자 입력 중에는 무시
        if (toggleKey.matchesKey(key, scancode)) doToggle();
        else if (gradeKey.matchesKey(key, scancode)) doGrade();
    }

    private static void doToggle() {
        enabled = !enabled;
        save();
        flash("세공 도우미: " + (enabled ? "켜짐" : "꺼짐 (다시 켜려면 V)"));
    }

    private static void doGrade() {
        gradeMode = (gradeMode + 1) % 4;
        String extra = "";
        Gem g = currentGem;
        if (gradeMode > 0 && g != null && !g.sig.isEmpty()) {
            learned.put(g.sig, LIMITS[gradeMode]);      // 이 보석의 등급 표시를 기억 → 다음부터 자동
            extra = " · 이 등급 표시를 기억함";
        }
        save();
        flash("등급 한도: " + GRADE_NAME[gradeMode] + extra);
    }

    private static void flash(String s) {
        flash = s;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player != null && c.currentScreen == null) c.player.sendMessage(Text.literal("§e[세공] §f" + s), true);
    }

    // ---------------------------------------------------------------- 보석 읽기

    private static List<Text> tooltipTexts(MinecraftClient client, ItemStack stack) {
        scanning = true;
        try {
            return new ArrayList<>(stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC));
        } catch (Throwable e) {
            return new ArrayList<>();
        } finally {
            scanning = false;
        }
    }

    /** 툴팁에 순도/광도/강도가 모두 있으면 Gem, 아니면 null */
    private static Gem makeGem(ItemStack stack, List<Text> texts) {
        List<String> plain = new ArrayList<>();
        for (Text t : texts) plain.add(t.getString());
        int[] st = GemParser.parseStats(plain);
        if (st == null) return null;
        Gem g = new Gem();
        g.stats = st;
        g.gradeLimit = GemParser.parseGradeLimit(plain);
        g.lines = plain;
        g.texts = texts;
        g.sig = signature(plain);
        try {
            String info = Registries.ITEM.getId(stack.getItem()) + " " + stack.getComponents();
            g.info = info.length() > 400 ? info.substring(0, 400) + "…" : info;
        } catch (Throwable e) {
            g.info = "(정보 읽기 실패)";
        }
        return g;
    }

    /** 첫 줄(등급 그림 글자 + 이름)에서 영문/숫자/한글이 아닌 글자만 모은 값. 등급 표시 구분용. */
    private static String signature(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        if (lines.isEmpty()) return "";
        lines.get(0).codePoints().forEach(cp -> {
            boolean ascii = cp < 128;
            boolean hangul = (cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0x1100 && cp <= 0x11FF) || (cp >= 0x3130 && cp <= 0x318F);
            if (!ascii && !hangul && !Character.isWhitespace(cp) && cp != 0xA0) {
                if (sb.length() > 0) sb.append('-');
                sb.append(Integer.toHexString(cp));
            }
        });
        return sb.toString();
    }

    /** 열린 화면의 '내 인벤토리가 아닌' 칸에서 보석을 찾는다. 0.25초마다 한 번. */
    private static void scanModule(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - moduleScanAt < 250) return;
        moduleScanAt = now;
        moduleGem = null;
        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;
        for (Slot sl : hs.getScreenHandler().slots) {
            if (sl.inventory == client.player.getInventory()) continue;
            ItemStack st = sl.getStack();
            if (st.isEmpty()) continue;
            Gem g = makeGem(st, tooltipTexts(client, st));
            if (g != null) { moduleGem = g; lastAnyGem = g; return; }
        }
    }

    /** 내 인벤토리에서 광채 개수를 직접 센다. */
    static long[] countOrbs(MinecraftClient client) {
        long[] n = new long[3];
        if (client == null || client.player == null) return n;
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            String name = s.getName().getString();
            if (!name.contains("광채")) continue;
            for (int k = 0; k < 3; k++) {
                if (name.contains(NAMES[k])) { n[k] += s.getCount(); break; }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 그리기

    private static void draw(MinecraftClient client, DrawContext ctx) {
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        if (now < flashUntil) out.add("§b» " + flash);

        if (enabled) {
            boolean screenOpen = client.currentScreen != null;
            Gem g = null;
            if (screenOpen) {
                scanModule(client);
                g = moduleGem;
            }
            if (g == null && hoverGem != null && (screenOpen || now - hoverAt <= HOVER_MS)) g = hoverGem;
            currentGem = g;

            if (g != null) {
                int[] st = g.stats;
                int lim; String how;
                if (g.gradeLimit > 0) { lim = g.gradeLimit; how = "자동"; }
                else if (!g.sig.isEmpty() && learned.containsKey(g.sig)) { lim = learned.get(g.sig); how = "기억"; }
                else if (gradeMode > 0) { lim = LIMITS[gradeMode]; how = "수동"; }
                else { lim = 0; how = ""; }
                long[] stock = countOrbs(client);

                out.add("§e세공 도우미 §7(V 끄기 / G 등급)");
                out.add("§f현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]));
                if (lim == 0) {
                    out.add("§c등급을 못 읽음 → G 키로 등급 선택");
                    out.add("§7(한 번 고르면 같은 등급 표시는 기억합니다" + (g.sig.isEmpty() ? "" : " · 표시 " + g.sig) + ")");
                } else {
                    Calc.Result r = Calc.best(st[0], st[1], st[2], lim, stock);
                    out.add("§7한도 " + lim + " (" + how + ")");
                    out.add("§a추천 §f바이올렛 §b" + r.a + " §f오팔 §b" + r.b + " §f아다만티움 §b" + r.c);
                    out.add("§7결과 합 " + r.sum + " · 광채 " + r.n + "개 사용");
                }
                out.add("§8보유 " + stock[0] + " / " + stock[1] + " / " + stock[2]);
            }
        }
        if (out.isEmpty()) return;

        int x = 6, y = 6, w = 0;
        for (String l : out) w = Math.max(w, client.textRenderer.getWidth(l));
        ctx.fill(x - 3, y - 3, x + w + 3, y + out.size() * 11 + 1, 0xB0000000);
        for (String l : out) { ctx.drawText(client.textRenderer, l, x, y, 0xFFFFFFFF, true); y += 11; }
    }

    // ---------------------------------------------------------------- 점검용 글자 처리

    /** 영문/숫자/한글 외의 글자는 U+코드 형태로 보여준다 (특수문자 확인용). */
    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if ((cp >= 32 && cp < 127) || (cp >= 0xAC00 && cp <= 0xD7A3)) b.appendCodePoint(cp);
            else b.append("<U+").append(Integer.toHexString(cp)).append('>');
        });
        return b.toString();
    }

    private static String runs(Text t) {
        StringBuilder b = new StringBuilder();
        t.visit((style, str) -> {
            String st = String.valueOf(style);
            if (st.length() > 110) st = st.substring(0, 110) + "…";
            b.append('[').append(esc(str)).append(" | ").append(st).append(']');
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return b.toString();
    }

    // ---------------------------------------------------------------- 설정 저장

    /** 확인된 등급 그림 글자: a002 = COMMON(200). 나머지는 G 키로 한 번 고르면 기억한다. */
    private static void seedDefaults() {
        learned.putIfAbsent("a002", 200);
    }

    private static Path cfgPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("segong.properties");
    }

    private static void load() {
        try {
            Path p = cfgPath();
            if (!Files.exists(p)) return;
            Properties pr = new Properties();
            try (InputStream in = Files.newInputStream(p)) { pr.load(in); }
            enabled = !"false".equals(pr.getProperty("enabled", "true"));
            gradeMode = Math.max(0, Math.min(3, Integer.parseInt(pr.getProperty("grade", "0"))));
            for (String k : pr.stringPropertyNames()) {
                if (k.startsWith("sig.")) learned.put(k.substring(4), Integer.parseInt(pr.getProperty(k)));
            }
        } catch (Throwable e) {
            // 설정을 못 읽어도 기본값으로 동작
        }
    }

    private static void save() {
        try {
            Properties pr = new Properties();
            pr.setProperty("enabled", String.valueOf(enabled));
            pr.setProperty("grade", String.valueOf(gradeMode));
            for (Map.Entry<String, Integer> e : learned.entrySet()) pr.setProperty("sig." + e.getKey(), String.valueOf(e.getValue()));
            try (OutputStream out = Files.newOutputStream(cfgPath())) { pr.store(out, "segong-helper"); }
        } catch (Throwable e) {
            // 저장 실패해도 동작에는 지장 없음
        }
    }
}
