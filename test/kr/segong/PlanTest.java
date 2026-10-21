package kr.segong;
public class PlanTest {
  static int fails=0;
  static void check(boolean ok,String msg){ if(!ok){fails++; System.out.println("  FAIL: "+msg);} }
  static String fmt(Calc.Result r,int[] cur){ return r.a+"/"+r.b+"/"+r.c+" 목표 "+(cur[0]+10*r.a)+"/"+(cur[1]+10*r.b)+"/"+(cur[2]+10*r.c)+" 합 "+r.sum; }
  public static void main(String[] x){
    long t=1000; long[] eff=new long[3];
    // [A] 광채를 하나씩 넣어 보석 수치가 바로 오르고 보유가 줄어드는 경우
    Plan.clear();
    int[] cur={11,31,18}; long[] st={184,149,248};
    int[] order={0,0,0,0,0,1,1,1,1,2,2,2,2,2,2};
    Calc.Result first=Plan.resolve(cur,212,"sigA",st,eff,t);
    int[] tgt={cur[0]+10*first.a,cur[1]+10*first.b,cur[2]+10*first.c};
    System.out.println("[A] 시작: "+fmt(first,cur)+" 보유 "+eff[0]+"/"+eff[1]+"/"+eff[2]);
    for(int i=0;i<order.length;i++){
      int k=order[i]; cur[k]+=10; st[k]--; t+=700;
      Calc.Result r=Plan.resolve(cur,212,"sigA",st,eff,t);
      int[] now={cur[0]+10*r.a,cur[1]+10*r.b,cur[2]+10*r.c};
      check(now[0]==tgt[0]&&now[1]==tgt[1]&&now[2]==tgt[2],"목표가 바뀜 step "+(i+1)+" "+fmt(r,cur));
      check(r.sum==first.sum,"합이 바뀜");
      check(r.n==first.n-(i+1),"남은 개수 "+r.n);
      check(eff[0]==st[0]&&eff[1]==st[1]&&eff[2]==st[2],"보유 표시 "+eff[0]+"/"+eff[1]+"/"+eff[2]);
      if(i==4||i==9||i==14) System.out.println("   넣은 "+(i+1)+"개: "+fmt(r,cur)+" 보유 "+eff[0]+"/"+eff[1]+"/"+eff[2]);
    }
    // [B] 광채가 UI 칸으로 옮겨져서 보유만 줄고 수치는 그대로
    Plan.clear();
    int[] c2={11,31,18}; long[] s2={6,5,7};     // 일부러 적게
    Calc.Result f2=Plan.resolve(c2,212,"sigA",s2,eff,t+=100);
    System.out.println("[B] 시작(적은 보유): "+fmt(f2,c2));
    for(int i=0;i<6;i++){ s2[i%3]--; t+=300; Calc.Result r=Plan.resolve(c2,212,"sigA",s2,eff,t);
      check(r.a==f2.a&&r.b==f2.b&&r.c==f2.c,"[B] 추천이 바뀜 "+fmt(r,c2));
      check(eff[0]==6&&eff[1]==5&&eff[2]==7,"[B] 보유 표시가 계획 기준이어야 함 "+eff[0]+"/"+eff[1]+"/"+eff[2]); }
    // [C] 다른 보석(수치가 안 맞음) → 새 계획
    Calc.Result o=Plan.resolve(new int[]{50,50,50},212,"sigA",new long[]{9,9,9},eff,t+=100);
    check(o.sum<=212,"[C] 한도 초과"); System.out.println("[C] 다른 보석: "+fmt(o,new int[]{50,50,50}));
    // 다시 처음 보석으로 돌아오면 (hover 후 복귀) 계획 유지
    Calc.Result back=Plan.resolve(c2,212,"sigA",new long[]{3,3,3},eff,t+=100);
    check(back.a==f2.a&&back.b==f2.b&&back.c==f2.c,"[C] 복귀 시 계획 유지 안 됨 "+fmt(back,c2));
    // [D] 같은 시작 수치의 두 번째 보석: 첫 보석을 끝까지 넣은 뒤 시작 수치로 돌아오면 새로 계산
    Plan.clear(); int[] c3={11,31,18}; long[] s3={184,149,248};
    Calc.Result d1=Plan.resolve(c3,212,"sigA",s3,eff,t+=100);
    c3[0]+=10*d1.a; c3[1]+=10*d1.b; c3[2]+=10*d1.c; s3[0]-=d1.a; s3[1]-=d1.b; s3[2]-=d1.c;
    Calc.Result d2=Plan.resolve(c3,212,"sigA",s3,eff,t+=100);
    check(d2.n==0,"[D] 완료 후 남은 개수 0 이어야 함 "+d2.n);
    Calc.Result d3=Plan.resolve(new int[]{11,31,18},212,"sigA",s3,eff,t+=100);
    check(eff[0]==s3[0]&&eff[1]==s3[1]&&eff[2]==s3[2],"[D] 두 번째 보석은 현재 보유로 새 계산 "+eff[0]);
    System.out.println("[D] 완료 "+d2.n+"개 남음 → 같은 수치 새 보석: "+d3.a+"/"+d3.b+"/"+d3.c+" 보유 "+eff[0]+"/"+eff[1]+"/"+eff[2]);
    // [E] 10초 넘게 안 보면 잊고 새로 계산
    Plan.clear(); int[] c5={11,31,18}; Plan.resolve(c5,212,"sigA",new long[]{184,149,248},eff,t+=100);
    Calc.Result e=Plan.resolve(c5,212,"sigA",new long[]{2,2,2},eff,t+=20000);
    check(e.a<=2&&e.b<=2&&e.c<=2&&eff[0]==2,"[E] 오래 뒤에는 새 보유로 계산 "+e.a+"/"+e.b+"/"+e.c);
    // [F] 등급 한도가 다르면 새 계획
    Plan.clear(); Plan.resolve(c5,212,"sigA",new long[]{184,149,248},eff,t+=100);
    Calc.Result g=Plan.resolve(c5,236,"sigA",new long[]{184,149,248},eff,t+=100);
    check(g.sum>210,"[F] 한도 236 새 계획 "+g.sum);
    // [G] 광채 부족 계산 + 계획 따라가는 동안에도 유지
    Plan.clear(); int[] c7={11,31,18}; long[] s7={2,3,248};
    Calc.Result g1=Plan.resolve(c7,212,"sigA",s7,eff,t+=100);
    check(g1.missing==2,"[G] 부족 2개여야 함 "+g1.missing);
    c7[0]+=10; s7[0]--; Calc.Result g2=Plan.resolve(c7,212,"sigA",s7,eff,t+=100);
    check(g2.missing==2&&g2.sum==g1.sum,"[G] 넣는 동안에도 부족/합 유지 "+g2.missing+" "+g2.sum);
    Plan.clear(); Calc.Result g3=Plan.resolve(new int[]{11,31,18},212,"sigA",new long[]{0,0,0},eff,t+=100);
    check(g3.missing==15&&g3.n==0,"[G] 보유 0이면 부족 15 "+g3.missing);
    Plan.clear(); Calc.Result g4=Plan.resolve(new int[]{11,31,18},212,"sigA",new long[]{184,149,248},eff,t+=100);
    check(g4.missing==0,"[G] 넉넉하면 부족 0 "+g4.missing);
    Calc.Result g5=Calc.best(11,31,18,212,new long[]{-1,-1,-1});
    check(g5.missing==0,"[G] 무제한이면 부족 0");
    System.out.println("[G] 부족: "+g1.missing+" / "+g3.missing+" / "+g4.missing);
    System.out.println(String.join("\n",Plan.describe()));
    System.out.println(fails==0?"ALL OK":"FAILS="+fails);
  }
}
