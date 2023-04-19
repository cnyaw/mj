
//
// MahjongProtocol.java, Net Protocol.
// 2014/04/28 Waync Cheng
//

import java.io.*;

public class MahjongProtocol {

  static final String TokStart = "MjSt";
  static final String TokEnd = "MjEd";

  static final String INIT_TOKEN = "nobody";

  static final byte byTokStart[] = TokStart.getBytes();
  static final byte byTokEnd[] = TokEnd.getBytes();

  //
  // Net command ID.
  //   1, The format of a command stream is: CmdID(1) [other data(n)]  CheckSum(1).
  //   2, All commands have a command realy.
  //

  //
  // New Connection
  //      A client connects to the game server, and sends a token at the same
  //      time. Then wait replay to know is server busy or ready.
  // Format: StrToken
  //

  //
  // C_SERVER_IS_BUSY
  //      Server replys to new connection if there is no free AUR available.
  // Format: Cmd
  //

  public static final byte C_SERVER_IS_BUSY = 0x08;

  public static String getServerIsBusyCmd() {
    return Integer.toString(C_SERVER_IS_BUSY);
  }

  //
  // C_INVALID_COMMAND
  //      Severs received an invalid command.
  // Format: Cmd
  //

  public static final byte C_INVALID_COMMAND = 0x09;

  public static String getInvalidCommandCmd() {
    return Integer.toString(C_INVALID_COMMAND);
  }

  //
  // C_SERVER_IS_READY
  //      Server sends to new connection if new AUR is initialized.
  // Format: Cmd, iAur
  //

  public static final byte C_SERVER_IS_READY = 0x0a;

  public static String getServerIsReadyCmd(int uid) {
    return Integer.toString(C_SERVER_IS_READY) + "," + uid;
  }

  //
  // C_KEEP_ALIVE
  //      Client sends to server periodically to let server knows this
  //      connection is still alive.
  // Format: Cmd
  //

  public static final byte C_KEEP_ALIVE = 0x0b;

  //
  // C_NEW_PLAYER
  //      New connection enters lobby successful. Server sends this notify to
  //      all clients.
  // Format: Cmd, iAur, token
  //

  public static final byte C_NEW_PLAYER = 0x10;

  public static String getNewPlayerCmd(int uid, String token) {
    return Integer.toString(C_NEW_PLAYER) + "," + uid + "," + token;
  }

  //
  // C_PLAYER_LEAVE
  //      Server informs all clients that a client leaves game server.
  // Format: Cmd, iAur
  //

  public static final byte C_PLAYER_LEAVE = 0x11;

  public static String getPlayerLeaveCmd(int uid) {
    return Integer.toString(C_PLAYER_LEAVE) + "," + uid;
  }

  //
  // C_NEW_GAME
  //      1, Client sends to server to create a new game, the packet includes
  //         settings of the game.
  //         If create game successful, server returns C_NEW_GAME, otherwise
  //         C_SERVER_IS_BUSY. Or the client is force to disconnect if there
  //         is any state error.
  // Format: Cmd
  //      2, Server notify all clients in the lobby that a new game is added,
  //         this notification allows clients to update their local status.
  // Format: Cmd, iAgr, iAur[4],IsPlaying
  //

  public static final byte C_NEW_GAME = 0x20;

  public static String getNewGameCmd(int gid, int[] aur, boolean running) {
    return Integer.toString(C_NEW_GAME) + "," +
                     gid + "," +
                     aur[0] + "," + aur[1] + "," + aur[2] + "," + aur[3] + "," +
                     (running ? "1" : "0");
  }

  //
  // C_LEAVE_GAME
  //      A player leaves the game.
  // Format:
  //      1, Client to server: Cmd
  //      2, Server to client: Cmd, iAgr, iAur
  // Note:
  //      -1 == iAur means game is destroyed.
  //

  public static final byte C_LEAVE_GAME = 0x22;

  public static String getLeaveGameCmd(int gid, int uid) {
    return Integer.toString(C_LEAVE_GAME) + "," + gid + "," + uid;
  }

  //
  // C_JOIN_GAME
  //      A player joins a game.
  // Format:
  //      1, Client to server: Cmd, iAgr
  //      2, Server to client: Cmd, iAgr, iAur, Pos
  //

