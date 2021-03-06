/*
 * Copyright 2016 jagrosh.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spectra;

import spectra.entities.AsyncInterfacedEventManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javafx.util.Pair;
import javax.imageio.ImageIO;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.*;
import net.dv8tion.jda.JDA.Status;
import net.dv8tion.jda.entities.*;
import net.dv8tion.jda.events.*;
import net.dv8tion.jda.events.guild.*;
import net.dv8tion.jda.events.guild.member.*;
import net.dv8tion.jda.events.message.*;
import net.dv8tion.jda.events.message.guild.*;
import net.dv8tion.jda.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.events.user.*;
import net.dv8tion.jda.events.voice.*;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.dv8tion.jda.utils.*;
import spectra.commands.*;
import spectra.datasources.*;
import spectra.misc.*;
import spectra.tempdata.*;
import spectra.utils.*;
import spectra.web.*;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class Spectra extends ListenerAdapter {
    
    private JDA jda;
    private Command[] commands;
    private boolean idling = false;
    private boolean safetyMode = false;
    private boolean debugMode = false;
    //private static final OffsetDateTime start = OffsetDateTime.now();
    private OffsetDateTime lastDisconnect;
    private Color currentColor;
    private int colorCounter;
    
    //datasources
    private final AFKs afks;
    private final Contests contests;
    private final Donators donators;
    private final Entries entries;
    private final Feeds feeds;
    private final GlobalLists globallists;
    private final Guides guides;
    private final Mutes mutes;
    private final Overrides overrides;
    private final Profiles profiles;
    private final Reminders reminders;
    private final RoleGroups groups;
    private final Rooms rooms;
    private final SavedNames savednames;
    private final Settings settings;
    private final Tags tags;
    private final LocalTags localtags;
    
    //handlers
    private final FeedHandler handler;
    
    //tempdata
    private final MessageCache messagecache;
    private final PhoneConnections phones;
    private final Statistics statistics;
    private final LogInfo loginfo;
    
    //searchers
    private final BingImageSearcher imagesearcher;
    private final GoogleSearcher googlesearcher;
    private final YoutubeSearcher youtubesearcher;
    
    //timers
    private final ScheduledExecutorService unmuter;
    private final ScheduledExecutorService roomchecker;
    private final ScheduledExecutorService reminderchecker;
    private final ScheduledExecutorService cachecleaner;
    private final ScheduledExecutorService avatarchanger;
    private final ScheduledExecutorService contestchecker;
    
    //eventhandler
    private final AsyncInterfacedEventManager eventmanager;
    
    public static void main(String[] args)
    {
        new Spectra().init();
    }
    
    public Spectra()
    {
        afks        = new AFKs();
        contests    = new Contests();
        donators    = new Donators();
        entries     = new Entries();
        feeds       = new Feeds();
        guides      = new Guides();
        mutes       = new Mutes();
        overrides   = new Overrides();
        profiles    = new Profiles();
        reminders   = new Reminders();
        globallists = new GlobalLists();
        groups      = new RoleGroups();
        rooms       = new Rooms();
        savednames  = new SavedNames();
        settings    = new Settings();
        tags        = new Tags();
        localtags   = new LocalTags();
        
        statistics = new Statistics();
        handler = new FeedHandler(feeds,statistics,globallists);
        messagecache = new MessageCache();
        phones = new PhoneConnections();
        loginfo = new LogInfo();
        
        imagesearcher = new BingImageSearcher(OtherUtil.readFileLines("bing.apikey"));
        googlesearcher = new GoogleSearcher();
        youtubesearcher = new YoutubeSearcher(OtherUtil.readFileLines("youtube.apikey").get(0));
        
        unmuter  = Executors.newSingleThreadScheduledExecutor();
        roomchecker = Executors.newSingleThreadScheduledExecutor();
        reminderchecker = Executors.newSingleThreadScheduledExecutor();
        cachecleaner  = Executors.newSingleThreadScheduledExecutor();
        avatarchanger = Executors.newSingleThreadScheduledExecutor();
        contestchecker = Executors.newSingleThreadScheduledExecutor();
        
        eventmanager = new AsyncInterfacedEventManager();
    }
    
    private void init()
    {
        afks.read();
        contests.read();
        donators.read();
        entries.read();
        feeds.read();
        globallists.read();
        groups.read();
        guides.read();
        mutes.read();
        overrides.read();
        profiles.read();
        reminders.read();
        rooms.read();
        savednames.read();
        settings.read();
        tags.read();
        localtags.read();
        
        commands = new Command[]{
            new About(),
            new Achievements(profiles,tags,donators,savednames),
            new AFK(afks),
            new Archive(),
            new Avatar(),
            new ChannelCmd(),
            new ColorMe(groups),
            new Contest(contests, entries),
            new Donate(donators),
            new Draw(),
            new GoogleSearch(googlesearcher),
            new ImageSearch(imagesearcher),
            new Info(),
            //new Invite(),
            new Names(savednames),
            new Nick(),
            new Ping(),
            new Profile(profiles),
            new Reminder(reminders),
            new Roll(),
            new Room(rooms, settings, handler),
            new Server(settings),
            new Speakerphone(phones),
            new Stats(statistics),
            new Tag(tags, localtags, overrides, settings, handler, this),
            new Timefor(profiles),
            new WelcomeGuide(guides),
            new YoutubeSearch(youtubesearcher),
            
            new Ban(settings, loginfo),
            new BotScan(),
            new Clean(handler),
            new Kick(handler, settings),
            new Mute(handler, settings, mutes),
            new Softban(settings, mutes, loginfo),
            new Unban(loginfo),
            new Unmute(handler,settings, mutes),
            
            new Authorize(globallists, handler),
            new CommandCmd(settings, this),
            new Feed(feeds),
            new Ignore(settings),
            new Leave(settings),
            new ModCmd(settings),
            new Prefix(settings),
            new RoleCmd(settings),
            new Say(),
            new Welcome(settings),
            new WelcomeDM(guides),
                
            new Announce(handler,feeds),
            new BlackList(globallists),
            new Donator(donators),
            new Eval(this),
            new SystemCmd(this,feeds,statistics),
            new WhiteList(globallists),
        };
        
        OtherUtil.writeArchive(OtherUtil.compileCommands(commands), "Commands");
        
        try {
            jda = new JDABuilder()
                    .addListener(this)
                    .setBotToken(OtherUtil.readFileLines("discordbot.login").get(0))
                    .setBulkDeleteSplittingEnabled(false)
                    .setEventManager(eventmanager)
                    .buildAsync();
        } catch (LoginException | IllegalArgumentException ex) {
            System.err.println("ERROR - Building JDA : "+ex.toString());
            System.exit(1);
        }
    }
    
    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getAccountManager().setGame("Type "+SpConst.PREFIX+"help");
        
        handler.submitText(Feeds.Type.BOTLOG, event.getJDA().getGuildById(SpConst.JAGZONE_ID),
                SpConst.SUCCESS+"**"+SpConst.BOTNAME+"** is now <@&182294060382289921>\n"
                        + "Connected to **"+event.getJDA().getGuilds().size()+"** servers.\n"
                        + "Started up in "+FormatUtil.secondsToTime(statistics.getUptime()));
        unmuter.scheduleWithFixedDelay(()-> {mutes.checkUnmutes(jda, handler);},0, 10, TimeUnit.SECONDS);
        roomchecker.scheduleWithFixedDelay(() -> {rooms.checkExpires(jda, handler);}, 0, 120, TimeUnit.SECONDS);
        reminderchecker.scheduleWithFixedDelay(() -> {reminders.checkReminders(jda);}, 0, 30, TimeUnit.SECONDS);
        contestchecker.scheduleWithFixedDelay(() -> {contests.notifications(jda);}, 0, 1, TimeUnit.MINUTES);
        cachecleaner.scheduleWithFixedDelay(() -> {
            imagesearcher.clearCache();
            googlesearcher.clearCache();}, 6, 3, TimeUnit.HOURS);
        avatarchanger.scheduleWithFixedDelay(()->{
            if(jda.getStatus()!=Status.CONNECTED)
                return;
            if(colorCounter<=0)
            {
                colorCounter=10;
                currentColor = Color.getHSBColor((float)Math.random(), 1.0f, .5f);
                ArrayList<Role> modifiable = new ArrayList<>();
                for(Guild g: jda.getGuilds())
                {
                    if(!g.isAvailable())
                        continue;
                    if(!PermissionUtil.checkPermission(g, jda.getSelfInfo(), Permission.MANAGE_ROLES))
                        continue;
                    List<Role> guildroles = g.getRolesForUser(jda.getSelfInfo());
                    for(int i=1; i<guildroles.size(); i++)
                        if(guildroles.get(i).getName().equalsIgnoreCase(jda.getSelfInfo().getUsername()))
                        {
                            modifiable.add(guildroles.get(i));
                            break;
                        }
                }
                jda.getAccountManager().setAvatar(AvatarUtil.getAvatar(OtherUtil.makeWave(currentColor))).update();
                modifiable.stream().forEach((r) -> {
                    try{
                    r.getManager().setPermissionsRaw(r.getPermissionsRaw()).setColor(currentColor.brighter()).update();
                    }catch(Exception e){
                        SimpleLog.getLog("ColorChange").fatal(e);
                    }
                });
            }
            else
            {
                jda.getAccountManager().setAvatar(AvatarUtil.getAvatar(OtherUtil.makeWave(currentColor))).update();
            }
            colorCounter--;
        }, 5, 10, TimeUnit.MINUTES);
        sendStats();
    }
    
    public void shutdown()
    {
        jda.shutdown();
    }
    
    @Override
    public void onShutdown(ShutdownEvent event) {
        eventmanager.shutdown();
        
        afks.shutdown();
        contests.shutdown();
        donators.shutdown();
        entries.shutdown();
        feeds.shutdown();
        globallists.shutdown();
        groups.shutdown();
        guides.shutdown();
        mutes.shutdown();
        overrides.shutdown();
        profiles.shutdown();
        reminders.shutdown();
        rooms.shutdown();
        savednames.shutdown();
        settings.shutdown();
        tags.shutdown();
        localtags.shutdown();
        
        handler.shutdown();
        unmuter.shutdown();
        roomchecker.shutdown();
        reminderchecker.shutdown();
        cachecleaner.shutdown();
        avatarchanger.shutdown();
        contestchecker.shutdown();
    }
    
    @Override
    public void onDisconnect(DisconnectEvent event) {
        lastDisconnect = OffsetDateTime.now();
    }
    
    @Override
    public void onReconnect(ReconnectedEvent event) {
        handler.submitText(Feeds.Type.BOTLOG, event.getJDA().getGuildById(SpConst.JAGZONE_ID),
                SpConst.WARNING+"**"+SpConst.BOTNAME+"** has <@&196723905354924032>\n"
                        + (lastDisconnect==null ? "No disconnect time recorded." : 
                        "Downtime: "+FormatUtil.secondsToTime(lastDisconnect.until(OffsetDateTime.now(), ChronoUnit.SECONDS))) );
        lastDisconnect = null;
    }

    @Override
    public void onResume(ResumedEvent event) {
        handler.submitText(Feeds.Type.BOTLOG, event.getJDA().getGuildById(SpConst.JAGZONE_ID),
                SpConst.WARNING+"**"+SpConst.BOTNAME+"** has <@&196723649061847040>\n"
                        + (lastDisconnect==null ? "No disconnect time recorded." : 
                        "Downtime: "+FormatUtil.secondsToTime(lastDisconnect.until(OffsetDateTime.now(), ChronoUnit.SECONDS))) );
        lastDisconnect = null;
    }

    /*@Override
    public void onStatusChange(StatusChangeEvent event) {
        SimpleLog.getLog("Status").info("Status changed from ["+event.getOldStatus()+"] to ["+event.getStatus()+"]");
    }*/

    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        
        PermLevel perm;
        boolean ignore;
        boolean isCommand = false;
        boolean successful;
        
        if(afks.get(event.getAuthor().getId())!=null)
        {
            Sender.sendPrivate(SpConst.SUCCESS+"Welcome back, I have removed your AFK status.", event.getAuthor().getPrivateChannel());
            afks.remove(event.getAuthor().getId());
        }
        if(!event.isPrivate())
            event.getMessage().getMentionedUsers().stream().filter((u) -> (afks.get(u.getId())!=null)).forEach((u) -> {
                String relate = "__"+event.getGuild().getName()+"__ <#"+event.getTextChannel().getId()+"> <@"+event.getAuthor().getId()+">:\n"+event.getMessage().getRawContent();
                if(relate.length()>2000)
                    relate = relate.substring(0,2000);
                Sender.sendPrivate(relate, u.getPrivateChannel());
        });
        
        //blacklist everyone while idling
        //blacklist users while not
        if((idling 
                || globallists.isBlacklisted(event.getAuthor().getId()) 
                || (!event.isPrivate() && globallists.isBlacklisted(event.getGuild().getId()))) 
                && !event.getAuthor().getId().equals(SpConst.JAGROSH_ID))
            return;
        //get the settings for the server
        //settings will be null for private messages
        //make default settings if no settings exist for a server
        String[] currentSettings = (event.isPrivate() ? null : settings.getSettingsForGuild(event.getGuild().getId()));
        if(currentSettings==null && !event.isPrivate())
            currentSettings = settings.makeNewSettingsForGuild(event.getGuild().getId());
        
        //get a sorted list of prefixes
        String[] prefixes = event.isPrivate() ?
            new String[]{SpConst.PREFIX,SpConst.ALTPREFIX} :
            Settings.prefixesFromList(currentSettings[Settings.PREFIXES]);
        
        //compare against each prefix
        String strippedMessage=null;
        for(int i=prefixes.length-1;i>=0;i--)
        {
            if(event.getMessage().getRawContent().toLowerCase().startsWith(prefixes[i].toLowerCase()))
            {
                strippedMessage = event.getMessage().getRawContent().substring(prefixes[i].length()).trim();
                break; 
            }
        }
        //find permission level
        perm = PermLevel.getPermLevelForUser(event.getAuthor(), event.getGuild(), currentSettings);
        
        //check if should ignore
        ignore = false;
        if(!event.isPrivate())
        {
            if( currentSettings[Settings.IGNORELIST].contains("u"+event.getAuthor().getId()) || currentSettings[Settings.IGNORELIST].contains("c"+event.getTextChannel().getId()) )
                ignore = true;
            else if(currentSettings[Settings.IGNORELIST].contains("r"+event.getGuild().getId()) && event.getGuild().getRolesForUser(event.getAuthor()).isEmpty())
                ignore = true;
            else
                for(Role r: event.getGuild().getRolesForUser(event.getAuthor()))
                    if(currentSettings[Settings.IGNORELIST].contains("r"+r.getId()))
                    {
                        ignore = true;
                        break;
                    }
        }
        
        if(!ignore && !event.isPrivate() && !event.getMessage().getMentionedUsers().isEmpty() && !event.getAuthor().isBot())
        {
            StringBuilder builder = new StringBuilder("");
            event.getMessage().getMentionedUsers().stream().forEach(u -> {
                if(afks.get(u.getId())!=null)
                {
                    String response = afks.get(u.getId())[AFKs.MESSAGE];
                    if(response!=null)
                        builder.append("\n\uD83D\uDCA4 **").append(u.getUsername()).append("** is currently AFK:\n").append(response);
                }});
            String afkmessage = builder.toString().trim();
            if(!afkmessage.equals(""))
            Sender.sendMsg(afkmessage, event.getTextChannel());
        }
        
        if(strippedMessage!=null && !event.getAuthor().isBot())//potential command right here
        {
            strippedMessage = strippedMessage.trim();
            if(strippedMessage.equalsIgnoreCase("help"))//send full help message (based on access level)
            {//we don't worry about ignores for help
                isCommand = true;
                String helpmsg = "**Available help ("+(event.isPrivate() ? "Direct Message" : "<#"+event.getTextChannel().getId()+">")+")**:";
                PermLevel current = PermLevel.EVERYONE;
                for(Command com: commands)
                {
                    if(com.hidden)
                        continue;
                    if(!event.isPrivate() && com.command.equals("authorize") && globallists.isAuthorized(event.getGuild().getId()))
                        continue;
                    if( perm.isAtLeast(com.level) )
                    {
                        if(com.level==PermLevel.MODERATOR&& !current.isAtLeast(PermLevel.MODERATOR))
                        {
                            current = PermLevel.MODERATOR;
                            helpmsg+= "\n**Moderator Commands:**";
                        }
                        else if(com.level==PermLevel.ADMIN && !current.isAtLeast(PermLevel.ADMIN))
                        {
                            current = PermLevel.ADMIN;
                            helpmsg+= "\n**Admin Commands:**";
                        }
                        else if(com.level==PermLevel.JAGROSH && !current.isAtLeast(PermLevel.JAGROSH))
                        {
                            current = PermLevel.JAGROSH;
                            helpmsg+= "\n**jagrosh Commands:**";
                        }
                        helpmsg += "\n`"+SpConst.PREFIX+com.command+Argument.arrayToString(com.arguments)+"` - "+com.help;
                    }
                }
                helpmsg+="\n\nFor more information, call "+SpConst.PREFIX+"<command> help. For example, `"+SpConst.PREFIX+"tag help`";
                helpmsg+="\nFor commands, `<argument>` refers to a required argument, while `[argument]` is optional";
                helpmsg+="\nDo not add <> or [] to your arguments, nor quotation marks";
                helpmsg+="\nFor more help, contact **@jagrosh**, check the wiki (<https://github.com/jagrosh/Spectra/wiki>), or join "+SpConst.JAGZONE_INVITE;
                Sender.sendHelp(helpmsg, event.getAuthor().getPrivateChannel(), event);
            }
            else//wasn't base help command
            {
                Command toRun = null;
                String[] args = FormatUtil.cleanSplit(strippedMessage);
                if(args[0].equalsIgnoreCase("help"))
                {
                    String endhelp = args[1]+" "+args[0];
                    args = FormatUtil.cleanSplit(endhelp);
                }
                if(event.getMessage().getAttachments()!=null && !event.getMessage().getAttachments().isEmpty())
                    args[1]+=" "+event.getMessage().getAttachments().get(0).getUrl();
                for(Command com: commands)
                    if(com.isCommandFor(args[0]))
                    {
                        toRun = com;
                        break;
                    }
                if(toRun!=null)
                {
                    isCommand = true;
                    //check if banned
                    boolean banned = false;
                    boolean whitelisted = false;
                    if(!event.isPrivate())
                    {
                        whitelisted = globallists.isWhitelisted(event.getGuild().getId());
                        if(event.getTextChannel().getTopic()!=null && (event.getTextChannel().getTopic().contains("{-"+toRun.command+"}") || event.getTextChannel().getTopic().contains("{-all}")))
                            banned = true;
                        else
                        {
                            for(String bannedCmd : Settings.restrCmdsFromList(currentSettings[Settings.BANNEDCMDS]))
                                if(bannedCmd.equalsIgnoreCase(toRun.command))
                                    banned = true;
                        }
                        if(banned && event.getTextChannel().getTopic()!=null && event.getTextChannel().getTopic().contains("{"+toRun.command+"}"))
                            banned = false;
                    }
                    successful = toRun.run(args[1], event, perm, ignore, banned, whitelisted);
                    if(debugMode)
                        SimpleLog.getLog("Command").info(event.getGuild()+" "+event.getAuthor()+" "+event.getMessage().getContent());
                    statistics.ranCommand(event.isPrivate() ? "0" : event.getGuild().getId(), toRun.command, successful);
                }
                else if (!event.isPrivate() && (!ignore || perm.isAtLeast(PermLevel.ADMIN) || event.getTextChannel().getId().equals(SpecialCase.JDA_GUILD_GENERAL_ID)))
                {
                    String[] tagCommands = Settings.tagCommandsFromList(currentSettings[Settings.TAGIMPORTS]);
                    for(String cmd : tagCommands)
                        if(cmd.equalsIgnoreCase(args[0]))
                        {
                            isCommand=true;
                            boolean nsfw = JagTag.isNSFWAllowed(event);
                            String[] tag = findTag(cmd, event.getGuild(), false, nsfw);
                            if(tag==null)
                            {
                                Sender.sendResponse(SpConst.ERROR+"Tag \""+cmd+"\" no longer exists!", event);
                                successful = false;
                            }
                            else
                            {
                                Sender.sendResponse("\u200B"+JagTag.convertText(JagTag.getContents(tag), args[1], event.getAuthor(), event.getGuild(), event.getChannel()), event);
                                successful = true;
                                
                            }
                            statistics.ranCommand(event.getGuild().getId(), "tag", successful);
                        }
                }
            }
        }
        
        if(!event.isPrivate() && !isCommand && !ignore && !event.getAuthor().isBot())
        {
            TextChannel chan = phones.getOtherLine(event.getTextChannel());
            if(chan!=null)
            {
                String toSend = (PhoneConnections.LINE+FormatUtil.appendAttachmentUrls(event.getMessage()))
                        .replaceAll("(?i)spectra", "you")
                        .replace(event.getAuthorName(), "Spectra")
                        .replace("@everyone", "@\u200Beveryone")
                        .replace("@here", "@\u200Bhere");
                if(toSend.length()>2000)
                    toSend = toSend.substring(0,2000);
                try{
                    chan.sendMessage(toSend);
                }catch(Exception e)
                {
                    phones.endCall(event.getJDA());
                }
            }
        }
    }

    
    @Override
    public void onUserTyping(UserTypingEvent event) {
        if(afks.get(event.getUser().getId())!=null)
        {
            Sender.sendPrivate(SpConst.SUCCESS+"Welcome back, I have removed your AFK status.", event.getUser().getPrivateChannel());
            afks.remove(event.getUser().getId());
        }
        if(!idling)
            if(event.getChannel() instanceof TextChannel)
            {
                TextChannel other = phones.getOtherLine(((TextChannel)event.getChannel()));
                if(other!=null && !event.getUser().isBot())
                    other.sendTyping();
            }
    }
    
    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        if(!(event.getMessage().getRawContent().startsWith("[<@")&&event.getAuthor().equals(event.getJDA().getSelfInfo())))
        {
            rooms.setLastActivity(event.getChannel().getId(), event.getMessage().getTime());
        }
        if(feeds.feedForGuild(event.getGuild(), Feeds.Type.SERVERLOG)!=null)
            messagecache.addMessage(event.getGuild().getId(), event.getMessage());
        statistics.sentMessage(event.getGuild().getId());
        String[] currsettings = settings.getSettingsForGuild(event.getGuild().getId());
        if(currsettings!=null)
        {
            String autorole = currsettings[Settings.AUTOROLE];
            if(autorole!=null)
            {
                String[] parts = autorole.split("\\|");
                Role role = event.getGuild().getRoleById(parts[0]);
                if(role!=null)
                {
                    if(parts.length<2 || parts[1].equals(""))
                    {
                        if(event.getMessage().getTime().isBefore(event.getGuild().getJoinDateForUser(event.getAuthor()).plusMinutes(30)))
                        {
                            if(!event.getGuild().getRolesForUser(event.getAuthor()).contains(role))
                            {
                                event.getGuild().getManager().addRoleToUser(event.getAuthor(), role).update();
                            }
                        }
                    }
                    else if(event.getMessage().getRawContent().equals(parts[1]))
                    {
                        if(!event.getGuild().getRolesForUser(event.getAuthor()).contains(role))
                        {
                            if(event.getChannel().checkPermission(event.getJDA().getSelfInfo(), Permission.MESSAGE_MANAGE))
                                event.getMessage().deleteMessage();
                            event.getGuild().getManager().addRoleToUser(event.getAuthor(), role).update();
                            Sender.sendPrivate(SpConst.SUCCESS+"I have given you the role *"+role.getName()+"* on **"+event.getGuild().getName()+"**", event.getAuthor().getPrivateChannel());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
        SpecialCase.giveMonsterHunterRole(event);
    }
    
    private boolean shouldBeLogged(String type, String id, boolean isBot, String details)
    {
        if(details==null)
            return !isBot;
        if(details.contains("+"+type+id))
            return true;
        return !(details.contains("-"+type+id) || details.contains("+"+type) || (isBot && !details.contains("+bots")));
    }
    
    @Override
    public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        if(event.getAuthor()==null || event.getAuthor().getId().equals(event.getJDA().getSelfInfo().getId()))
            return;
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] feed = feeds.feedForGuild(event.getGuild(), Feeds.Type.SERVERLOG);
        if(feed!=null)
        {
            String id = event.getAuthor().getId();
            Message msg = messagecache.updateMessage(event.getGuild().getId(), event.getMessage());
            String details = feed[Feeds.DETAILS];
            boolean show = shouldBeLogged("m",id,event.getAuthor().isBot(),details) && shouldBeLogged("c",event.getChannel().getId(),event.getAuthor().isBot(),details);
            if(msg!=null && !msg.getRawContent().equals(event.getMessage().getRawContent()) && show)
            {
                String old = FormatUtil.appendAttachmentUrls(msg);
                String newmsg = FormatUtil.appendAttachmentUrls(event.getMessage());

                handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(),
                        "\u26A0 **"+event.getAuthor().getUsername()+"** edited a message in <#"+event.getChannel().getId()+">\n**From:** "
                        +old+"\n**To:** "+newmsg );
            }
        }
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        CallDepend.getInstance().delete(event.getMessageId());
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] feed = feeds.feedForGuild(event.getGuild(), Feeds.Type.SERVERLOG);
        if(feed!=null)
        {
            Message msg = messagecache.deleteMessage(event.getGuild().getId(), event.getMessageId());
            if(msg!=null)
            {
                String id = msg.getAuthor().getId();
                boolean bot = msg.getAuthor().isBot();
                if(event.getJDA().getSelfInfo().getId().equals(id))
                    return;
                String details = feed[Feeds.DETAILS];
                boolean show = shouldBeLogged("m",id,bot,details) && shouldBeLogged("c",msg.getChannelId(),bot,details);
                if( show )
                {
                    String del = FormatUtil.appendAttachmentUrls(msg);
                    handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(),
                            "\u274C **"+msg.getAuthor().getUsername()
                            +"**'s message has been deleted from <#"+msg.getChannelId()+">:\n"+del );
                }
            }
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(), 
                "\uD83D\uDCE5 **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") joined the server."
                        +(MiscUtil.getCreationTime(event.getUser().getId()).plusMinutes(15).isAfter(OffsetDateTime.now()) ? " \uD83C\uDD95" : ""));
        if(mutes.getMute(event.getUser().getId(), event.getGuild().getId())!=null)
            if(PermissionUtil.checkPermission(event.getGuild(), event.getJDA().getSelfInfo(), Permission.MANAGE_ROLES))
                event.getGuild().getRoles().stream().filter((role) -> (role.getName().equalsIgnoreCase("muted") 
                        && PermissionUtil.canInteract(event.getJDA().getSelfInfo(), role))).forEach((role) -> {
                    event.getGuild().getManager().addRoleToUser(event.getUser(), role).update();
        });
        String[] currentsettings = settings.getSettingsForGuild(event.getGuild().getId());
        String current = currentsettings==null ? null : currentsettings[Settings.WELCOMEMSG];
        if(current!=null && !current.equals(""))
        {
            String[] parts = Settings.parseWelcomeMessage(current);
            TextChannel channel = parts[0]==null ? event.getGuild().getPublicChannel() : event.getJDA().getTextChannelById(parts[0]);
            if(channel==null || !channel.getGuild().equals(event.getGuild()))
                channel = event.getGuild().getPublicChannel();
            String toSend = JagTag.convertText(parts[1].replace("%user%", event.getUser().getUsername()).replace("%atuser%", event.getUser().getAsMention()), "", event.getUser(),event.getGuild(), channel).trim();
            if(!toSend.equals(""))
                Sender.sendMsg(toSend, channel);
        }
        String[] guide = guides.get(event.getGuild().getId());
        if(guide!=null)
            for(int i=1; i<guide.length; i++)
                if(guide[i]!=null && !guide[i].equals(""))
                    Sender.sendPrivate(guide[i], event.getUser().getPrivateChannel());
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        
        handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(),
                "\uD83D\uDCE4 **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") left or was kicked from the server.");
        String[] currentsettings = settings.getSettingsForGuild(event.getGuild().getId());
        String current = currentsettings==null ? null : currentsettings[Settings.LEAVEMSG];
        if(current!=null && !current.equals(""))
        {
            String[] parts = Settings.parseWelcomeMessage(current);
            TextChannel channel = parts[0]==null ? event.getGuild().getPublicChannel() : event.getJDA().getTextChannelById(parts[0]);
            if(channel==null || !channel.getGuild().equals(event.getGuild()))
                channel = event.getGuild().getPublicChannel();
            String toSend = JagTag.convertText(parts[1].replace("%user%", event.getUser().getUsername()).replace("%atuser%", event.getUser().getAsMention()), "", event.getUser(),event.getGuild(), channel).trim();
            if(!toSend.equals(""))
                Sender.sendMsg(toSend, channel);
        }
    }

    @Override
    public void onGuildMemberBan(GuildMemberBanEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] info = loginfo.removeInfo(event.getUser().getId(), LogInfo.Type.BAN);
        String[] sinfo = loginfo.removeInfo(event.getUser().getId(), LogInfo.Type.SOFTBAN);
        handler.submitText(Feeds.Type.MODLOG, event.getGuild(), info==null ? ( sinfo==null ?
            "\uD83D\uDD28 **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") was banned from the server." : 
            "\uD83C\uDF4C "+sinfo[0]+" softbanned **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") for "+sinfo[1]) :
            "\uD83D\uDD28 "+info[0]+" banned **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") for "+info[1]);
    }

    @Override
    public void onGuildMemberUnban(GuildMemberUnbanEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] info = loginfo.removeInfo(event.getUser().getId(), LogInfo.Type.UNBAN);
        String[] sinfo = loginfo.removeInfo(event.getUser().getId(), LogInfo.Type.SOFTUNBAN);
        if(sinfo==null)
            handler.submitText(Feeds.Type.MODLOG, event.getGuild(), info==null ?
                "\uD83D\uDD27 **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") was unbanned from the server." :
                "\uD83D\uDD27 "+info[0]+" unbanned **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") for "+info[1]);
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        if(globallists.isBlacklisted(event.getChannel().getGuild().getId()))
            return;
        int size = event.getMessageIds().size();
        boolean servlogon = feeds.feedForGuild(event.getChannel().getGuild(), Feeds.Type.SERVERLOG)!=null;
        String guildid = event.getChannel().getGuild().getId();
        String header = "\u274C\u274C "+size+" messages were deleted from <#"+event.getChannel().getId()+">";
        StringBuilder builder = new StringBuilder("-- deleted messages --\n");
        event.getMessageIds().stream().forEach((id) -> {
            CallDepend.getInstance().delete(id);
            if(servlogon)
                {
                    Message m = messagecache.deleteMessage(guildid, id);
                    if(m!=null)
                    {
                        builder.append("[").append(m.getTime()==null ? "UNKNOWN TIME" : m.getTime().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("] ");
                        builder.append( m.getAuthor() == null ? "????" : m.getAuthor().getUsername() ).append(" : ");
                        builder.append(m.getContent()).append(m.getAttachments()!=null && m.getAttachments().size()>0 ? " "+m.getAttachments().get(0).getUrl() : "").append("\n\n");
                    }
                }
        });
        if(servlogon)
        {
            if(safetyMode)
            {
                handler.submitText(Feeds.Type.SERVERLOG, event.getChannel().getGuild(), builder.toString().length()>2000 ? builder.toString().substring(0,2000) : builder.toString());
            }
            else
                handler.submitFile(Feeds.Type.SERVERLOG, event.getChannel().getGuild(), ()->{
                File f = OtherUtil.writeArchive(builder.toString(), "deleted_messages");
                return new Pair<>(header,f);}, header);
        }
            
    }

    @Override
    public void onGuildMemberNickChange(GuildMemberNickChangeEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] feed = feeds.feedForGuild(event.getGuild(), Feeds.Type.SERVERLOG);
        if(feed!=null)
        {
            String id = event.getUser().getId();
            String details = feed[Feeds.DETAILS];
            boolean show = shouldBeLogged("n",id,event.getUser().isBot(),details);
            if( show )
                handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(), "\u270D **"+event.getUser().getUsername()+"** (ID:"
                        +event.getUser().getId()+") has changed nicknames from "+(event.getPrevNick()==null ? "[none]" : "**"+event.getPrevNick()+"**")+" to "+
                        (event.getNewNick()==null ? "[none]" : "**"+event.getNewNick()+"**"));
        }
    }
    
    @Override
    public void onUserNameUpdate(UserNameUpdateEvent event) {
        savednames.addName(event.getUser().getId(), event.getPreviousUsername());
        ArrayList<Guild> guilds = new ArrayList<>();
        jda.getGuilds().stream().filter((g) -> (g.isMember(event.getUser()))).forEach((g) -> {
            guilds.add(g);
        });
        handler.submitText(Feeds.Type.SERVERLOG, guilds, "\uD83D\uDCDB **"+event.getPreviousUsername()+"** (ID:"
                        +event.getUser().getId()+") has changed names to **"
                        +event.getUser().getUsername()+"**");
    }

    @Override
    public void onUserAvatarUpdate(UserAvatarUpdateEvent event)  {
        if(debugMode)
            SimpleLog.getLog("FeedLog").info(event.getUser()+" has changed avatars");
        if(event.getUser().getId().equals(event.getJDA().getSelfInfo().getId()))
            return;
        String id = event.getUser().getId();
        String oldurl = event.getPreviousAvatarUrl()==null ? event.getUser().getDefaultAvatarUrl() : event.getPreviousAvatarUrl();
        String newurl = event.getUser().getAvatarUrl()==null ? event.getUser().getDefaultAvatarUrl() : event.getUser().getAvatarUrl();
        ArrayList<Guild> guilds = new ArrayList<>();
        jda.getGuilds().stream().filter((g) -> (g.isMember(event.getUser()))).forEach((g) -> {
            String[] feed = feeds.feedForGuild(g, Feeds.Type.SERVERLOG);
            if(feed!=null)
            {
                String details = feed[Feeds.DETAILS];
                if(shouldBeLogged("a",id,event.getUser().isBot(),details))
                    guilds.add(g);
            }
        });
        if(safetyMode)
        {
            handler.submitText(Feeds.Type.SERVERLOG, guilds, "\uD83D\uDDBC **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") has changed avatars:"
                    + "\nOld: "+event.getPreviousAvatarUrl()
                    + "\nNew: "+event.getUser().getAvatarUrl());
        }
        else
            handler.submitFile(Feeds.Type.SERVERLOG, guilds, ()->{
                BufferedImage oldimg = OtherUtil.imageFromUrl(oldurl);
                BufferedImage newimg = OtherUtil.imageFromUrl(newurl);
                BufferedImage combo = new BufferedImage(256,128,BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g2d = combo.createGraphics();
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, 256, 128);
                if(oldimg!=null)
                {
                    g2d.drawImage(oldimg, 0, 0, 128, 128, null);
                }
                if(newimg!=null)
                {
                    g2d.drawImage(newimg, 128, 0, 128, 128, null);
                }
                File f = new File("avatarchange.png");
                try {
                    ImageIO.write(combo, "png", f);
                } catch (IOException ex) {
                    System.out.println("[ERROR] An error occured drawing the avatar.");
                }
                return new Pair<>("\uD83D\uDDBC **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") has changed avatars:",f);
                }, 
                "\uD83D\uDDBC **"+event.getUser().getUsername()+"** (ID:"+event.getUser().getId()+") has changed avatars:"
                    + "\nOld: "+event.getPreviousAvatarUrl()
                    + "\nNew: "+event.getUser().getAvatarUrl());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        handler.submitText(Feeds.Type.BOTLOG, event.getJDA().getGuildById(SpConst.JAGZONE_ID), 
                "```diff\n+ JOINED : "+guild.getName()+" (ID:"+guild.getId()+")"
                + "```Users : **"+guild.getUsers().size()
                +  "**\nOwner : **"+guild.getOwner().getUsername()+"** (ID:"+guild.getOwnerId()
                +   ")\nCreation : **"+MiscUtil.getCreationTime(guild.getId()).format(DateTimeFormatter.RFC_1123_DATE_TIME)+"**");
        sendStats();
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        Guild guild = event.getGuild();
        handler.submitText(Feeds.Type.BOTLOG, event.getJDA().getGuildById(SpConst.JAGZONE_ID), 
                "```diff\n- LEFT : "+guild.getName()+" (ID:"+guild.getId()+")"
                + "```Users : **"+guild.getUsers().size()
                +  "**\nOwner : **"+(guild.getOwner()==null ? "???" : guild.getOwner().getUsername())+"** (ID:"+guild.getOwnerId()
                +   ")\nCreation : **"+MiscUtil.getCreationTime(guild.getId()).format(DateTimeFormatter.RFC_1123_DATE_TIME)+"**");
        sendStats();
    }

    @Override
    public void onVoiceJoin(VoiceJoinEvent event) {
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] feed = feeds.feedForGuild(event.getGuild(), Feeds.Type.SERVERLOG);
        if(feed!=null)
        {
            String id = event.getUser().getId();
            String details = feed[Feeds.DETAILS];
            boolean show = shouldBeLogged("v",id,event.getUser().isBot(),details);
            if( show )
            {
                if(event.getOldChannel() == null)
                {
                    handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(), SafeEmote.VOICEJOIN.get(event.getJDA())+" **"+event.getUser().getUsername()+"** (ID:"
                        +event.getUser().getId()+") has joined voice channel *"+event.getVoiceStatus().getChannel().getName()+"*");
                }
            }
        }
    }
    
    @Override
    public void onVoiceLeave(VoiceLeaveEvent event) {
        if(event.getOldChannel().getUsers().isEmpty())
        {
            String[] room = rooms.get(event.getOldChannel().getId());
            if(room!=null)
            {
                event.getOldChannel().getManager().delete();
                handler.submitText(Feeds.Type.SERVERLOG, event.getOldChannel().getGuild(), "\uD83C\uDF99 Voice channel **"+event.getOldChannel().getName()+
                            "** (ID:"+event.getOldChannel().getId()+") is empty and has been removed.");
                rooms.remove(event.getOldChannel().getId());
            }
        }
        if(globallists.isBlacklisted(event.getGuild().getId()))
            return;
        String[] feed = feeds.feedForGuild(event.getGuild(), Feeds.Type.SERVERLOG);
        if(feed!=null)
        {
            String id = event.getUser().getId();
            String details = feed[Feeds.DETAILS];
            boolean show = shouldBeLogged("v",id,event.getUser().isBot(),details);
            if( show )
            {
                if(event.getVoiceStatus().inVoiceChannel())//change
                {
                    handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(), SafeEmote.VOICEMOVE.get(event.getJDA())+" **"+event.getUser().getUsername()+"** (ID:"
                        +event.getUser().getId()+") has moved voice channels from *"+event.getOldChannel().getName()+"* to *"+event.getVoiceStatus().getChannel().getName()+"*");
                }
                else
                {
                    handler.submitText(Feeds.Type.SERVERLOG, event.getGuild(), SafeEmote.VOICELEAVE.get(event.getJDA())+" **"+event.getUser().getUsername()+"** (ID:"
                        +event.getUser().getId()+") has left voice channel *"+event.getOldChannel().getName()+"*");
                }
            }
        }
    }
    
    private void sendStats()
    {
        //not currently implemented
    }
    
    public boolean isIdling()
    {
        return idling;
    }
    
    public void setIdling(boolean value)
    {
        idling = value;
    }
    
    public boolean isDebug()
    {
        return debugMode;
    }
    
    public void setDebug(boolean value)
    {
        debugMode = value;
    }
    
    public boolean isSafety()
    {
        return safetyMode;
    }
    
    public void setSafeMode(boolean value)
    {
        safetyMode = value;
    }
    
    public Command[] getCommandList()
    {
        return commands;
    }

    public String[] findTag(String tagname, Guild guild, boolean local, boolean nsfw) {
        if(guild!=null)
        {
            String[] tag = localtags.findTag(tagname, guild, nsfw);
            if(tag!=null)
                return tag;
            String[] currentsettings = settings.getSettingsForGuild(guild.getId());
            String[] mirrors = currentsettings==null || currentsettings[Settings.TAGMIRRORS]==null || currentsettings[Settings.TAGMIRRORS].equals("") ?
                    new String[0] : currentsettings[Settings.TAGMIRRORS].split("\\s+");
            for(String id: mirrors)
            {
                Guild g = guild.getJDA().getGuildById(id);
                if(g==null)
                    continue;
                tag = localtags.findTag(tagname, g, nsfw);
                if(tag!=null)
                    return tag;
            }
        }
        return tags.findTag(tagname, guild, local, nsfw);
    }
}
