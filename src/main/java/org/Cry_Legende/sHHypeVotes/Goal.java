package org.Cry_Legende.sHHypeVotes;

import java.util.List;
import java.util.Optional;

public record Goal(
    String key,
    String displayName,
    int threshold,
    List<String> commands,
    Optional<String> bossbarThemeColor,
    int timeExtensionSeconds // NOUVEAU
) {
}
