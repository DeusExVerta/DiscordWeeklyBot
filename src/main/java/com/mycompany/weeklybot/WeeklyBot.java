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

import java.util.concurrent.ConcurrentHashMap;

import java.time.*;

import java.util.logging.*;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.time.zone.ZoneRulesException;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;

/* TODO:
 *
 * updateEvent command
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

    private static MapManager mapMan;
    private static Logger logger = Logger.getGlobal();

    /**
     * @param args the command line arguments
     *
     * @throws javax.security.auth.login.LoginException
     * @throws java.io.FileNotFoundException
     */
    public static void main( String[] args ) throws LoginException, FileNotFoundException, IOException
    {

        SimpleFormatter formatter = new SimpleFormatter();
        StreamHandler handler = new StreamHandler(System.out, formatter);
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);
        FileReader fin = new FileReader("./src/main/resources/config.txt");//read bot token from config.txt
        char[] c = new char[100];
        fin.read(c);

        JDA jda = JDABuilder.createLight(new String(c).split("token=")[1].trim(), EnumSet.noneOf(
          GatewayIntent.class)) // slash commands don't need any intents
          .addEventListeners(new WeeklyBot())
          .setActivity(Activity.watching("You..."))
          .build();

        mapMan = new MapManager(jda);

        // These commands take up to an hour to be activated after creation/update/delete
        CommandListUpdateAction commands = jda.updateCommands();

        // Simple reply commands
        commands.addCommands(
          Commands.slash("say", "Makes the bot say what you tell it to")
            .addOption(STRING, "content", "What the bot should say", true)
        );

        commands.addCommands(
          Commands.slash("leave", "Make the bot leave the server")
        );

        commands.addCommands(
          Commands.slash("prune", "Prune messages from this channel")
            .addOption(INTEGER, "amount", "How many messages to prune (Default 100)")
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

        commands.addCommands(Commands.slash("update", "Update an existing recurring event")//defaults to no modification.
          .addOption(STRING, "name", "The name of the event", false)
          .addOption(INTEGER, "month", "month of the next occurence of the event", false)
          .addOption(INTEGER, "day", "day of the next occurence of the event", false)
          .addOption(INTEGER, "year", "year of the next occurence of the event", false)
          .addOption(INTEGER, "hour", "the time at which the event occurs hour", false)
          .addOption(INTEGER, "minute", "Minute of the event", false)
          .addOption(STRING, "timezone", "Event Timezone", false)
          .addOption(INTEGER, "interval", "the interval in days between event occurances", false)
        );

        commands.addCommands(Commands.slash("delete", "Deletes an existing recurring event")
        );
        commands.addCommands(Commands.slash("attend",
          "add yourself to the attendee list for an event!"));
        commands.addCommands(Commands.slash("unattend",
          "remove yourself from the attendee list for an event."));

        /* subscribe to an RSS feed.
         * the bot will post updates from that feed that match the specified filter every *N*
         * minutes.
         * filters will default to a whitelist posting only articles with a term from the filter in
         * their headline
         */
        commands.addCommands(Commands.slash("rsssub",
          "Subscribes to an RSS feed in the given channel with a filter")
          .addOption(STRING, "url", "the RSS url")
          .addOption(CHANNEL, "channel", "the channel to post updates to", false) // default current channel
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

        commands.addCommands(Commands.slash("now", "tells you the current date and time."));

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
            case "now":
                now(event);
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
                attend(event);
                break;
            case "unattend":
                unattend(event);
                break;
            case "rsssub":
            case "rssunsub":
            case "rssupdatefilter":
            default:
                event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        }
    }

    @Override
    public void onSelectMenuInteraction( SelectMenuInteractionEvent event )
    {
        String[] ids = event.getComponentId().split(":");
        String authorId = ids[0];
        String userId = event.getUser().getId();
        String type = ids[1];
        MeetingEvent tbd;
        String channelId = event.getChannel().getId();

        if ( !authorId.equals(userId) )
        {
            return;
        }

        event.deferEdit().queue();

        switch ( type )
        {
            case "deleteEvent":
                mapMan.deleteEvent(
                  channelId,
                  Integer.valueOf(event.getSelectedOptions().get(0).getValue()));
                break;
            case "updateEvent":
                //determine updates to make to the event.
                //name,month,day,year,hour,min,tz,interval
                tbd = mapMan.identifyMeeting(
                  channelId,
                  Integer.valueOf(event.getSelectedOptions().get(0).getValue()));
                event.reply(String.format("Select field to update for %s:", tbd.getName()))
                  .addActionRow(
                    eventPropertySelection(String.format("%s:updateField(s):%s", userId, tbd.hashCode())))
                  .queue(); 
                //update the event fields as neccessary
                break;
            case "attendEvent":
                //identify selected meetingEvent
                tbd = mapMan.identifyMeeting(
                  channelId,
                  Integer.valueOf(event.getSelectedOptions().get(0).getValue())
                );
                //if the user can be added to the event.(is not already attending)
                if ( tbd.addAttendee(userId) )
                {
                    event.getMessageChannel().sendMessage(
                      String.format("%s, You are now attending %s",
                        User.fromId(userId).getName(),
                        tbd.getName()
                      )
                    ).queue();
                }
                else
                {
                    event.getMessageChannel().sendMessage(
                      String.format("%s, You were already attending %s",
                        User.fromId(userId).getName(),
                        tbd.getName()
                      )
                    ).queue();
                }
                break;
            case "unattendEvent":
                tbd = mapMan.identifyMeeting(
                  channelId,
                  Integer.valueOf(event.getSelectedOptions().get(0).getValue())
                );
                if ( tbd.removeAttendee(userId) )
                {
                    event.getMessageChannel().sendMessage(
                      String.format("%s, You are no longer attending %s",
                        User.fromId(userId).getName(),
                        tbd.getName()
                      )
                    ).queue();
                }
                else
                {
                    event.getMessageChannel().sendMessage(
                      String.format("%s, You were not attending %s",
                        User.fromId(userId).getName(),
                        tbd.getName()
                      )
                    ).queue();
                }
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
                break;
            default:
                break;
        }
    }

    public void say( SlashCommandInteractionEvent event, String content )
    {
        event.reply(content).queue(); // This requires no permissions!
    }

    public void now( SlashCommandInteractionEvent event )
    {
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("MM/dd/YY '@' hh:mm a z");
        event.reply(sdf.format(ZonedDateTime.now())).queue();
    }

    public void leave( SlashCommandInteractionEvent event )
    {
        if ( !event.getMember().hasPermission(Permission.KICK_MEMBERS) )
        {
            event.reply("You do not have permissions to kick me.").setEphemeral(true).queue();
        }
        else
        {
            //Clean meetingMap and rssMap for server.
            mapMan.clearServer(event.getGuild().getIdLong());
            event.reply("Leaving the server... :wave:") // Yep we received it
              .flatMap(v -> event.getGuild().leave()) // Leave server after acknowledging the command
              .queue();
        }
    }

    public void prune( SlashCommandInteractionEvent event )
    {
        int amount = ( int ) enforceOption(event.getOption("amount"), 2, 200, 100); // enforcement: must be between 2-200
        String userId = event.getUser().getId();
        event.reply(String.format("This will delete %d messages.\nAre you sure?", amount)) // prompt the user with a button menu
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
        String channelId = event.getMessageChannel().getId();
        //name
        String name = event.getOption("name").getAsString();

        OptionMapping tzOption = event.getOption("timezone");
        try
        {
            ZoneId tz = ZoneId.of(
              tzOption == null ? "America/New_York" : tzOption.getAsString()
            );

            LocalDateTime ldt = LocalDateTime.now();
            ZonedDateTime zdt = ldt.atZone(tz);
            //month,day,hour,tz,intervalDays
            zdt = zdt.withYear(( int ) enforceZDTOption(event.getOption("year"), zdt,
              ChronoField.YEAR));

            zdt = zdt.withMonth(( int ) enforceZDTOption(event.getOption("month"), zdt,
              ChronoField.MONTH_OF_YEAR));

            zdt = zdt.withDayOfMonth(( int ) enforceZDTOption(event.getOption("day"), zdt,
              ChronoField.DAY_OF_MONTH));

            zdt = zdt.withHour(( int ) enforceZDTOption(event.getOption("hour"), zdt,
              ChronoField.HOUR_OF_DAY));

            zdt = zdt.withMinute(( int ) enforceZDTOption(event.getOption("minute"), zdt,
              ChronoField.MINUTE_OF_HOUR));

            int interval = ( int ) enforceOption(event.getOption("interval"), 1, 365, 7);
            logger.info(
              String.format(
                "%s to be scheduled %s with %d interval",
                name,
                zdt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                interval
              ));
            MessageChannel channel = event.getChannel();
            MeetingEvent meeting = new MeetingEvent(
              name,
              interval,
              zdt,
              channel,
              event.getJDA());
            if ( mapMan.addMeeting(meeting, channelId) )
            {
                event.reply(
                  String.format(
                    "%s created %s \noccuring every %d days",
                    meeting.getName(),
                    meeting.getNextDate(),
                    meeting.getInterval()
                  )
                ).queue();
            }
            else
            {
                event.reply("Too many events! clean up unused events with /delete.").setEphemeral(
                  true).queue();
            }
        }
        catch ( ZoneRulesException ex )
        {
            event.reply(
              String.format("failed to create event.\n%s is not a valid timezone\n%s", tzOption, ex.
                getMessage())
            ).queue();
        }
        catch ( DateTimeException ex )
        {
            event.reply("failed to create event(other dt execption).\n" + ex.getMessage()).queue();
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

    public void attend( SlashCommandInteractionEvent event )
    {
        createEventListMenu(event, "attend");
    }

    public void unattend( SlashCommandInteractionEvent event )
    {
        createEventListMenu(event, "unattend");
    }

    /*
     * Creates and sends a select menu message for the given action.
     */
    private void createEventListMenu( GenericCommandInteractionEvent event, String actionString )
    {
        String channelId = event.getMessageChannel().getId();
        List<MeetingEvent> eventList = mapMan.listChannelEvents(channelId);
        if ( !eventList.isEmpty() )
        {
            String userId = event.getUser().getId();
            event.reply(String.format("Select event to %s:", actionString))
              .addActionRow(eventListSelection(String.format("%s:%sEvent", userId, actionString),
                eventList))
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
                eventMenuBuilder.addOption(meetingEvent.getName(), String.valueOf(meetingEvent.
                  hashCode()));
            });
        }
        return eventMenuBuilder.build();
    }

    private SelectMenu eventPropertySelection( String name )
    {
        SelectMenu.Builder mb = SelectMenu.create(name)
          .setRequiredRange(0, 8)
          .setPlaceholder("Select one or more");
        List<String> ls = Arrays.
          asList("name", "month", "day", "year", "hour", "minute", "timezone", "interval");
        ls.forEach(field -> mb.addOption(field, field));
        return mb.build();
    }

    private long enforceZDTOption( OptionMapping option, ZonedDateTime zdt, TemporalField field )
    {
        ValueRange range = zdt.range(field);
        return enforceOption(option, range.getMinimum(), range.getMaximum(), zdt.get(field));
    }

    private long enforceOption( OptionMapping option, long min, long max, long base )
    {
        logger.info(
          String.format(
            "%s == null ? %d : Math.min(%d, Math.max(%d, %d))",
            option == null ? null : option,
            base,
            max,
            min,
            option == null ? null : option.getAsInt()
          )
        );
        return option == null ? base : Math.min(max, Math.max(min, option.getAsInt()));
    }

    private String menuName( String[] args )
    {
        String name = "";
        for ( String arg : args )
        {
            name += ( arg + ":" );
        }
        return name.substring(0, name.length() - 1);
    }
}
