
//
// MahjongGame.java, 麻將GamePlay
// 2010/11/10 Waync Cheng
//

import java.lang.Integer;
import java.lang.Math;
import java.util.Random;
import java.util.Vector;

public class MahjongGame {

  //
  // Constants
  //

  public static final int NUM_PLAYER = 4;     // 玩家數目.

  public static final int AI_FAIL = 0;        // 錯誤(順位)
  public static final int AI_PASS = 1;        // pass
  public static final int AI_EXCHANGE = 2;    // 丟牌
  public static final int AI_CHI = 3;         // 吃
  public static final int AI_PON = 4;         // 碰
  public static final int AI_GUN = 5;         // 摃
  public static final int AI_LON = 6;         // 胡
  public static final int AI_PICK = 7;        // 摸牌
  public static final int AI_STATE = 8;       // 狀態(可吃,可碰等)
  public static final int AI_END = 9;         // 流局
  public static final int AI_SHOWCARDS = 10;  // 亮牌
  public static final int AI_TIN = 11;        // 宣告聽牌(不能再換牌)
  public static final int AI_PATCH = 12;      // 補花

  public static final int COLOR_MAN = 0;      // 萬
  public static final int COLOR_TON = 1;      // 筒
  public static final int COLOR_SO = 2;       // 索
  public static final int COLOR_FON = 3;      // 風
  public static final int COLOR_FLOWER = 4;   // 花

  public static final int MJ_1 = 1;
  public static final int MJ_2 = 2;
  public static final int MJ_3 = 3;
  public static final int MJ_4 = 4;
  public static final int MJ_5 = 5;
  public static final int MJ_6 = 6;
  public static final int MJ_7 = 7;
  public static final int MJ_8 = 8;
  public static final int MJ_9 = 9;
  public static final int MJ_EAST = 1;        // 東
  public static final int MJ_SOUTH = 2;       // 南
  public static final int MJ_WEST = 3;        // 西
  public static final int MJ_NORTH = 4;       // 北
  public static final int MJ_RED = 5;         // 中
  public static final int MJ_GREEN = 6;       // 發
  public static final int MJ_WHITE = 7;       // 白
  public static final int MJ_SPRING = 1;      // 春
  public static final int MJ_SUMMER = 2;      // 夏
  public static final int MJ_FALL = 3;        // 秋
  public static final int MJ_WINTER = 4;      // 冬
  public static final int MJ_MAY = 5;         // 梅
  public static final int MJ_LAN = 6;         // 蘭
  public static final int MJ_G = 7;           // 菊
  public static final int MJ_ZU = 8;          // 竹

  public static final int MJ_INITSTATE = 0;   // 啟始狀態
  public static final int MJ_ISTIN = 1 << 8;  // 已宣告聽牌(不能換牌)
  public static final int MJ_CANCHI = 1 << 6; // 可吃牌
  public static final int MJ_CANPON = 1 << 5; // 可碰牌
  public static final int MJ_CANGUN = 1 << 4; // 可摃牌
  public static final int MJ_CANLON = 1 << 3; // 可胡(或自摸)
  public static final int MJ_CANTIN = 1 << 2; // 可宣告聽

  public static final int MJF_ANGUN = 0x1000; // 暗摃(不公開)
  public static final int MJF_GUN = 0x2000;   // 摃(公開)
  public static final int MJF_PON = 0x4000;   // 碰(公開)
  public static final int MJF_CHI = 0x8000;   // 吃(公開)

  //
  // Member data
  //

  int active;                           // 目前順位
  int turn;                             // 內部順位

  Vector player[];                      // 玩家手上未開的牌
  Vector opPlayer[];                    // 玩家手上公開的牌

  int state[];                          // 玩家目前的狀態旗標: 有無聽牌,可吃,可碰...

  Vector tin[];                         // 列出玩家可胡的牌
  Vector tinDiscard[];                  // 列出捨牌(丟棄)後即成為聽牌狀態的牌
  Vector chi;                           // 若可以吃牌,底下列出可吃的啟始牌(共用)
  Vector gun[];                         // 若可以摃牌,底下列出可摃的牌

  Vector cards;                         // 摸牌
  Vector hdCards;                       // 海底

  int pick;                             // 摸牌, 對應 active player(turn)

  int bitFlag[];                        // 輔助計算排序組合
  Vector _c, _s;                        // 牌型檢查, 未開牌分解後的刻子(c)和順子(s)

  int aiCard;                           // AI動作對應的牌.

  Random rand;

  MahjongOpen mjOpen;

  //
  // Constructor.
  //

