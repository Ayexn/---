package kr.segong;

import java.time.LocalDateTime;

/**
 * 오늘 세공 횟수.
 * 세공 모듈 칸에 보석이 들어 있는 동안 내 인벤토리의 광채가 줄어들면(= 광채가 소모되면) 1번으로 센다.
 * 한 번의 세공으로 여러 광채가 몇 번에 나뉘어 줄어도 한 번으로 묶는다 (묶는 시간 gapMs).
 * 날짜가 바뀌면 0부터 다시 센다 (초기화 시각은 resetHour, 기본 0시).
 * Minecraft 코드와 무관한 순수 로직.
 */
final class Tally {
    private Tally() {}

    private static final long GRACE_MS = 1500;     // 보석이 잠깐 안 보여도(화면 깜빡임·다시 열림) 이만큼은 보석이 있는 것으로 본다

    static boolean show = true;    // 카드에 표시할지
    static int resetHour = 0;      // 하루가 바뀌는 시각 (0 = 자정)
    static long gapMs = 350;       // 이 시간 안에 이어서 줄어든 광채는 한 번으로 묶는다
    static int count;              // 오늘 횟수
    static String day = "";        // count 가 속한 날짜 (yyyy-MM-dd)

    private static long[] prev;                    // 지난번에 센 광채 개수
    private static long gemSeenAt = Long.MIN_VALUE / 2;
    private static long lastDropAt = Long.MIN_VALUE / 2;
    private static long lastDropSum;               // 점검용: 마지막으로 줄어든 개수
    private static long lastDropWhen;              // 점검용: 그 시각

    /** 오늘 날짜 글자 (resetHour 시 이전이면 전날로 친다). */
    static String dayKey(LocalDateTime t, int resetHour) {
        return t.minusHours(Math.max(0, Math.min(23, resetHour))).toLocalDate().toString();
    }

    /** 게임 밖이거나 인벤토리를 아직 못 읽는 동안: 개수 비교를 쉰다 (다시 읽기 시작할 때 가짜로 줄어든 걸로 세지 않게). */
    static void pause() {
        prev = null;
    }

    /**
     * 0.05초마다 한 번 부른다.
     * @param stock       지금 인벤토리의 광채 개수 {바이올렛, 오팔, 아다만티움}
     * @param gemPresent  세공 모듈 칸에 보석이 들어 있는지
     * @param today       dayKey(...) 값
     * @return 횟수나 날짜가 바뀌어서 저장이 필요하면 true
     */
    static boolean observe(long[] stock, boolean gemPresent, long now, String today) {
        boolean changed = false;
        if (!today.equals(day)) {                   // 날이 바뀌면 0부터
            day = today;
            count = 0;
            changed = true;
        }
        if (gemPresent) gemSeenAt = now;
        if (prev == null) {
            prev = stock.clone();
            return changed;
        }
        long dropped = 0;
        for (int i = 0; i < 3; i++) {
            if (stock[i] < prev[i]) dropped += prev[i] - stock[i];
        }
        if (dropped > 0 && now - gemSeenAt <= GRACE_MS) {     // 보석을 넣어 둔 상태에서 광채가 소모됨
            if (now - lastDropAt > gapMs) {
                count++;
                changed = true;
            }
            lastDropAt = now;
            lastDropSum = dropped;
            lastDropWhen = now;
        }
        prev = stock.clone();
        return changed;
    }

    static void add(int delta) {
        count = Math.max(0, count + delta);
    }

    static void reset() {
        count = 0;
    }

    /** 점검용: 지금 상태 한 줄 */
    static String describe(long now) {
        return "오늘(" + day + ") " + count + "회 · 표시 " + (show ? "켜짐" : "꺼짐") + " · 묶는 시간 " + gapMs + "ms · 초기화 " + resetHour + "시"
                + (lastDropWhen == 0 ? "" : " · 마지막 소모 " + lastDropSum + "개 (" + Math.max(0, (now - lastDropWhen) / 1000) + "초 전)");
    }
}
