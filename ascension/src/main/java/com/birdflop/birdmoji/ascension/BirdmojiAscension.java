package com.birdflop.birdmoji.ascension;

import com.birdflop.birdmoji.Birdmoji;
import com.birdflop.birdmoji.DiscordHook;

public final class BirdmojiAscension extends Birdmoji {

    @Override
    protected DiscordHook createHook() {
        return new AscensionHook(this);
    }
}