  public MahjongGame() {
    cards = new Vector();
    hdCards = new Vector();
    player = new Vector[NUM_PLAYER];
    opPlayer = new Vector[NUM_PLAYER];
    tin = new Vector[NUM_PLAYER];
    tinDiscard = new Vector[NUM_PLAYER];
    chi = new Vector();
    gun = new Vector[NUM_PLAYER];
    for (int i = 0; i < NUM_PLAYER; i++) {
      player[i] = new Vector();
      opPlayer[i] = new Vector();
      tin[i] = new Vector();
      tinDiscard[i] = new Vector();
      gun[i] = new Vector();
    }
    state = new int[NUM_PLAYER];

    rand = new Random();
    rand.setSeed(System.currentTimeMillis());

    bitFlag = new int[32];
    for (int i = 0; i < 32; i++) {
      bitFlag[i] = 1 << i;
    }

    _c = new Vector();
    _s = new Vector();

    mjOpen = new MahjongOpen();         // 宣告開局物件
  }

  //
  // 開始新遊戲. 重設所有狀態.
  //

  public void newGame() {

    //
    // 設定
    //

    turn = mjOpen.getMaster();

    for (int i = 0; i < NUM_PLAYER; i++) {
      state[i] = MJ_INITSTATE;
    }

    //
    // 產生及洗牌
    //

    makeupCards();

    hdCards.clear();

    //
    // 重設並發牌
    //

    for (int i = 0; i < NUM_PLAYER; i++) {
      player[i].clear();
      opPlayer[i].clear();
      tin[i].clear();
      tinDiscard[i].clear();
    }

    for (int i = 0; i < 16; i++) {
      for (int j = 0; j < NUM_PLAYER; j++) {
        player[j].addElement(new Integer(pickFront()));
      }
    }

    //
    // 排序各家牌
    //

    for (int i = 0; i < NUM_PLAYER; i++) {
      sort(player[i]);
    }

    //
    // 摸第一張牌(莊家)
    //

    pick = pickFront();

    //
    // 更新狀態旗標
    //

    checkState();
  }

  //
  // 是否流局?
  // 回傳
  //      true    表示遊戲流局
  //      false   表示遊戲繼續中
  //

  public boolean isGameOver() {
    return 16 >= cards.size();
  }

  //
  // 摸牌
  // 回傳
  //      0       表示現在有人要吃或碰, 也就是不能摸牌
  //      非0    摸到的牌
  //

  public int getPick() {
    return pick;
  }

  //
  // 換牌或捨牌
  // 參數
  //      card    自己手上的牌或pickCard摸到的牌
  // 回傳
  //      true    成功
  //      false   失敗
  // 註釋
  //      如果拿pickCard摸到的牌作交換,則表示捨牌
  //

  public boolean exchange(int card) {

    //
    // 不要摸到的牌,直接捨牌
    //

    if (card == pick) {
      discard(card);
      return true;
    }

    //
    // 已經聽牌了, 就不能換牌
    //

    if (0 != (state[active] & MJ_ISTIN)) {
      return false;
    }

    //
    // 手上有這張牌?
    //

    Vector p = player[turn];

    int found = find(p, card);
    if (-1 == found) {
      return false;
    }

    //
    // 交換並且重新排序
    //

    p.setElementAt(new Integer(pick), found);

    sort(p);

    //
    // 重新檢查是否聽牌
    //

    checkTin(turn);

    //
    // 捨牌(和已摸到的牌交換)
    //

    discard(card);

    return true;
  }

  //
  // 換牌或捨牌,同時宣告聽牌
  // 參數
  //      card    自己手上的牌或pickCard摸到的牌
  // 回傳
  //      true    成功
  //      false   失敗
  // 註釋
  //      同exchange,差別是如果成功即狀態切換為聽牌狀態
  //

  public boolean tin(int card) {

    //
    // 是否已經是聽牌狀態?
    //

    if (0 != (state[active] & MJ_ISTIN)) {
      return false;
    }

    //
    // 丟棄這張牌就能聽牌?
    //

    if (-1 == find(tinDiscard[active], card)) {
      return false;
    }

    //
    // 一般捨牌操作
    //

    if (!exchange(card)) {
      return false;
    }

    //
    // 成功宣告聽牌
    //

    state[active] |= MJ_ISTIN;

    return true;
  }

  //
  // 放棄吃碰摃等
  // 回傳
  //      true    成功
  //      false   失敗
  //

  public boolean pass() {

    //
    // 順位正確?
    //

    if (0 != pick && active == turn) {
      return false;
    }

    //
    // 清除旗標,只保留聽牌的旗標
    //

    if (0 != (state[active] & MJ_ISTIN)) {
      state[active] = MJ_ISTIN;
    } else {
      state[active] = 0;
    }

    //
    // 若所有人都已PASS,則再摸下一張牌
    //

    for (int i = 0; i < NUM_PLAYER; i++) {
      if (0 != state[i] && MJ_ISTIN != state[i]) {

        //
        // 還有人沒PASS, 再一次設定順位
        //

        setTurn();

        return true;
      }
    }

    //
    // 流局?
    //

    if (isGameOver()) {
      return true;
    }

    //
    // 自動模下一張牌
    //

    pick = pickFront();

    //
    // 下一個玩家
    //

    turn = (turn + 1) % NUM_PLAYER;

    //
    // 重新檢查狀態
    //

    checkState();

    return true;
  }

