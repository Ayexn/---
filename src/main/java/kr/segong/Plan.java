package kr.segong;

import java.util.ArrayList;
import java.util.List;

/**
 * 추천 고정.
 * 보석을 처음 본 순간 정한 목표(몇 개를 넣어 최종 순도/광도/강도를 얼마로 만들지)를 광채를 넣는 동안 그대로 유지한다.
 * 광채를 하나 넣을 때마다 보석 수치와 보유 개수가 바뀌는데, 그때마다 처음부터 다시 계산하면
 * 같은 합이 나오는 여러 방법 중 다른 것이 뽑혀서 목표가 계속 흔들리기 때문이다.
 * Minecraft 코드와 무관한 순수 계산.
 */
final class Plan {
    private static final int MAX_PLANS = 8;        // 동시에 기억하는 보석 수
    private static final long IDLE_MS = 10_000;    // 이만큼 보석을 안 보면 계획을 잊는다 (화면을 닫았다 다시 열면 새로 계산)

    private static final List<Plan> book = new ArrayList<>();   // 가장 최근에 쓴 계획이 앞
    private static long lastUse;

    private final int lim;
    private final String sig;
    private final int[] start;          // 계획을 세울 때 보석 수치
    private final int[] steps;          // 계획한 사용 개수 (바이올렛, 오팔, 아다만티움)
    private final long[] stock0;        // 계획을 세울 때 보유 개수
    private final int[] seen = new int[3];   // 지금까지 본 '이미 넣은 개수' 중 가장 큰 값
    private final int missing;          // 계획을 세울 때 모자랐던 광채 개수

    private Plan(int lim, String sig, int[] start, int[] steps, long[] stock, int missing) {
        this.missing = missing;
        this.lim = lim;
        this.sig = sig == null ? "" : sig;
        this.start = start.clone();
        this.steps = steps.clone();
        this.stock0 = stock.clone();
    }

    /** 모든 계획을 잊는다 (다음에 보석을 보면 새로 계산). */
    static void clear() {
        book.clear();
        lastUse = 0;
    }

    /**
     * @param stats    지금 보석 수치 {순도, 광도, 강도}
     * @param stock    지금 인벤토리 광채 개수
     * @param effOut   (출력) 화면에 보여줄 보유 개수. 광채가 UI 칸으로 옮겨져 인벤토리에서 줄어도 계획 기준 개수를 유지
     * @return 남은 사용 개수 (a,b,c) · 최종 합 (sum) · 남은 총 개수 (n)
     */
    static Calc.Result resolve(int[] stats, int lim, String sig, long[] stock, long[] effOut, long now) {
        if (now - lastUse > IDLE_MS) book.clear();
        lastUse = now;
        int[] cur = new int[3];
        for (int i = 0; i < 3; i++) cur[i] = Math.max(0, Math.min(100, stats[i]));

        for (int idx = 0; idx < book.size(); idx++) {
            Plan p = book.get(idx);
            Calc.Result r = p.follow(cur, lim, sig, stock, effOut);
            if (r != null) {
                if (idx > 0) { book.remove(idx); book.add(0, p); }
                return r;
            }
        }

        // 맞는 계획이 없으면 새로 세운다
        Calc.Result r = Calc.best(cur[0], cur[1], cur[2], lim, stock);
        Plan p = new Plan(lim, sig, cur, new int[]{r.a, r.b, r.c}, stock, r.missing);
        book.add(0, p);
        while (book.size() > MAX_PLANS) book.remove(book.size() - 1);
        for (int i = 0; i < 3; i++) effOut[i] = stock[i];
        return r;
    }

    /** 점검용: 기억 중인 계획을 글자로 (/segong check) */
    static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < book.size(); i++) {
            Plan p = book.get(i);
            out.add((i == 0 ? "  ▶ " : "    ") + p.lim + "한도 | 시작 " + p.start[0] + "/" + p.start[1] + "/" + p.start[2]
                    + " | 계획 " + p.steps[0] + "/" + p.steps[1] + "/" + p.steps[2]
                    + " | 넣은 " + p.seen[0] + "/" + p.seen[1] + "/" + p.seen[2]
                    + " | 계획 때 보유 " + p.stock0[0] + "/" + p.stock0[1] + "/" + p.stock0[2]
                    + (p.missing > 0 ? " | 부족 " + p.missing : ""));
        }
        return out;
    }

    /** 이 계획으로 지금 보석을 이어서 안내할 수 있으면 남은 개수를, 아니면 null. */
    private Calc.Result follow(int[] cur, int lim, String sig, long[] stock, long[] effOut) {
        if (this.lim != lim || !this.sig.equals(sig == null ? "" : sig)) return null;
        int[] applied = new int[3];
        for (int i = 0; i < 3; i++) {
            int d = cur[i] - start[i];
            if (d < 0 || d % 10 != 0) return null;     // 시작 수치에서 10씩 올라간 값이 아니면 다른 보석
            applied[i] = d / 10;
            if (applied[i] > steps[i]) return null;    // 계획보다 더 넣었으면 새로 계산
            if (applied[i] < seen[i]) return null;     // 수치가 뒤로 갔으면 다른 보석
        }
        Calc.Result r = new Calc.Result();
        int sumStart = 0, totalSteps = 0;
        for (int i = 0; i < 3; i++) {
            seen[i] = applied[i];
            int rem = steps[i] - applied[i];
            if (i == 0) r.a = rem; else if (i == 1) r.b = rem; else r.c = rem;
            r.n += rem;
            sumStart += start[i];
            totalSteps += steps[i];
            effOut[i] = Math.max(stock0[i] - applied[i], stock[i]);   // 넣은 만큼만 빼고, 더 얻었으면 그만큼 반영
        }
        r.sum = sumStart + 10 * totalSteps;
        r.missing = missing;
        return r;
    }
}
