package com.birdflop.birdmoji.discordsrv;

import com.birdflop.birdmoji.Birdmoji;
import com.birdflop.birdmoji.DiscordHook;

public final class BirdmojiDiscordSrv extends Birdmoji {

    @Override
    protected DiscordHook createHook() {
        return new DiscordSrvHook(this);
    }
}