  //
  // 吃牌
  // 參數
  //      card    從那一張吃起
  // 回傳
  //      true    成功
  //      false   失敗
  // 註釋
  //      如有2,3,4萬3種吃法, 吃2萬表示吃234萬, 由chiPai()取得
  //

  public boolean chi(int card) {

    //
    // 是否可以吃牌
    //

    if (MJ_CANCHI != (state[active] & MJ_CANCHI)) {
      return false;
    }

    if (-1 == find(chi, card)) {
      return false;
    }

    //
    // 由海底取回要吃的牌
    //

    int c = getLastHdCard();

    hdCards.removeElementAt(hdCards.size() - 1);

    //
    // 從自己手上拿掉2張(其中有一個會失敗,忽略)
    //

    Vector p = player[active];

    try {

      if (c != card) {
        p.removeElementAt(find(p, card));
      }

      if (c != card + 1) {
        p.removeElementAt(find(p, card + 1));
      }

      if (c != card + 2) {
        p.removeElementAt(find(p, card + 2));
      }

    } catch (ArrayIndexOutOfBoundsException e) {
    }

    //
    // 吃
    //

    Vector op = opPlayer[active];

    op.addElement(new Integer(card | MJF_CHI));
    sort(op);

    //
    // 排序後取出最後一張作為摸牌
    //

    sort(p);

    pick = ((Integer)p.elementAt(p.size() - 1)).intValue();
    p.removeElementAt(p.size() - 1);

    //
    // 重新檢查是否聽牌
    //

    checkTin(active);

    //
    // 重設旗標
    //

    turn = active;

    checkState();

    return true;
  }

  //
  // 碰牌
  // 回傳
  //      true    成功
  //      false   失敗
  //

  public boolean pon() {

    //
    // 是否可以碰牌
    //

    if (MJ_CANPON != (state[active] & MJ_CANPON)) {
      return false;
    }

    //
    // 由海底取回要碰的牌
    //

    int pon = getLastHdCard();

    hdCards.removeElementAt(hdCards.size() - 1);

    //
    // 從自己手上拿掉2張
    //

    Vector p = player[active];

    p.removeElementAt(find(p, pon));
    p.removeElementAt(find(p, pon));

    //
    // 碰
    //

    Vector op = opPlayer[active];

    op.addElement(new Integer(pon | MJF_PON));
    sort(op);

    //
    // 排序後取出最後一張作為摸牌
    //

    sort(p);

    pick = ((Integer)p.elementAt(p.size() - 1)).intValue();
    p.removeElementAt(p.size() - 1);

    //
    // 重新檢查是否聽牌
    //

    checkTin(active);

    //
    // 重設旗標
    //

    turn = active;

    checkState();

    return true;
  }

  //
  // 摃牌
  // 參數
  //      card    摃那一張牌
  // 回傳
  //      true    成功
  //      false   失敗
  // 註釋
  //      card 由gunPai() 取得, 可以摃超過一張牌,包含海底或暗摃
  //

  public boolean gun(int card) {

    //
    // 是否可以摃牌
    //

    if (MJ_CANGUN != (state[active] & MJ_CANGUN)) {
      return false;
    }

    if (-1 == find(gun[active], card)) {
      return false;
    }

    Vector p = player[active];

    //
    // 自己模自己摃
    //

    if (turn == active) {

      //
      // 暗摃,本身已有四張
      //

      for (int i = 0; i < p.size(); i++) {

        int c = ((Integer)p.elementAt(i)).intValue();
        if (4 == count(p, c)) {

          //
          // 從自己手上拿掉4張
          //

          for (int j = 0; j < 4; j++) {
            p.removeElementAt(find(p, c));
          }

          //
          // 暗摃
          //

          Vector op = opPlayer[active];
          op.addElement(new Integer(card | MJF_GUN));
          sort(op);

          //
          // 摸牌直接取得, 再加摸一張
          //

          p.addElement(new Integer(pick));
          sort(p);

          //
          // 從最後摸一張牌, 重新檢查是否聽牌及重設旗標
          //

          pick = pickBack();
          checkTin(active);
          turn = active;
          checkState();

          return true;
        }
      }

      //
      // 暗摃
      //

      if (3 == count(p, pick)) {

        //
        // 從自己手上拿掉3張
        //

        for (int i = 0; i < 4; i++) {
          p.removeElementAt(find(p, pick));
        }

        //
        // 暗摃
        //

        Vector op = opPlayer[active];
        op.addElement(new Integer(pick | MJF_GUN));
        sort(op);

      } else {

        //
        // 加摃
        //

        Vector op = opPlayer[active];
        op.setElementAt(new Integer(MJF_GUN | pick), find(op, MJF_PON | pick));

        sort(op);
      }
    } else {

      //
      // 摃別人丟的牌, 由海底取回要摃的牌.
      //

      int gun = getLastHdCard();;

      hdCards.removeElementAt(hdCards.size() - 1);

      //
      // 從自己手上拿掉3張
      //

      for (int i = 0; i < 3; i++) {
        p.removeElementAt(find(p, gun));
      }

      //
      // 明摃
      //

      Vector op = opPlayer[active];
      op.addElement(new Integer(gun | MJF_GUN));

      sort(op);
    }

    //
    // 從最後摸一張牌
    //

    pick = pickBack();

    //
    // 重新檢查是否聽牌
    //

    checkTin(active);

    //
    // 重設旗標
    //

    turn = active;

    checkState();

    return true;
  }

