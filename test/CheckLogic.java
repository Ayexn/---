import kr.segong.*;
import java.util.*;

/** Calc 가 웹 계산기(JS)와 같은 답을 내는지 확인용. 입력: p l s lim a b c  (한 줄에 하나) */
public class CheckLogic {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLong()) {
            int p = sc.nextInt(), l = sc.nextInt(), s = sc.nextInt(), lim = sc.nextInt();
            long[] st = {sc.nextLong(), sc.nextLong(), sc.nextLong()};
            Calc.Result r = Calc.best(p, l, s, lim, st);
            System.out.println(r.a + " " + r.b + " " + r.c + " " + r.sum + " " + r.n);
        }
        List<String> t = Arrays.asList("COMMON 키론", "코룸 정동석을 감정한 보석입니다.", "◇ 순도 : 20%", "◇ 광도 : 19%", "◇ 강도 : 47%");
        System.out.println("PARSE " + Arrays.toString(GemParser.parseStats(t)) + " " + GemParser.parseGradeLimit(t));
        System.out.println("PARSE2 " + Arrays.toString(GemParser.parseStats(Arrays.asList("테라온", "순도 : 50%"))));
    }
}
