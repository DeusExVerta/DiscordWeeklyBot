package com.mycompany.weeklybot;


import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Objects;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.User;

import java.util.logging.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;


/**
 *
 * @author Gary Howard
 */
public class MeetingEvent
{
    public static enum FEILD_NAMES {MONTH,DAY,YEAR,HOUR,MINUTE,TIME_ZONE,INTERVAL}
    private String name;
    private final ZonedDateTime nextDate;
    private final int intervalDays;
    private final String channelId;
    private final ArrayList<String> attendeeIds = new ArrayList<>();
    private final JDA jda;
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<Void> nextFuture;
    private final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("MM/dd/YY '@' hh:mm a z");

    protected class HostEvent implements Callable<Void>
    {
        @Override
        public Void call()
        {
            Logger logger = Logger.getGlobal();
            logger.info("HostEvent called");
            MessageBuilder messageBuilder = new MessageBuilder();
            jda.getChannelById(MessageChannel.class, channelId).sendMessage(
              notifyAttendees(messageBuilder, "starting now!").build()
            ).queue();
            nextFuture = scheduler.schedule(new HostEvent(), intervalDays, TimeUnit.DAYS);
            nextDate.plusDays(intervalDays);
            return ( null );
        }
    }

    MeetingEvent( String name, int interval, int yr, int mo, int dy, int hr, int mn, ZoneId tz,
      MessageChannel channel, JDA jda )
    {
        this(
          name,
          interval,
          ZonedDateTime.of(yr,mo,dy,hr,mn,0,0,tz),
          channel,
          jda
        );
    }

    MeetingEvent( String name, int interval, ZonedDateTime date, MessageChannel channel, JDA jda )
    {
        Logger logger = Logger.getGlobal();
        ZonedDateTime now = ZonedDateTime.now(date.getZone());
        logger.info(String.format("now is %s",sdf.format(now)));
        this.name = name;
        ZonedDateTime tmpZDT = date;
        logger.info(String.format("nextDate set to %s", sdf.format(tmpZDT)));
        //get delay until next occurence
        long initialDelay = now.until(tmpZDT,ChronoUnit.SECONDS);
        logger.info(String.format("Initial Delay is %d seconds", initialDelay));
        intervalDays = interval;
        long sInterval = interval * ( 24 * 60 * 60 );//compute the interval between events in SECONDS
        logger.info(String.format("Interval is %d seconds", initialDelay));
        //if the event was scheduled in the past...
        if ( tmpZDT.isBefore(now) )
        {
            logger.warning(String.format("Meeting scheduled %d seconds in the past", Math.abs(initialDelay)));
            initialDelay = sInterval-(Math.abs(initialDelay)%sInterval);
            nextDate=tmpZDT.plusSeconds(sInterval);
        }
        else
        {
            nextDate=tmpZDT;
        }
        this.channelId = channel.getId();
        this.jda = jda;
        nextFuture = scheduler.schedule(new HostEvent(), initialDelay, TimeUnit.SECONDS);
        logger.info(String.format("%s scheduled after %d seconds, with a(n) %d day interval", name, initialDelay, interval));
    }

    public boolean addAttendee( String userId )
    {
        //TODO:add check for valid userId.
        if(!attendeeIds.contains(userId))
            return attendeeIds.add(userId);
        else
            return false;
    }

    public boolean removeAttendee( String userId)
    {
        return attendeeIds.remove(userId);
    }

    public boolean cancelEvent(boolean isSilent)
    {
        if ( !isSilent )
        {
            MessageBuilder messageBuilder = new MessageBuilder();
            jda.getChannelById(MessageChannel.class, channelId).sendMessage(
              notifyAttendees(messageBuilder, "canceled").build()
            ).queue();
        }
        return nextFuture.cancel(true);
    }
    
    public boolean updateEventField(String field,int val,boolean isSilent)
    {
        
        if(!nextFuture.cancel(true))
            return false;
        if ( !isSilent )
        {
            MessageBuilder messageBuilder = new MessageBuilder();
            jda.getChannelById(MessageChannel.class, channelId).sendMessage(
              notifyAttendees(messageBuilder, "updated").build()
            ).queue();
        }
        return true;
    }
    
    public MessageBuilder notifyAttendees( MessageBuilder messageBuilder, String action )
    {
        messageBuilder.allowMentions(MentionType.USER);
        if ( !attendeeIds.isEmpty() )
        {
            attendeeIds.forEach(id -> 
            {
                messageBuilder.append(User.fromId(id));
            });
        }
        messageBuilder.append(String.format(" %s is %s", name, action));
        return messageBuilder;
    }

    public String getName()
    {
        return name;
    }

    String ToString()
    {
        return String.format("%s on %s with %d attendees",
          name,
          this.getNextDate(),
          attendeeIds.size());
    }

    public String getNextDate()
    {
        return sdf.format(nextDate);
    }

    public int getInterval()
    {
        return this.intervalDays;
    }

    @Override
    //hash code to exclude volatile fields
    public int hashCode()
    {
        final int prime = 31;
        return ( ( ( ( name.hashCode() * prime ) + intervalDays ) * prime + channelId.hashCode() ) *
          prime + ( int ) nextDate.toEpochSecond() ) * prime;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final MeetingEvent other = ( MeetingEvent ) obj;
        if ( this.intervalDays != other.intervalDays )
        {
            return false;
        }
        if ( !Objects.equals(this.name, other.name) )
        {
            return false;
        }
        if ( !Objects.equals(this.channelId, other.channelId) )
        {
            return false;
        }
        if ( !Objects.equals(this.nextDate, other.nextDate) )
        {
            return false;
        }
        return true;
    }
}
