/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.weeklybot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Guild;

/**
 *
 * @author Gary Howard
 */
public class MapManager
{
    private final ConcurrentHashMap<String, List<MeetingEvent>> meetingMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<SimpleRSSSubscriber>> rssMap = new ConcurrentHashMap<>();
    private final JDA jda;
    private static final Logger LOGGER = Logger.getGlobal();
    private final ScheduledExecutorService CLEANER = new ScheduledThreadPoolExecutor(1);
    private final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(10);
    
    MapManager(JDA jda)
    {
        this.jda = jda;
        CLEANER.schedule(new cleanMaps(), 7, TimeUnit.DAYS);
    }
    
    public boolean addMeeting(MeetingEvent meeting,String channelId)
    {
        //prepare to add the event by initalizing neccesary lists.
        List<MeetingEvent> eventList;
        if ( meetingMap.containsKey(channelId) )
        {
            eventList = meetingMap.get(channelId);
        }
        else
        {
            eventList = Collections.synchronizedList(new ArrayList<>());
            meetingMap.put(channelId, eventList);
        }
        //add the meeting to the list.
        synchronized ( eventList )
        {
            if ( eventList.size() < 25 )
            {
                eventList.add(meeting);
                return true;
                
            }
            else
            {
                return false;
            }
        }
    }
    
    public void deleteEvent(String channelId,int meetingId)
    {
        MeetingEvent tbd = identifyMeeting(
            channelId,
            meetingId
          );
        //cancel next occurence of event
        tbd.cancelEvent(false);
        //remove event from meetingMap
        meetingMap.get(channelId).remove(tbd);
    }
    
    public List<MeetingEvent> listChannelEvents(String channelId)
    {
        if(meetingMap.containsKey(channelId))
        {
            return meetingMap.get(channelId);
        }
        else
        {
            return new ArrayList<>();
        }
    }

    public void removeServerEvents(long guildId){
        jda.getGuildById(guildId).getChannels().forEach(channel->
            {
                removeChannelEvents(channel.getId());
            });
    }
    
    public void removeChannelEvents(String channelId)
    {
        if(meetingMap.containsKey(channelId))
        {
            meetingMap.get(channelId).forEach(meetingEvent -> meetingEvent.cancelEvent(true));
            meetingMap.remove(channelId);
        }
    }
    public void removeServerFeeds(long guildId){
        Guild guild =jda.getGuildById(guildId);
        if(guild==null)
            return;
        guild.getChannels().forEach(channel->
            {
                removeChannelFeeds(channel.getId());
            });
    }
    public void removeChannelFeeds(String channelId){
        if(rssMap.containsKey(channelId))
        {
            rssMap.get(channelId).forEach(rssSubscriber -> rssSubscriber.cancelSubscription());
            rssMap.remove(channelId);
        }
    }
    public void clearServer(long guildId)
    {
        removeServerEvents(guildId);
        removeServerFeeds(guildId);
    }
    public void clearChannel(String channelId){
        removeChannelEvents(channelId);
        removeChannelFeeds(channelId);
    }
    private class cleanMaps implements Callable<Void>
    {
        @Override
        public Void call()
        {//may need to register keys to be removed in a list then remove after.
            rssMap.forEachKey(5, key->
            {
                if(jda.getChannelById(MessageChannel.class, key)==null)
                {
                    rssMap.remove(key);
                }
            });
            meetingMap.forEachKey(5, key->
            {
                if(jda.getChannelById(MessageChannel.class, key)==null)
                {
                    meetingMap.remove(key);
                }
            });
            CLEANER.schedule(new cleanMaps(),7,TimeUnit.DAYS);
            return null;
        }
    }
    
    //returns a meeting for a specified channel by Id
    public MeetingEvent identifyMeeting( String channelId, int meetingId )
    {
        for ( MeetingEvent meeting : meetingMap.get(channelId) )
        {
            if ( meeting.hashCode() == meetingId )
            {
                return meeting;
            }
        }
        return null;
    }
}
