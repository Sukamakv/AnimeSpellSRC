package com.example.jjkspells.capri.client;

import java.util.Optional;

/**
 * Extension points. NOTHING here calls the network: the shipped Capri answers with her built-in rule based voice.
 * To plug an LLM or text-to-speech engine, assign BRAIN / VOICE from your own code (never ship API keys in the jar).
 */
public final class CapriProviders {
    public interface Brain {
        /** Return a reply, or empty to fall back to the built-in answers. Must never block the game thread for long. */
        Optional<String> reply(String playerMessage, String context);
    }

    public interface Voice {
        /** Speak a line aloud (stub: the default voice only plays short blips, there is no canonical voice). */
        void speak(String text);
    }

    public static Brain BRAIN = (m, c) -> Optional.empty();
    public static Voice VOICE = t -> {
    };

    private CapriProviders() {
    }
}