  public static final byte C_JOIN_GAME = 0x23;

  public static String getJoinGameCmd(int gid, int uid, int pos) {
    return Integer.toString(C_JOIN_GAME) + "," + gid + "," + uid + "," + pos;
  }

  //
  // C_GAME_IS_BUSY
  //      Cannot join game, because of the game is full.
  // Format: Cmd
  //

  public static final byte C_GAME_IS_BUSY = 0x24;

  public static String getGameIsBusyCmd() {
    return Integer.toString(C_GAME_IS_BUSY);
  }

  //
  // C_START_GAME
  //      Game creater commands to start the game.
  // Format: Cmd, iAgr
  //

  public static final byte C_START_GAME = 0x25;

  public static String getStartGameCmd(int gid) {
    return Integer.toString(C_START_GAME) + "," + gid;
  }

  //
  // C_GAME_INIT
  //      Initialial state of a new game.
  // Format: Cmd, Round, WindRound, WindPlay, TotalPlay, Master, MasterCount
  //

  public static final byte C_GAME_INIT = 0x32;

  public static String getGameInitCmd(int round, int wind, int count, int play, int master, int masterCount) {
    return Integer.toString(C_GAME_INIT) + "," +
                     round + "," +      // 圈
                     wind + "," +       // 風
                     count + "," +      // 局
                     play + "," +       // Total game played.
                     master + "," +     // 莊家
                     masterCount;       // 連莊
  }

  //
  // C_GAME_ENDS
  //      Completed 4 rounds, game is finished.
  // Format: Cmd
  //

  public static final byte C_GAME_ENDS = 0x33;

  public static String getGameEndsCmd() {
    return Integer.toString(C_GAME_ENDS);
  }

  //
  // C_GAME_ACTION
  //      Game playing, action of player or AI.
  // Format: Cmd, [Pos, ]Act(MahjongGame.AI_XXX) [Dat]
  //      AI_FAIL
  //      AI_PASS
  //      AI_EXCHANGE, Pick
  //      AI_CHI. Chi, Pick
  //      AI_PON, Pon, Pick
  //      AI_GUN, Gun, Pick
  //      AI_LON, Pick
  //      AI_PICK, Pick
  //      AI_STATE, State [,Cards]
  //      AI_END
  //

  public static final byte C_GAME_ACTION = 0x34;

  public static String getGameActionCmd(int pos, int act) {
    return Integer.toString(C_GAME_ACTION) + "," + pos + "," + act;
  }

  //
  // C_ENABLE_AUTO_PLAY
  //      Toggle auto play by AI.
  // Format: Cmd, 0(disable)/1(enable)
  //

  public static final byte C_ENABLE_AUTO_PLAY = 0x35;

  public static String getEnableAutoPlayCmd(boolean enable) {
    return Integer.toString(C_ENABLE_AUTO_PLAY) + "," + (enable ? "1" : "0");
  }

  //
  // C_SET_TOKEN
  //      Changed player display name. (may duplicate)
  // Format:
  //      1, Client to server: Cmd, token
  //      2, Server to client: Cmd, iAur, token
  //

  public static final byte C_SET_TOKEN = 0x36;

  public static String getSetTokenCmd(String token) {
    return Integer.toString(C_SET_TOKEN) + "," + token;
  }

  public static String getSetTokenCmd(int uid, String token) {
    return Integer.toString(C_SET_TOKEN) + "," + uid + "," + token;
  }

  //
  // Send a packet. A packet is in the form of: LenStrData(1) StrData(n)
  // CheckSum(1) The first byte is the length of the StrDat. The sum of
  // all bytes in the StrData and the check sum byte will be 0.
  //

  static boolean send(OutputStream os, String s) {

    byte by[] = s.getBytes();

    //
    // Calc checksum.
    //

    int sum = 0;
    for (int i = 0; i < by.length; i++) {
      sum = (sum + by[i]) & 0xff;
    }

    //
    // Write packet.
    //

    try {
      os.write(new byte[]{(byte)(by.length & 0xff)});
      os.write(by, 0, by.length);
      os.write(new byte[]{(byte)(0xff - sum)}, 0, 1);
    } catch (Exception e) {
      return false;
    }

    return true;
  }

