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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 60 세공 도우미 (클라이언트 전용).
 *  - 마우스를 올린 보석의 툴팁에서 순도/광도/강도 + 등급을 읽고
 *  - 광채 개수는 화면 패널이 아니라 "내 인벤토리"에서 직접 센 다음
 *  - 추천 개수를 화면에 표시한다.
 */
public class SegongClient implements ClientModInitializer {
    private static final String[] NAMES = {"바이올렛", "오팔", "아다만티움"};
    private static final String[] LABEL = {"순도", "광도", "강도"};
    private static final int[] LIMITS = {0, 200, 212, 236};         // 0 = 자동
    private static final String[] GRADE_NAME = {"자동", "COMMON 200", "NORMAL 212", "RARE 236"};
    private static final long SHOW_MS = 300;                         // 툴팁이 사라진 뒤 표시 유지 시간

    private static KeyBinding toggleKey, gradeKey;
    private static boolean enabled = true;
    private static int gradeMode = 0;

    // 마지막으로 본 보석 툴팁
    private static volatile int[] lastStats;
    private static volatile int lastGradeLimit;
    private static volatile long lastSeen;
    private static volatile List<String> lastLines = new ArrayList<>();

    // 세공 모듈 칸에 들어 있는 보석 (마우스를 안 올려도 읽음)
    private static boolean scanning = false;
    private static int[] modStats;
    private static int modGradeLimit;
    private static List<String> modLines = new ArrayList<>();
    private static long modScanAt;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.segong"));
        gradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.grade", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.segong"));

        // 1) 툴팁 읽기 (타입을 적지 않아 버전 차이에 덜 민감하게)
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            List<String> plain = new ArrayList<>();
            if (scanning) return;   // 내가 직접 읽는 중에는 '마우스 올림'으로 치지 않음
            for (Text t : lines) plain.add(t.getString());
            int[] st = GemParser.parseStats(plain);
            if (st == null) return;
            lastStats = st;
            lastGradeLimit = GemParser.parseGradeLimit(plain);
            lastLines = plain;
            lastSeen = System.currentTimeMillis();
        });

        // 2) 키 입력
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                msg(client, "세공 도우미: " + (enabled ? "켜짐" : "꺼짐"));
            }
            while (gradeKey.wasPressed()) {
                gradeMode = (gradeMode + 1) % 4;
                msg(client, "등급 한도: " + GRADE_NAME[gradeMode]);
            }
        });

        // 3) 화면 그리기: 인벤토리 같은 화면이 열려 있을 때 / 평소 화면 둘 다
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            lastStats = null;       // 새 화면을 열면 이전 기록은 지움
            modStats = null;
            modScanAt = 0;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(client, ctx));
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.currentScreen == null) draw(c, ctx);
        });

        // 4) 점검용 명령어
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("segong")
                        .then(ClientCommandManager.literal("inv").executes(c -> {
                            long[] n = countOrbs(MinecraftClient.getInstance());
                            c.getSource().sendFeedback(Text.literal("[세공] 인벤토리 광채: 바이올렛 " + n[0] + " / 오팔 " + n[1] + " / 아다만티움 " + n[2]));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("dump").executes(c -> {
                            c.getSource().sendFeedback(Text.literal("[세공] 마지막 보석 툴팁(" + lastLines.size() + "줄)"));
                            for (String l : lastLines) c.getSource().sendFeedback(Text.literal("  " + l));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("slots").executes(c -> {
                            // 지금 열린 화면에서 '내 인벤토리가 아닌' 칸의 아이템을 보여줌 (모듈 칸 확인용)
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (!(mc.currentScreen instanceof HandledScreen<?> hs)) {
                                c.getSource().sendFeedback(Text.literal("[세공] 세공 화면을 연 상태에서 입력하세요"));
                                return Command.SINGLE_SUCCESS;
                            }
                            for (Slot sl : hs.getScreenHandler().slots) {
                                if (sl.inventory == mc.player.getInventory()) continue;
                                ItemStack st = sl.getStack();
                                if (st.isEmpty()) continue;
                                boolean gem = GemParser.parseStats(tooltipOf(mc, st)) != null;
                                c.getSource().sendFeedback(Text.literal("  칸" + sl.id + ": " + st.getName().getString() + " x" + st.getCount() + (gem ? "  ← 보석" : "")));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("items").executes(c -> {
                            // 인벤토리 안의 이름 있는 아이템 이름을 모두 보여줌 (광채 이름이 다를 때 확인용)
                            PlayerInventory inv = MinecraftClient.getInstance().player.getInventory();
                            for (int i = 0; i < inv.size(); i++) {
                                ItemStack s = inv.getStack(i);
                                if (!s.isEmpty()) c.getSource().sendFeedback(Text.literal("  [" + i + "] " + s.getName().getString() + " x" + s.getCount()));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    /** 내 인벤토리(핫바+가방+방어구+왼손)에서 광채 개수를 직접 센다. */
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

    /** 아이템의 툴팁 글자를 마우스 없이 직접 만들어서 읽는다. */
    private static List<String> tooltipOf(MinecraftClient client, ItemStack stack) {
        List<String> out = new ArrayList<>();
        scanning = true;
        try {
            for (Text t : stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC)) out.add(t.getString());
        } catch (Throwable e) {
            // 읽기 실패하면 빈 목록
        } finally {
            scanning = false;
        }
        return out;
    }

    /** 열린 화면의 '내 인벤토리가 아닌' 칸 중 보석(순도/광도/강도가 있는 것)을 찾는다. 0.25초마다 한 번만. */
    private static void scanModule(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - modScanAt < 250) return;
        modScanAt = now;
        modStats = null;
        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;
        for (Slot sl : hs.getScreenHandler().slots) {
            if (sl.inventory == client.player.getInventory()) continue;
            ItemStack st = sl.getStack();
            if (st.isEmpty()) continue;
            List<String> lines = tooltipOf(client, st);
            int[] stats = GemParser.parseStats(lines);
            if (stats != null) {
                modStats = stats;
                modGradeLimit = GemParser.parseGradeLimit(lines);
                modLines = lines;
                return;
            }
        }
    }

    private static void draw(MinecraftClient client, DrawContext ctx) {
        if (!enabled || client.player == null) return;
        boolean screenOpen = client.currentScreen != null;
        int[] st = null;
        int gradeFound = 0;
        if (screenOpen) {
            scanModule(client);
            if (modStats != null) { st = modStats; gradeFound = modGradeLimit; }
        }
        if (st == null) {
            // 모듈 칸에서 못 찾으면: 마우스를 올렸던 마지막 보석 (화면이 열려 있는 동안 유지)
            boolean recent = System.currentTimeMillis() - lastSeen <= SHOW_MS;
            if (lastStats != null && (screenOpen || recent)) { st = lastStats; gradeFound = lastGradeLimit; }
        }
        if (st == null) return;

        int lim = gradeMode == 0 ? gradeFound : LIMITS[gradeMode];
        long[] stock = countOrbs(client);
        List<String> out = new ArrayList<>();
        out.add("§e세공 도우미 §7(V 끄기 / G 등급)");
        out.add("§f현재 " + st[0] + " / " + st[1] + " / " + st[2] + " = " + (st[0] + st[1] + st[2]));
        if (lim == 0) {
            out.add("§c등급을 못 읽음 → G 키로 선택");
        } else {
            Calc.Result r = Calc.best(st[0], st[1], st[2], lim, stock);
            out.add("§7한도 " + lim + (gradeMode == 0 ? " (자동)" : " (수동)"));
            out.add("§a추천 §f바이올렛 §b" + r.a + " §f오팔 §b" + r.b + " §f아다만티움 §b" + r.c);
            out.add("§7결과 합 " + r.sum + " · 광채 " + r.n + "개 사용");
        }
        out.add("§8보유 " + stock[0] + " / " + stock[1] + " / " + stock[2]);

        int x = 6, y = 6, w = 0;
        for (String l : out) w = Math.max(w, client.textRenderer.getWidth(l));
        ctx.fill(x - 3, y - 3, x + w + 3, y + out.size() * 11 + 1, 0xB0000000);
        for (String l : out) { ctx.drawText(client.textRenderer, l, x, y, 0xFFFFFFFF, true); y += 11; }
    }

    private static void msg(MinecraftClient client, String s) {
        if (client.player != null) client.player.sendMessage(Text.literal("§e[세공] §f" + s), true);
    }
}
