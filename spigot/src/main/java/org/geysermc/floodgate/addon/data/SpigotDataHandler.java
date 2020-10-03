/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Floodgate
 */

package org.geysermc.floodgate.addon.data;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.geysermc.floodgate.util.ReflectionUtils.getCastedValue;
import static org.geysermc.floodgate.util.ReflectionUtils.getFieldOfType;
import static org.geysermc.floodgate.util.ReflectionUtils.getMethod;
import static org.geysermc.floodgate.util.ReflectionUtils.getPrefixedClass;
import static org.geysermc.floodgate.util.ReflectionUtils.makeAccessible;
import static org.geysermc.floodgate.util.ReflectionUtils.setValue;

import com.google.common.base.Joiner;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.geysermc.floodgate.HandshakeHandler;
import org.geysermc.floodgate.HandshakeHandler.HandshakeResult;
import org.geysermc.floodgate.HandshakeHandler.ResultType;
import org.geysermc.floodgate.api.logger.FloodgateLogger;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.config.FloodgateConfig;
import org.geysermc.floodgate.util.BedrockData;
import org.geysermc.floodgate.util.ReflectionUtils;

@RequiredArgsConstructor
public final class SpigotDataHandler extends SimpleChannelInboundHandler<Object> {
  private static final Field SOCKET_ADDRESS;
  private static final Class<?> HANDSHAKE_PACKET;
  private static final Field HANDSHAKE_HOST;

  private static final Class<?> GAME_PROFILE;
  private static final Constructor<?> GAME_PROFILE_CONSTRUCTOR;
  private static final Field LOGIN_PROFILE;

  private static final Class<?> LOGIN_START_PACKET;
  private static final Class<?> LOGIN_LISTENER;
  private static final Method INIT_UUID;

  private static final Class<?> LOGIN_HANDLER;
  private static final Constructor<?> LOGIN_HANDLER_CONSTRUCTOR;
  private static final Method FIRE_LOGIN_EVENTS;

  private static final Field PACKET_LISTENER;
  private static final Field PROTOCOL_STATE;
  private static final Object READY_TO_ACCEPT_PROTOCOL_STATE;

