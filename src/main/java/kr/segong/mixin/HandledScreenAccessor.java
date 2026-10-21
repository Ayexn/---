package kr.segong.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 열린 화면의 왼쪽 위 좌표를 읽기 위한 통로 (슬롯 배지 표시용). 실패해도 모드는 계속 동작한다. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int segong_getX();

    @Accessor("y")
    int segong_getY();
}
