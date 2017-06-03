
//
// MahjongGameTest.java, 麻將GamePlay自動測試
// 2010/11/19 Waync Cheng
//

import java.util.Vector;

public class MahjongGameTest {
  static String Numbers[] = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
  static String Winds[] = {"東風", "南風", "西風", "北風", "紅中", "青發", "白皮"};
  static String Flowers[] = {"春", "夏", "秋", "冬", "梅", "蘭", "竹", "菊"};

  static String TransCard(int card) {

    if (0 == card) {
      return "0";
    }

    int color = MahjongGame.MJCOLOR(card);
    int num = MahjongGame.MJNUM(card);

    String s = "";
    switch (color)
    {
    case MahjongGame.COLOR_MAN:
      s = Numbers[num - 1] + "萬";
      break;
    case MahjongGame.COLOR_TON:
      s = Numbers[num - 1] + "筒";
      break;
    case MahjongGame.COLOR_SO:
      s = Numbers[num - 1] + "索";
      break;
    case MahjongGame.COLOR_FON:
      s = Winds[num - 1];
      break;
    case MahjongGame.COLOR_FLOWER:
      s = Flowers[num - 1];
      break;
    }

    if (0 != (card & MahjongGame.MJF_GUN)) {
      return s + "x4";
    }

    if (0 != (card & MahjongGame.MJF_PON)) {
      return s + "x3";
    }

    if (0 != (card & MahjongGame.MJF_CHI)) {
      return s + "..";
    }

    return s;
  }

  public static void main(String args[]) throws Exception
  {
    final String wind[] = {"東","西","南","北"};
    int windCount = 0;
    int count = 0;
    boolean open = true;

    MahjongGame mj = new MahjongGame();
    MahjongScore mahjongScore = new  MahjongScore();

    //
    // 跑四個風局.
    //

    while (true) {

      if (open) {

        mj.newGame();

        //
        // 補花.
        //

        int active = mj.getActive();
        for (int i = 0; i < 4; i++) {
          int iPlayer = (active - i + MahjongGame.NUM_PLAYER) % MahjongGame.NUM_PLAYER;
          while (true) {
            Vector p1 = mj.patchFlower(iPlayer);
            if (null == p1) {
              break;
            }
          }
        }

        //
        //北風北.
        //

        if (mj.mjOpen.wind == 4) {

          mj.mjOpen.wind = 0;

          //
          //跑四個風局.
          //

          if (++windCount >= 4) {
            return;
          }
        }

        System.out.println("\n***************************************************************************");
        System.out.println("\n"+"第"+(windCount+1)+"圈 "+wind[mj.mjOpen.wind]+"風"+wind[mj.mjOpen.gameCount%4]+"局"+" 開始遊戲:"+count++);
        System.out.print("\n"+"莊家："+mj.mjOpen.master+" 連莊:"+mj.mjOpen.masterCount+"\n");

        open = false;
      }

      //
      // 是否流局.
      //

      if (mj.isGameOver()) {
        mj.mjOpen.winner = -1;
        System.out.println("~~~流局~~~");
        open = true;
      } else {
        int active = mj.getActive();
        int aiRet = mj.ai();

        //
        // 摸牌補花.
        //

        if (null != mj.patchFlower(mj.getActive())) {
          continue;
        }

        //
        // AI.
        //

        switch (aiRet)
        {
        case MahjongGame.AI_LON:

          if (0 != mj.getPick()) {
            System.out.println("玩家" + (active) + ": 自摸!!! " + TransCard(mj.getPick()));
          } else {
            System.out.println("玩家" +  (active) + ": 胡!!! "+TransCard(mj.getLastHdCard()));
          }

          for (int j = 0; j < 4; j++) {
            Vector v = mj.getTinPai(j);
            String s = "";
            for (int i = 0; i < v.size(); i++) {
              s += TransCard((Integer)v.elementAt(i)) + ",";
            }
            System.out.println("聽" + j + " [" + s + "]");
          }

          //
          //  胡台測試.
          //

          //
          // 紀錄贏家編號.
          //

          mj.mjOpen.winner = active;

          Vector score = mahjongScore.score(mj, active);
          mahjongScore.print();

          open = true;
          break;
        }
      }
    }
  }
}
