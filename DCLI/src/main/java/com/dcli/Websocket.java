package com.dcli;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.random.RandomGenerator;

@WebSocket(maxTextMessageSize = 100000000, maxBinaryMessageSize = 100000000)
public class Websocket
{
    private final Logger logger;
    private Thread heartbeatThread = null;
    private int lastSequenceNumber = -1;
    private boolean gotHeartbeatAck = false;
    private Session session = null;
    private String reconnectURL = "";
    private String sessionID = "";
    private boolean reconnect = false;
    private final String token;

    public Websocket(String token){
        logger = new Logger("Websocket");
        this.token = token;
    }

    public void connect(String url){

        if(url == null || url.isEmpty()) {url = "wss://gateway.discord.gg/?v=9&encoding=json";}

        HttpClient httpClient = getHttpClient();
        try
        {
            httpClient.start();
            WebSocketClient client = new WebSocketClient(httpClient);
            client.start();
            client.connect(this, URI.create(url));
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        try {
            Thread.sleep(2000000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendEvent(int opcode, JsonValue data) throws IOException {
        JsonObject event = new JsonObject();
        event.set("op", opcode);
        event.set("d", data);
        session.getRemote().sendString(event.toString());
    }

    public void reconnect(){
        logger.info("Reconnecting...\nWaiting 5 seconds.");
        session.close();
        heartbeatThread.interrupt();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        connect(null);
    }

    private void heartbeatSender(int heartbeatInterval){
        float jitter = RandomGenerator.getDefault().nextFloat();
        try {
            Thread.sleep((int) (heartbeatInterval * jitter));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            if(lastSequenceNumber != -1) {
                sendEvent(1, Json.value(lastSequenceNumber));
            }else {
                sendEvent(1, Json.NULL);
            }
        } catch (IOException e) {
            logger.info("Couldn't send heartbeat, ending heartbeat thread.");
            return;
        }
        while (true){
            try {
                Thread.sleep(heartbeatInterval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if(!gotHeartbeatAck){
                logger.info("Didn't get heartbeat ack.");
                return;
            }
            gotHeartbeatAck = false;
            try {
                if(lastSequenceNumber != -1) {
                    sendEvent(1, Json.value(lastSequenceNumber));
                }else{
                    sendEvent(1, Json.NULL);
                }
            } catch (IOException e) {
                logger.info("Couldn't send heartbeat, ending heartbeat thread.");
                return;
            }
        }
    }

    private static HttpClient getHttpClient() {
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");
        return new HttpClient(sslContextFactory);
    }


    @OnWebSocketConnect
    public void onConnect(Session session)
    {
        logger.info("Connected.");
        this.session = session;
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        logger.info("Connection closed.");
    }

    @OnWebSocketError
    public void onError(Throwable cause)
    {
        logger.info("Error occurred in socket.");
        cause.printStackTrace();
    }

    @OnWebSocketMessage
    public void onMessage(String msg)
    {
        logger.info("Message: " + msg);
        JsonObject event = Json.parse(msg).asObject();
        switch (event.getInt("op", -1)){
            case 0: {
                lastSequenceNumber = event.getInt("s", -1);
                switch (event.getString("t", "")){
                    case "READY": {
                        reconnectURL = event.get("d").asObject().getString("resume_gateway_url", "");
                        sessionID = event.get("d").asObject().getString("session_id", "");
                        break;
                    }
                    case "MESSAGE_CREATE": {
                        System.out.println("Message: " + event.get("d").asObject().getString("content", ""));
                    }
                }
                break;
            }
            case 1: {
                try {
                    gotHeartbeatAck = false;
                    if (lastSequenceNumber != -1) {
                        sendEvent(1, Json.value(lastSequenceNumber));
                    } else {
                        sendEvent(1, Json.NULL);
                    }
                }catch (IOException err){
                    reconnect();
                }
                break;
            }
            case 7: {
                break;
            }
            case 9: {
                break;
            }
            case 10: {
                logger.info("Got hello.");
                int heartbeatInterval = event.get("d").asObject().getInt("heartbeat_interval", -1);
                assert heartbeatInterval != -1;
                heartbeatThread = new Thread(() -> heartbeatSender(heartbeatInterval));
                heartbeatThread.start();

                JsonObject identify = Json.parse("""
                        {
                                      "token": "token",
                                      "capabilities": 16381,
                                      "properties": {
                                        "os": "Linux",
                                        "browser": "Firefox",
                                        "device": "",
                                        "system_locale": "en-US",
                                        "browser_user_agent": "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/119.0",
                                        "browser_version": "119.0",
                                        "os_version": "",
                                        "referrer": "https://discord.com/",
                                        "referring_domain": "discord.com",
                                        "referrer_current": "",
                                        "referring_domain_current": "",
                                        "release_channel": "stable",
                                        "client_build_number":247232,
                                        "client_event_source":null
                                      },
                                      "presence": {
                                        "status": "online",
                                        "since": 0,
                                        "activities": [],
                                        "afk": false
                                      },
                                      "compress": false,
                                      "client_state": {
                                        "guild_versions": {},
                                        "highest_last_message_id": "0",
                                        "read_state_version": 0,
                                        "user_guild_settings_version": -1,
                                        "user_settings_version": -1,
                                        "private_channels_version": "0",
                                        "api_code_version": 0
                                      }
                                    }""").asObject();
                identify.set("token", token);
                try {
                    sendEvent(2, identify);
                } catch (IOException e) {
                    reconnect();
                }
                break;
            }
            case 11: {
                if(gotHeartbeatAck){
                    logger.info("Got heartbeat ack when not expected.");
                    reconnect();
                }
                gotHeartbeatAck = true;
                break;
            }


        }
    }
}