  static {
    Class<?> networkManager = getPrefixedClass("NetworkManager");
    checkNotNull(networkManager, "NetworkManager class cannot be null");

    SOCKET_ADDRESS = getFieldOfType(networkManager, SocketAddress.class, false);
    checkNotNull(SOCKET_ADDRESS, "SocketAddress field cannot be null");

    HANDSHAKE_PACKET = getPrefixedClass("PacketHandshakingInSetProtocol");
    checkNotNull(HANDSHAKE_PACKET, "PacketHandshakingInSetProtocol cannot be null");

    HANDSHAKE_HOST = getFieldOfType(HANDSHAKE_PACKET, String.class);
    checkNotNull(HANDSHAKE_HOST, "Host field from handshake packet cannot be null");

    LOGIN_START_PACKET = getPrefixedClass("PacketLoginInStart");
    checkNotNull(LOGIN_START_PACKET, "PacketLoginInStart cannot be null");

    GAME_PROFILE = ReflectionUtils.getClass("com.mojang.authlib.GameProfile");
    checkNotNull(GAME_PROFILE, "GameProfile class cannot be null");

    Constructor<?> gameProfileConstructor = null;
    try {
      gameProfileConstructor = GAME_PROFILE.getConstructor(UUID.class, String.class);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    GAME_PROFILE_CONSTRUCTOR = gameProfileConstructor;
    checkNotNull(GAME_PROFILE_CONSTRUCTOR, "GameProfileConstructor cannot be null");

    LOGIN_LISTENER = getPrefixedClass("LoginListener");
    checkNotNull(LOGIN_LISTENER, "LoginListener cannot be null");

    LOGIN_PROFILE = getFieldOfType(LOGIN_LISTENER, GAME_PROFILE);
    checkNotNull(LOGIN_PROFILE, "Profile from LoginListener cannot be null");

    INIT_UUID = getMethod(LOGIN_LISTENER, "initUUID");
    checkNotNull(INIT_UUID, "initUUID from LoginListener cannot be null");

    Field protocolStateField = null;
    for (Field field : LOGIN_LISTENER.getDeclaredFields()) {
      if (field.getType().isEnum()) {
        protocolStateField = field;
      }
    }
    PROTOCOL_STATE = protocolStateField;
    checkNotNull(PROTOCOL_STATE, "Protocol state field from LoginListener cannot be null");

    Enum<?>[] protocolStates = (Enum<?>[]) PROTOCOL_STATE.getType().getEnumConstants();
    Object readyToAcceptState = null;
    for (Enum<?> protocolState : protocolStates) {
      if (protocolState.name().equals("READY_TO_ACCEPT")) {
        readyToAcceptState = protocolState;
      }
    }
    READY_TO_ACCEPT_PROTOCOL_STATE = readyToAcceptState;
    checkNotNull(READY_TO_ACCEPT_PROTOCOL_STATE, "A required Protocol state cannot be null");

    Class<?> packetListenerClass = getPrefixedClass("PacketListener");
    PACKET_LISTENER = getFieldOfType(networkManager, packetListenerClass);
    checkNotNull(PACKET_LISTENER, "PacketListener cannot be null");

    LOGIN_HANDLER = getPrefixedClass("LoginListener$LoginHandler");
    checkNotNull(LOGIN_HANDLER, "LoginHandler cannot be null");

    Constructor<?> loginHandlerConstructor = null;
    try {
      loginHandlerConstructor =
          makeAccessible(LOGIN_HANDLER.getDeclaredConstructor(LOGIN_LISTENER));
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    LOGIN_HANDLER_CONSTRUCTOR = loginHandlerConstructor;
    checkNotNull(LOGIN_HANDLER_CONSTRUCTOR, "LoginHandler constructor cannot be null");

    FIRE_LOGIN_EVENTS = getMethod(LOGIN_HANDLER, "fireEvents");
    checkNotNull(FIRE_LOGIN_EVENTS, "fireEvents from LoginHandler cannot be null");
  }

  /* per player stuff */
  private final FloodgateConfig config;
  private final HandshakeHandler handshakeHandler;
  private final AttributeKey<FloodgatePlayer> playerAttribute;
  private final FloodgateLogger logger;
  private Object networkManager;
  private FloodgatePlayer floodgatePlayer;
  private boolean bungee;
  private boolean done;

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object packet) throws Exception {
    // we're done but we're not yet removed from the connection
    if (done) {
      ctx.fireChannelRead(packet);
      return;
    }

    boolean isHandshake = HANDSHAKE_PACKET.isInstance(packet);
    boolean isLogin = LOGIN_START_PACKET.isInstance(packet);

    try {
      if (isHandshake) {
        networkManager = ctx.channel().pipeline().get("packet_handler");

        String handshakeValue = getCastedValue(packet, HANDSHAKE_HOST);
        HandshakeResult result = handshakeHandler.handle(handshakeValue);

        if (result.getResultType() == ResultType.INVALID_DATA_LENGTH) {
            int dataLength = result.getBedrockData().getDataLength();
            logger.info(
                config.getMessages().getInvalidArgumentsLength(),
                BedrockData.EXPECTED_LENGTH,
                dataLength);
            ctx.close();
            return;
        }

        // we only want to handle the Floodgate players
        if (result.getResultType() != ResultType.SUCCESS) {
          return;
        }

        ctx.channel().attr(playerAttribute).set(result.getFloodgatePlayer());

        floodgatePlayer = result.getFloodgatePlayer();
        BedrockData bedrockData = result.getBedrockData();
        String[] data = result.getHandshakeData();

        if (bungee = result.isBungeeData()) {
          String skin = data.length == 5 ? '\0' + data[4] : "";

          String newData =
              Joiner.on("\0")
                  .join(data[0], bedrockData.getIp(), floodgatePlayer.getCorrectUniqueId() + skin);

          setValue(packet, HANDSHAKE_HOST, newData);
        } else {
          // Use a spoofedUUID for initUUID (just like Bungeecord)
          setValue(networkManager, "spoofedUUID", floodgatePlayer.getCorrectUniqueId());

          // Use the player his IP for stuff instead of Geyser his IP
          int port = ((InetSocketAddress) ctx.channel().remoteAddress()).getPort();
          SocketAddress newAddress = new InetSocketAddress(bedrockData.getIp(), port);

          setValue(networkManager, SOCKET_ADDRESS, newAddress);
        }
      } else if (isLogin) {
        if (!bungee) {
          // we have to fake the offline player (login) cycle
          Object loginListener = PACKET_LISTENER.get(networkManager);

          // check if the server is actually in the Login state
          if (!LOGIN_LISTENER.isInstance(loginListener)) {
            // player is not in the login state, abort
            return;
          }

          // set the player his GameProfile, we can't change the username without this
          Object gameProfile =
              GAME_PROFILE_CONSTRUCTOR.newInstance(
                  floodgatePlayer.getCorrectUniqueId(), floodgatePlayer.getCorrectUsername());
          setValue(loginListener, LOGIN_PROFILE, gameProfile);

          // just like on Spigot:

          // LoginListener#initUUID
          // new LoginHandler().fireEvents();
          // LoginListener#protocolState = READY_TO_ACCEPT

          // and the tick of LoginListener will do the rest

          INIT_UUID.invoke(loginListener);
          FIRE_LOGIN_EVENTS.invoke(LOGIN_HANDLER_CONSTRUCTOR.newInstance(loginListener));
          setValue(loginListener, PROTOCOL_STATE, READY_TO_ACCEPT_PROTOCOL_STATE);
        }
      }
    } finally {
      // don't let the packet through if the packet is the login packet
      // because we want to skip the login cycle
      if (!isLogin) {
        ctx.fireChannelRead(packet);
      }

      if (isHandshake && bungee || isLogin && !bungee || floodgatePlayer == null) {
        // we're done, we'll just wait for the loginSuccessCall
        done = true;
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    cause.printStackTrace();
  }
}
