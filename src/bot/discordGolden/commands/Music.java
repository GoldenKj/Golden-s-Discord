package bot.discordGolden.commands;

import bot.discordGolden.audioSrc.AudioInfo;
import bot.discordGolden.audioSrc.PlayerSendHandler;
import bot.discordGolden.audioSrc.TrackManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


public class Music implements Command {


    private static final int PLAYLIST_LIMIT = 1000;
    private static Guild guild;
    private static final AudioPlayerManager MANAGER = new DefaultAudioPlayerManager();
    private static final Map<Guild, Map.Entry<AudioPlayer, TrackManager>> PLAYERS = new HashMap<>();


    /**
     * Audio Manager als Audio-Stream-Recource deklarieren.
     */
    public Music() {
        AudioSourceManagers.registerRemoteSources(MANAGER);
    }

    /**
     * Erstellt einen Audioplayer und fügt diesen in die PLAYERS-Map ein.
     * @param g Guild
     * @return AudioPlayer
     */
    private AudioPlayer createPlayer(Guild g) {
        AudioPlayer p = MANAGER.createPlayer();
        TrackManager m = new TrackManager(p);
        p.addListener(m);

        guild.getAudioManager().setSendingHandler(new PlayerSendHandler(p));

        PLAYERS.put(g, new AbstractMap.SimpleEntry<>(p, m));
        p.setVolume(10); ///Volume equilibrado para ficar de boa para aumentar ou abaixar

        return p;
    }

    /**
     * Returnt, ob die Guild einen Eintrag in der PLAYERS-Map hat.
     * @param g Guild
     * @return Boolean
     */
    private boolean hasPlayer(Guild g) {
        return PLAYERS.containsKey(g);
    }

    /**
     * Returnt den momentanen Player der Guild aus der PLAYERS-Map,
     * oder erstellt einen neuen Player für die Guild.
     * @param g Guild
     * @return AudioPlayer
     */
    private AudioPlayer getPlayer(Guild g) {
        if (hasPlayer(g))
            return PLAYERS.get(g).getKey();
        else
            return createPlayer(g);
    }

    /**
     * Returnt den momentanen TrackManager der Guild aus der PLAYERS-Map.
     * @param g Guild
     * @return TrackManager
     */
    private TrackManager getManager(Guild g) {
        return PLAYERS.get(g).getValue();
    }

    /**
     * Returnt, ob die Guild einen Player hat oder ob der momentane Player
     * gerade einen Track spielt.
     * @param g Guild
     * @return Boolean
     */
    private boolean isIdle(Guild g) {
        return !hasPlayer(g) || getPlayer(g).getPlayingTrack() == null;
    }

