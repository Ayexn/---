package kr.segong;

/** 추천 광채 계산 (웹 계산기의 calc()와 같은 규칙). Minecraft 코드와 무관한 순수 계산. */
public final class Calc {
    public static final class Result {
        public int a, b, c;   // 바이올렛(순도) / 오팔(광도) / 아다만티움(강도) 사용 개수
        public int sum;       // 사용 후 합
        public int n;         // 총 사용 개수
        public int missing;   // 보유가 모자라서 못 채운 광채 개수 (보유가 넉넉했다면 더 쓸 수 있었던 개수)
    }

    private Calc() {}

    /**
     * @param p,l,s  현재 순도/광도/강도 (0~100)
     * @param lim    등급 한도 (200/212/236)
     * @param stock  보유 개수 {바이올렛, 오팔, 아다만티움}. 음수면 무제한
     */
    public static Result best(int p, int l, int s, int lim, long[] stock) {
        int[] cur = {clamp(p), clamp(l), clamp(s)};
        int cs = cur[0] + cur[1] + cur[2];
        double[] sk = new double[3];
        double mxw = 1;
        boolean anyFinite = false;
        for (int i = 0; i < 3; i++) {
            sk[i] = stock[i] < 0 ? Double.POSITIVE_INFINITY : stock[i];
            if (stock[i] >= 0) { mxw = anyFinite ? Math.max(mxw, stock[i]) : stock[i]; anyFinite = true; }
        }
        double[] w2 = new double[3];
        for (int i = 0; i < 3; i++) w2[i] = Math.max(1, Double.isInfinite(sk[i]) ? mxw : sk[i]);

        Result best = null;
        double bestSum = 0, bestSp = 0;
        for (int a = 0; a <= 10; a++) {
            for (int b = 0; b <= 10; b++) {
                for (int c = 0; c <= 10; c++) {
                    int s0 = Math.min(100, cur[0] + a * 10);
                    int s1 = Math.min(100, cur[1] + b * 10);
                    int s2 = Math.min(100, cur[2] + c * 10);
                    int sum = s0 + s1 + s2;
                    if (sum > lim || a > sk[0] || b > sk[1] || c > sk[2]
                            || cur[0] + a * 10 > 100 || cur[1] + b * 10 > 100 || cur[2] + c * 10 > 100) continue;
                    double r0 = a / w2[0], r1 = b / w2[1], r2 = c / w2[2];
                    double sp = Math.max(r0, Math.max(r1, r2)) - Math.min(r0, Math.min(r1, r2));
                    double sc1 = sum, sc2 = -sp;
                    boolean better;
                    if (best == null) better = true;
                    else if (sc1 != bestSum) better = sc1 > bestSum;
                    else if (sc2 != -bestSp) better = sc2 > -bestSp;
                    else better = false;
                    if (better) {
                        best = new Result();
                        best.a = a; best.b = b; best.c = c; best.sum = sum; best.n = a + b + c;
                        bestSum = sum; bestSp = sp;
                    }
                }
            }
        }
        if (best == null) {
            best = new Result();
            best.sum = cs;
        }
        if (anyFinite) {
            // 보유가 무제한이라면 도달할 수 있는 합과 비교해서, 모자란 광채 개수를 구한다
            Result ideal = best(p, l, s, lim, new long[]{-1, -1, -1});
            best.missing = Math.max(0, (ideal.sum - best.sum) / 10);
        }
        return best;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
}
