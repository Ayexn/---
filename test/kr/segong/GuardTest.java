package kr.segong;
public class GuardTest {
  static int fails=0; static void check(boolean ok,String m){ if(!ok){fails++; System.out.println("  FAIL: "+m);} else System.out.println("  ok: "+m); }
  public static void main(String[] x){
    Throwable e=new IllegalStateException("시험 오류");
    check(!Guard.drawFailed(e,1000),"1번째 오류: 아직 안 꺼짐");
    check(!Guard.drawFailed(e,1100),"2번째 오류: 아직 안 꺼짐");
    check(Guard.drawFailed(e,1200)&&Guard.autoOff,"5초 안에 3번째: 꺼짐");
    check(!Guard.drawFailed(e,1300),"이미 꺼졌으면 또 알리지 않음");
    Guard.rearm(); check(!Guard.autoOff,"다시 켜면 풀림");
    check(!Guard.drawFailed(e,10000)&&!Guard.drawFailed(e,16000)&&!Guard.drawFailed(e,22000)&&!Guard.autoOff,"5초 넘게 떨어진 오류는 쌓이지 않음");
    check(Guard.iconFailed(e)&&Guard.iconBroken,"아이콘 오류: 처음엔 true");
    check(!Guard.iconFailed(e),"아이콘 오류: 두 번째부터 false");
    Guard.rearm(); check(!Guard.iconBroken,"다시 켜면 아이콘도 복구");
    check(Guard.total>=8,"오류 수 기록 total="+Guard.total);
    String d=Guard.describe(e); System.out.println("  describe: "+d);
    check(d.contains("IllegalStateException")&&d.contains("시험 오류")&&d.contains("GuardTest")==false||d.contains("@"),"설명 글자");
    System.out.println(fails==0?"ALL OK":"FAILS="+fails);
  }
}
