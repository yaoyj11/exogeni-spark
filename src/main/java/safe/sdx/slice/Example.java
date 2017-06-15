/**
 * 
 */
package safe.sdx;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import org.renci.ahab.libndl.LIBNDL;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.SliceGraph;
import org.renci.ahab.libndl.extras.PriorityNetwork;
import org.renci.ahab.libndl.resources.common.ModelResource;
import org.renci.ahab.libndl.resources.request.BroadcastNetwork;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Interface;
import org.renci.ahab.libndl.resources.request.InterfaceNode2Net;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libndl.resources.request.Node;
import org.renci.ahab.libndl.resources.request.StitchPort;
import org.renci.ahab.libndl.resources.request.StorageNode;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.ITransportProxyFactory;
import org.renci.ahab.libtransport.JKSTransportContext;
import org.renci.ahab.libtransport.PEMTransportContext;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.TransportContext;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;
import org.renci.ahab.ndllib.transport.OrcaSMXMLRPCProxy;

import java.rmi.RMISecurityManager;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
/**

 * @author geni-orca
 *
 */

public class Example {
  public Example()throws RemoteException{}
	private static final String RequestResource = null;
  private static String controllerUrl;
	private static String sliceName;
	private static String pemLocation;
	private static String keyLocation;
  private static ISliceTransportAPIv1 sliceProxy;
  private static SliceAccessContext<SSHAccessToken> sctx;
  private static String privkey="~/.ssh/id_rsa";
	
