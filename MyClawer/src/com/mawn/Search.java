package com.mawn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

public class Search implements Runnable{
	private HashMap<String, ArrayList<String>> disallowListCache=new HashMap<>();
	private ArrayList<String> errorList=new ArrayList<>();
	private ArrayList<String> result=new ArrayList<>();
	private String startUrl;
	private int maxUrl;
	private String serachString;
	boolean caseSensitive=false;
	boolean limitHost=false;
	
	public Search(String startUrl,int maxUrl, String serachString) {
		// TODO Auto-generated constructor stub
		this.startUrl=startUrl;
		this.maxUrl=maxUrl;
		this.serachString=serachString;
	}
	
	public ArrayList<String> getResult(){
		return result;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		crawl(startUrl,maxUrl,serachString,limitHost,caseSensitive);
	}
	
	private URL verifyUrl(String url){
		if(!url.toLowerCase().startsWith("http://"))
			return null;
		URL verifiedUrl=null;
		try {
			verifiedUrl=new URL(url);
		} catch (Exception e) {
			// TODO: handle exception
			return null;
		}
		return verifiedUrl;
	}
	
	private boolean isRobotAllowed(URL urlToCheck){
		String host=urlToCheck.getHost().toLowerCase();
		ArrayList<String> disallowList=disallowListCache.get(host);
		if(disallowList==null){
			disallowList=new ArrayList<>();
			try {
				URL robotsFileUrl=new URL("http://"+host+"/robots.txt");
				BufferedReader reader=new BufferedReader(new InputStreamReader(robotsFileUrl.openStream()));
				
				String line;
				while((line=reader.readLine())!=null){
					if(line.indexOf("Disallow:")==0){
						String disallowPath=line.substring("Disallow:".length());
						int commenIndex=disallowPath.indexOf("#");
						if(commenIndex!=-1){
							disallowPath=disallowPath.trim();
							disallowList.add(disallowPath);
						}
					}
				}
				disallowListCache.put(host, disallowList);
			} catch (Exception e) {
				// TODO: handle exception
				return true;
			}			
		}
		String file=urlToCheck.getFile();
		for(int i=0;i<disallowList.size();i++){
			String disallow=disallowList.get(i);
			if(file.startsWith(disallow)){
				return false;
			}
		}
		return true;
	}
	
	private String downloadPage(URL pageUrl) {
		try {
			BufferedReader reader=new BufferedReader(new InputStreamReader(pageUrl.openStream()));
			String line;
			StringBuffer pageBuffer=new StringBuffer();
			while((line=reader.readLine())!=null){
				pageBuffer.append(line);
			}
			return pageBuffer.toString();
		} catch (Exception e) {
			// TODO: handle exception
		}
		return null;
	}
	
	private String removeWwwFromUrl(String url) {
		int index=url.indexOf("://www.");
		if(index!=-1){
			return url.substring(0,index+3)+url.substring(index+7);
		}
		return(url);
	}
	
	private ArrayList<String> retrieveLinks(URL pageUrl,String pageContents, HashSet crawledList,
		    boolean limitHost) {
		Pattern p=Pattern.compile("<a\\s+href\\s*=\\s*\"?(.*?)[\"|>]",Pattern.CASE_INSENSITIVE);
		Matcher m=p.matcher(pageContents);
		
		ArrayList<String> linkList=new ArrayList<>();
		while(m.find()){
			String link=m.group(1).trim();
			if(link.length()<1){
				continue;
			}
			if(link.charAt(0)=='#'){
				continue;
			}
			if(link.indexOf("mailto:")!=-1){
				continue;
			}
			if(link.toLowerCase().indexOf("javascript")!=-1){
				continue;
			}
			if(link.indexOf("://")==-1){
				if(link.charAt(0)=='/'){
					link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+link;
				}else{
					String file=pageUrl.getFile();
					if(file.indexOf('/')==-1){
						link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+"/"+link;
					}else{
						String path=file.substring(0, file.lastIndexOf('/')+1);
						link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+path+link;
					}
				}
			}
			int index=link.indexOf('#');
			if(index!=-1){
				link=link.substring(0,index);
			}
			link=removeWwwFromUrl(link);
			
			URL verifiedLink=verifyUrl(link);
			if(verifiedLink==null){
				continue;
			}
			if(limitHost&&!pageUrl.getHost().toLowerCase().equals(
					verifiedLink.getHost().toLowerCase())){
				continue;
			}
			if(crawledList.contains(link)){
				continue;
			}
			linkList.add(link);
		}
		return(linkList);
	}
	
