
//
// MahjongServer.java, 麻將GameServer
// 2010/11/18 Waync Cheng
//

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;

//
// Active User Record.
//

class AUR {

  int id;                               // Index in the AUR pool.

  //
  // Socket.
  //

  Socket s;

  InputStream is;
  OutputStream os;

  long keepAlive;

  //
  // Account and session info.
  //

  String account;                       // User account.
  String token;                         // User token.

  Date upTime;                          // Login time.

  int iAgr;                             // AGR index, -1 for invalid.
} // AUR

//
// Active Game Record.
//

class AGR {

  //
  // State.
  //

  int id;                               // Index in the AGR pool.

  boolean running;

  MahjongGame mj;                       // The game.

  boolean newPlay;                      // Start new game in the next process?
  int totalPlay;                        // How many games have played.
  int round;                            // Which round?

  long timeout;

  //
  // Total 4 players, the first place is the game creater of this game. Before
  // game starts, if the creater leaves then this game ends.
  //

  int iAur[];
} // AGR

//
// Mahjong Game server.
//

public class MahjongServer {

  //
  // Constants.
  //

  static final String SERVER_NAME = "Mahjong Game Server";
  static final String LOG_PATH = "./log/";

  static final int SERVER_PORT = 18800;
  static final int WEBSOCKET_SERVER_PORT = 18802;
  static final int MAX_SOCK_QUEUE = 256;
  static final int MAX_AUR = 128;     // Max client connections.
  static final int MAX_AGR = MAX_AUR; // Max game.
  static final int MAX_PLAYER_OF_GAME = 4;

  static final int TIMEOUT_KEEP_ALIVE = 8;

  static final int TIMEOUT_AI_PLAY_GAME = 1;
  static final int TIMEOUT_PLAY_GAME = 1;

  //
  // Members.
  //

  Socket sq[];                          // Queue for pending new connections.

  int nAur;
  AUR aur[];                            // AUR(Active User Record) pool.

  int nAgr;
  AGR agr[];                            // AGR(Active Game Record) pool.

  Date upTime;                          // Startup time.
  FileWriter log;                       // Log file.

  //
  // Entry point.
  //

  public static void main(String args[]) throws Exception {

    //
    // Startup the server and create first thread.
    //

    new MahjongServer();
  }

  //
  // Constructor.
  //

  public MahjongServer() throws Exception {

    //
    // Create log file.
    //

    Calendar cal = Calendar.getInstance();

    File dirLog = new File(LOG_PATH);
    if (!dirLog.exists()) {
      dirLog.mkdir();
    }

    log = new FileWriter(
                LOG_PATH + cal.get(Calendar.YEAR) +
                "-" + (1 + cal.get(Calendar.MONTH)) +
                "-" + cal.get(Calendar.DAY_OF_MONTH) +
                "-MjSvr.log",
                true);

    //
    // Initialize pools and tartup server.
    //

    upTime = new Date();

    sq = new Socket[MAX_SOCK_QUEUE];
    for (int i = 0; i < sq.length; i++) {
      sq[i] = null;
    }

    nAur = 0;
    aur = new AUR[MAX_AUR];             // Active User Record pool.
    for (int i = 0; i < aur.length; i++) {
      aur[i] = null;
    }

    nAgr = 0;
    agr = new AGR[MAX_AGR];             // Active Game Record pool.
    for (int i = 0; i < agr.length; i++) {
      agr[i] = null;
    }

    PrintLog(SERVER_NAME + " Startup!!\n");
    PrintLog("Ready for requests, listen port: " + SERVER_PORT + "," + WEBSOCKET_SERVER_PORT + "...\n");

    //
    // Startup a thread for waiting new connection. A new connection will put
    // in the pending queue and then handle by the main loop.
    //

    startServerSocketThread(SERVER_PORT);
    startServerSocketThread(WEBSOCKET_SERVER_PORT);

    //
    // Main server loop.
    //

    while (true) {

      Thread.sleep(1);

      //
      // Is there any new connection?
      //

      synchronized (sq) {

        //
        // Process all pending new connection at the same time. Attempt
        // to allocate new AUR for each new connection. If the allocation
        // is failed then destroy the connection.
        //

        for (int i = 0; i < sq.length; i++) {
          if (null != sq[i]) {
            AUR u = newAur(sq[i]);
            sq[i] = null;               // Set handed.
          }
        }
      }

      //
      // Process all AGRs.
      //

      for (int i = 0; i < agr.length; i++) {
        if (null != agr[i]) {
          triggerAgr(agr[i]);
        }
      }

      //
      // Process all AURs.
      //

      for (int i = 0; i < aur.length; i++) {
        if (null != aur[i]) {
          triggerAur(aur[i]);
        }
      }
    }
  }

  //
  // Add new connection to pending queue.
  //

