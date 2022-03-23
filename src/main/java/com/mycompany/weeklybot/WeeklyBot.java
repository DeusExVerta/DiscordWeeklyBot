/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.weeklybot;


import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.*;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import javax.security.auth.login.LoginException;

import java.util.*;

import java.io.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import java.util.concurrent.ConcurrentHashMap;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;

/* TODO:
 * createEvent command 
 * updateEvent command
 * deleteEvent command
 * 
 * attendEvent command
 * unattendEvent command
 * 
 * proposeBan command
 * proposeUnban command
 *  
 * RSSSub command
 * RSSUnsub command
 * RSSUpdateFilter command
 *
 * validateDeck
 * 
 * @author Gary Howard
 */
public class WeeklyBot extends ListenerAdapter
{
    private class BasicOption extends SelectOption
    {
        BasicOption( String s )
        {
            super(s, s);
        }
    }
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(3);
    private final ConcurrentHashMap<String, List<MeetingEvent>> meetingMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<SimpleRSSSubscriber>> rssMap = new ConcurrentHashMap<>();

    /**
     * @param args the command line arguments
     *
     * @throws javax.security.auth.login.LoginException
     * @throws java.io.FileNotFoundException
     */
    public static void main( String[] args ) throws LoginException, FileNotFoundException, IOException
    {
        FileReader fin = new FileReader("./src/main/resources/config.txt");//read bot token from config.txt
        char[] c = new char[100];
        fin.read(c);

        JDA jda = JDABuilder.createLight(new String(c).split("token=")[1].trim(), EnumSet.noneOf(
          GatewayIntent.class)) // slash commands don't need any intents
          .addEventListeners(new WeeklyBot())
          .build();

        // These commands take up to an hour to be activated after creation/update/delete
        CommandListUpdateAction commands = jda.updateCommands();

        // Simple reply commands
        commands.addCommands(
          Commands.slash("say", "Makes the bot say what you tell it to")
            .addOption(STRING, "content", "What the bot should say", true) // you can add required options like this too
        );

        // Commands without any inputs
        commands.addCommands(
          Commands.slash("leave", "Make the bot leave the server")
        );

        commands.addCommands(
          Commands.slash("prune", "Prune messages from this channel")
            .addOption(INTEGER, "amount", "How many messages to prune (Default 100)") // simple optional argument
        );

        commands.addCommands(Commands.slash("create", "Creates a recurring event")
          .addOption(STRING, "name", "The name of the event", true)
          .addOption(INTEGER, "month", "month of the next occurence of the event", false)//default current month
          .addOption(INTEGER, "day", "day of the next occurence of the event", false)//default current day
          .addOption(INTEGER, "year", "year of the next occurence of the event", false)//default current year
          .addOption(INTEGER, "hour", "the time at which the event occurs hour", false)//default current hour
          .addOption(INTEGER, "minute", "Minute of the event", false)//default 0
          .addOption(STRING, "timezone", "Event Timezone", false)//default GMT
          .addOption(INTEGER, "interval", "the interval in days between event occurances", false)//default 7 days
        );

        commands.addCommands(Commands.slash("update", "Update an existing recurring event")
        );

        commands.addCommands(Commands.slash("delete", "Deletes an existing recurring event")
        );
        commands.addCommands(Commands.slash("attend", "add yourself to the attendee list for an event!"));
        commands.addCommands(Commands.slash("unattend", "remove yourself from the attendee list for an event."));

        /* subscribe to an RSS feed.
         * the bot will post updates from that feed that match the specified filter every *N*
         * minutes.
         * filters will default to a whitelist posting only articles with a term from the filter in
         * their headline
         */
        commands.addCommands(Commands.slash("rsssub",
          "Subscribes to an RSS feed in the given channel with a filter")
          .addOption(STRING, "url", "the RSS url")
          .addOption(CHANNEL, "channel", "the channel to post updates to",false) // default current channel
          .addOption(BOOLEAN, "isblacklist",
            "whether the subsequent filter string is a blacklist",
            false)//default false.(treats the subsequent filter as a whitelist) 
          .addOption(STRING, "filter", "a list of words to filter for", false)
        );

        /* Unsubscribe from an RSS feed
         * if no feeds are present in the selected channel
         */
        commands.addCommands(Commands.slash("rssunsub", "Unsubscribes from an RSS feed")
        );

        /* change the filter for a RSS feed.
         * ommited parameters will be left unchanged.
         * the bot will follow the same procedure as Unsubscribe.
         */
        commands.addCommands(Commands.slash("rssupdatefilter", "Updates the filter for an RSS feed")
          .addOption(BOOLEAN, "isblacklist", "whether the modified filter should be a blacklist",
            false)
          .addOption(STRING, "filter", "a list of words to filter for", false)
        );

        // Send the new set of commands to discord, this will override any existing global commands with the new set provided here
        commands.queue();

    }

