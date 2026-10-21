package kr.segong;

/**
 * 오류 방어 기록. 그리기·툴팁 읽기 중 오류가 나도 게임이 꺼지지 않게 하려고 오류를 잡아서 여기에 적어 둔다.
 * 5초 안에 그리기 오류가 3번 나면 모드를 잠시 끄고, 아이콘 그리기가 실패하면 아이콘만 끈다.
 * Minecraft 코드와 무관한 순수 로직 (저장하지 않는 값이라 게임을 다시 켜면 처음 상태).
 */
final class Guard {
    private Guard() {}

    static boolean autoOff;        // 오류가 반복돼서 잠시 끈 상태
    static boolean iconBroken;     // 아이콘 그리기가 실패해서 아이콘만 끈 상태
    static String lastError = "";
    static int total;              // 지금까지 잡은 오류 수

    private static int burst;
    private static long burstAt;
    private static boolean logged;

    /** 오류를 기록만 한다 (게임은 계속 돌아간다). */
    static void note(Throwable e) {
        total++;
        lastError = describe(e);
        if (!logged) {             // 로그에는 처음 한 번만 자세히 남긴다
            logged = true;
            try { e.printStackTrace(); } catch (Throwable ignored) { /* 무시 */ }
        }
    }

    /** 그리기 오류: 5초 안에 3번 나면 모드를 잠시 끈다. 이번에 막 꺼졌으면 true. */
    static boolean drawFailed(Throwable e, long now) {
        note(e);
        if (now - burstAt > 5000) { burstAt = now; burst = 0; }
        burst++;
        if (burst >= 3 && !autoOff) {
            autoOff = true;
            return true;
        }
        return false;
    }

    /** 아이콘 그리기 실패: 아이콘만 끈다. 이번에 처음 꺼졌으면 true. */
    static boolean iconFailed(Throwable e) {
        note(e);
        if (!iconBroken) {
            iconBroken = true;
            return true;
        }
        return false;
    }

    /** 오류로 꺼진 상태를 풀고 다시 시작한다. */
    static void rearm() {
        autoOff = false;
        iconBroken = false;
        burst = 0;
    }

    static String describe(Throwable e) {
        StringBuilder b = new StringBuilder(e.getClass().getSimpleName());
        String m = e.getMessage();
        if (m != null) b.append(": ").append(m.length() > 120 ? m.substring(0, 120) + "…" : m);
        for (StackTraceElement st : e.getStackTrace()) {
            if (st.getClassName().startsWith("kr.segong")) {
                b.append(" @ ").append(st.getFileName()).append(':').append(st.getLineNumber());
                break;
            }
        }
        return b.toString();
    }
}