  void addNewConnection(Socket s) throws Exception {

    //
    // Put this new connection into the pending queue, main server loop
    // will handle later.
    //

    synchronized (sq) {

      //
      // Try to add new connection socket to the queue to an empty slot.
      //

      for (int i = 0; i < sq.length; i++) {
        if (null == sq[i]) {
          sq[i] = s;
          return;
        }
      }

      //
      // Pending socket pool is full.
      //

      PrintLog("Pending connection queue is full, disconnect!!!\n");

      AUR u = new AUR();
      u.s = s;
      u.os = s.getOutputStream();

      usend(u, MahjongProtocol.getServerIsBusyCmd());

      u.os.close();
      s.close();
    }
  }

  //
  // TCPIP worker thread entry. This thread is only used for waiting new connection and save
  // the new connection in the pending queue.
  //

  void tcpipWorker(int port) {
    try {
      ServerSocket server = new ServerSocket(port);
      while (true) {

        //
        // Wait a new request.
        //

        Socket s = server.accept();

        PrintLog(
          "-->New connection from '" +
          s.getRemoteSocketAddress().toString() +
          "'!!!\n");

        //
        // Put this new connection into the pending queue, main server loop
        // will handle later.
        //

        addNewConnection(s);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  //
  // HTML5 WebSocket worker thread.
  //

  String getWebsockAcceptKey(String req) {
    if (0 > req.indexOf("Upgrade: websocket") ||
        0 > req.indexOf("Connection:") ||
        0 > req.indexOf("Sec-WebSocket-Version: 13") ||
        0 > req.indexOf("Sec-WebSocket-Protocol: mj")) {
      return null;
    }

    //
    // Extract Sec-WebSocket-Key.
    //

    int keyIndex = req.indexOf("Sec-WebSocket-Key");
    if (0 > keyIndex) {
      return null;
    }

    String key = req.substring(keyIndex + "Sec-WebSocket-Key:".length(), req.indexOf("\r\n", keyIndex)).trim();

    //
    // Create accept key by append a SPEC defined GUID to the key and SHA1 it
    // than base64 it.
    //

    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }

    byte[] by = (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes();

    return javax.xml.bind.DatatypeConverter.printBase64Binary(md.digest(by));
  }

  void websockWorker(int port) {
    try {
      ServerSocket server = new ServerSocket(port);
      while (true) {

        //
        // Wait a new connection.
        //

        Socket s = server.accept();

        PrintLog(
          "-->New ws connection from '" +
          s.getRemoteSocketAddress().toString() +
          "'!!!\n");

        //
        // Handle websocket connection handshake.
        //

        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));

        String request = "";
        while (true) {
          String line = reader.readLine();
          if (null == line) {
            break;
          }
          request += line + "\r\n";
          if (0 == line.length()) {
            break;
          }
        }

        String keyAccept = getWebsockAcceptKey(request);
        if (null == keyAccept) {
          s.close();
          continue;
        }

        //
        //  Send accept key to complete handshake.
        //

        int connIndex = request.indexOf("Connection:");
        String connStr = request.substring(connIndex, request.indexOf("\r\n", connIndex)).trim();

        DataOutputStream os = new DataOutputStream(s.getOutputStream());
        os.writeBytes(
             "HTTP/1.1 101 Switching Protocols\r\n" +
             "Upgrade: websocket\r\n" +
             connStr + "\r\n" +
             "Sec-WebSocket-Accept: " + keyAccept + "\r\n" +
             "Sec-WebSocket-Protocol: mj\r\n\r\n");

        //
        // Put this new connection into the pending queue, main server loop
        // will handle later.
        //

        addNewConnection(s);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  void startServerSocketThread(final int port) {
    Thread worker = new Thread(new Runnable() {
      public void run() {
        switch (port)
        {
        case SERVER_PORT:
          tcpipWorker(port);
          break;
        case WEBSOCKET_SERVER_PORT:
          websockWorker(port);
          break;
        }
      }
    });
    worker.start();
  }

  //
  // Close AUR.
  //

  void closeAur(AUR u) throws Exception {

    //
    // Close AGR if it's need.
    //

    if (-1 != u.iAgr) {
      leaveGame(u);
    }

    //
    // Close connection.
    //

    try {
      u.os.close();
      u.is.close();
      u.s.close();
    } catch (Exception e) {
    }

    //
    // Release AUR.
    //

    aur[u.id] = null;
    nAur -= 1;

    //
    // Notify player leave to all clients.
    //

    if (MahjongProtocol.INIT_TOKEN == u.token) {
      PrintLog("<--AUR [" + u.id + "] Kicked!!!\n");
      return;
    }

    usendAll(null, MahjongProtocol.getPlayerLeaveCmd(u.id));
  }

  //
  // Create a new AUR and initialize it.
  //

  AUR newAur(Socket s) throws Exception {

    //
    // Find a free AUR.
    //

    if (aur.length == nAur) {
      PrintLog("AUR pool is full, disconnect!!!\n");

      AUR u = new AUR();
      u.s = s;
      u.os = s.getOutputStream();

      usend(u, MahjongProtocol.getServerIsBusyCmd());

      u.os.close();
      s.close();

      return null;
    }

    AUR u = null;
    for (int i = 0; i < aur.length; i++) {
      if (null == aur[i]) {
        u = new AUR();
        u.id = i;
        break;
      }
    }

    //
    // Initialize the new connection and AUR.
    //

    u.upTime = new Date();
    u.iAgr = -1;
    u.token = MahjongProtocol.INIT_TOKEN; // Not verified login.

    u.s = s;
    u.is = s.getInputStream();
    u.os = s.getOutputStream();

    updateKeepAlive(u);

    aur[u.id] = u;
    nAur += 1;

    //
    // Get the user token.
    //

    String str[] = new String[1];
    if (uread(u, str)) {
      u.token = str[0];
    } else {
      PrintLog("AUR [" + u.id + "] Get token failed!!!\n");
      usend(u, MahjongProtocol.getInvalidCommandCmd());
      closeAur(u);
      return null;
    }

    //
    // Initialize successful, send client a server is ready notification.
    //

    PrintLog("-->New AUR [" + u.id + "," + URLDecoder.decode(u.token, "UTF-8") + "] is READY!!!  #" + nAur + "u\n");

    usend(u, MahjongProtocol.getServerIsReadyCmd(u.id));

    //
    // Send current player list to this new connection.
    //

    for (int i = 0; i < aur.length; i++) {
      if (null != aur[i]) {
        usend(u, MahjongProtocol.getNewPlayerCmd(aur[i].id, aur[i].token));
      }
    }

    //
    // Notify new player to all clients.
    //

    usendAll(u, MahjongProtocol.getNewPlayerCmd(u.id, u.token));

    //
    // Send current game list to this new connection.
    //

    for (int i = 0; i < agr.length; i++) {
      if (null == agr[i]) {
        continue;
      }
      AGR g = agr[i];
      usend(u, MahjongProtocol.getNewGameCmd(g.id, g.iAur, g.running));
    }

    return u;
  }

  //
  // Process the AUR.
  //

  void triggerAur(AUR u) throws Exception {

    //
    // Connection lost?
    //

    if (System.currentTimeMillis() > u.keepAlive) {
      closeAur(u);
      PrintLog("<--AUR [" + u.id + "] timeout Leave!!! #" + nAur + "u\n");

      return;
    }

    //
    // Data ready?
    //

    if (0 >= u.is.available()) {
      return;
    }

    //
    // Handle command.
    //

    String str[] = new String[1];
    if (!uread(u, str)) {
      PrintLog("<--AUR [" + u.id + "] read packet failed!\n");
      closeAur(u);
      return;
    }

    String items[] = str[0].split(",");

    int cmd = Integer.parseInt(items[0]);

    //
    // Update keep alive timeout.
    //

    if (MahjongProtocol.C_KEEP_ALIVE == cmd) {
      updateKeepAlive(u);
    }

    //
    // Create new game.
    //

    else if (MahjongProtocol.C_NEW_GAME == cmd) {
      newGame(u, items);
    }

    //
    // Join an existing game.
    //

    else if (MahjongProtocol.C_JOIN_GAME == cmd) {
      joinGame(u, items);
    }

    //
    // Leave game.
    //

    else if (MahjongProtocol.C_LEAVE_GAME == cmd) {
      leaveGame(u);
    }

    //
    // Start playing game.
    //

    else if (MahjongProtocol.C_START_GAME == cmd) {
      startGame(u);
    }

    //
    // Play game.
    //

    else if (MahjongProtocol.C_GAME_ACTION == cmd) {
      playGame(u, items);
    }

    //
    // Invalid command.
    //

    else {
      PrintLog("<--AUR [" + u.id + "] KICK, invalid command!\n");
      closeAur(u);
    }
  }

  //
  // Handle new game request.
  //

  boolean newGame(AUR u, String items[]) throws Exception {

    PrintLog("AUR [" + u.id + "] attempt to create new game!\n");

    //
    // Is this client already in a game? It is not allowed to
    // create a game if this client is already in a game.
    //

    if (-1 != u.iAgr) {
      PrintLog("<--AUR [" + u.id + "] KICK, double create game!\n");
      closeAur(u);
      return false;
    }

    //
    // todo, Read game settings.
    //

    //
    // Find a free AGR and initialize it.
    //

    if (agr.length == nAgr) {
      PrintLog("<--AUR [" + u.id + "] create game failed, AGR full!\n");
      usend(u, MahjongProtocol.getServerIsBusyCmd());
      return true;
    }

    AGR g = null;
    for (int i = 0; i < agr.length; i++) {
      if (null == agr[i]) {
        g = new AGR();
        g.id = i;
        g.iAur = new int[MAX_PLAYER_OF_GAME];
        for (int j = 0; j < g.iAur.length; j++) {
          g.iAur[j] = -1;
        }
        break;
      }
    }

    //
    // Initialize the AGR.
    //

    g.iAur[0] = u.id;                   // First one is always the game creater.
    g.running = false;

    agr[g.id] = g;
    nAgr += 1;

    u.iAgr = g.id;

    PrintLog("-->New game [" + g.id + "] is created by AUR [" + u.id + "]!!!  #" + nAgr + "g\n");

    //
    // Notify all clients a new game is created.
    //

    usendAll(null, MahjongProtocol.getNewGameCmd(g.id, new int[]{u.id, -1, -1, -1}, false));

    return true;
  }

  //
  // Join an existing game.
  //

  boolean joinGame(AUR u, String items[]) throws Exception {

    int iAgr = Integer.parseInt(items[1]);

    PrintLog("AUR [" + u.id + "] attempt to join game " + iAgr + "!\n");

    //
    // Is valid to join game?
    //

    if (-1 != u.iAgr) {
      PrintLog("<--AUR [" + u.id + "] KICK, join game but already in game!\n");
      closeAur(u);
      return false;
    }

    //
    // Which game to join?
    //

    if (0 > iAgr || agr.length <= iAgr || null == agr[iAgr]) {
      PrintLog("<--AUR [" + u.id + "] KICK, join invalid game!\n");
      closeAur(u);
      return false;
    }

    //
    // Is valid to join?
    //

    AGR g = agr[iAgr];

    if (g.running) {
      PrintLog("AUR [" + u.id + "] attempts to join a playing game " + iAgr + " failed!\n");
      return false;
    }

    //
    // Does the game have free entity(or not start)?
    //

    int pos = -1;
    for (int i = 0; i < g.iAur.length; i++) {
      if (-1 == g.iAur[i]) {
        pos = i;
        break;
      }
    }

    if (-1 == pos) {                    // No free space to join.

      //
      // This is possible because of timeing. Therefore we can't treat
      // this fail as a cheat and kick the client.
      //

      usend(u, MahjongProtocol.getGameIsBusyCmd());

      return false;
    }

    //
    // Join the game.
    //

    g.iAur[pos] = u.id;

    u.iAgr = iAgr;

    PrintLog("AUR [" + u.id + "] join game [" + iAgr + "] at pos " + pos + "!\n");

    //
    // Notify all clients.
    //

    usendAll(null, MahjongProtocol.getJoinGameCmd(g.id, u.id, pos));

    return true;
  }

  //
  // Handle leave game.
  //

  boolean leaveGame(AUR u) throws Exception {

    PrintLog("AUR [" + u.id + "] attempt to leave game!\n");

    //
    // Is valid to leave game?
    //

    if (-1 == u.iAgr) {
      PrintLog("<--AUR [" + u.id + "] KICK, leave game but not in game!\n");
      closeAur(u);
      return false;
    }

    //
    // Remove this player from game.
    //

    AGR g = agr[u.iAgr];

    boolean destroy = !g.running && g.iAur[0] == u.id;

    int pos = 0;
    for (; pos < g.iAur.length; pos++) {
      if (u.id == g.iAur[pos]) {
        g.iAur[pos] = -1;
        u.iAgr = -1;
        break;
      }
    }

    PrintLog("AUR [" + u.id + "] leave game [" + g.id + "]!\n");

    for (pos = 0; pos < g.iAur.length; pos++) {
      if (-1 != g.iAur[pos]) {
        break;
      }
    }

    if (g.iAur.length == pos) {
      destroy = true;
    }

    //
    // Notify all clients a player leave game.
    //

    usendAll(null, MahjongProtocol.getLeaveGameCmd(g.id, destroy ? -1 : u.id));

    //
    // If the player is the game creater(pos 0) then destroy this game.
    //

    if (destroy) {

      for (int i = 1; i < g.iAur.length; i++) {
        if (-1 != g.iAur[i]) {
          aur[g.iAur[i]].iAgr = -1;
        }
      }

      agr[g.id] = null;
      nAgr -= 1;

      PrintLog("<--Game [" + g.id + "] destroyed!!!  #" + nAgr + "g\n");
    }

    return true;
  }

  //
  // Start playing game.
  //

  boolean startGame(AUR u) throws Exception {

    //
    // Is player in a game?
    //

    if (-1 == u.iAgr) {
      PrintLog("<--AUR [" + u.id + "] KICK, not in game!\n");
      closeAur(u);
      return false;
    }

    AGR g = agr[u.iAgr];

    //
    // Is game creater?
    //

    if (g.iAur[0] != u.id) {
      PrintLog("<--AUR [" + u.id + "] KICK, not game creater!\n");
      closeAur(u);
      return false;
    }

    //
    // Is the game already started?
    //

    if (g.running) {
      PrintLog("<--AUR [" + u.id + "] KICK, game already start!\n");
      closeAur(u);
      return false;
    }

    //
    // Start the game.
    //

    g.running = true;

    g.newPlay = true;
    g.totalPlay = 0;
    g.round = 0;

    g.mj = new MahjongGame();

    //
    // Notify all clients game starts.
    //

    usendAll(null, MahjongProtocol.getStartGameCmd(g.id));

    PrintLog("Game [" + g.id + "] start!!!\n");

    return true;
  }

  //
  // Handle game play commands.
  //

  boolean playGame(AUR u, String items[]) throws Exception {

    //
    // Is this player in the game?
    //

    if (-1 == u.iAgr) {
      return false;
    }

    //
    // Is the game playing?
    //

    AGR g = agr[u.iAgr];

    if (!g.running) {
      return false;
    }

    //
    // Handle play game commands.
    //

    int active = g.mj.getActive();
    int action = Integer.parseInt(items[1]), card;

    //
    // Pass.
    //

    if (MahjongGame.AI_PASS == action) {
      if (g.mj.pass()) {
        playGame_i(g, active, action, 0);
        sendNextGameState(g);
        resetTimer(g);
        return true;
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to pass!\n");
      }
    }

    //
    // Exchange card.
    //

    else if (MahjongGame.AI_EXCHANGE == action) {
      card = Integer.parseInt(items[2]);
      if (g.mj.exchange(card)) {
        playGame_i(g, active, action, card);
        sendNextGameState(g);
        resetTimer(g);
        return true;
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to exchange!\n");
      }
    }

    //
    // Chi.
    //

    else if (MahjongGame.AI_CHI == action) {
      card = Integer.parseInt(items[2]);
      if (g.mj.chi(card)) {
        playGame_i(g, active, action, card);
        resetTimer(g);
        return true;
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to chi!\n");
      }
    }

    //
    // Pon.
    //

    else if (MahjongGame.AI_PON == action) {
      card = g.mj.getLastHdCard();
      if (g.mj.pon()) {
        playGame_i(g, active, action, card);
        resetTimer(g);
        return true;
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to pon!\n");
      }
    }

    //
    // Gun.
    //

    else if (MahjongGame.AI_GUN == action) {
      card = Integer.parseInt(items[2]);
      if (g.mj.gun(card)) {
        playGame_i(g, active, action, card);
        resetTimer(g);
        return true;
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to gun!\n");
      }
    }

    //
    // Lon.
    //

    else if (MahjongGame.AI_LON == action) {
      if (g.mj.lon()) {
        card = g.mj.getPick();
        if (0 == card) {
          card = g.mj.getLastHdCard();
        }
        playGame_i(g, active, action, card);
        g.mj.mjOpen.winner = active;
        g.newPlay = true;
        return true;
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to lon!\n");
      }
    }

    //
    // Declare tin pai.
    //

    else if (MahjongGame.AI_TIN == action) {
      card = Integer.parseInt(items[2]);
      if (g.mj.tin(card)) {
        playGame_i(g, active, action, card);
        sendNextGameState(g);
        resetTimer(g);
      } else {
        PrintLog("<--AUR [" + u.id + "] KICK, fail to tin!\n");
      }
    }

    //
    // Invalid commands.
    //

    else {
      PrintLog("<--AUR [" + u.id + "] KICK, invalid command!\n");
    }

    closeAur(u);

    return false;
  }

  //
  // Handle play game inner.
  //

  boolean playGame_i(AGR g, int active, int action, int card) {

    //
    // Pass.
    //

    if (MahjongGame.AI_PASS == action) {
      send(g, MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_PASS));
      return true;
    }

    //
    // Exchange card.
    //

    if (MahjongGame.AI_EXCHANGE == action) {
      String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_EXCHANGE);
      sCmd += "," + card;
      send(g, sCmd);
      return true;
    }

    //
    // Chi.
    //

    if (MahjongGame.AI_CHI == action) {
      String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_CHI);
      sCmd += "," + card;
      send(g, sCmd);
      return true;
    }

    //
    // Pon.
    //

    if (MahjongGame.AI_PON == action) {
      String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_PON);
      sCmd += "," + card;
      send(g, sCmd);
      return true;
    }

    //
    // Gun.
    //

    if (MahjongGame.AI_GUN == action) {
      String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_GUN);
      sCmd += "," + card + "," + g.mj.getPick(); // Additional pick card after gun.
      send(g, sCmd);
      return true;
    }

