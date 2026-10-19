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
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
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

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.segong"));
        gradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.segong.grade", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.segong"));

        // 1) 툴팁 읽기 (타입을 적지 않아 버전 차이에 덜 민감하게)
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            List<String> plain = new ArrayList<>();
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
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(client, ctx)));
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

    private static void draw(MinecraftClient client, DrawContext ctx) {
        if (!enabled || client.player == null) return;
        int[] st = lastStats;
        if (st == null || System.currentTimeMillis() - lastSeen > SHOW_MS) return;

        int lim = gradeMode == 0 ? lastGradeLimit : LIMITS[gradeMode];
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
