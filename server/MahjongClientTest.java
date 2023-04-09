
//
// MahjongClientTest.java, Test Client
// 2010/12/19 Waync Cheng
//

import java.io.*;
import java.net.*;
import java.util.*;

public class MahjongClientTest {

  public static void main(String args[]) throws Exception {

    //
    // Init.
    //

    Socket s = new Socket((String)null, MahjongServer.SERVER_PORT);
    DataInputStream is = new DataInputStream(s.getInputStream());
    OutputStream os = s.getOutputStream();

    //
    // Send token.
    //

    Random rand = new Random();
    String token = Integer.toString(rand.nextInt());

    System.out.println("Test token = " + token);

    MahjongProtocol.tcpipSend(os, token);

    //
    // Main loop.
    //

    long timeKeepAlive = System.currentTimeMillis() + 1000 * (MahjongServer.TIMEOUT_KEEP_ALIVE - 1);

    int MyId = -1;
    int MyGameId = -1;
    boolean GameStart = false;
    boolean IsAutoPlay = false;
    long timeNewGame = System.currentTimeMillis() + 1000 * 5;

    int Games[][] = new int[MahjongServer.MAX_AGR][];
    for (int i = 0; i < Games.length; i++) {
      Games[i] = new int[4 + 1];
      for (int j = 0; j < 4; j++) {
        Games[i][j] = -1;
      }
      Games[i][4] = 0;
    }

    String str[] = new String[1];

    while (true) {
      Thread.sleep(1);

      if (0 < is.available()) {
        if (!MahjongProtocol.tcpipRead(is, str)) {
          break;
        }

        String items[] = str[0].split(",");
        int cmd = Integer.parseInt(items[0]);

        if (MahjongProtocol.C_SERVER_IS_READY == cmd) {
          MyId = Integer.parseInt(items[1]);
          System.out.println("SERVER IS READY, My Id = " + MyId);
        } else if (MahjongProtocol.C_NEW_PLAYER == cmd) {
          int Aur1 = Integer.parseInt(items[1]);
          System.out.println("NEW PLAYER " + Aur1 + "(" + URLDecoder.decode(items[2], "UTF-8") + ")");
        } else if (MahjongProtocol.C_PLAYER_LEAVE == cmd) {
          int Aur1 = Integer.parseInt(items[1]);
          System.out.println("PLAYER LEAVE " + Aur1);
        } else if (MahjongProtocol.C_NEW_GAME == cmd) {

          int GameId = Integer.parseInt(items[1]);
          int Aur1 = Integer.parseInt(items[2]);
          int Aur2 = Integer.parseInt(items[3]);
          int Aur3 = Integer.parseInt(items[4]);
          int Aur4 = Integer.parseInt(items[5]);
          int IsPlaying = Integer.parseInt(items[6]);

          System.out.println("NEW GAME [" + GameId + "], player=[" + Aur1 + "," + Aur2 + "," + Aur3 + "," + Aur4 + "]" + (1 == IsPlaying ? ",Playing" : ""));

          Games[GameId][0] = Aur1;
          Games[GameId][1] = Aur2;
          Games[GameId][2] = Aur3;
          Games[GameId][3] = Aur4;
          Games[GameId][4] = IsPlaying;

          if (Aur1 == MyId) {
            MyGameId = GameId;
          }
        } else if (MahjongProtocol.C_LEAVE_GAME == cmd) {
          int GameId = Integer.parseInt(items[1]);
          int Aur1 = Integer.parseInt(items[2]);

          if (Aur1 == MyId) {
            MyGameId = -1;
          }

          if (-1 == Aur1) {             // Destroy game.
            Games[GameId][0] = -1;
            Games[GameId][1] = -1;
            Games[GameId][2] = -1;
            Games[GameId][3] = -1;
            Games[GameId][4] = 0;

            if (GameId == MyGameId) {
              MyGameId = -1;
            }

            System.out.println("LEAVE GAME [" + GameId + "], game destroy");
          } else {
            for (int i = 0; i < 4; i++) {
              if (Games[GameId][i] == Aur1) {

                Games[GameId][i] = -1;
                System.out.println("LEAVE GAME [" + GameId + "], player=" + Aur1 + ",  [" + Games[GameId][0] + "," + Games[GameId][1] + "," + Games[GameId][2] + "," + Games[GameId][3] + "]");

                break;
              }
            }
          }
        } else if (MahjongProtocol.C_JOIN_GAME == cmd) {
          int GameId = Integer.parseInt(items[1]);
          int Aur1 = Integer.parseInt(items[2]);
          int pos = Integer.parseInt(items[3]);

          Games[GameId][pos] = Aur1;

          System.out.println("JOIN GAME [" + GameId + "], player=" + Aur1 + ", [" + Games[GameId][0] + "," + Games[GameId][1] + "," + Games[GameId][2] + "," + Games[GameId][3] + "]");

          if (Aur1 == MyId) {
            MyGameId = GameId;
          }
        } else if (MahjongProtocol.C_START_GAME == cmd) {
          int GameId = Integer.parseInt(items[1]);

          if (MyGameId == GameId) {
            System.out.println("GAME START [" + GameId + "], My Game");
          } else {
            System.out.println("GAME START [" + GameId + "]");
          }
        } else if (MahjongProtocol.C_GAME_INIT == cmd) {
          int round = Integer.parseInt(items[1]);
          int wind = Integer.parseInt(items[2]);
          int windCount = Integer.parseInt(items[3]);
          int total = Integer.parseInt(items[4]);
          int master = Integer.parseInt(items[5]);
          int masterCount = Integer.parseInt(items[6]);

          final String WindName[] = {"東","西","南","北"};

          System.out.println("第" + (round+1) + "圈" + WindName[wind] + "風" + WindName[windCount] + "局 開始遊戲:" + total);
          System.out.println("莊家:" + master + " 連莊:" + masterCount);

          for (int i = 7; i < items.length; i++) {
            System.out.print(MahjongGameTest.TransCard(Integer.parseInt(items[i])) + " ");
          }
          System.out.println("");
        } else if (MahjongProtocol.C_GAME_ENDS == cmd) {
          // game complete
        } else if (MahjongProtocol.C_GAME_ACTION == cmd) {
          int pos = Integer.parseInt(items[1]);
          int act = Integer.parseInt(items[2]);
          int c, c2, count;
          String s2 = "";

          switch (act)
          {
          case MahjongGame.AI_PASS:
            System.out.println("玩家" + pos + " - PASS");
            break;
          case MahjongGame.AI_EXCHANGE:
            c = Integer.parseInt(items[3]);
            System.out.println("玩家" + pos + "捨牌(" + c + "): " + MahjongGameTest.TransCard(c));
            break;
          case MahjongGame.AI_CHI:
            c = Integer.parseInt(items[3]);
            System.out.println("玩家" + pos + "吃牌(" + c + "): " + MahjongGameTest.TransCard(c));
            break;
          case MahjongGame.AI_PON:
            c = Integer.parseInt(items[3]);
            System.out.println("玩家" + pos + "碰牌(" + c + "): " + MahjongGameTest.TransCard(c));
            break;
          case MahjongGame.AI_GUN:
            c = Integer.parseInt(items[3]);
            c2 = Integer.parseInt(items[4]); // 海底補牌.
            System.out.println("玩家" + pos + "摃牌(" + c + "): " + MahjongGameTest.TransCard(c));
            break;
          case MahjongGame.AI_LON:
            c = Integer.parseInt(items[3]);
            System.out.println("玩家" + pos + "胡牌(" + c + "): " + MahjongGameTest.TransCard(c));
            for (int i = 4; i < items.length; i++) {
              System.out.println(MahjongScore.WO_TYPE[Integer.parseInt(items[i])]);
            }
            break;
          case MahjongGame.AI_PICK:
            c = Integer.parseInt(items[3]);
            if (c == 0) {
              System.out.println("玩家" + pos + "摸牌");
            } else {
              System.out.println("自己摸牌(" + c + "): " + MahjongGameTest.TransCard(c));
              // 從items[4]到最後一個item,格式同下一個case,MahjongGame.AI_STATE
              // 用來知道目前的狀態,主要是用來檢查可否暗摃或胡
            }
            break;
          case MahjongGame.AI_STATE:
            {
              int state = Integer.parseInt(items[3]);
              int idx = 4;
              if (0 != (state & MahjongGame.MJ_CANTIN)) {
                count = Integer.parseInt(items[idx]);
                idx++;
                for (int i = idx; i < idx + count; i++) {
                  c = Integer.parseInt(items[i]);
                  s2 = s2 + " " + MahjongGameTest.TransCard(c);
                }
                System.out.println("可以聽牌(" + s2 + ")");
                idx += count;
              }
              if (0 != (state & MahjongGame.MJ_CANGUN)) {
                count = Integer.parseInt(items[idx]);
                idx++;
                for (int i = idx; i < idx + count; i++) {
                  c = Integer.parseInt(items[i]);
                  s2 = s2 + " " + MahjongGameTest.TransCard(c);
                }
                System.out.println("可以摃(" + s2 + ")");
                idx += count;
              }
              if (0 != (state & MahjongGame.MJ_CANPON)) {
                count = Integer.parseInt(items[idx]);
                idx++;
                c = Integer.parseInt(items[idx]);
                System.out.println("可以碰(" + MahjongGameTest.TransCard(c) + ")");
                idx += count;
              }
              if (0 != (state & MahjongGame.MJ_CANCHI)) {
                count = Integer.parseInt(items[idx]);
                idx++;
                for (int i = idx; i < idx + count; i++) {
                  c = Integer.parseInt(items[i]);
                  s2 = s2 + " " + MahjongGameTest.TransCard(c);
                }
                idx += count;
                System.out.println("可以吃(" + s2 + ")");
              }
              if (0 != (state & MahjongGame.MJ_CANLON)) {
                System.out.println("可以胡");
              }
            }
            break;
          case MahjongGame.AI_END:
            System.out.println("流局~~~");
            break;
          case MahjongGame.AI_SHOWCARDS:
            {
              int idx = 3;
              for (int i = 0; i < 3; i++) {
                int who = Integer.parseInt(items[idx++]);
                count = Integer.parseInt(items[idx++]);
                System.out.print("玩家" + who + ":");
                for (int j = idx; j < idx + count; j++) {
                  c = Integer.parseInt(items[j]);
                  System.out.print(MahjongGameTest.TransCard(c));
                }
                idx += count;
                System.out.println();
              }
            }
            break;
          case MahjongGame.AI_TIN:
            c = Integer.parseInt(items[3]);
            System.out.println("玩家" + pos + "宣告聽牌,捨牌(" + c + "): " + MahjongGameTest.TransCard(c));
            break;
          case MahjongGame.AI_PATCH:
            System.out.print("玩家" + pos + "補花(");
            for (int i = 3; i < items.length;) {
              c = Integer.parseInt(items[i]);         // 花牌
              System.out.print(MahjongGameTest.TransCard(c));
              c2 = Integer.parseInt(items[i + 1]);    // 補牌(只有自己才知道補什麼,其它人只收到0)
              if (0 != c2) {
                System.out.print("->" + MahjongGameTest.TransCard(c2) + ",");
              } else {
                System.out.print(",");
              }
              i += 2;
            }
            System.out.println(")");
            break;
          }
        }
      }

      //
      // Test.
      //

      if (System.currentTimeMillis() >= timeNewGame) {

        //
        // Create new game or joing existing game if need.
        //

        if (-1 == MyGameId) {
          int JoinGameId = -1;
          for (int i = 0; i < Games.length; i++) {
            if (-1 != Games[i][0] && 0 == Games[i][4]) { // Try to join a game that is not playing.
              for (int j = 1; j < Games[i].length; j++) {
                if (-1 == Games[i][j]) {
                  JoinGameId = i;
                  break;
                }
              }
            }
            if (-1 != JoinGameId) {
              break;
            }
          }

          if (-1 == JoinGameId) {
            MahjongProtocol.tcpipSend(os, Integer.toString(MahjongProtocol.C_NEW_GAME));
            GameStart = false;
          } else {
            MahjongProtocol.tcpipSend(os, Integer.toString(MahjongProtocol.C_JOIN_GAME) + "," + JoinGameId);
            JoinGameId = -1;
            GameStart = false;
          }
        }

        //
        // Enable auto play if in game.
        //

        if (-1 != MyGameId && !IsAutoPlay) {
          MahjongProtocol.tcpipSend(os, MahjongProtocol.getEnableAutoPlayCmd(true));
          IsAutoPlay = true;
        }

        //
        // Creater starts game if need.
        //

        if (-1 != MyGameId && MyId == Games[MyGameId][0] && !GameStart) {
          int n = 0;
          for (int i = 0; i < Games[MyGameId].length; i++) {
            if (-1 != Games[MyGameId][i]) {
              n++;
            }
          }
          if (Games[MyGameId].length == n) {
            GameStart = true;
            MahjongProtocol.tcpipSend(os, Integer.toString(MahjongProtocol.C_START_GAME));
          }
        }

        timeNewGame = System.currentTimeMillis() + 1000 * 5;
      }

      //
      // Send keep alive.
      //

      if (System.currentTimeMillis() >= timeKeepAlive) {
        timeKeepAlive = System.currentTimeMillis() + 1000 * (MahjongServer.TIMEOUT_KEEP_ALIVE - 1);
        MahjongProtocol.tcpipSend(os, Integer.toString(MahjongProtocol.C_KEEP_ALIVE));
      }
    }

    //
    // Uninit.
    //

    is.close();
    os.close();
    s.close();
  }
} // MahjongClientTest

// MahjongClientTest.java
