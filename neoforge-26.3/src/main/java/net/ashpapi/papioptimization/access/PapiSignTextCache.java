package net.ashpapi.papioptimization.access;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.level.block.entity.SignText;
import org.jspecify.annotations.Nullable;

public interface PapiSignTextCache {

    FormattedText.ContentConsumer<Boolean> PAPI$NON_EMPTY_VISITOR =
            content -> content.isEmpty() ? Optional.empty() : Optional.of(Boolean.TRUE);

    default boolean papi$hasVisibleMessage(boolean filtered) {
        return papi$computeVisibleMessage((SignText) this, filtered);
    }

    default @Nullable Object papi$getClientRenderCache() {
        return null;
    }

    default void papi$setClientRenderCache(@Nullable Object cache) {
    }

    static boolean papi$computeVisibleMessage(SignText signText, boolean filtered) {
        List<Component> messages = signText.getMessages(filtered);
        for (int i = 0, n = messages.size(); i < n; i++) {
            Component line = messages.get(i);
            if (line != null && line != CommonComponents.EMPTY && line.visit(PAPI$NON_EMPTY_VISITOR).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
