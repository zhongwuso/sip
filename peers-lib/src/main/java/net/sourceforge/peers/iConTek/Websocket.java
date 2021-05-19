package net.sourceforge.peers.iConTek;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

public class Websocket {
  public static void main(String[] args) throws URISyntaxException {
    String destUri = "ws://114.142.144.42:38128/api/audio/websocket";
    File pcmFile = new File("./sample/2017_2_13_17_46_27_6681.raw");
    
    URI uri = new URI(destUri);
    WebSocketClient client = new WebSocketClient();
    WebsocketClientEndpoint socket = new WebsocketClientEndpoint();
    try {
      client.start();
      ClientUpgradeRequest request = new ClientUpgradeRequest();
      Future<Session> future = client.connect(socket, uri, request);
      System.out.printf("Connecting to : %s%n", uri);
      future.get();
      System.out.println("Connected");

      // Start
      // {"ACTION":"START","sampleRate":44100,"responseId":"<UNIQUE_ID>","questionId":"<QUESTION_ID>"}
      Map<String, Object> command = new HashMap<>();
      command.put("ACTION", "START");
      command.put("sampleRate", 8000);
      command.put("responseId", UUID.randomUUID());
      command.put("questionId", "ROOT");
      socket.sendMessage(command);
      
      // wait for recording started
      while( !socket.isRecording() ){
        Thread.sleep(100);
      }
      
      // send file
      sendFile(pcmFile, socket);
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      try {
        client.stop();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  public static void sendFile(File pcmFile, WebsocketClientEndpoint socket) throws Exception{
    // send pcm data
    FileInputStream fs = new FileInputStream(pcmFile);
    byte[] buffer = new byte[8192];
    int size;
    while ( (size = fs.read(buffer)) > 0){
      ByteBuffer data = ByteBuffer.wrap(buffer, 0, size);
      System.out.println("Sending bytes: " + size);
      socket.sendBytes(data);
    }
    
    fs.close();
    
    //send slient block
    fs = new FileInputStream(pcmFile);
    fs.skip(pcmFile.length()-8192);
    size = fs.read(buffer);
    fs.close();
    for ( int i = 0 ; i < 100 ; i++ ){
      Thread.sleep(500);
      ByteBuffer data = ByteBuffer.wrap(buffer, 0, size);
      System.out.println("Sending bytes: " + size);
      boolean status = socket.sendBytes(data);
      
      if ( status ==  false ){
        System.out.println("Result:");
        List<Map<String, Object>> results = socket.getResults();
        for (Map<String, Object> result: results){
          System.out.println(result);
        }          
        return;
      }
    }
  }
}
