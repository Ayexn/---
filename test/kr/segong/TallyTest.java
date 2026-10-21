package kr.segong;
import java.time.LocalDateTime;
public class TallyTest {
  static int fails=0; static void check(boolean ok,String m){ if(!ok){fails++; System.out.println("  FAIL: "+m);} else System.out.println("  ok: "+m); }
  public static void main(String[] x){
    String D="D1"; long t=100000;
    Tally.gapMs=350; Tally.count=0; Tally.day=D;
    long[] s={184,149,248};
    Tally.observe(s.clone(),true,t,D);                          // 기준 잡기
    t+=1000; s=new long[]{179,145,242}; boolean ch=Tally.observe(s.clone(),true,t,D);
    check(Tally.count==1&&ch,"광채가 소모되면 1회 (한 번에 15개 줄어도 1회)");
    t+=200; s=new long[]{179,145,241}; Tally.observe(s.clone(),true,t,D);
    check(Tally.count==1,"0.2초 뒤에 이어서 줄어든 건 한 번으로 묶음");
    t+=5000; s=new long[]{178,145,241}; Tally.observe(s.clone(),true,t,D);
    check(Tally.count==2,"5초 뒤 다시 소모되면 2회");
    t+=500; s=new long[]{177,145,241}; Tally.observe(s.clone(),true,t,D);
    t+=500; s=new long[]{176,145,241}; Tally.observe(s.clone(),true,t,D);
    check(Tally.count==4,"0.5초 간격으로 하나씩 넣으면 각각 셈 count="+Tally.count);
    t+=1000; s=new long[]{176,150,241}; Tally.observe(s.clone(),true,t,D);
    check(Tally.count==4,"광채가 늘어나는 건(주움/꺼냄) 안 셈");
    t+=2000; Tally.observe(s.clone(),false,t,D);
    t+=2000; s=new long[]{170,150,241}; Tally.observe(s.clone(),false,t,D);
    check(Tally.count==4,"세공 보석이 없을 때 줄어드는 건(버림/보관) 안 셈 count="+Tally.count);
    // 화면 깜빡임: 보석이 0.5~1초 안 보였다가 소모
    t+=1000; Tally.observe(s.clone(),true,t,D);
    t+=500; Tally.observe(s.clone(),false,t,D);
    t+=500; s=new long[]{169,150,241}; Tally.observe(s.clone(),false,t,D);
    check(Tally.count==5,"보석이 잠깐(1.5초 이내) 안 보여도 소모는 셈 count="+Tally.count);
    // 오래 안 보임
    t+=3000; Tally.observe(s.clone(),false,t,D);
    t+=500; s=new long[]{160,150,241}; Tally.observe(s.clone(),false,t,D);
    check(Tally.count==5,"보석이 오래 없으면 안 셈 count="+Tally.count);
    // 일시정지(게임 밖) 후 재개
    Tally.pause(); t+=1000;
    Tally.observe(new long[]{0,0,0},true,t,D);
    check(Tally.count==5,"일시정지 뒤 첫 값은 기준만 잡음");
    t+=1000; Tally.observe(new long[]{0,0,0},true,t,D); check(Tally.count==5,"변화 없으면 안 셈");
    // 날짜
    boolean c2=Tally.observe(new long[]{0,0,0},true,t+1000,"D2");
    check(Tally.count==0&&c2&&Tally.day.equals("D2"),"날짜가 바뀌면 0부터");
    check(Tally.dayKey(LocalDateTime.of(2026,10,21,3,0),6).equals("2026-10-20"),"초기화 6시: 새벽 3시는 전날");
    check(Tally.dayKey(LocalDateTime.of(2026,10,21,6,0),6).equals("2026-10-21"),"초기화 6시: 6시 정각은 오늘");
    check(Tally.dayKey(LocalDateTime.of(2026,10,21,0,5),0).equals("2026-10-21"),"초기화 0시");
    Tally.add(-10); check(Tally.count==0,"빼기는 0 밑으로 안 내려감");
    Tally.add(3); Tally.add(-1); check(Tally.count==2,"더하기/빼기");
    Tally.reset(); check(Tally.count==0,"0으로");
    Tally.gapMs=1200; Tally.count=0; Tally.pause(); t+=10000;
    Tally.observe(new long[]{10,10,10},true,t,"D2"); t+=500; Tally.observe(new long[]{9,10,10},true,t,"D2"); t+=500; Tally.observe(new long[]{8,10,10},true,t,"D2");
    check(Tally.count==1,"묶는 시간을 1200ms 로 늘리면 연속 클릭이 한 번으로 묶임 count="+Tally.count);
    System.out.println(Tally.describe(t+1000));
    System.out.println(fails==0?"ALL OK":"FAILS="+fails);
  }
}
