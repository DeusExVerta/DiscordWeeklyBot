package com.mycompany.weeklybot;

import java.util.List;

import java.net.*;

import java.util.concurrent.*;

import java.io.*;

import java.util.Arrays;

public class SimpleRSSSubscriber implements Callable<Void>
{
    private URL url;
    private boolean isBlacklist;
    private List<String> filter;
    
    SimpleRSSSubscriber(String urlString,boolean isBlacklist,String filterString) throws MalformedURLException
    {
        url = new URL(urlString);
        this.isBlacklist = isBlacklist;
        filter = Arrays.asList(filterString.split("\\s"));//need to clean the string.
    }
    SimpleRSSSubscriber(String urlString,String filterString) throws MalformedURLException
    {
        this(urlString,false,filterString);
    }
    
    /*this version of the constructor differs from the specified default behavior
     * in that it provides an empty blacklist filter to the
     */
    SimpleRSSSubscriber(String urlString) throws MalformedURLException
    {
        this(urlString,true,"");
    }
    
    public void cancelSubscription()
    {
        
    }
    
    //TODO:write to only check for updates.
    public Void call() throws IOException
    {   
        return null;
    }
    
}