	public static void main(String [] args){
		//Example usage:   ./target/appassembler/bin/SafeSdxExample  ~/.ssl/geni-pruth1.pem ~/.ssl/geni-pruth1.pem "https://geni.renci.org:11443/orca/xmlrpc" name OPTION workernum
		System.out.println("ndllib TestDriver: START");
		pemLocation = args[0];
		keyLocation = args[1];
		controllerUrl = args[2]; //"https://geni.renci.org:11443/orca/xmlrpc";
		sliceName = args[3]; //"pruth.sdx.1";
    // create secure context

		sliceProxy = Example.getSliceProxy(pemLocation,keyLocation, controllerUrl);		

		//SSH context
		sctx = new SliceAccessContext<>();
		try {
			SSHAccessTokenFileFactory fac;
			fac = new SSHAccessTokenFileFactory("~/.ssh/id_rsa.pub", false);
			SSHAccessToken t = fac.getPopulatedToken();			
			sctx.addToken("root", "root", t);
			sctx.addToken("root", t);
		} catch (UtilTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    if(args[4].equals("spark")){
      int workernum=Integer.valueOf(args[5]);
      privkey=args[6];
      try{
        System.setProperty("java.security.policy","~/project/exo-geni/ahabserver/allow.policy");
//        Slice spark=createSparkSlice(sliceName,workernum);
        Slice spark=Slice.loadManifestFile(sliceProxy, sliceName);
//        copyFile2Slice(spark,args[7],"~/spark.tar.gz",privkey,"node.*");
        runCmdSlice(spark, "tar -xvf spark.tar.gz; cd ~/spark;tar -xvf spark-2.1.1.tar.gz;/bin/bash builddocker.sh;/bin/bash rundocker.sh",privkey,false,"node.*");
        //runCmdSlice(spark, "tar -xvf spark.tar.gz; cd ~/spark;wget https://d3kbcqa49mib13.cloudfront.net/spark-2.1.1.tgz;tar -xvf spark-2.1.1.tgz;/bin/bash builddocker.sh;/bin/bash rundocker.sh",privkey,false,"node.*");
        configureSpark(spark,workernum);
      }catch (Exception e){
        e.printStackTrace();
      }
    }
    else if (args[4].equals("delete")){
      Slice s2 = null;
      try{
        s2=Slice.loadManifestFile(sliceProxy, args[3]);
        s2.delete();
      }catch (Exception e){
        e.printStackTrace();
      }
    }
		System.out.println("XXXXXXXXXX Done XXXXXXXXXXXXXX");
	}

  private static void configureSpark(Slice s, int num){
    ComputeNode master=(ComputeNode) s.getResourceByName("node0");
    String masterip=master.getManagementIP();
    //start master
    String res=Exec.sshExec("root",masterip,"cd ~/spark;docker exec  sparkserver /bin/bash -c \"export SPARK_HOME=/root/spark/spark-2.1.1 && /root/master.sh\"&",privkey);
    for(int i=1;i<num;i++){
      ComputeNode worker=(ComputeNode) s.getResourceByName("node"+String.valueOf(i));
      String mip=worker.getManagementIP();
      //start master
      res=Exec.sshExec("root",mip,"cd ~/spark;docker exec  sparkserver /bin/bash -c \"export SPARK_HOME=/root/spark/spark-2.1.1 && /root/worker.sh spark://"+masterip+":7077\"&",privkey);
    }
  }

  public static Slice createSparkSlice(String sliceName,int num){
		System.out.println("ndllib TestDriver: START");
		//Main Example Code
		
		Slice s = Slice.create(sliceProxy, sctx, sliceName);
		String nodeImageShortName="Ubuntu 14.04 Docker";
		String nodeImageURL ="http://geni-orca.renci.org/owl/5e2190c1-09f1-4c48-8ed6-dacae6b6b435#Ubuntu+14.0.4+Docker";//http://geni-images.renci.org/images/standard/ubuntu/ub1304-ovs-opendaylight-v1.0.0.xml
		String nodeImageHash ="b4ef61dbd993c72c5ac10b84650b33301bbf6829";
		String nodeNodeType="XO Extra Large";
		String nodeDomain=domains.get(0);

		String controllerDomain=domains.get(0);
	    		
		boolean sliceActive = false;
		
    PriorityNetwork net=PriorityNetwork.create(s,sliceName,controllerDomain,1000000000l);
    for(int i=0;i<1;i++){
      String domain="";
      domain=domains.get(0);
      net.bind("site"+String.valueOf(i),domain);
      for(int j=0;j<num;j++){
        ComputeNode node = s.addComputeNode("node"+String.valueOf(j));
        node.setImage(nodeImageURL,nodeImageHash,nodeImageShortName);
        node.setNodeType(nodeNodeType);
        node.setDomain(domain);
        net.addNode(node,"site"+String.valueOf(i),"192.168.1."+String.valueOf(j+1),"255.255.255.0");
      }
    }
    s.commit();
    waitTillActive(s);
		System.out.println("Done");
    return s;
  }


  private static void copyDir2Slice(Slice s, String ldir, String rdir,String tarfile){
    Exec.exec("tar -zcvf "+tarfile+" "+ldir);
    copyFile2Slice(s,tarfile,rdir+"/"+tarfile,privkey);
		for(ComputeNode c : s.getComputeNodes()){
      String mip=c.getManagementIP();
      Exec.sshExec ("root",mip,"cd "+rdir+";tar -xvf "+tarfile,privkey);
		}
    Exec.exec("rm "+tarfile);
  }

  private static void copyFile2Slice(Slice s, String lfile, String rfile,String privkey){
		for(ComputeNode c : s.getComputeNodes()){
        String mip=c.getManagementIP();
        try{
          ScpTo.Scp(lfile,"root",mip,rfile,privkey);
          //Exec.sshExec("yaoyj11","152.3.136.145","/bin/bash "+rfile,privkey);
        }catch (Exception e){
          System.out.println("exception when copying file");
        }
		}
  }

  private static void copyFile2Slice(Slice s, String lfile, String rfile,String privkey,String p){
    Pattern pattern = Pattern.compile(p);
		for(ComputeNode c : s.getComputeNodes()){
      String name=c.getName();
      Matcher matcher = pattern.matcher(name);
      if(matcher.matches()){
        String mip=c.getManagementIP();
        try{
          ScpTo.Scp(lfile,"root",mip,rfile,privkey);
          //Exec.sshExec("yaoyj11","152.3.136.145","/bin/bash "+rfile,privkey);
        }catch (Exception e){
          System.out.println("exception when copying file");
        }
      }
		}
  }

  private static void runCmdSlice(Slice s, String cmd, String privkey,boolean repeat){
		for(ComputeNode c : s.getComputeNodes()){
        String mip=c.getManagementIP();
        try{
          System.out.println(mip+" run commands:"+cmd);
          //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
          String res=Exec.sshExec("root",mip,cmd,privkey);
          while(res.startsWith("error")&&repeat){
            sleep(5);
            res=Exec.sshExec("root",mip,cmd,privkey);
          }

        }catch (Exception e){
          System.out.println("exception when copying config file");
        }
		}
  }

  private static void runCmdSlice(Slice s, String cmd, String privkey,boolean repeat,String p){
    Pattern pattern = Pattern.compile(p);
		for(ComputeNode c : s.getComputeNodes()){
      String name=c.getName();
      Matcher matcher = pattern.matcher(name);
      if(matcher.matches()){
        String mip=c.getManagementIP();
        try{
          System.out.println(mip+" run commands:"+cmd);
          //ScpTo.Scp(lfile,"root",mip,rfile,privkey);
          String res=Exec.sshExec("root",mip,cmd,privkey);
          while(res.startsWith("error")&&repeat){
            sleep(5);
            res=Exec.sshExec("root",mip,cmd,privkey);
          }

        }catch (Exception e){
          System.out.println("exception when copying config file");
        }
      }
		}
  }


  public static void getNetworkInfo(Slice s){
    //getLinks
    for(Network n :s.getLinks()){
      System.out.println(n.getLabel());
    }
    //getInterfaces
    for(Interface i: s.getInterfaces()){
      InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
      System.out.println("MacAddr: "+inode2net.getMacAddress());

      System.out.println("GUID: "+i.getGUID());
    }
    for(ComputeNode node: s.getComputeNodes()){
      System.out.println(node.getName()+node.getManagementIP());
      for(Interface i: node.getInterfaces()){
        InterfaceNode2Net inode2net=(InterfaceNode2Net)i;
        System.out.println("MacAddr: "+inode2net.getMacAddress());
        System.out.println("GUID: "+i.getGUID());
      }
    }
  }


	public static Slice getSlice(ISliceTransportAPIv1 sliceProxy, String sliceName){
		Slice s = null;
		try {
			s = Slice.loadManifestFile(sliceProxy, sliceName);
		} catch (ContextTransportException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		return s;
	}

	public static void sleep(int sec){
		try{

			Thread.sleep(sec*1000);                 //1000 milliseconds is one second.
		} catch(InterruptedException ex) {  
			Thread.currentThread().interrupt();
		}
	}


	public static ISliceTransportAPIv1 getSliceProxy(String pem, String key, String controllerUrl){

		ISliceTransportAPIv1 sliceProxy = null;
		try{
			//ExoGENI controller context
			ITransportProxyFactory ifac = new XMLRPCProxyFactory();
			System.out.println("Opening certificate " + pem + " and key " + key);
			TransportContext ctx = new PEMTransportContext("", pem, key);
			sliceProxy = ifac.getSliceProxy(ctx, new URL(controllerUrl));

		} catch  (Exception e){
			e.printStackTrace();
			System.err.println("Proxy factory test failed");
			assert(false);
		}

		return sliceProxy;
	}



	public static final ArrayList<String> domains;
	static {
		ArrayList<String> l = new ArrayList<String>();

		for (int i = 0; i < 100; i++){
//			l.add("PSC (Pittsburgh, TX, USA) XO Rack");
//			l.add("UAF (Fairbanks, AK, USA) XO Rack");
		
//			l.add("UH (Houston, TX USA) XO Rack");
//			l.add("TAMU (College Station, TX, USA) XO Rack");
			l.add("RENCI (Chapel Hill, NC USA) XO Rack");
//			
//			l.add("SL (Chicago, IL USA) XO Rack");
//			
//			
//			l.add("OSF (Oakland, CA USA) XO Rack");
//			
//		l.add("UMass (UMass Amherst, MA, USA) XO Rack");
			//l.add("WVN (UCS-B series rack in Morgantown, WV, USA)");
	//		l.add("UAF (Fairbanks, AK, USA) XO Rack");
//   l.add("UNF (Jacksonville, FL) XO Rack");
//		l.add("UFL (Gainesville, FL USA) XO Rack");
//			l.add("WSU (Detroit, MI, USA) XO Rack");
//			l.add("BBN/GPO (Boston, MA USA) XO Rack");
//			l.add("UvA (Amsterdam, The Netherlands) XO Rack");

		}
		domains = l;
	}

  private static void  AppendFile(String filepath, String data){
    BufferedWriter bw=null;
    FileWriter fw=null;
    try{
      File file=new File(filepath);
      if (!file.exists()){
        file.createNewFile();
      }
      fw=new FileWriter(file.getAbsoluteFile(),true);
      bw = new BufferedWriter(fw);
      bw.write(data);
      System.out.print("NewNeighbor: "+data);
    }catch (IOException e){
      e.printStackTrace();
    }finally{
      try{
        if(bw!=null)
          bw.close();
        if (fw!=null)
          fw.close();
      }catch (IOException ex){
        ex.printStackTrace();
      }
    }
  }

  public static void waitTillActive(Slice s){
		boolean sliceActive = false;
		while (true){		
			s.refresh();
			sliceActive = true;
			System.out.println("");
			System.out.println("Slice: " + s.getAllResources());
			for(ComputeNode c : s.getComputeNodes()){
				System.out.println("Resource: " + c.getName() + ", state: "  + c.getState());
				if(c.getState() != "Active") sliceActive = false;
			}
			for(Network l: s.getBroadcastLinks()){
				System.out.println("Resource: " + l.getName() + ", state: "  + l.getState());
				if(l.getState() != "Active") sliceActive = false;
			}
		 	
		 	if(sliceActive) break;
		 	sleep(10);
		}
		System.out.println("Done");
		for(ComputeNode n : s.getComputeNodes()){
			String out="ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP()+"\n";
      System.out.print(out);
      AppendFile("nodes.txt",out);
		}
  }
}