  //
  // 自摸或胡牌
  // 回傳
  //      true    成功
  //      false   失敗
  //

  public boolean lon() {

    //
    // 是否可以胡牌
    //

    if (MJ_CANLON != (state[active] & MJ_CANLON)) {
      return false;
    }

    //
    // 自模?
    //

    Vector p = tin[active];

    if (0 != pick) {
      if (-1 != find(p, pick)) {
        return true;
      }
    } else {

      //
      // 胡別人捨牌
      //

      int card = getLastHdCard();       // 取得最後的捨牌.
      if (-1 != find(p, card)) {
        return true;
      }
    }

    return false;
  }

  //
  // 補花
  // 參數
  //      index 那個玩家
  // 回傳
  //      回傳花牌及補牌(pair), 一次只補一張花牌. 若沒有花牌可補則回傳null.
  //

  public Vector patchFlower(int index) {

    //
    // 如果已流局,則不再補牌
    //

    if (isGameOver()) {
      return null;
    }

    //
    // 摸牌是否花牌?
    //

    if (0 != pick && COLOR_FLOWER == MJCOLOR(pick)) {

      int newPick = pickBack();

      Vector patch = new Vector();
      patch.add(new Integer(pick));
      patch.add(new Integer(newPick));

      Vector op = opPlayer[index];
      op.addElement(new Integer(pick));
      sort(op);

      pick = newPick;

      return patch;
    }

    //
    // 找找看手上的牌是否有花牌
    //

    Vector p = player[index];

    for (int i = 0; i < p.size(); i++) {

      int card = ((Integer)p.elementAt(i)).intValue();

      if (COLOR_FLOWER == MJCOLOR(card)) {

        //
        // 補一張牌
        //

        int newPick = pickBack();

        p.removeElementAt(i);
        p.add(new Integer(newPick));
        sort(p);

        Vector op = opPlayer[index];
        op.addElement(new Integer(card));
        sort(op);

        Vector patch = new Vector();
        patch.add(new Integer(card));
        patch.add(new Integer(newPick));

        return patch;
      }
    }

    return null;
  }

  //
  // 電腦接手
  // 回傳
  //      回傳動作代號, AI_XXX
  //

  public int ai() {

    //
    // 0. 胡牌
    //

    if (0 != (state[active] & MJ_CANLON)) { // 胡
      lon();
      return AI_LON;
    }

    //
    // 手上的牌, 分組
    //

    Vector p = player[active];

    Vector c[] = new Vector[4];
    for (int i = 0; i < c.length; i++) {
      c[i] = new Vector();
    }

    try {
      for (int i = 0; i < p.size(); i++) {
        int A = ((Integer)p.elementAt(i)).intValue();
        c[MJCOLOR(A)].addElement(new Integer(MJNUM(A)));
      }
    } catch (Exception e) {
      for (int i = 0; i < p.size(); i++) {
        System.out.print(MahjongGameTest.TransCard(((Integer)p.elementAt(i)).intValue()) + " ");
      }
      System.out.println("");
      e.printStackTrace();
      c[5] = c[1];
    }

    //
    // A. 摸牌, 丟牌 AI
    //

    if (0 != pick) {

      //
      // a. 如果已聽牌,直接捨牌
      //

      if (0 != tin[active].size()) {
        aiCard = pick;
        exchange(aiCard);
        return AI_EXCHANGE;
      }

      //
      // b. 單張字牌
      //

      if (COLOR_FON == MJCOLOR(pick) && 0 == count(c[COLOR_FON], MJNUM(pick))) {
        aiCard = pick;
        exchange(aiCard);
        return AI_EXCHANGE;
      }

      for (int i = 0; i < c[COLOR_FON].size(); i++) {
        int num = ((Integer)c[COLOR_FON].elementAt(i)).intValue();
        if (1 == count(c[COLOR_FON], num) && num != MJNUM(pick)) {
          aiCard = MJCARD(COLOR_FON, num);
          exchange(aiCard);
          return AI_EXCHANGE;
        }
      }

      //
      // c. 單張廢牌: 沒有對子, 且沒有可以湊成順子的牌(+-2間)
      //

      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < c[i].size(); j++) {
          int num = ((Integer)c[i].elementAt(j)).intValue();
          if (1 == count(c[i], num) &&
              -1 == find(c[i], num + 1) &&
              -1 == find(c[i], num + 2) &&
              -1 == find(c[i], num - 1) &&
              -1 == find(c[i], num - 2)) {
            aiCard = MJCARD(i, num);
            exchange(aiCard);
            return AI_EXCHANGE;
          }
        }
      }

