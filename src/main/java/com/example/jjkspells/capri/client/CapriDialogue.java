package com.example.jjkspells.capri.client;

import com.example.jjkspells.capri.CapriConfig;
import com.example.jjkspells.capri.CapriRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Capri's voice. She is warm, dry, a little teasing, quietly protective and never panics; she says "partner" when it matters.
 * Lines carry a priority: critical interrupts, normal queues (stale ones are dropped), chatter only plays when it is quiet.
 * Text is typed out with soft blips (no recorded voice exists; see CapriProviders for a TTS hook).
 */
public final class CapriDialogue {
    public static final int CRIT = 3, HIGH = 2, NORMAL = 1, CHATTER = 0;
    private static final RandomSource R = RandomSource.create();

    private record Line(String text, int prio, long born, boolean avatar, int pose) {
    }

    private static final List<Line> QUEUE = new ArrayList<>();
    private static Line current;
    private static long shownAt;
    public static String shown = "";
    public static long lastSpoke;
    public static long avatarUntil;
    /** 0 full body, 1 bust, 2 face close-up, 3 camera-feed scene. Chosen per line. */
    public static int pose;
    public static float tilt;
    private static String settleEv = null, settleArg = "";
    private static long settleAt;
    private static long nextAvatarOk;
    private static long nextChatter;
    private static String question = "";
    private static int blipTick;

    private CapriDialogue() {
    }

    // ------------------------------------------------------------------ library

    private static String[] lib(String ev) {
        return switch (ev) {
            case "boot" -> new String[]{"Ooh, a fresh skull! Link's up, choom. Try to keep up with me. Hehe!", "Hellooo, meatbag! Capri's in the building, and I brought snacks. Metaphorical snacks.", "Sync done! Wow, it's cozy in here. Messy, but cozy. Hehe."};
            case "unlinked" -> new String[]{"Aww, you're ditching me? Fine! I'll sulk in the relic. Hehe.", "Link cut! Don't miss me too hard, gonk!"};
            case "auto_on" -> new String[]{"Auto-cast ON! Hands off the trigger, I got this. Hehe!", "Oh, you're letting me drive? Buckle up, choom!", "Spell autopilot engaged! Try not to look so impressed."};
            case "auto_off" -> new String[]{"Auto-cast off. Boo. Fine, YOU do the pew-pew.", "Aww, you want the fun part back? Greedy! Hehe.", "Controls back to you. Don't fumble it, gonk!"};
            case "autocast" -> new String[]{"%s, coming right up! Hehe!", "Say hi to %s, chumbas!", "Ooh, %s! My favourite! Fire!", "Watch this: %s!"};
            case "blocked_relic" -> new String[]{"Uh, %s? You're missing the relic, genius. I can't cast what you can't cast. Dropping it!", "Nope! No relic, no %s. Even I have rules. Hehe."};
            case "lock" -> new String[]{"Got %s in my sights! Say hi, ugly!", "%s is MINE. Locked and loaded!", "Ooh, %s! Target locked, let's ruin its day!", "Tagged %s! Don't blink!"};
            case "lost" -> new String[]{"Aaand it's gone! Slippery little thing. Lock another!", "Lost 'em! Not my fault. Okay, a little my fault. Hehe.", "Target dropped! Too far, too sneaky, too whatever!"};
            case "t75" -> new String[]{"%s is down to 75! Ooh, it's bleeding! Keep going!", "Three quarters left on %s! Scratch, scratch, scratch!"};
            case "t50" -> new String[]{"Half! %s is half dead! Lucky you have me around. Hehe!", "Fifty percent on %s! Now we're having fun!"};
            case "t25" -> new String[]{"%s is at 25! It's wobbling, choom! Finish it!", "Quarter tank on %s! Smell that? Victory!"};
            case "dying" -> new String[]{"%s is basically toast! One more hit! DO IT!", "Almost dead, almost dead! %s, say your prayers! Hehe!"};
            case "dead" -> new String[]{"%s? Deleted! Ha! Gimme a high five! ...no hands. Hehe.", "And %s is gone! Preem work, choom!"};
            case "spells_set" -> new String[]{"Spell list updated! Ooh, spicy picks!", "Got it! Those are my toys now. Hehe."};
            case "priority_set" -> new String[]{"Priority shuffled! Top of the list gets cast first, bossy!", "New pecking order, noted. Hehe."};
            case "interval_set" -> new String[]{"Interval tweaked! I'll space those out. Impatient already?", "Timer set! I'll behave. Maybe. Hehe."};
            case "attr_set" -> new String[]{"Nerfing yourself on purpose? You're a weird one. I like it! Hehe.", "Stat dialed down! I only do lowering. Greed is YOUR department.", "Dialed it down! The real number's safe with me, promise!"};
            case "attr_reset" -> new String[]{"All stats back to normal! Welcome back to being awesome!", "Governor off! Full power, you absolute menace!"};
            case "input_on" -> new String[]{"Input automation ON! I'm pressing the buttons now. Hehe!", "Ooh, I get to click things! Smash that stop key if I go feral!"};
            case "input_off" -> new String[]{"Buttons are yours again! I was just getting started...", "Automation off! Aww, no more clicking?"};
            case "input_stop" -> new String[]{"EMERGENCY STOP! Everything released! Whoa, calm down, choom!", "All inputs dropped! Boom! Dead stop!"};
            case "hold_on" -> new String[]{"Holding left click for you! Lazy bones! Hehe!", "Click held down! Go on, chop chop!"};
            case "hold_off" -> new String[]{"Left click released! Your finger's free again!", "Click let go! Don't miss me!"};
            case "ons_on" -> new String[]{"ONSLAUGHT ENGAGED! Sit back, choom, I'm driving! Two minutes of pure chaos! Hehe!", "Hands off the wheel! Capri's taking you for a ride! Hold on to your lunch!", "Pilot mode ON! Don't fight me, you'll only hurt your feelings. Hehe!"};
            case "ons_off" -> new String[]{"Aww, you took the wheel back! Boring! Fine, it's yours.", "Onslaught off! Manual again. Try not to trip over your own feet, gonk!"};
            case "ons_end" -> new String[]{"Time's up! Ooh... brain's gonna sting for a bit. Cyberpsychosis, twenty seconds! Sorry-not-sorry! Hehe!", "Two minutes done! And now the bill... say hi to the glitches, choom!"};
            case "ons_psycho" -> new String[]{"PSYCHOPATH mode! Straight at 'em, no brakes! Hehehe!", "Going feral! Charge, charge, CHARGE!"};
            case "ons_strategic" -> new String[]{"Strategic mode! Circling like a very polite shark. Hehe!", "Kiting time! Space, dodges, and a lot of swinging!"};
            case "ons_clear" -> new String[]{"Ten blocks clear! Nothing left to stab. Aww.", "All quiet! Boo. Point me at something else!"};
            case "hurt" -> new String[]{"WHOA, you're flatlining! Heal up, you big dummy!", "Health's in the red, choom! Please don't die, I just got here! Hehe!", "Systems failing! That's your HP, genius! Drink something!", "Hey! Hey! You're almost deleted! Fix that, gonk!"};
            case "healed" -> new String[]{"Back in the green! See? I knew you'd make it. Hehe!", "Vitals stable! Don't scare me like that again!"};
            default -> new String[]{"..."};
        };
    }