    @Override
    public void onSlashCommandInteraction( SlashCommandInteractionEvent event )
    {
        // Only accept commands from guilds
        if ( event.getGuild() == null )
        {
            return;
        }
        switch ( event.getName() )
        {
            case "say":
                say(event, event.getOption("content").getAsString()); 
                break;
            case "leave":
                leave(event);
                break;
            case "prune": 
                prune(event);
                break;
            case "create":
                create(event);
                break;
            case "update":
                update(event);
                break;
            case "delete":
                delete(event);
                break;
            case "attend":
            case "unattend":
            case "rsssub":
            case "rssunsub":
            default:
                event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        }
    }

    @Override
    public void onSelectMenuInteraction( SelectMenuInteractionEvent event )
    {
        String[] ids = event.getComponentId().split(":");
        String authorId = ids[0];
        String type = ids[1];
        MeetingEvent tbd;
        String channelId = event.getChannel().getId();

        if ( !authorId.equals(event.getUser().getId()) )
        {
            return;
        }
        event.deferEdit().queue();

        switch ( type )
        {
            case "deleteEvent":
                //identify meeting to be deleted
                tbd = identifyMeeting(
                  channelId,
                  Integer.valueOf(event.getSelectedOptions().get(0).getValue())
                );                
                //notify attendees of deletion
                //cancel next occurence of event
                tbd.cancelEvent();
                //remove event from meetingMap
                meetingMap.get(channelId).remove(tbd);
                event.getHook().deleteOriginal().queue();
                break;
            case "updateEvent":
                //identify meeting to be updated
                tbd = identifyMeeting(
                  event.getChannel().getId(),
                  Integer.getInteger(event.getSelectedOptions().get(0).getValue())
                );
                //determine updates to make to the event.
                //multiple selct menus...
                //notify attendees of update
                //update the event fields as neccessary
                break;
            case "attendEvent":
                tbd = identifyMeeting(
                  event.getChannel().getId(),
                  Integer.getInteger(event.getValues().get(0))
                );
                tbd.addAttendee(event.getUser());
                break;
            case "unattendEvent":
                tbd = identifyMeeting(
                  event.getChannel().getId(),
                  Integer.getInteger(event.getValues().get(0))
                );
                tbd.removeAttendee(event.getUser());
                break;
            case "updateRSSFilter":
            case "unsubRSS":
            default:
                break;
        }
        event.getHook().deleteOriginal().queue();
    }

    @Override
    public void onButtonInteraction( ButtonInteractionEvent event )
    {
        String[] id = event.getComponentId().split(":"); //button Id
        String authorId = id[0];
        String type = id[1];
        //Check that the button is for the user that clicked it, otherwise let interaction fail
        if ( !authorId.equals(event.getUser().getId()) )
        {
            return;
        }
        event.deferEdit().queue(); // acknowledge the button was clicked

        MessageChannel channel = event.getChannel();
        switch ( type )
        {
            case "prune":
                int amount = Integer.parseInt(id[2]);
                event.getChannel().getIterableHistory()
                  .skipTo(event.getMessageIdLong())
                  .takeAsync(amount)
                  .thenAccept(channel::purgeMessages);
                event.getHook().deleteOriginal().queue();
                break;
            case "delete":
                event.getHook().deleteOriginal().queue();
                break;
            default:
                break;
        }
    }

    public void say( SlashCommandInteractionEvent event, String content )
    {
        event.reply(content).queue(); // This requires no permissions!
    }

    public void leave( SlashCommandInteractionEvent event )
    {
        if ( !event.getMember().hasPermission(Permission.KICK_MEMBERS) )
        {
            event.reply("You do not have permissions to kick me.").setEphemeral(true).queue();
        }
        else
        {
            event.reply("Leaving the server... :wave:") // Yep we received it
              .flatMap(v -> event.getGuild().leave()) // Leave server after acknowledging the command
              .queue();
            //Clean meetingMap and rssMap for server.
        }
    }

    public void prune( SlashCommandInteractionEvent event )
    {
        int amount = enforceOption(event.getOption("amount"),2,200,100); // enforcement: must be between 2-200
        String userId = event.getUser().getId();
        event.reply(String.format("This will delete %d messages.\nAre you sure?",amount)) // prompt the user with a button menu
          .addActionRow(
            Button.secondary(userId + ":delete", "Nevermind!"),
            Button.danger(userId + ":prune:" + amount, "Yes!")) // the first parameter is the component id we use in onButtonInteraction above
          .queue();
    }