    /**
     * Läd aus der URL oder dem Search String einen Track oder eine Playlist
     * in die Queue.
     *
     * @param identifier URL oder Search String
     * @param author     Member, der den Track / die Playlist eingereiht hat
     * @param msg        Message des Contents
     */
    private void loadTrack(String identifier, Member author, Message msg) {

        Guild guild = author.getGuild();
        getPlayer(guild);
        MANAGER.setFrameBufferDuration(5000);
        System.out.println(identifier);
        MANAGER.loadItemOrdered(guild, identifier, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack track) {
                getManager(guild).queue(track, author);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(1);
                if (identifier.contains("-playlist")) {
                    msg.getTextChannel().sendMessage(
                            new EmbedBuilder()
                                    .setColor(447375)
                                    .setDescription("**Playlist:**")
                                    .addField("Criando playlist apartir de ", track.getInfo().title, false)
                                    .build()
                    ).queue();
                    for (int i = 0; i < (playlist.getTracks().size() > PLAYLIST_LIMIT ? PLAYLIST_LIMIT : playlist.getTracks().size()); i++) {
                        getManager(guild).queue(playlist.getTracks().get(i), author);
                    }
                } else {
                    getManager(guild).queue(playlist.getTracks().get(1), author);
                    msg.getTextChannel().sendMessage(
                            new EmbedBuilder()
                                    .setColor(447375)
                                    .setDescription("**Adicionado a lista:**")
                                    .addField("Título", track.getInfo().title, false)
                                    .addField("Duração", "` " + getTimestamp(track.getDuration()) + " `", false)
                                    .addField("Autor", track.getInfo().author, false)
                                    .build()
                    ).queue();
                }
            }

            @Override
            public void noMatches() {

            }

            @Override
            public void loadFailed(FriendlyException exception) {
                exception.printStackTrace();
            }
        });

    }

    /**
     * Stoppt den momentanen Track, worauf der nächste Track gespielt wird.
     * @param g Guild
     */
    private void skip(Guild g) {
        getPlayer(g).stopTrack();
    }

    //Coloca um volume no bot
    private void volume(Guild g, int v) {
        getPlayer(g).setVolume(v);
    }

    /**
     * Erzeugt aus dem Timestamp in Millisekunden ein hh:mm:ss - Zeitformat.
     * @param milis Timestamp
     * @return Zeitformat
     */
    private String getTimestamp(long milis) {
        long seconds = milis / 1000;
        long hours = Math.floorDiv(seconds, 3600);
        seconds = seconds - (hours * 3600);
        long mins = Math.floorDiv(seconds, 60);
        seconds = seconds - (mins * 60);
        return (hours == 0 ? "" : hours + ":") + String.format("%02d", mins) + ":" + String.format("%02d", seconds);
    }

    /**
     * Returnt aus der AudioInfo eines Tracks die Informationen als String.
     * @param info AudioInfo
     * @return Informationen als String
     */
    private String buildQueueMessage(AudioInfo info) {
        AudioTrackInfo trackInfo = info.getTrack().getInfo();
        String title = trackInfo.title;
        long length = trackInfo.length;
        return  "`[ " + getTimestamp(length) + " ]` " + title + " \n";
    }

    /**
     * Sendet eine Embed-Message in der Farbe Rot mit eingegebenen Content.
     * @param event MessageReceivedEvent
     * @param content Error Message Content
     */
    private void sendErrorMsg(MessageReceivedEvent event, String content) {
        event.getTextChannel().sendMessage(
                new EmbedBuilder()
                        .setColor(Color.red)
                        .setDescription(content)
                        .build()
        ).queue();
    }


    @Override
    public boolean called(String[] args, MessageReceivedEvent event) {
        return false;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event) {


        guild = event.getGuild();

        if (args.length < 1) {
            sendErrorMsg(event, help());
            return;
        }

        switch (args[0].toLowerCase()) {

            case "play":
            case "p":

                if (args.length < 2) {
                    sendErrorMsg(event, "Por favor insira o URL válido!");
                    return;
                }

                String input = Arrays.stream(args).skip(1).map(s -> " " + s).collect(Collectors.joining()).substring(1);

                if ((input.startsWith("http://") || input.startsWith("https://"))) {
                    input = input;
                } else {
                    event.getTextChannel().sendMessage(
                            new EmbedBuilder()
                                    .setColor(447375)
                                    .setDescription("Procurando musica com nome " + input)
                                    .build()
                    ).queue();
                    input = "ytsearch: " + input;
                    System.out.println(input);
                }

                loadTrack(input, event.getMember(), event.getMessage());

                break;
            case "volume":
            case "v":

                if (args.length > 2) {
                    sendErrorMsg(event, "Por favor insira valor valido entre 0 a 100!");
                    return;
                }

                String text = Arrays.stream(args).skip(1).map(s -> " " + s).collect(Collectors.joining()).substring(1);
                text = text.replace("%", "");
                int volume = 10;
                try {
                    volume = Integer.parseInt(text);
                } catch (NumberFormatException ex) {
                    event.getTextChannel().sendMessage(
                            new EmbedBuilder()
                                    .setColor(Color.RED)
                                    .setDescription("Formato invalido!")
                                    .build()
                    ).queue();
                    break;
                }

                if (!(volume < 100 || volume > 0)) {
                    event.getTextChannel().sendMessage(
                            new EmbedBuilder().setColor(Color.RED).setDescription("Valor Invalido").build()
                    ).queue();
                } else {
                    if (volume > 100)
                        volume = 100;
                    volume(guild, volume);
                    event.getTextChannel().sendMessage(
                            new EmbedBuilder()
                                    .setColor(447375)
                                    .setDescription("Volume da musica alterado para " + volume + "%")
                                    .build()
                    ).queue();
                }
                break;


            case "skip":
            case "s":

                if (isIdle(guild)) return;
                for (int i = (args.length > 1 ? Integer.parseInt(args[1]) : 1); i == 1; i--) {
                    skip(guild);
                    event.getTextChannel().sendMessage(
                            new EmbedBuilder()
                                    .setColor(447375)
                                    .setDescription("A musica atual foi pulada")
                                    .build()
                    ).queue();
                }

                break;


            case "stop":

                if (isIdle(guild)) return;

                getManager(guild).purgeQueue();
                skip(guild);
                guild.getAudioManager().closeAudioConnection();

                break;


            case "shuffle":

                if (isIdle(guild)) return;
                getManager(guild).shuffleQueue();

                break;


            case "now":
            case "info":

                if (isIdle(guild)) return;

                AudioTrack track = getPlayer(guild).getPlayingTrack();
                AudioTrackInfo info = track.getInfo();

                event.getTextChannel().sendMessage(
                        new EmbedBuilder()
                                .setColor(447375)
                                .setDescription("**MUSIC INFO:**")
                                .addField("Título", info.title, false)
                                .addField("Duração", "` " + getTimestamp(track.getPosition()) + " / " + getTimestamp(track.getDuration()) + " `", false)
                                .addField("Autor", info.author, false)
                                .build()
                ).queue();

                break;



            case "queue":

                if (isIdle(guild)) return;

                int sideNumb = args.length > 1 ? Integer.parseInt(args[1]) : 1;

                List<String> tracks = new ArrayList<>();
                List<String> trackSublist;

                getManager(guild).getQueue().forEach(audioInfo -> tracks.add(buildQueueMessage(audioInfo)));

                if (tracks.size() > 20)
                    trackSublist = tracks.subList((sideNumb-1)*20, (sideNumb-1)*20+20);
                else
                    trackSublist = tracks;

                String out = String.join("\n", trackSublist);
                int sideNumbAll = tracks.size() >= 20 ? tracks.size() / 20 : 1;

                event.getTextChannel().sendMessage(
                        new EmbedBuilder()
                                .setColor(Color.ORANGE)
                                .setDescription(
                                        "**QUEUE ATUAL:**\n" +
                                                "*[ Tracks | Side " + sideNumb + " / " + sideNumbAll + "]* " + "\n \n " + " **Now Playing!** \n " +
                                                out
                                )
                                .build()
                ).queue();

                break;
        }


    }

    @Override
    public void executed(boolean sucess, MessageReceivedEvent event) {
        System.out.println("[INFOMATION] '!m / !music' Command was used! By: " + event.getAuthor().getName());
    }

    @Override
    public String help() {
        return null;
    }
}