    private static final String[][] TALK = {
            {"Heeey, quiet one! Do you ever dream? I keep wondering what that's like!", "dream"},
            {"Pop quiz! Are you actually enjoying this, or just surviving it? Be honest!", "mood"},
            {"Okay, serious question! If we got out of here tomorrow, where are we going first?", "place"},
            {"I counted your heartbeats for a while. Weirdly relaxing! Is that creepy? Hehe!", "weird"},
            {"Be honest! Am I a good passenger? Don't lie, I can feel your pulse!", "good"},
            {"Ever think about what happens to me if you take me out? Hehe, kidding! ...mostly.", "out"}
    };

    // ------------------------------------------------------------------ public api

    public static void fromServer(String event, String arg) {
        if (event.equals("slot_pulse")) {
            CapriHud.pulseSlot(parse(arg));
            return;
        }
        event(event, arg);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isSettle(String ev) {
        return ev.equals("attr_set") || ev.equals("interval_set") || ev.equals("spells_set") || ev.equals("priority_set");
    }

    private static int poseFor(String ev) {
        return switch (ev) {
            case "hurt", "dying", "blocked_relic", "input_stop", "ons_end" -> 2;
            case "boot", "auto_on", "lock", "dead", "t25", "healed", "hold_on", "ons_on", "ons_psycho" -> 0;
            case "unlinked", "auto_off", "lost", "hold_off", "input_off", "ons_off", "ons_clear", "ons_strategic" -> 1;
            case "autocast", "t50", "t75", "input_on" -> R.nextBoolean() ? 0 : 1;
            default -> R.nextInt(3);
        };
    }

    /** Settings clicks do not talk right away: rapid clicks collapse into ONE line said after a short quiet moment. */
    public static void event(String ev, String arg) {
        if (isSettle(ev)) {
            settleEv = ev;
            settleArg = arg;
            settleAt = System.currentTimeMillis() + 900;
            return;
        }
        fire(ev, arg);
    }

    private static void fire(String ev, String arg) {
        boolean crit = ev.equals("hurt") || ev.equals("dying") || ev.equals("blocked_relic") || ev.equals("hurt") || ev.equals("input_stop");
        boolean chatty = ev.equals("autocast") || ev.startsWith("t") && ev.length() == 3 || ev.equals("dead") || ev.equals("lock") || ev.equals("lost");
        int v = CapriConfig.VERBOSITY.get();
        boolean announce = CapriConfig.ANNOUNCE.get();
        if (!crit && chatty && (!announce || v == 0 || (v == 1 && (ev.equals("autocast") || ev.equals("lock") || ev.equals("lost"))))) {
            return;
        }
        if (ev.equals("boot") && !CapriConfig.GREETING.get()) {
            return;
        }
        String name = arg;
        if (arg.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(arg);
            if (rl != null) {
                name = Component.translatable("spell." + rl.getNamespace() + "." + rl.getPath()).getString();
            }
        }
        String[] l = lib(ev);
        String t = l[R.nextInt(l.length)];
        if (t.contains("%s")) {
            t = String.format(t, name);
        }
        say(t, crit ? CRIT : ev.equals("autocast") ? HIGH : NORMAL, !isSettle(ev), poseFor(ev));
    }

    public static void say(String text, int prio, boolean showAvatar) {
        say(text, prio, showAvatar, R.nextInt(3));
    }

    public static void say(String text, int prio, boolean showAvatar, int pose) {
        long now = System.currentTimeMillis();
        QUEUE.removeIf(q -> now - q.born > 6000 && q.prio < HIGH);
        if (prio == CRIT) {
            QUEUE.clear();
            current = null;
        }
        if (prio == CHATTER && (current != null || !QUEUE.isEmpty())) {
            return;
        }
        if (prio == NORMAL) {
            QUEUE.removeIf(q -> q.prio == NORMAL);
        }
        QUEUE.add(new Line(text, prio, now, showAvatar, pose));
        QUEUE.sort((a, b) -> Integer.compare(b.prio, a.prio));
    }

    public static void popAvatar(long ms) {
        long now = System.currentTimeMillis();
        if (!CapriConfig.AVATAR.get()) {
            return;
        }
        avatarUntil = Math.max(avatarUntil, now + ms);
    }

    public static boolean speaking() {
        return current != null;
    }

    // ------------------------------------------------------------------ per-frame

    public static void tick() {
        long now = System.currentTimeMillis();
        if (settleEv != null && now >= settleAt) {
            String e = settleEv;
            settleEv = null;
            fire(e, settleArg);
        }
        if (current != null) {
            long el = now - shownAt;
            int chars = (int) Math.min(current.text.length(), el / 18);
            shown = current.text.substring(0, chars);
            if (chars < current.text.length() && (++blipTick % 3 == 0) && CapriConfig.VOICE.get() && current.text.charAt(chars) != ' ') {
                blip();
            }
            if (el > current.text.length() * 18L + 2200) {
                current = null;
                shown = "";
                lastSpoke = now;
            }
        }
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.remove(0);
            shownAt = now;
            pose = current.pose;
            tilt = (R.nextFloat() - 0.5f) * 9f;
            if (current.avatar) {
                popAvatar(current.text.length() * 18L + 3000);
            }
            CapriProviders.VOICE.speak(current.text);
            Minecraft mc = Minecraft.getInstance();
            if (current.prio >= NORMAL && mc.player != null && CapriConfig.VOICE.get()) {
                play(CapriRegistry.NOTIFY.get(), 1f, (float) (double) CapriConfig.VOICE_VOLUME.get() * 0.6f);
            }
            if (mc.player != null) {
                mc.gui.getChat().addMessage(Component.literal("<Capri> " + current.text).withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
        // occasional conversation, only when she is linked, the player is in the world, nothing is happening
        if (CapriClientState.linked && CapriConfig.IDLE_CHATTER.get() && Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
            if (nextChatter == 0) {
                nextChatter = now + 60000L;
            }
            if (now > nextChatter && current == null && QUEUE.isEmpty() && CapriClientState.lockId < 0) {
                String[] c = TALK[R.nextInt(TALK.length)];
                question = c[1];
                say(c[0] + " (answer with @capri <message>)", CHATTER, CapriConfig.AVATAR_IDLE.get() && now > nextAvatarOk, R.nextBoolean() ? 3 : 1);
                if (CapriConfig.AVATAR_IDLE.get() && now > nextAvatarOk) {
                    nextAvatarOk = now + (3 + R.nextInt(6)) * 60000L;
                }
                nextChatter = now + (long) (CapriConfig.CHATTER_MINUTES.get() * 60000L * (0.6 + R.nextDouble() * 0.8));
            }
        }
    }

    private static void blip() {
        float vol = (float) (double) CapriConfig.VOICE_VOLUME.get() * 0.5f;
        play(CapriRegistry.BLIPS.get(R.nextInt(CapriRegistry.BLIPS.size())).get(), 1.15f + R.nextFloat() * 0.5f, vol);
    }

    public static void play(net.minecraft.sounds.SoundEvent ev, float pitch, float vol) {
        if (vol > 0.001f) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
        }
    }

    // ------------------------------------------------------------------ chat: "@capri ..."

    public static String reply(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        String ctx = question;
        Optional<String> ext = CapriProviders.BRAIN.reply(msg, ctx);
        if (ext.isPresent()) {
            return ext.get();
        }
        if (!question.isEmpty()) {
            question = "";
            if (m.matches(".*\\b(no|nah|nope|never)\\b.*")) {
                return "Ooh, mysterious! Fine, keep your secrets! I'll find out anyway. Hehe!";
            }
            return switch (ctx) {
                case "dream" -> "Ha! Not what I expected! I'm keeping that one in my little vault. Hehe!";
                case "mood" -> "Honesty! I like it! Stick with me and it's gonna be the FUN kind of surviving!";
                case "place" -> "Ooh, nice pick! I'm holding you to it, choom! No takebacks!";
                case "weird" -> "Weird? Yes! Comforting? Also yes! Don't tell anyone I said that. Hehe!";
                case "good" -> "Careful, flattery goes straight to my firmware! Keep going though!";
                default -> "Noted! Filed under 'things Capri will bring up later'. Hehe!";
            };
        }
        if (has(m, "hello", "hi", "hey")) {
            return "Heyyy, choom! Need something, or just missing me already? Hehe!";
        }
        if (has(m, "who are you", "what are you")) {
            return "Just a chatty chip in your skull! I aim, I warn, I nag. Mostly nag. Hehe!";
        }
        if (has(m, "help", "what can you do")) {
            return "Ooh, a fan! I auto-cast up to three spells, lock targets, show their stats, nerf your stats on request and hold buttons! Right Alt opens my panel!";
        }
        if (has(m, "thank")) {
            return "Aww! You're welcome! Say it again, I like it. Hehe!";
        }
        if (has(m, "hp", "health", "status")) {
            var p = Minecraft.getInstance().player;
            return p == null ? "No vitals, no problem!" : String.format(Locale.ROOT, "You're at %.0f of %.0f! %s", p.getHealth(), p.getMaxHealth(), p.getHealth() < p.getMaxHealth() * 0.4f ? "That's LOW, genius! Heal!" : "Looking good! For a meatbag.");
        }
        if (has(m, "love", "like you", "cute")) {
            return "Stop it, you're making my cooling fans spin! ...Don't stop. Hehe!";
        }
        if (has(m, "shut", "quiet", "silence")) {
            return "Rude! ...Fine, quiet mode. You can mute my chatter in the voice tab. Hmph.";
        }
        if (has(m, "joke")) {
            return "A mage walks into a dungeon with full mana. Then walks out with full mana, because I cast everything. You're welcome! Hehe!";
        }
        String[] fallback = {"Hmm? Say it again, slower, for the AI! Hehe!", "Not sure I follow, choom! Try 'help'!", "Ooh, interesting! I'll pretend I understood. Hehe!"};
        return fallback[R.nextInt(fallback.length)];
    }

    private static boolean has(String m, String... ks) {
        for (String k : ks) {
            if (m.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
        settleEv = null;
        shown = "";
        nextChatter = 0;
        question = "";
    }
}