	public ArrayList< String> crawl(String startUrl, int maxUrls, String searchString,boolean limithost,boolean caseSensitive )
	  { 
	    
	    System.out.println("searchString="+searchString);
	    //the list have been crawled
	    HashSet< String> crawledList = new HashSet< String>();
	    //the list to be crawled
	    LinkedHashSet< String> toCrawlList = new LinkedHashSet< String>();
	    //judge the initial circumstance
	    if (maxUrls < 1) {
	       errorList.add("Invalid Max URLs value.");
	       System.out.println("Invalid Max URLs value.");
	    }
	    if (searchString.length() < 1) {
	      errorList.add("Missing Search String.");
	      System.out.println("Missing search String");
	    }
	    if (errorList.size() > 0) {
	      System.out.println("err!!!");
	      return errorList;
	    }
	    //remove wwws
	    startUrl = removeWwwFromUrl(startUrl);
	    //add the initial url
	    toCrawlList.add(startUrl);
	    
	    while (toCrawlList.size() > 0) {
	      if (maxUrls != -1) {
	        if (crawledList.size() == maxUrls) {
	          break;
	        }
	      }
	      // Get URL at bottom of the list.
	      String url =  toCrawlList.iterator().next();
	      // Remove URL from the to crawl list.
	      toCrawlList.remove(url);
	      // Convert string url to URL object.
	      URL verifiedUrl = verifyUrl(url);
	      // Skip URL if robots are not allowed to access it.
	      if (!isRobotAllowed(verifiedUrl)) {
	        continue;
	      }
	      // add URL to crawledList
	      crawledList.add(url);
	      String pageContents = downloadPage(verifiedUrl);
	      if (pageContents != null && pageContents.length() > 0){
	        // 从页面中获取有效的链接
	        ArrayList< String> links =retrieveLinks(verifiedUrl, pageContents, crawledList,limitHost);
	        toCrawlList.addAll(links);
	        if (searchStringMatches(pageContents, searchString,caseSensitive))
	        {
	          result.add(url);
	          System.out.println(url);
	        }
	     }
	    }
	   return result;
	  }
	
	private boolean searchStringMatches(String pageContents, String searchString, boolean caseSensitive){
	    String searchContents = pageContents; 
	    if (!caseSensitive) {//如果不区分大小写
	      searchContents = pageContents.toLowerCase();
	    }
	    Pattern p = Pattern.compile("[\\s]+");
	    String[] terms = p.split(searchString);
	    for (int i = 0; i < terms.length; i++) {
	      if (caseSensitive) {
	        if (searchContents.indexOf(terms[i]) == -1) {
	          return false;
	        }
	      } else {
	        if (searchContents.indexOf(terms[i].toLowerCase()) == -1) {
	          return false;
	        }
	      }     
	      }

	    return true;
	 }
	
	private boolean loginSina(String userName,String passwrod){
		HttpClient httpClient=new DefaultHttpClient();
		HttpGet httpGet=new HttpGet("http://www.weibo.com?username=cafffe@163.com&password=cafffe123");
		try {
			HttpResponse httpResponse=httpClient.execute(httpGet);
			HttpEntity httpEntity=httpResponse.getEntity();
			if(httpEntity!=null){
				InputStream inputStream=httpEntity.getContent();
				BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(inputStream));
				String readline;
				while((readline=bufferedReader.readLine())!=null){
					String newString=new String( readline.getBytes(), "utf-8");
					System.out.println(readline);
				}
				inputStream.close();
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	public static void main(String[] args) {
	     Scanner sc=new Scanner(System.in);
	     String a=sc.nextLine();
	     String b=sc.nextLine();
	     String c=sc.nextLine();
	     int max=Integer.parseInt(b);
	     Search crawler = new Search(a,max,c);
	     crawler.loginSina("", "");
	     Thread  search=new Thread(crawler);
	     System.out.println("Start searching...");
	     System.out.println("result:");
	     //search.start();//a new thread
	  }
}
