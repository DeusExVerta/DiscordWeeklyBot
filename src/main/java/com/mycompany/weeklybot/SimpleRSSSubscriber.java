/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.weeklybot;

import java.util.List;

import java.net.*;

import java.util.concurrent.*;

import java.io.*;

import java.util.Arrays;

/**
 *
 * @author Gary Howard
 */
public class SimpleRSSSubscriber implements Callable<URL>
{
    private URL url;
    private boolean isBlacklist;
    private List<String> filter;
    private BufferedReader br;
    
    SimpleRSSSubscriber(String urlString,boolean isBlacklist,String filterString) throws MalformedURLException
    {
        url = new URL(urlString);
        this.isBlacklist = isBlacklist;
        filter = Arrays.asList(filterString.split("\\s"));
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
    
    //TODO:rewrite to only check for updates.
    public URL call() throws IOException
    {
        br = new BufferedReader(new InputStreamReader(url.openStream()));
        br.lines().forEach(line->
        {
            String[] lines = line.split(".*<a.*?\"|\">|</a>.*");
            if(lines.length==2)
            {
                String articleUrl=lines[0];
                String title = lines[1];
                filter.forEach(word->
                {
                    if(title.contains(word)^isBlacklist)
                    {
                        
                    }
                }
                );
            }
        }
        );
        br.close();
        return url;
    }
    
}
