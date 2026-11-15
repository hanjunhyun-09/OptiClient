package dev.opticlient.fabric.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 밝기(gamma) 값을 슬라이더 범위 밖으로 설정하기 위한 접근자 */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("value")
    void opticlient$setValue(Object value);
}
