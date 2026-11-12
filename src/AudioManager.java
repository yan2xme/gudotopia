import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Background music and sound effects (a SINGLETON - see getInstance(), like SaveManager).
 * Sounds are WAV files in assets/sounds/<name>.wav. A missing file, or a computer without
 * audio, simply means silence - sound can never crash the game.
 */
public final class AudioManager {
    private static AudioManager instance;

    /** The one shared AudioManager, created the first time this is called. */
    public static synchronized AudioManager getInstance() {
        if (instance == null) instance = new AudioManager();
        return instance;
    }

    private final Map<String, byte[]> wavs = new HashMap<>();   // file bytes, loaded once
    private Clip music;
    private boolean muted;

    private AudioManager() {}

    /** Plays a short sound effect, e.g. play("click"). Several sounds may overlap. */
    public void play(String name) {
        if (muted) return;
        Clip clip = open(name);
        if (clip == null) return;
        clip.addLineListener(e -> { if (e.getType() == LineEvent.Type.STOP) clip.close(); });
        clip.start();
    }

    /** Starts looping background music (does nothing if it is already playing). */
    public void playMusic(String name) {
        if (music != null) return;
        music = open(name);
        if (music != null && !muted) music.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public boolean isMuted() { return muted; }

    /** Turns all sound on/off; the music pauses and resumes. */
    public void toggleMute() {
        muted = !muted;
        if (music == null) return;
        if (muted) music.stop();
        else music.loop(Clip.LOOP_CONTINUOUSLY);
    }

    /** Opens assets/sounds/<name>.wav as a ready-to-play Clip, or null if that is not possible. */
    private Clip open(String name) {
        try {
            byte[] bytes = wavs.computeIfAbsent(name, n -> {
                File f = new File("assets/sounds", n + ".wav");
                try { return f.isFile() ? java.nio.file.Files.readAllBytes(f.toPath()) : null; }
                catch (Exception e) { return null; }
            });
            if (bytes == null) return null;
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes)));
            return clip;
        } catch (Exception | LinkageError e) {   // no sound card, headless test run, unsupported file ...
            return null;
        }
    }
}