      //
      // d. 拆不連續的順
      //

      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < c[i].size(); j++) {
          int num = ((Integer)c[i].elementAt(j)).intValue();
          if (1 == count(c[i], num) &&
              -1 == find(c[i], num + 1) &&
              -1 == find(c[i], num - 1)) {
            aiCard = MJCARD(i, num);
            exchange(aiCard);
            return AI_EXCHANGE;
          }
        }
      }

      //
      // . 加摃
      //

      //
      // . 即摸即丟
      //

      aiCard = pick;
      exchange(aiCard);

      return AI_EXCHANGE;
    }

    //
    // B. 吃碰摃 AI
    //

    if (0 != (state[active] & MJ_CANCHI)) { // 吃
      aiCard = ((Integer)chi.elementAt(0)).intValue();
      chi(aiCard);
      return AI_CHI;
    }

    if (0 != (state[active] & MJ_CANPON)) { // 碰

      int hd = getLastHdCard();
      int color = MJCOLOR(hd);
      int num = MJNUM(hd);

      //
      // a. 風牌
      //

      if (COLOR_FON == color && 2 == count(c[COLOR_FON], num)) {
        aiCard = hd;
        pon();
        return AI_PON;
      }

      //
      // b. 沒有連順
      //

      if (2 == count(c[color], num) &&
          -1 == find(c[color], num - 1) &&
          -1 == find(c[color], num + 1)) {
        aiCard = hd;
        pon();
        return AI_PON;
      }
    }

    if (0 != (state[active] & MJ_CANGUN)) { // 摃

      int hd = getLastHdCard();
      int color = MJCOLOR(hd);
      int num = MJNUM(hd);

      //
      // a. 摃別家的牌, 不是風牌且沒有連順
      //

      if (COLOR_FON != color &&
          -1 == find (c[color], num + 1) &&
          -1 == find (c[color], num + 2) &&
          -1 == find (c[color], num - 1) &&
          -1 == find (c[color], num - 2)) {
        aiCard = hd;
        gun(aiCard);
        return AI_GUN;
      }
    }

    //
    // PASS
    //

    pass();

    return AI_PASS;
  }

  //
  // 取得目前順位(0~3)
  //

  public int getActive() {
    return active;
  }

  public int getTurn() {
    return turn;
  }

  //
  // 取得指定順位的狀態
  //

  public int getState(int index) {
    return state[index];
  }

  //
  // 取得指定順位的未公開牌
  //

  public Vector getPlayer(int index) {
    return player[index];
  }

  //
  // 取得指定順位的公開牌
  //

  public Vector getOpPlayer(int index) {
    return opPlayer[index];
  }

  //
  // 取得指定順位的聽牌
  //

  public Vector getTinPai(int index) {
    return tin[index];
  }

  //
  // 取得指定順位的捨牌後聽牌列表
  //

  public Vector getTinDiscard(int index) {
    return tinDiscard[index];
  }

  //
  // 取得目前可吃牌
  //

  public Vector getChiPai() {
    return chi;
  }

  //
  // 取得目前可摃牌
  //

  public Vector getGunPai() {
    return gun[active];
  }

  //
  // 取得在海底的牌
  //

  public Vector getHdCards() {
    return hdCards;
  }

  //
  // Private interface.
  //

  //
  // 是否可吃
  //

  boolean canChi(int index, int card) {

    //
    // 重設
    //

    chi.clear();

    //
    // 不是風牌也不是花牌
    //

    if (COLOR_FON == MJCOLOR(card) || COLOR_FLOWER == MJCOLOR(card)) {
      return false;
    }

    //
    // 取出需要檢查的牌(同類)
    //

    Vector p = player[index];

    if (2 > p.size()) {
      return false;
    }

    //
    // 撿查是否符合條件
    //

    if (-1 != find(p, card - 2) && -1 != find(p, card - 1)) {
      chi.addElement(new Integer(card - 2));
    }

    if (-1 != find(p, card - 1) && -1 != find(p, card + 1)) {
      chi.addElement(new Integer(card - 1));
    }

    if (-1 != find(p, card + 1) && -1 != find(p, card + 2)) {
      chi.addElement(new Integer(card));
    }

    return 0 != chi.size();
  }

  //
  // 是否可摃
  //

  boolean canGun(int index, int card) {

    //
    // 重設
    //

    gun[index].clear();

    //
    // 不是花牌
    //

    if (COLOR_FLOWER == MJCOLOR(card)) {
      return false;
    }

    //
    // 暗摃,加摃
    //

    Vector p = player[index];

    if (0 != pick) {

      //
      // 暗摃? 本身已有四張
      //

      for (int i = 0; i < p.size(); i++) {
        int c = ((Integer)p.elementAt(i)).intValue();
        if (4 == count(p, c)) {
          gun[index].addElement(new Integer(c));
          i += 3;                       // Should clarify this.
        }
      }

      //
      // 暗摃? 本身已有3張再加上摸牌
      //

      if (3 == count(p, pick)) {
        gun[index].addElement(new Integer(pick));
      }

      //
      // 加摃?
      //

      Vector op = opPlayer[index];

      if (-1 != find(op, MJF_PON | card)) {
        gun[index].addElement(new Integer(card));
      }

    } else {

      //
      // 摃別人的牌
      //

      if (3 == count (p, card)) {
        gun[index].addElement(new Integer(card));
      }
    }

    return 0 != gun[index].size();
  }

  //
  // 是否可胡牌
  //

  boolean canLon(int index, int card) {
    return -1 != find(tin[index], card);
  }

  //
  // 是否可碰
  //

  boolean canPon(int index, int card) {
    return 2 <= count(player[index], card);
  }

  //
  // 重設旗標
  //

  void checkState() {

    //
    // 重設,只保留是否已聽牌狀態
    //

    for (int i = 0; i < NUM_PLAYER; i++) {
      if (0 != (state[i] & MJ_ISTIN)) {
        state[i] = MJ_ISTIN;
      } else {
        state[i] = MJ_INITSTATE;
      }
    }

    //
    // 摸一張牌的狀況
    //

    if (0 != pick) {

      //
      // 是否可以暗摃,加摃
      //

      if (canGun(turn, pick)) {
        state[turn] |= MJ_CANGUN;
      }

      //
      // 是否可以自摸
      //

      if (canLon(turn, pick)) {
        state[turn] |= MJ_CANLON;
      }

      //
      // 是否可以宣告聽牌
      //

      for (int i = 0; i < NUM_PLAYER; i++) {

        //
        // 還未宣告聽
        //

        if (0 == (state[i] & MJ_ISTIN)) {

          //
          // 根據目前手上及摸的牌,找出所有捨牌後即可聽牌的列表
          //

          checkTinDiscard(i);

          //
          // 找到至少一張捨棄後即可聽的牌,則將狀態更新為可宣告聽
          //

          if (0 != tinDiscard[i].size()) {
            state[i] |= MJ_CANTIN;
          }
        }
      }

    } else {

      //
      // 吃碰別人捨牌的狀況
      //

      int card = getLastHdCard();       // 取得最後的捨牌
      for (int i = 1; i < NUM_PLAYER; i++) {

        int iPlayer = (turn + i) % NUM_PLAYER;

        //
        // 是否可吃牌(只允許下一家)
        //

        if (iPlayer == ((turn + NUM_PLAYER + 1) % NUM_PLAYER) && canChi(iPlayer, card)) {
          state[iPlayer] |= MJ_CANCHI;
        }

        //
        // 是否可碰牌
        //

        if (canPon(iPlayer, card)) {
          state[iPlayer] |= MJ_CANPON;
        }

        //
        // 是否可摃牌
        //

        if (canGun(iPlayer, card)) {
          state[iPlayer] |= MJ_CANGUN;
        }

        //
        // 是否可胡牌
        //

        if (canLon(iPlayer, card)) {
          state[iPlayer] |= MJ_CANLON;
        }
      }
    }

    //
    // 設定順位
    //

    setTurn();
  }

  //
  // 找出所有捨牌後即可聽牌的列表
  //

  void checkTinDiscard(int index) {

    Vector t = tinDiscard[index];
    t.clear();

    Vector p = (Vector)player[index].clone();

    Vector v = new Vector();

    //
    // 測試捨棄摸牌
    //

    checkTin(p, v);
    if (0 != v.size()) {
      t.addElement(new Integer(pick));
    }

    //
    // 測試捨棄手上各別牌
    //

    int temp, pick = getPick();
    for (int i = 0; i < p.size(); i++) {

      v.clear();

      temp = pick;
      pick = ((Integer)p.elementAt(i)).intValue();
      p.setElementAt(new Integer(temp), i);

      checkTin(p, v);
      if (0 != v.size()) {
        t.addElement(new Integer(pick));
      }
    }
  }

  //
  // 是否聽牌
  //

  void checkTin(int index) {

    //
    // 已經宣告聽牌就不再檢查(不能換牌)
    //

    if (0 != (state[index] & MJ_ISTIN)) {
      return;
    }

    //
    // 檢查所有可能的牌看是否有聽牌
    //

    Vector p = player[index];
    Vector t = tin[index];
    t.clear();

    checkTin(p, t);
  }

  void checkTin(Vector p, Vector t) {
    for (int i = 0; i < 9; i++) {       // 萬
      if (checkTin0(p, MJCARD(COLOR_MAN, i + 1))) {
        t.addElement(new Integer(MJCARD(COLOR_MAN, i + 1)));
      }
    }

    for (int i = 0; i < 9; i++) {       // 筒
      if (checkTin0(p, MJCARD(COLOR_TON,i + 1))) {
        t.addElement(new Integer(MJCARD(COLOR_TON, i + 1)));
      }
    }

    for (int i = 0; i < 9; i++) {       // 索
      if (checkTin0(p, MJCARD(COLOR_SO,i + 1))) {
        t.addElement(new Integer(MJCARD(COLOR_SO, i + 1)));
      }
    }

    for (int i = 0; i < 7; i++) {       // 風
      if (checkTin0(p, MJCARD(COLOR_FON,i + 1))) {
        t.addElement(new Integer(MJCARD(COLOR_FON, i + 1)));
      }
    }
  }

  //
  // 是否聽牌0
  //

  boolean checkTin0(Vector p, int card) {

    //
    // 特殊牌型(七搶一等)
    //

    if (checkTinSpecial(p, card)) {
      return true;
    }

    //
    // 一般牌型
    //

    return checkTinNormal(p, card);
  }

  //
  // 是否聽牌1
  //

  boolean checkTin1(Vector cards, boolean check123, boolean analysis) {

    //
    // 剩下0,1,2張的狀況
    //

    if (2 >= cards.size()) {

      if (2 == cards.size() &&
        ((Integer)cards.elementAt(0)).intValue() == ((Integer)cards.elementAt(1)).intValue()) {
        return true;
      }

      if (0 == cards.size()) {
        return true;
      }

      return false;
    }

    //
    // 檢查所有組合
    //

    Vector comb = new Vector();         // 記錄已檢查過的組合

    for (int i = 0; i < Math.pow(2, cards.size()); i++) {

      //
      // 只取2或3的組合
      //

      int bc = bitCount(i);
      if (2 != bc && 3 != bc) {
        continue;
      }

      //
      // 取得組合
      //

      int s0 = 0, s[] = new int[3], idx[] = new int[3];
      for (int j = 0; j < 32; j++) {
        if (0 != (i & bitFlag[j])) {
          idx[s0] = j;
          s[s0++] = ((Integer)cards.elementAt(j)).intValue();
          if (s0 == bc) {
            break;
          }
        }
      }

      //
      // 檢查是否為刻子
      //

      boolean br1 = false;
      for (int a = s[0], j = 0; j < s0; j++) {
        if (a != s[j]) {
          br1 = true;
          break;
        }
      }

      //
      // 檢查是否為順子
      //

      boolean br2 = false;
      if (!check123 || 3 != bc) {
        br2 = true;
      } else {
        for (int j = 0; j < s0 - 1; j++) {
          if (s[j] != s[j + 1] - 1) {
            br2 = true;
            break;
          }
        }
      }

      //
      // 若不是最後一對,也不是刻子或順子,則略過
      //

      if ((2 < cards.size() && 2 == s0) || (2 == s0 && br1) || (br1 && br2)) {
        continue;
      }

      //
      // 略過已檢查過的組合
      //

      int cc = 0;
      for (int j = 0; j < s0; j++) {
        cc = (cc << 8) | s[j];
      }

      if (-1 != find(comb, cc)) {
        continue;
      }

      //
      // 記錄已檢查過的組合
      //

      comb.addElement(new Integer(cc));

      //
      // 記錄分解組合
      //

      if (analysis) {
        if (br1) {
          _s.addElement(new Integer(s[0]));
        } else {
          _c.addElement(new Integer(s[0]));
        }
      }

      //
      // 拿掉這幾個組合,並往下繼續檢查
      //

      Vector c0 = (Vector)cards.clone();
      for (int j = 0; j < s0; j++) {
        c0.removeElementAt(find(c0, ((Integer)cards.elementAt(idx[j])).intValue()));
      }

      //
      // 遞迴剩下的組合
      //

      if (checkTin1(c0, check123, analysis)) {
        return true;
      }

      //
      // 復原分解組合
      //

      if (analysis) {
        if (br1) {
          _s.removeElementAt(_s.size() - 1);
        } else {
          _c.removeElementAt(_c.size() - 1);
        }
      }
    }

    return false;
  }

  //
  // 是否聽牌(一般)
  //

  boolean checkTinNormal(Vector p, int card) {

    //
    // 不同花色個別檢查
    //

    Vector c[] = new Vector[5];
    for (int i = 0; i < c.length; i++) {
      c[i] = new Vector();
    }

    for (int i = 0; i < p.size(); i++) {
      int A = ((Integer)p.elementAt(i)).intValue();
      c[MJCOLOR(A)].addElement(new Integer(MJNUM(A)));
    }

    c[MJCOLOR(card)].addElement(new Integer(MJNUM(card)));
    sort(c[MJCOLOR(card)]);

    for (int i = 0; i < c.length - 1; i++) { // 略過花牌
      if (0 != c[i].size() && !checkTin1(c[i], COLOR_FON != i, false)) {
        return false;
      }
    }

    return true;
  }

  //
  // 是否聽牌(特殊)
  //

  boolean checkTinSpecial(Vector p, int card) {
    return false;                       // todo.
  }

  //
  // 建立所有牌, 洗牌
  //

  void makeupCards() {

    //
    // 重設
    //

    cards.clear();

    //
    // 產生
    //

    for (int i = 0; i < 9; i++) {       // 萬
      for (int j = 0; j < 4; j++) {
        cards.addElement(new Integer(MJCARD(COLOR_MAN, i + 1)));
      }
    }

    for (int i = 0; i < 9; i++) {       // 筒
      for (int j = 0; j < 4; j++) {
        cards.addElement(new Integer(MJCARD(COLOR_TON, i + 1)));
      }
    }

    for (int i = 0; i < 9; i++) {       // 索
      for (int j = 0; j < 4; j++) {
        cards.addElement(new Integer(MJCARD(COLOR_SO, i + 1)));
      }
    }

    for (int i = 0; i < 7; i++) {       // 風
      for (int j = 0; j < 4; j++) {
        cards.addElement(new Integer(MJCARD(COLOR_FON, i + 1)));
      }
    }

    for (int i = 0; i < 8; i++) {       // 花
      cards.addElement(new Integer(MJCARD(COLOR_FLOWER, i + 1)));
    }

    //
    // 洗牌
    //

    int n = cards.size();
    for (int i = 1; i < n; i++) {
      int r = rand.nextInt(n);
      Object rn = cards.elementAt(r);
      cards.setElementAt(cards.elementAt(i), r);
      cards.setElementAt(rn, i);
    }
  }

  //
  // 從尾摸一張牌
  //

  int pickBack() {
    int c = ((Integer)cards.elementAt(cards.size() - 1)).intValue();
    cards.removeElementAt(cards.size() - 1);
    return c;
  }

  //
  // 從頭摸一張牌
  //

  int pickFront() {
    int c = ((Integer)cards.elementAt(0)).intValue();
    cards.removeElementAt(0);
    return c;
  }

  //
  // 設定順位
  //

  void setTurn() {

    //
    // 胡牌優先
    //

    for (int i = 0; i < NUM_PLAYER; i++) {
      int iPlayer = (turn + i) % NUM_PLAYER;
      if (0 != (state[iPlayer] & MJ_CANLON)) {
        active = iPlayer;
        return;
      }
    }

    //
    // 碰摃次之
    //

    for (int i = 0; i < NUM_PLAYER; i++) {
      int iPlayer = (turn + i + 1) % NUM_PLAYER;
      if (0 != (state[iPlayer] & MJ_CANGUN) || 0 != (state[iPlayer] & MJ_CANPON)) {
        active = iPlayer;
        return;
      }
    }

    //
    // 吃牌
    //

    if (0 != (state[(turn + 1) % NUM_PLAYER] & MJ_CANCHI)) {
      active = (turn + 1) % NUM_PLAYER;
      return;
    }

    //
    // 最後, 當然只剩下自己
    //

    active = turn;
  }

  // 捨牌
  void discard(int card) {

    //
    // 這張牌丟到海底
    //

    pick = 0;
    hdCards.addElement(new Integer(card));

    //
    // 檢查狀態
    //

    checkState();

    //
    // 無效一
    //

    active = turn;
    pass();
  }

  //
  // Define a card.
  //

  public int MJCARD(int color, int num) {
    return (color << 8) | num;
  }

  //
  // Retrive card color.
  //

  static public int MJCOLOR(int card) {
    return ((card) >> 8) & 0xf;
  }

  //
  // Retrive card number.
  //

  static public int MJNUM(int card) {
    return card & 0xf;
  }

  //
  // 泡沬排序
  //

  void sort(Vector cards) {
    int n = cards.size();
    for (int i = 0; i < n - 1; i++) {
      for (int j = 0; j < n - i - 1; j++) {
        Integer a = (Integer)cards.elementAt(j);
        Integer b = (Integer)cards.elementAt(j + 1);
        if (a.intValue() > b.intValue()) {
          cards.setElementAt(a, j + 1);
          cards.setElementAt(b, j);
        }
      }
    }
  }

  //
  // 線性搜尋, 回傳在v中的索引值, -1表示不在v中
  //

  int find(Vector v, int card) {
    for (int i = 0; i < v.size(); i++) {
      if (card == ((Integer)v.elementAt(i)).intValue()) {
        return i;
      }
    }
    return -1;
  }

  //
  // 計數
  //

  int count(Vector v, int card) {
    int c = 0;
    for (int i = 0; i < v.size(); i++) {
      if (card == ((Integer)v.elementAt(i)).intValue()) {
        c += 1;
      }
    }
    return c;
  }

  //
  // 取得最後的捨牌
  //

  int getLastHdCard() {
    return ((Integer)hdCards.elementAt(hdCards.size() - 1)).intValue();
  }

  //
  // 計算位元1個數
  //

  int bitCount(int a) {
    int c = 0;
    for (; 0 != a; a >>= 1) {
      if (0 != (a & 1)) {
        c += 1;
      }
    }
    return c;
  }
}

// end of MahjongGame.java