  //
  // Read packet.
  //

  static boolean read(InputStream is, String s[]) {

    try {

      //
      // Read packet.
      //

      byte len[] = new byte[1];
      is.read(len);

      byte by[] = new byte[(len[0] & 0xff)];
      is.read(by);

      byte chkSum[] = new byte[1];
      is.read(chkSum);

      int sum = 0;
      for (int i = 0; i < by.length; i++) {
        sum = (sum + by[i]) & 0xff;
      }

      if ((byte)(0xff - sum) != chkSum[0]) {
        System.out.print("CheckSum error: " + chkSum[0] + " != " + (byte)(0xff - sum) + "\n");
        return false;
      }

      s[0] = new String(by);

    } catch (IOException e) {
      return false;
    }

    return true;
  }

  //
  // TCP/IP read/write.
  //

  static boolean tcpipSend(OutputStream os, String msg) {

    try {

      //
      // Write start token.
      //

      os.write(byTokStart);

      //
      // Write msg.
      //

      if (!send(os, msg)) {
        return false;
      }

      //
      // Write end token.
      //

      os.write(byTokEnd);

      os.flush();

    } catch (IOException e) {
      return false;
    }

    return true;
  }

  static boolean tcpipRead(InputStream is, String msg[]) {

    byte by1[] = new byte[1];

    try {

      //
      // Read start token.
      //

      for (int i = 0; i < byTokStart.length; i++) {
        is.read(by1);
        if (byTokStart[i] != by1[0]) {
          System.out.print("Read start tok failed!\n");
          return false;
        }
      }

      //
      // Read msg.
      //

      if (!read(is, msg)) {
        return false;
      }

      //
      // Read end token.
      //

      for (int i = 0; i < byTokEnd.length; i++) {
        is.read(by1);
        if (byTokEnd[i] != by1[0]) {
          System.out.print("Read end tok failed!\n");
          return false;
        }
      }

    } catch (IOException e) {
      return false;
    }

    return true;
  }

  //
  // WebSocket read/write.
  //

  static boolean websockSend(OutputStream os, String msg) {
    try {

      //
      // Send OP code frame.
      //

      byte[] op = {(byte)0x82};
      os.write(op);

      //
      // Send length.
      //

      int len = msg.length() + 2;
      if (125 >= len) {
        os.write(new byte[]{(byte)len});
      } else if (65535 >= len) {
        os.write(new byte[]{(byte)126, (byte)(len >> 8), (byte)len});
      } else {
        os.write(new byte[]{(byte)127, (byte)(len >> 56), (byte)(len >> 48), (byte)(len >> 40), (byte)(len >> 32), (byte)(len >> 24), (byte)(len >> 16), (byte)(len >> 8), (byte)len});
      }

      //
      // Send payload.
      //

      return send(os, msg);
    } catch (IOException e) {
      return false;
    }
  }

  static boolean websockRead(InputStream is, String msg[]) {
    try {

      //
      // Read header.
      //

      byte b1[] = new byte[2];          // 0x82 binary data, length.
      is.read(b1);
      if (0x82 != (b1[0] & 0xff)) {
        return false;
      }

      //
      // Get message length.
      //

      int length = (b1[1] & 0xff) - 128;
      if (126 == length) {              // 16-bits length.
        byte b2[] = new byte[2];
        is.read(b2);
        length = (b2[0] & 0xff) | ((b2[1] & 0xff) << 8);
      } else if (127 == length) {       // 64-bits length.
        byte b2[] = new byte[8];
        is.read(b2);
        length = b2[0] & 0xff;
        for (int i = 1; i < 8; i++) {
          length |= ((b2[i] & 0xff) << (8 * i));
        }
      }

      //
      // Read decode key.
      //

      byte key[] = new byte[4];
      is.read(key);

      //
      // Read message.
      //

      byte b[] = new byte[length];
      is.read(b);

      //
      // Decode message.
      //

      for (int i = 0; i < b.length; i++) {
        b[i] ^= (byte)key[i % 4];
      }

      msg[0] = new String(b);

    } catch (IOException e) {
      return false;
    }

    return true;
  }
}

// end of MahjongProtocol.java