    /*
     * creates an event on the specified date occurring every N days.
     */
    public void create( SlashCommandInteractionEvent event )
    {
        List<MeetingEvent> eventList;
        String channelId = event.getMessageChannel().getId();
        if ( meetingMap.containsKey(channelId) )
        {
            eventList = meetingMap.get(channelId);
        }
        else
        {
            eventList = Collections.synchronizedList(new ArrayList<>());
            meetingMap.put(channelId, eventList);
        }

        synchronized ( eventList )
        {
            if ( eventList.size() < 25 )
            {
                //name
                String name = event.getOption("name").getAsString();

                //month,day,hour,tz,intervalDays
                int mo = enforceCalendarOption(event.getOption("month"), Calendar.MONTH);
                
                int dy = enforceCalendarOption(event.getOption("day"), Calendar.DAY_OF_MONTH);
                
                int yr = enforceCalendarOption(event.getOption("year"), Calendar.YEAR);
                
                int hr = enforceCalendarOption(event.getOption("hour"), Calendar.HOUR_OF_DAY);
                
                int mn = enforceCalendarOption(event.getOption("minute"), Calendar.MINUTE);
                
                OptionMapping tzOption = event.getOption("timezone");
                
                TimeZone tz = TimeZone.getTimeZone(
                  tzOption == null ? "America/New_York" : tzOption.getAsString()
                );
                
                int interval = enforceOption(event.getOption("interval"), 1, 365, 7);

                MessageChannel channel = event.getChannel();
                MeetingEvent meeting = new MeetingEvent(
                  name,
                  interval,
                  yr, mo, dy, hr, mn, tz,
                  channel,
                  event.getJDA());
                eventList.add(meeting);
                event.reply(String.format(
                    "%s created %d/%d/%d @ %d/%d \n occuring every %d days (debug)",
                    name,mo,dy,yr,hr,mn,interval)).queue();
                /*event.reply(
                  String.format(
                    "%s created %s \noccuring every %d days",
                    meeting.getName(),
                    meeting.getNextDate(),
                    meeting.getInterval()
                  )
                ).queue();*/
            }
            else
            {
                event.reply("Too many events! clean up unused events with /delete.").setEphemeral(
                  true).queue();
            }
        }
    }

    public void update( SlashCommandInteractionEvent event )
    {
        createEventListMenu(event, "update");
    }

    public void delete( SlashCommandInteractionEvent event )
    {
        createEventListMenu(event, "delete");
    }

    public void attend(SlashCommandInteractionEvent event)
    {
        createEventListMenu(event, "attend");
    }
    
    public void unattend(SlashCommandInteractionEvent event)
    {
        createEventListMenu(event, "unattend");
    }
    
    /*
     * Creates and sends a select menu message for the given action.
     */
    private void createEventListMenu( GenericCommandInteractionEvent event, String actionString )
    {
        String channelId = event.getMessageChannel().getId();
        if ( meetingMap.containsKey(channelId) )
        {
            List<MeetingEvent> eventList = meetingMap.get(channelId);
            String userId = event.getUser().getId();
            event.reply(String.format("Select event to %s:", actionString))
              .addActionRow(eventListSelection(String.format("%s:%sEvent", userId, actionString),
                eventList))
              .setEphemeral(true)
              .queue();
        }
        else
        {
            event.reply("No events scheduled in this channel.").setEphemeral(true).queue();
        }
    }

    /* 
     * Creates a select menu for all events in the given list.
     */
    private SelectMenu eventListSelection( String name, List<MeetingEvent> eventList )
    {
        SelectMenu.Builder eventMenuBuilder = SelectMenu.create(name)
          .setRequiredRange(0, 1)
          .setPlaceholder("Event");
        synchronized ( eventList )
        {
            eventList.forEach(meetingEvent ->
            {
                eventMenuBuilder.addOption(meetingEvent.getName(), String.valueOf(meetingEvent.hashCode()));
            });
        }
        return eventMenuBuilder.build();
    }
    
    private int enforceCalendarOption( OptionMapping option, int calendarField )
    {
        Calendar c = Calendar.getInstance();
        return enforceOption(option, c.getActualMinimum(calendarField), c.getActualMaximum(
          calendarField), c.get(calendarField));
    }

    private int enforceOption( OptionMapping option, int min, int max, int base )
    {
        return option == null ? base : Math.min(max, Math.max(min, option.getAsInt()));
    }
    
    private MeetingEvent identifyMeeting(String channelId,int meetingId)
    {
        for(MeetingEvent meeting:meetingMap.get(channelId))
        {
            if(meeting.hashCode()==meetingId)
            {
                return meeting;
            }
        }
        return null;
    }
}
