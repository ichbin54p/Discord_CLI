
import com.dcli.Websocket;
import org.junit.jupiter.api.Test;

public class WebsocketTest {

    @Test
    public void WebsocketTest(){
        Websocket websocket = new Websocket("Insert token here");
        websocket.connect(null);
    }
}
