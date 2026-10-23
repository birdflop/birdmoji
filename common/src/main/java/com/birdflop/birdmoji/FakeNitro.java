package com.birdflop.birdmoji;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds emojis sent with FakeNitro (and similar client mods), which are links to the emoji's image instead of
 * real emojis, either as [name](url) or just the url.
 */
public final class FakeNitro {

    private static final String URL = "https?://(?:cdn|media)\\.discordapp\\.(?:com|net)/emojis/\\d{16,20}\\.(?:png|gif|webp|jpe?g|avif)(?:\\?[^\\s)<>\\\\]*)?";
    private static final Pattern URL_PATTERN = Pattern.compile(URL);
    private static final Pattern ID_PATTERN = Pattern.compile("/emojis/(\\d{16,20})\\.");
    private static final Pattern NAME_PARAM_PATTERN = Pattern.compile("[?&]name=([^&]+)");
    /** The url may be wrapped in <> to hide the embed, which DiscordSRV may have escaped */
    private static final Pattern TEXT_PATTERN = Pattern.compile(
            "\\[(?<name>[^\\]\\n]{1,64})]\\((?:\\\\?<)?(?<url>" + URL + ")(?:\\\\?>)?\\)|(?<bare>" + URL + ")");

    public interface Replacement {
        Component emoji(long id, String name);
    }

    private FakeNitro() {}

    public static Component replace(Component component, Replacement replacement) {
        return replaceLinks(component, replacement).replaceText(builder -> builder
                .match(TEXT_PATTERN)
                .replacement((match, text) -> {
                    String url = match.group("url") != null ? match.group("url") : match.group("bare");
                    Long id = id(url);
                    return id == null ? text : replacement.emoji(id, match.group("name") != null ? clean(match.group("name")) : name(url));
                }));
    }

    /**
     * Masked links that were already turned into clickable text.
     */
    private static Component replaceLinks(Component component, Replacement replacement) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL) {
            @SuppressWarnings("deprecation")
            String url = click.value();
            Long id = URL_PATTERN.matcher(url).matches() ? id(url) : null;
            if (id != null) {
                String text = component instanceof TextComponent textComponent ? textComponent.content().trim() : "";
                return replacement.emoji(id, text.isEmpty() || text.startsWith("http") ? name(url) : clean(text));
            }
        }

        List<Component> children = component.children();
        if (children.isEmpty()) return component;
        return component.children(children.stream().map(child -> replaceLinks(child, replacement)).toList());
    }

    private static Long id(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String name(String url) {
        Matcher matcher = NAME_PARAM_PATTERN.matcher(url);
        return matcher.find() ? clean(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8)) : "emoji";
    }

    private static String clean(String name) {
        String cleaned = name.replace(":", "").replace("\\", "").trim();
        return cleaned.isEmpty() ? "emoji" : cleaned;
    }
}
