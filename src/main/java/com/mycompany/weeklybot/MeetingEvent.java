/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.weeklybot;
import java.util.Calendar;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TimeZone;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

import java.text.SimpleDateFormat;


/**
 *
 * @author Gary Howard
 */
public class MeetingEvent implements Callable<Void>
{
    private String name;
    private final Calendar nextDate;
    private final int intervalDays;
    private final String channelId;
    private final ArrayList<String> attendeeIds = new ArrayList<>();
    private final JDA jda;
    private ScheduledFuture<Void> nextFuture;
    
    MeetingEvent(String name,int interval,int yr,int mo,int dy,int hr,int mn,TimeZone tz,MessageChannel channel,JDA jda)
    {
        this(
          name,
          interval,
          new Calendar.Builder().setCalendarType("iso8601").setDate(yr, mo, dy).setTimeOfDay(hr, mn, 0).setTimeZone(tz).build(),
          channel,
          jda
          );   
    }
    MeetingEvent(String name, int interval, Calendar date, MessageChannel channel, JDA jda)
    {
        this.name = name;
        this.nextDate = date;
        //get delay until next occurence
        int initialDelay = nextDate.compareTo(Calendar.getInstance());
        intervalDays = interval;
        if(initialDelay<=0)
        {
            int msInterval = interval*24*60*60*1000;//compute MS interval
            initialDelay = msInterval-(Math.abs(initialDelay)%msInterval);
        }
        this.channelId = channel.getId();
        this.jda = jda;
        nextFuture = jda.getGatewayPool().schedule(this,initialDelay, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public Void call()
    {
        //TODO: Build Message Embed.
        MessageBuilder messageBuilder = new MessageBuilder();
        jda.getChannelById(MessageChannel.class,channelId).sendMessage(
          notifyAttendees(messageBuilder,"starting now!").build()
        ).queue();
        nextFuture = jda.getGatewayPool().schedule(this,intervalDays,TimeUnit.DAYS);
        nextDate.add(Calendar.DAY_OF_MONTH, intervalDays);
        return (null);
    }
    
    public boolean addAttendee(User user)
    {
        return attendeeIds.add(user.getId());
    }
    
    public boolean removeAttendee(User user)
    {
        return attendeeIds.remove(user.getId());
    }
        
    public boolean cancelEvent()
    {
        MessageBuilder messageBuilder = new MessageBuilder();
        jda.getChannelById(MessageChannel.class,channelId).sendMessage(
          notifyAttendees(messageBuilder,"canceled").build()
        ).queue();
        return nextFuture.cancel(true);
    }
    
    public MessageBuilder notifyAttendees(MessageBuilder messageBuilder,String action)
    {
        messageBuilder.allowMentions(MentionType.USER);
        if(!attendeeIds.isEmpty()) {
            attendeeIds.forEach(id->{
                messageBuilder.append("<@&").append(id).append(">");
            });
        }
        messageBuilder.append(String.format("%s is %s", name,action));
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
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/YY '@' hh:mm a z");      
        return sdf.format(nextDate.getTime());
    }
    public int getInterval()
    {
        return this.intervalDays;
    }
    
    @Override
    public int hashCode()
    {
       final int prime = 31;
       return ((((name.hashCode()*prime)+intervalDays)*prime+channelId.hashCode())*prime+(int)nextFuture.getDelay(TimeUnit.MINUTES))*prime;
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
        if ( !Objects.equals(this.nextFuture, other.nextFuture) )
        {
            return false;
        }
        return true;
    }
}
