package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SequenceRule.class, priority = 1500)
public abstract class SequenceRuleFastMixin {

    @Shadow @Final private List<MaterialRule> sequence;

    @WrapMethod(method = "compile")
    private RuleEvaluator papi$fastCompileSequence(MaterialRuleContext context, Operation<RuleEvaluator> original) {
        List<MaterialRule> seq = this.sequence;
        int len = seq.size();
        if (len == 0) {
            return (x, y, z) -> null;
        }
        if (len == 1) {
            return seq.get(0).compile(context);
        }
        if (len == 2) {
            RuleEvaluator e0 = seq.get(0).compile(context);
            RuleEvaluator e1 = seq.get(1).compile(context);
            return (x, y, z) -> {
                BlockState s = e0.tryApply(x, y, z);
                return s != null ? s : e1.tryApply(x, y, z);
            };
        }
        if (len == 3) {
            RuleEvaluator e0 = seq.get(0).compile(context);
            RuleEvaluator e1 = seq.get(1).compile(context);
            RuleEvaluator e2 = seq.get(2).compile(context);
            return (x, y, z) -> {
                BlockState s = e0.tryApply(x, y, z);
                if (s != null) return s;
                s = e1.tryApply(x, y, z);
                return s != null ? s : e2.tryApply(x, y, z);
            };
        }
        if (len == 4) {
            RuleEvaluator e0 = seq.get(0).compile(context);
            RuleEvaluator e1 = seq.get(1).compile(context);
            RuleEvaluator e2 = seq.get(2).compile(context);
            RuleEvaluator e3 = seq.get(3).compile(context);
            return (x, y, z) -> {
                BlockState s = e0.tryApply(x, y, z);
                if (s != null) return s;
                s = e1.tryApply(x, y, z);
                if (s != null) return s;
                s = e2.tryApply(x, y, z);
                return s != null ? s : e3.tryApply(x, y, z);
            };
        }
        return original.call(context);
    }
}
