/*
    This file is part of Peers, a java SIP softphone.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    Copyright 2007, 2008, 2009, 2010 Yohann Martineau 
*/

package net.sourceforge.peers.media;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import net.sourceforge.peers.Logger;
import net.sourceforge.peers.iConTek.WebsocketClientEndpoint;
import net.sourceforge.peers.rtp.RFC3551;
import net.sourceforge.peers.rtp.RtpListener;
import net.sourceforge.peers.rtp.RtpPacket;
import net.sourceforge.peers.rtp.RtpSession;
import net.sourceforge.peers.sdp.Codec;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class IncomingRtpReader implements RtpListener {

    private RtpSession rtpSession;
    private AbstractSoundManager soundManager;
    private Decoder decoder;

    public IncomingRtpReader(RtpSession rtpSession,
            AbstractSoundManager soundManager, Codec codec, Logger logger)
            throws IOException {
        logger.debug("playback codec:" + codec.toString().trim());
        this.rtpSession = rtpSession;
        this.soundManager = soundManager;
        switch (codec.getPayloadType()) {
        case RFC3551.PAYLOAD_TYPE_PCMU:
            decoder = new PcmuDecoder();
            break;
        case RFC3551.PAYLOAD_TYPE_PCMA:
            decoder = new PcmaDecoder();
            break;
        default:
            throw new RuntimeException("unsupported payload type");
        }
        rtpSession.addRtpListener(this);
    }
    
    public void start() {
        rtpSession.start();
    }

    @Override
    public void receivedRtpPacket(RtpPacket rtpPacket) {
        byte[] rawBuf = decoder.process(rtpPacket.getData());
        System.out.println("声音输出流：========================="+rawBuf);
        if (soundManager != null) {
            //调用icontek接口
            ByteBuffer data = ByteBuffer.wrap(rawBuf, 0,  rawBuf.length);
            try {
                socket(data);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }



            soundManager.writeData(rawBuf, 0, rawBuf.length);
        }
    }
    //调用iConTek语音流接口
    public void socket(ByteBuffer data) throws URISyntaxException {
        String destUri = "ws://218.249.92.76:25003/api/audio/websocket";
//        File pcmFile = new File("./sample/2017_2_13_17_46_27_6681.raw");

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
//            sendFile(pcmFile, socket);
            socket.sendBytes(data);
            for ( int i = 0 ; i < 100 ; i++ ){
                Thread.sleep(500);
                boolean status = socket.sendBytes(data);

                if ( status ==  false ){
                    System.out.println("Result:");
                    List<Map<String, Object>> results = socket.getResults();
                    for (Map<String, Object> result: results){
                        System.out.println(result);
                    }
                }
            }
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
}
