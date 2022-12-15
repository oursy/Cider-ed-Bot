package com.cider.bot.config.w3j.websocket;

import java.util.Map;
import org.web3j.protocol.websocket.WebSocketSubscription;

public interface WebSocketRequestProcess {

  Map<Long, WebSocketRequest<?>> requestForId();

  Map<Long, WebSocketSubscription<?>> subscriptionRequestForId();

  Map<String, WebSocketSubscription<?>> subscriptionForId();
}