    //
    // Lon.
    //

    if (MahjongGame.AI_LON == action) {

      //
      // 1st, notify to open all players' cards.(not include self)
      //

      sendShowAllCards(g);

      //
      // 2nd, notify active player wins this game, and send his score list.
      //

      String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_LON);
      sCmd += "," + card;

      MahjongScore s = new MahjongScore();
      Vector score = s.score(g.mj, active);

      for (int i = 0; i < score.size(); i++) {
        sCmd = sCmd + "," + score.elementAt(i);
      }

      send(g, sCmd);

      return true;
    }

    //
    // Declare tin pai.
    //

    if (MahjongGame.AI_TIN == action) {
      String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_TIN);
      sCmd += "," + card;
      send(g, sCmd);
      return true;
    }

    //
    // Invalid commands.
    //

    return false;
  }

  //
  // Handle mahjong game play.
  //

  boolean triggerAgr(AGR g) throws Exception {

    //
    // Is the game playing?
    //

    if (!g.running) {
      return false;
    }

    //
    // Start a new game?
    //

    if (g.newPlay) {

      //
      // Start a new game and notify new game states.
      //

      initGame(g);

      //
      // Handle the first time patch flower cards.
      //

      patchFlowers(g);

      return true;
    }

    //
    // Game over?
    //

    if (g.mj.isGameOver()) {
      gameOver(g);
      return true;
    }

    //
    // If the active player is not in the game then AI takes control.
    //

    int active = g.mj.getActive();

    if (-1 == g.iAur[active]) {

      //
      // Every 1 second.
      //

      if (System.currentTimeMillis() + (TIMEOUT_PLAY_GAME - TIMEOUT_AI_PLAY_GAME) * 1000 > g.timeout) {

        //
        // Patch the pick card automatically if this is a flower card.
        //

        Vector p = g.mj.patchFlower(active);
        if (null != p) {

          //
          // Notify the patch if this is really a flower card.
          //

          sendFlowerPatch(g, active, p);

        } else {

          //
          // Process by AI.
          //

          switch (g.mj.ai())
          {
          case MahjongGame.AI_PASS:
            playGame_i(g, active, MahjongGame.AI_PASS, 0);
            sendNextGameState(g);
            break;
          case MahjongGame.AI_EXCHANGE:
            playGame_i(g, active, MahjongGame.AI_EXCHANGE, g.mj.aiCard);
            sendNextGameState(g);
            break;
          case MahjongGame.AI_CHI:
            playGame_i(g, active, MahjongGame.AI_CHI, g.mj.aiCard);
            break;
          case MahjongGame.AI_PON:
            playGame_i(g, active, MahjongGame.AI_PON, g.mj.aiCard);
            break;
          case MahjongGame.AI_GUN:
            playGame_i(g, active, MahjongGame.AI_GUN, g.mj.aiCard);
            break;
          case MahjongGame.AI_LON:
            playGame_i(g, active, MahjongGame.AI_LON, g.mj.aiCard);
            g.mj.mjOpen.winner = active;
            g.newPlay = true;
            break;
          }
        }

        //
        // Reset timeout timer now.
        //

        resetTimer(g);
      }

      return true;
    }

    //
    // Auto discard card or pass.
    //

    if (System.currentTimeMillis() > g.timeout) {

      int pick = g.mj.getPick();

      if (0 == pick) {

        //
        // Pass this turn.
        //

        g.mj.pass();

        //
        // Notify pass.
        //

        send(g, MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_PASS));

      } else {

        Vector p = g.mj.patchFlower(active);
        if (null != p) {

          //
          // Notify the patch if this is really a flower card.
          //

          sendFlowerPatch(g, active, p);

        } else {

          //
          // Discad card.
          //

          g.mj.discard(pick);

          //
          // Notify discard pick card.
          //

          String sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_EXCHANGE);
          sCmd += "," + pick;

          send(g, sCmd);
        }
      }

      //
      // Send next pick or pass state to next active player.
      //

      sendNextGameState(g);

      //
      // Reset timeout timer.
      //

      resetTimer(g);

      return true;
    }

    //
    // Not handled.
    //

    return false;
  }

  //
  // Initialize game.
  //

  boolean initGame(AGR g) {

    //
    // Start a new game.
    //

    g.mj.newGame();

    g.newPlay = false;
    g.totalPlay += 1;

    resetTimer(g);

    //
    // Complete a round?
    //

    if (4 == g.mj.mjOpen.wind) {

      g.mj.mjOpen.wind = 0;

      //
      // Complete 4 rounds? End this game.
      //

      if (4 <= ++g.round) {
        completeGame(g);
        return false;
      }
    }

    //
    // Notify gamer the initial state of the game.
    //

    String sCmdHdr = MahjongProtocol.getGameInitCmd(
                                       g.round,
                                       g.mj.mjOpen.wind,
                                       (g.mj.mjOpen.gameCount % 4),
                                       g.totalPlay,
                                       g.mj.mjOpen.master,
                                       g.mj.mjOpen.masterCount);

    for (int i = 0; i < g.iAur.length; i++) {

      if (-1 == g.iAur[i]) {
        continue;
      }

      String sCards = "";

      Vector p = g.mj.getPlayer(i);
      for (int j = 0; j < p.size(); j++) {
        sCards = sCards + "," + ((Integer)p.elementAt(j)).intValue();
      }

      if (g.mj.mjOpen.master == i) {
        sCards = sCards + "," + g.mj.getPick();
      }

      usend(aur[g.iAur[i]], sCmdHdr + sCards);
    }

    return true;
  }

  //
  // Game is complete.
  //

  boolean completeGame(AGR g) {

    //
    // Notify game is finished.
    //

    send(g, MahjongProtocol.getGameEndsCmd());

    //
    // Close the game.
    //

    for (int i = 1; i < g.iAur.length; i++) {
      if (-1 != g.iAur[i]) {
        aur[g.iAur[i]].iAgr = -1;
      }
    }

    agr[g.id] = null;
    nAgr -= 1;

    //
    // Notify game is closed.
    //

    usendAll(null, MahjongProtocol.getLeaveGameCmd(g.id, g.iAur[0]));

    return true;
  }

  //
  // Send next pick or pass state to next active player.
  //

  boolean sendNextGameState(AGR g) {

    int active = g.mj.getActive();
    int state = g.mj.getState(active);

    String sCmd;

    int pick = g.mj.getPick();

    //
    // Wait for discarding a card after Chi/Pon etc states..
    //

    if (0 == pick) {

      if (-1 != g.iAur[active]) {

        sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_STATE);
        sCmd += "," + state;

        //
        // Append candidate cards according current state.
        //

        sCmd = sCmd + getStateStrng(g, state);

        usend(aur[g.iAur[active]], sCmd);
      }
    }

    //
    // Pick a new card.
    //

    else {

      //
      // Notify pick a new card.
      //

      sCmd = MahjongProtocol.getGameActionCmd(active, MahjongGame.AI_PICK);
      sCmd += ",";

      for (int i = 0; i < g.iAur.length; i++) {
        if (-1 != g.iAur[i]) {
          if (active == i) {

            //
            // Send the pick card to active player.
            //

            String sCmd2 = sCmd + pick + "," + state + getStateStrng(g, state);

            usend(aur[g.iAur[i]], sCmd2);

          } else {

            //
            // For other players, they only get a notification(card 0).
            //

            usend(aur[g.iAur[i]], sCmd + "0");
          }
        }
      }
    }

    return true;
  }

  //
  // Handle game over.
  //

  boolean gameOver(AGR g) {

    //
    // Reset state.
    //

    g.mj.mjOpen.winner = -1;
    g.newPlay = true;

    //
    // Notify show all cards first.
    //

    sendShowAllCards(g);

    //
    // Then notify game is over.
    //

    send(g, MahjongProtocol.getGameActionCmd(0, MahjongGame.AI_END));

    return true;
  }

  //
  // Notify all players in the game to display 4 player(not include self)
  // cards when someone wins the game or game is over.
  //

  boolean sendShowAllCards(AGR g) {

    //
    // Header.
    //

    String sCmd = MahjongProtocol.getGameActionCmd(0, MahjongGame.AI_SHOWCARDS);

    //
    // Prepare card lists for 4 players.
    //

    String sPlayer[] = new String[4];
    for (int i = 0; i < 4; i++) {
      Vector op = g.mj.getOpPlayer(i);
      Vector p = g.mj.getPlayer(i);

      sPlayer[i] = "," + i + "," + (op.size() + p.size());

      for (int j = 0; j < op.size(); j++) {
        sPlayer[i] = sPlayer[i] + "," + ((Integer)op.elementAt(j)).intValue();
      }

      for (int j = 0; j < p.size(); j++) {
        sPlayer[i] = sPlayer[i] + "," + ((Integer)p.elementAt(j)).intValue();
      }
    }

    //
    // Send lists to online players, the list sent to a player is not inlcude
    // self.
    //

    for (int i = 0; i < g.iAur.length; i++) {
      if (-1 != g.iAur[i]) {
        String s = sCmd;
        for (int j = 0; j < 4; j++) {
          if (j != i) {
            s = s + sPlayer[j];
          }
        }
        usend(aur[g.iAur[i]], s);
      }
    }

    return true;
  }

  //
  // Update user keep alive timeout timer.
  //

  void updateKeepAlive(AUR u) {
    u.keepAlive = System.currentTimeMillis() + 2 * TIMEOUT_KEEP_ALIVE * 1000;
  }

  //
  // Reset timoeut timer of the game.
  //

  void resetTimer(AGR g) {
    g.timeout = System.currentTimeMillis() + TIMEOUT_PLAY_GAME * 1000;
  }

  //
  // Append card list to string command list.
  //

  String appendListToSCmd(String sCmd, Vector v) {

    sCmd = sCmd + "," + v.size();
    for (int i = 0; i < v.size(); i++) {
      sCmd = sCmd + "," + ((Integer)v.elementAt(i)).intValue();
    }

    return sCmd;
  }

  //
  // Format the command list according specified state.
  //

  String getStateStrng(AGR g, int state) {

    String sCmd = "";
    Vector v = null;

    if (0 != (state & MahjongGame.MJ_CANTIN)) {
      v = g.mj.getTinDiscard(g.mj.getActive());
      sCmd = appendListToSCmd(sCmd, v);
    }

    if (0 != (state & MahjongGame.MJ_CANGUN)) {
      v = g.mj.getGunPai();
      sCmd = appendListToSCmd(sCmd, v);
    }

    if (0 != (state & MahjongGame.MJ_CANPON)) {
      sCmd = sCmd + ",1," + g.mj.getLastHdCard();
    }

    if (0 != (state & MahjongGame.MJ_CANCHI)) {
      v = g.mj.getChiPai();
      sCmd = appendListToSCmd(sCmd, v);
    }

    return sCmd;
  }

  //
  // New game patch flower cards process by counterclockwise order.
  //

  void patchFlowers(AGR g) {

    //
    // Loop all players for patching flower cards in counterclockwise order
    // until no flower card to patch.
    //

    int active = g.mj.getActive();

    for (int i = 0; i < g.iAur.length; i++) {

      //
      // Get player index in counterclockwise order.
      //

      int iPlayer = (active - i + MahjongGame.NUM_PLAYER) % MahjongGame.NUM_PLAYER;

      //
      // Loop patch flower cards until no flower card to path. The format
      // of return vector is: flower pick flower pick ...
      //

      Vector p = new Vector();

      while (true) {
        Vector p1 = g.mj.patchFlower(iPlayer);
        if (null == p1) {
          break;
        }

        //
        // Append.
        //

        for (int j = 0; j < p1.size(); j++) {
          p.add(p1.elementAt(j));
        }
      }

      //
      // Notify patches.
      //

      if (0 != p.size()) {
        sendFlowerPatch(g, iPlayer, p);
      }
    }
  }

  //
  // Notify flower patches.
  //

  void sendFlowerPatch(AGR g, int iPlayer, Vector p) {

    //
    // The new pick of a flower card will be 0 if this message is sent
    // to non-active player.
    //

    Vector p2 = (Vector)p.clone();  // Prepare patch for non-active player.

    for (int i = 0; i < p2.size(); i++) {
      if (1 == (i % 2)) {
        p2.setElementAt(new Integer(0), i);
      }
    }

    //
    // Prepare command strings for active and non-active players respectively.
    //

    String sCmd = MahjongProtocol.getGameActionCmd(iPlayer, MahjongGame.AI_PATCH);
    String sCmd1 = sCmd, sCmd2 = sCmd;

    for (int i = 0; i < p.size(); i++) {
      sCmd1 += "," + ((Integer)p.elementAt(i)).intValue();
      sCmd2 += "," + ((Integer)p2.elementAt(i)).intValue();
    }

    //
    // Notify the patch of this player to all players of the game.
    //

    for (int i = 0; i < g.iAur.length; i++) {
      if (-1 == g.iAur[i]) {
        continue;
      }
      if (iPlayer == i) {
        usend(aur[g.iAur[i]], sCmd1);
      } else {
        usend(aur[g.iAur[i]], sCmd2);
      }
    }
  }

  //
  // Notify message to game members.
  //

  boolean send(AGR g, String s) {
    for (int i = 0; i < g.iAur.length; i++) {
      if (-1 != g.iAur[i]) {
        usend(aur[g.iAur[i]], s);
      }
    }
    return true;
  }

  //
  // Output message on the screen and write to log file.
  //

  void PrintLog(String mess) throws IOException {
    String head = (new Date()).toString() + ", ";
    System.out.print(head + mess);
    log.write(head + mess);
    log.flush();
  }

  //
  // Send/Read net package.
  //

  boolean usend(AUR u, String msg) {
    switch (u.s.getLocalPort())
    {
    case SERVER_PORT:
      return MahjongProtocol.tcpipSend(u.os, msg);
    case WEBSOCKET_SERVER_PORT:
      return MahjongProtocol.websockSend(u.os, msg);
    default:
      return false;
    }
  }

  void usendAll(AUR except, String msg) {
    for (int i = 0; i < aur.length; i++) {
      if (null != aur[i] && except != aur[i]) {
        usend(aur[i], msg);
      }
    }
  }

  boolean uread(AUR u, String msg[]) {
    switch (u.s.getLocalPort())
    {
    case SERVER_PORT:
      return MahjongProtocol.tcpipRead(u.is, msg);
    case WEBSOCKET_SERVER_PORT:
      return MahjongProtocol.websockRead(u.is, msg);
    default:
      return false;
    }
  }
} // MahjongServer

// end of MahjongServer.java
