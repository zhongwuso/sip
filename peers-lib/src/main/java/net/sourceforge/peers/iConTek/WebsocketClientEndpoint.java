package net.sourceforge.peers.iConTek;


import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@WebSocket()
public class WebsocketClientEndpoint {

  private final CountDownLatch closeLatch;
  private Session session;
  private boolean recording = false;
  private List<Map<String, Object>> results;
  
  public Session getSession() {
    return session;
  }

  public boolean isRecording() {
    return recording;
  }

  public void setRecording(boolean recording) {
    this.recording = recording;
  }

  public void setSession(Session session) {
    this.session = session;
  }

  public WebsocketClientEndpoint() {
    this.closeLatch = new CountDownLatch(1);
  }

  public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
    return this.closeLatch.await(duration, unit);
  }

  @OnWebSocketClose
  public void onClose(int statusCode, String reason) {
    System.out.printf("Connection closed: " +  statusCode + " " + reason);
    this.session = null;
    this.closeLatch.countDown(); // trigger latch
  }

  @OnWebSocketConnect
  public void onConnect(Session session) {
    System.out.println("Got connect: " + session + "\n");
    this.session = session;
  }
  
  public boolean sendBytes(ByteBuffer buffer) throws IOException, RuntimeException{
    if ( session != null ){
      if ( recording ){
        session.getRemote().sendBytes(buffer);
        return true;
      }else{
        return false;
      }
    }else{
      throw new RuntimeException("INVALID_SESSION");
    }
  }
  
  @SuppressWarnings("unchecked")
  @OnWebSocketMessage
  public void onMessage(String msg) throws JsonParseException, JsonMappingException, IOException, InterruptedException {
    System.out.println("Got msg: " + msg + "\n");
    // recording
    //{"STATUS":"RECORDING","ACTION":"START","HANDLE":4}
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = mapper.readValue(msg, new TypeReference<Map<String, Object>>(){});
    String action = (String) map.get("ACTION");
    if ( "START".equals(action)  ){
      String status = (String) map.get("STATUS");
      if ( !"RECORDING".equals(status) ){
        System.out.println(status);
        recording = false;
        return;
      }else{
        recording = true;
        results = null;
      }
    }else if ( "STOP".equals(action) ){
      recording = false;
      results = (List<Map<String, Object>>) map.get("RESULT");
    }
  }

  /**
   * Send a message.
   *
   * @param message
   * @throws IOException 
   */
  public void sendMessage(String message) throws IOException, RuntimeException {
    if ( session == null ){
      throw new RuntimeException("SESSION_IS_NULL");
    }
    session.getRemote().sendString(message);
  }
  public void sendMessage(Map<String, Object> mapObj) throws IOException, RuntimeException {
    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(mapObj);
    this.sendMessage(json);
  }
  public void close(){
    if ( session != null )
      try {
        session.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
  }

  public List<Map<String, Object>> getResults() {
    return results;
  }
}
