// create 2010/12/3
import java.util.Vector;

public class MahjongScore
{
  //麻將牌編號
  public static final short MJ_CARD[] =
  {
     0, 1, 2, 3, 4, 5, 6, 7, 8,          //萬
     10, 11, 12, 13, 14, 15, 16, 17, 18, //筒
     20, 21, 22, 23, 24, 25, 26, 27, 28, //索
     30, 31, 32, 33, 34, 35, 36,         //東南西北中發白
     40,41,42,43,44,45,46,47,            //花牌

  };

  //胡的台型名稱[16張]
  public static final String WO_TYPE[]=
  {"門前清","平胡","斷么九","門前一杯口","面前聽","槓上開花","搶槓","海底撈月","河底撈魚","混帶么", //0~9
  "三色同順","一條龍","碰碰胡","三暗刻","三槓子","七對子","連風牌","八仙過海","混一色","純帶么",    //10~19
  "三色同碰","小三元","混老頭","清一色","大三元","小四喜","大四喜","字一色","清老頭","四暗刻",      //20~29
  "四槓子","全求人","五暗刻","天胡","地胡","人胡","風牌","台牌","立直","自摸",                      //30~39
  "胡牌","花牌","風牌","莊家","連莊","門清一摸三","紅中","青發","白板"};                            //40~48


  //16張胡的台數（對應台型）
  public  int WO_SCORE[]=
  {1,2,1,1,1,1,1,1,1,2,
   2,2,2,2,2,2,2,2,2,4,
   2,4,4,6,6,6,8,8,8,8,
   8,32,8,8,8,8,1,1,1,1,
   0,1,1,1,1,2,1,1,1};


  //變數宣告
  public int bonCount;                  //記錄碰得數量
  public int eatCount;                  //記錄吃的數量
  public int conCount;                  //紀錄槓的數量
  public int flowerCount;               //紀錄花牌數量
  public boolean woItSelf;              //是否自摸
  public Vector fixCard;                //擺放整理好的牌
  public int pCard[];                   //手上牌
  public int oCard[];                   //吃碰牌
  public Vector cardScore;              //放置胡的牌
  public Vector eatCard;                //儲存順的牌組
  public Vector bonCard;                //儲存對得牌組
  public Vector conCard;                //儲存槓的牌組
  public int    tazCard;                //儲存對子
  public boolean check[];               //檢查指標
  public MahjongScore()
  {
    init();
  }

  public void init()
  {
    fixCard=null;
    eatCard=null;
    bonCard=null;
    tazCard=-1;
    cardScore=null;
    woItSelf=false;
    bonCount=0;
    eatCount=0;
    conCount=0;
    flowerCount=0;
  }

  // 取得麻將編號（轉換成單一整數進行檢查）
   public  int getMJNumber(int card)
   {
     int mjNumber=0;

     int color = MahjongGame.MJCOLOR(card);
     int num = MahjongGame.MJNUM(card);

     switch (color)
     {
     case MahjongGame.COLOR_MAN:        //萬
       mjNumber=num - 1;
       break;
     case MahjongGame.COLOR_TON:        //筒
       mjNumber=(num - 1)+10;
       break;
     case MahjongGame.COLOR_SO:         //索
       mjNumber=(num - 1)+20;
       break;
     case MahjongGame.COLOR_FON:        //風
       mjNumber=(num - 1)+30;
       break;
     case MahjongGame.COLOR_FLOWER:     //花
       mjNumber=(num - 1)+40;
       break;
     }
     return mjNumber;
   }

   public void print()
   {
    String str[]={"萬","筒","索","東風","南風","西風","北風","紅中","青發","白板"};
    String number[]={"一","二","三","四 ","五","六","七","八","九"};
    String mjString="";
    System.out.print("\n======胡牌台型測試0.1=====================================================\n");
    if(oCard.length>0)
      mjString+="(";
    for(int i=0;i<oCard.length;i++)
    {
      //取得編號
      int num=oCard[i];
      //字牌
      if(num>=30)
        mjString+=str[3+num%10]+" ";
      else
      {
        //槓牌轉換
        if(num==-1)
          num=oCard[i-1];
        //一般牌
        mjString+=number[num%10]+str[num/10]+" ";
      }
    }
    if(oCard.length>0)
      mjString+=")";

    for(int i=0;i<pCard.length;i++)
    {
      //取得編號
      int num=pCard[i];
      //字牌
      if(num>=30)
        mjString+=str[3+num%10]+" ";
      else
        // 一般牌
        mjString+=number[num%10]+str[num/10]+" ";
    }

    System.out.print(mjString);

    System.out.print("\n=========================================================================\n");
    for(int i=0;i<cardScore.size();i++)
    {
      int num=((Integer)cardScore.elementAt(i)).intValue();
      System.out.print(WO_TYPE[num]+WO_SCORE[num]+"台\n");
    }
   }

   // 建立偵測用麻將牌
   public  void makeCard(MahjongGame mj,int index)
   {
     int type=0;
     woItSelf=false;
     fixCard=new Vector();
     //取得吃碰牌
     Vector op = mj.getOpPlayer(index);
     //取得主要牌
     Vector p = mj.getPlayer(index);

     //=========================================================================
     // 整合吃碰牌
     //=========================================================================
     for(int i=0;i<op.size();i++)
     {
       int num=((Integer)op.elementAt(i)).intValue();
       if (0 != (num & MahjongGame.MJF_GUN))  //槓牌
       {
         type=MahjongGame.MJF_GUN;
         conCount++;
       }
       else
       if (0 != (num & MahjongGame.MJF_PON))  //碰牌
       {
         type=MahjongGame.MJF_PON;
         bonCount++;
       }
       else
       if (0 != (num & MahjongGame.MJF_CHI))  //吃牌
       {
         type=MahjongGame.MJF_CHI;
         eatCount++;
       }

       //槓碰狀態（補齊牌）
       if(type==MahjongGame.MJF_GUN)
       {
         for(int j=0;j<2;j++)
          fixCard.addElement(new Integer(getMJNumber(((Integer)op.elementAt(i)).intValue())));
          //傳入-1當作檢查
          fixCard.addElement(new Integer(-1));
       }
       else
       if(type==MahjongGame.MJF_PON)
       {
         for(int j=0;j<3;j++)
           fixCard.addElement(new Integer(getMJNumber(((Integer)op.elementAt(i)).intValue())));
       }
       else
       //吃狀態（補齊牌）
       if(type==MahjongGame.MJF_CHI)
         for(int j=0;j<3;j++)
           fixCard.addElement(new Integer(getMJNumber(((Integer)op.elementAt(i)).intValue()+j)));
     }

     //轉換後存入外部牌
     oCard=new int[fixCard.size()];
     for(int i=0;i<oCard.length;i++)
       oCard[i]=((Integer)fixCard.elementAt(i)).intValue();

     //=========================================================================
     // 整合手上牌
     //=========================================================================
     //判斷是否是自摸（補上最後一張）
     if (0 != mj.pick)
     {
       p.addElement(new Integer(mj.pick));
       woItSelf=true;
     }
     else
     {
       //胡牌 (取海底最後一張牌）
       int card = mj.getLastHdCard();
       woItSelf=false;
       p.addElement(new Integer(card));
     }

     //手上牌進行排序
     sort(p);
     //整合傳入一般牌
     pCard=new int[p.size()];
     for(int i=0;i<pCard.length;i++)
     {
       //取牌
       int card=getMJNumber(((Integer)p.elementAt(i)).intValue());
       pCard[i]=card;
       fixCard.addElement(new Integer(card));
     }
   }

   // 回傳麻將台型
   public Vector score(MahjongGame mj,int index)
   {
     boolean haveScore=false;
     boolean haveWord =false;
     boolean haveOutCard=false;
     int tempCard[];
     int count=0;
     int num=0;
     //初始變數
     init();
     //組立牌
     makeCard(mj,index);

     //是否有吃碰外露牌
     if(oCard.length>0)
       haveOutCard=true;

     //備存檢查用的牌
     tempCard=new int[fixCard.size()];
     for(int i=0;i<tempCard.length;i++)
       tempCard[i]=((Integer)fixCard.elementAt(i)).intValue();

     //測試用牌================================================================
     final int TEMP_CARD[]={30,30,30,31,31,31,32,32,32,33,33,6,7,8,20,20,33};
     //tempCard=TEMP_CARD;
     //pCard=tempCard;
     //=======================================================================
     //搜尋牌組
     searchCard(tempCard);
     //開始算牌
     cardScore=new Vector();
     //---------------------------------------------------------------------
       // 39.自摸
       //---------------------------------------------------------------------
     if(woItSelf)
       cardScore.addElement(new Integer(39));
     //---------------------------------------------------------------------
     // 45.門清一摸三 && 0.門前清
     //---------------------------------------------------------------------
     if(!haveOutCard)
      if(woItSelf)
        cardScore.addElement(new Integer(45));
      else
        cardScore.addElement(new Integer(0));
     //---------------------------------------------------------------------
     // 43.莊家（連莊）
     //---------------------------------------------------------------------
     if(index==mj.mjOpen.master && mj.mjOpen.masterCount>=1)
     {
       cardScore.addElement(new Integer(43));

       //---------------------------------------------------------------------
       // 44.連莊
       //---------------------------------------------------------------------
       if(mj.mjOpen.masterCount>=2)
       {
         cardScore.addElement(new Integer(44));
         WO_SCORE[44] =(byte)((mj.mjOpen.masterCount-1)*2);
       }
     }
     //---------------------------------------------------------------------
     // 33.天胡
     //---------------------------------------------------------------------

     //---------------------------------------------------------------------
     // 34.地胡 && 35.人胡
     //---------------------------------------------------------------------

     //---------------------------------------------------------------------
     // 42.風牌
     //---------------------------------------------------------------------

     //---------------------------------------------------------------------
     // 41.花牌
     //---------------------------------------------------------------------


     //---------------------------------------------------------------------
     // 5.槓上開花
     //---------------------------------------------------------------------

     //---------------------------------------------------------------------
     // 17.八仙過海
     //---------------------------------------------------------------------
     if(flowerCount==8)
       cardScore.addElement(new Integer(8));

     //--------------------------------------------------------------------
     // 1.平胡 (無花   無刻子 且對子不是字）
     //---------------------------------------------------------------------
     if(flowerCount==0 && bonCard.size()==0 && conCard.size()==0)
       if(tazCard<30)
         cardScore.addElement(new Integer(1));

     //---------------------------------------------------------------------
     // 2.斷么九
     //---------------------------------------------------------------------
     haveScore=true;
     for(int i=0;i<tempCard.length;i++)
       if(tempCard[i]%10==0 || tempCard[i]%10==8 || tempCard[i]>=30) //如果有么九牌或字牌
       {
         haveScore=false;
         break;
       }

     if(haveScore)
       cardScore.addElement(new Integer(2));

     //---------------------------------------------------------------------
     // 3.一杯口 （需門清）
     //---------------------------------------------------------------------
     haveScore=false;
     if(!haveOutCard && eatCard.size()>=2)
     {
       num=0;
       for(int i=0;i<eatCard.size();i++)
       {
         num=((Integer)eatCard.elementAt(i)).intValue();
         for(int j=i;j<eatCard.size();j++)
         {
           //是否有兩組相同的吃
           if(num==((Integer)eatCard.elementAt(j)).intValue())
           {
             haveScore=true;
             break;
           }
         }
         if(haveScore)
         {
           cardScore.addElement(new Integer(3));
           break;
         }
       }
     }

     //---------------------------------------------------------------------
     //9.混帶么 19.全帶么
     //---------------------------------------------------------------------
     haveScore=true;      //是否有台
     boolean allone=true;  //是否全么

     for(int i=0;i<eatCard.size();i++)
     {
       num=((Integer)eatCard.elementAt(i)).intValue();
       //不是么九
       if(num%10!=0 && num%10!=6)
       {
         haveScore=false;
         allone=false;
         break;
       }
     }

     //找碰
     if(haveScore)
          for(int i=0;i<bonCard.size();i++)
       {
         num=((Integer)bonCard.elementAt(i)).intValue();
         //如果有字牌（就不是全帶么）
         if(num>=30)
           allone=false;
         //不是1跟9跟字牌
         if(num%10!=0 && num%10!=8 && num<30)
         {
           haveScore=false;
             break;
         }
       }

     //找對子
     if(haveScore)
     {
       //如果有字牌（就不是全帶么）
     if(tazCard>=30)
       allone=false;
     else
     //如果對子不是么九
       if(tazCard%10!=0 && tazCard%10!=8 && tazCard<30)
          haveScore=false;
     }

     if(haveScore)
     {
       //全帶么
       if(allone)
         cardScore.addElement(new Integer(19));
       else
       {
         //混全帶么
         cardScore.addElement(new Integer(9));
         //沒有門清 算一台
         if(haveOutCard)
             WO_SCORE[9]=1;
       }
     }

     //---------------------------------------------------------------------
     // 10.三色同順
     //---------------------------------------------------------------------
     haveScore=false;
     if(eatCard.size()>=3)
     {
       num=0;
       for(int i=0;i<eatCard.size();i++)
       {
         count=0;
         num=((Integer)eatCard.elementAt(i)).intValue();
         //是否有三組相同的吃（不同花色）
         for(int j=i;j<eatCard.size();j++)
         {
           int num1=((Integer)eatCard.elementAt(j)).intValue();
           if(num%10==num1%10 && num!=num1)
             for(int k=j;k<eatCard.size();k++)
             {
               int num2=((Integer)eatCard.elementAt(k)).intValue();
               if( num%10==num2%10 && num!=num2 && num1!=num2)
               {
                 haveScore=true;
                 break;
               }
             }
         }
         if(haveScore)
         {
           cardScore.addElement(new Integer(10));
           //沒門清算一台
           if(haveOutCard)
               WO_SCORE[10]=1;
           break;
         }
       }
     }

     //---------------------------------------------------------------------
     // 11.一條龍(123456789)
     //---------------------------------------------------------------------
     haveScore=false;
     if(eatCard.size()>=3)
          for(int i=0;i<eatCard.size();i++)
       {
         num=((Integer)eatCard.elementAt(i)).intValue();
         for(int j=i;j<eatCard.size();j++)
         {
           if(num+3==((Integer)eatCard.elementAt(j)).intValue())
             for(int k=j;k<eatCard.size();k++)
               if(num+6==((Integer)eatCard.elementAt(k)).intValue())
               {
                 haveScore=true;
                 break;
               }
           if(haveScore)
             break;
         }
         if(haveScore)
         break;
       }

     if(haveScore)
     {
       cardScore.addElement(new Integer(11));
       //吃碰破壞門清剩一台
       if(haveOutCard)
         WO_SCORE[11]=1;
     }

     //---------------------------------------------------------------------
     // 12.對對胡
     //---------------------------------------------------------------------
     if(bonCard.size()>=5)
       cardScore.addElement(new Integer(12));

     //---------------------------------------------------------------------
     // 32.五暗刻 & 29.四暗刻 & 13.三暗刻 （偵測手上牌）
     //---------------------------------------------------------------------
     int tempBon=searchBon(pCard,true).size();
     if(tempBon==5)
       cardScore.addElement(new Integer(32));
     else
     if(tempBon==4)
       cardScore.addElement(new Integer(29));
     else
     if(tempBon==3)
       cardScore.addElement(new Integer(13));

     //---------------------------------------------------------------------
     // 14.三槓子 && 30.四槓子
     //---------------------------------------------------------------------
     if(conCard.size()==3)
       cardScore.addElement(new Integer(14));
     else
     if(conCard.size()==4)
       cardScore.addElement(new Integer(30));

     //---------------------------------------------------------------------
     // 18.混一色111234567893333
     //---------------------------------------------------------------------
     num=-2;
     haveScore=true;
     //先找出不是字牌的花色代表
     for(int i=0;i<tempCard.length;i++)
       if(tempCard[i]!=-1)//不是槓牌
       if(tempCard[i]<30)
       {
         num=tempCard[i];
         break;
       }

     //沒有非字牌
     if(num!=-2)
     {
          for(int i=0;i<tempCard.length;i++)
            if(tempCard[i]!=-1) //不是槓牌
            if(tempCard[i]<30 && tempCard[i]/10!=num/10) //如果有不同花色
         {
           haveScore=false;
           break;
         }

          if(haveScore)
            cardScore.addElement(new Integer(18));
     }

     //---------------------------------------------------------------------
     // 20.三色同碰
     //---------------------------------------------------------------------
     haveScore=false;
     if(bonCard.size()>=3)
       for(int i=0;i<bonCard.size();i++)
       {
         num=((Integer)bonCard.elementAt(i)).intValue();
         for(int j=i;j<bonCard.size();j++)
         {
           int num1=((Integer)bonCard.elementAt(j)).intValue();
           //不是同花色
           if(num!=num1 && num%10==num1%10)
             for(int k=j;k<bonCard.size();k++)
             {
               int num2=((Integer)bonCard.elementAt(k)).intValue();
               if(num2!=num1 && num2!=num && num%10==num2%10)
               {
                 haveScore=true;
                 break;
               }
             }
           if(haveScore)
             break;
         }
         if(haveScore)
         break;
       }

     if(haveScore)
       cardScore.addElement(new Integer(20));

     //---------------------------------------------------------------------
     // 21.小三元(中發白) 24.大三元
     //---------------------------------------------------------------------
     count=0;
     for(int i=0;i<bonCard.size();i++)
     {
         num=((Integer)bonCard.elementAt(i)).intValue();
         if(num>=34 && num<37)
           count++;
     }

     if(count>=2)
     {
       //大三元
       if(count==3)
         cardScore.addElement(new Integer(24));
       else
       //小三元（對子要是三元牌）
       if(count==2 && (tazCard>=34 && tazCard<37))
         cardScore.addElement(new Integer(21));
     }

     //---------------------------------------------------------------------
     // 22.混老頭 && 28.清老頭 （不能有吃跟槓）
     //---------------------------------------------------------------------
     num=-1;
     haveScore=true;
     haveWord=false;

     //不能有吃跟槓
     if(eatCard.size()==0 && conCard.size()==0)
     {
       for(int i=0;i<bonCard.size();i++)
       {
         num=((Integer)bonCard.elementAt(i)).intValue();
         //非字牌就得是么九牌
         if(num<30)
         {
           if(num%10!=0 && num%10!=8)
           {
             haveScore=false;
             break;
           }
         }
         else
           haveWord=true;
       }

       //檢查是否是字牌
       if((tazCard>=34 && tazCard<37))
         haveWord=true;
       else
       //檢查對子
       if(tazCard%10!=0 && tazCard%10!=8)
         haveScore=false;

       if(haveScore)
       {
         //有字是混老頭
         if(haveWord)
           cardScore.addElement(new Integer(22));
         else
         //無字是清老頭
           cardScore.addElement(new Integer(28));
       }
     }

     //---------------------------------------------------------------------
     // 23.清一色（都由同一種花色組成，非字牌）
     //---------------------------------------------------------------------
     num=-2;
     haveScore=true;
     //先找出不是字牌的花色代表
     for(int i=0;i<tempCard.length;i++)
       if(tempCard[i]!=-1)//不是槓牌
       if(tempCard[i]<30)
       {
         num=tempCard[i];
         break;
       }

     //沒有非字牌
     if(num!=-2)
     {
         for(int i=0;i<tempCard.length;i++)
        if(num!=-1) //不是槓牌
         if(num/10!=tempCard[i]/10 || tempCard[i]>30)
           {
             haveScore=false;
               break;
           }
     }
     else
       haveScore=false;

     if(haveScore)
       cardScore.addElement(new Integer(23));

     //---------------------------------------------------------------------
     // 26.大四喜（東南西北） && 25.小四喜
     //---------------------------------------------------------------------
     count=0;
     for(int i=0;i<bonCard.size();i++)
     {
       num=((Integer)bonCard.elementAt(i)).intValue();
       if(num>=30 && num<34)
         count++;
     }

     if(count>=3)
     {
       //大四喜
       if(count==4)
         cardScore.addElement(new Integer(26));
       else
       //小四喜
       if(count==3 && (tazCard>=30 && tazCard<34))
         cardScore.addElement(new Integer(25));
     }

     //---------------------------------------------------------------------
     // 27.字一色
     //---------------------------------------------------------------------
     haveScore=true;
     for(int i=0;i<tempCard.length;i++)
    if(tempCard[i]!=-1)
    if(tempCard[i]<30)
      {
      haveScore=false;
      break;
    }
     if(haveScore)
       cardScore.addElement(new Integer(27));

     //---------------------------------------------------------------------
     // 31.全求人（手上剩下一張牌且不是自摸）
     //---------------------------------------------------------------------
     if(pCard.length==1 && !woItSelf) //剩一張牌及不是自摸
       cardScore.addElement(new Integer(31));

     //---------------------------------------------------------------------
     // 46.三元牌(中發白)
     //---------------------------------------------------------------------
     for(int i=0;i<bonCard.size();i++)
     {
       num=((Integer)bonCard.elementAt(i)).intValue();
       if(num>=34 && num<37)
          cardScore.addElement(new Integer(46+num%34));
     }

     //---------------------------------------------------------------------
     // 40.無台（胡牌）
     //---------------------------------------------------------------------
     if(cardScore.size()==0)
       cardScore.addElement(new Integer(40));

     return cardScore;
   }

   // 搜尋有幾組順(並記錄所有順的牌）
   public Vector searchEat(int card[],boolean clear)
   {
     if(clear)
       check=new boolean[card.length];
     Vector eat=new Vector();
     for(int i=0;i<card.length;i++)
     {
       int num=card[i];
       //字牌跳離
       if(num>=30)
         continue;
       //非尾牌
       if(!check[i] && num%10<7)
       for(int j=i+1;j<card.length;j++)
       {
         if(card[j]==num+1 && !check[j])
           for(int k=j+1;k<card.length;k++)
             if(card[k]==num+2 && !check[k])
             {
               //存入吃的牌
               eat.addElement(new Integer(card[i]));
               check[i]=check[j]=check[k]=true;
               break;
             }
       }
     }
     return eat;
   }

   // 搜尋有幾組碰(並記錄所有碰的牌）
   public Vector searchBon(int card[],boolean clear)
   {
     //boolean check[]=new boolean[card.length];
     if(clear)
       check=new boolean[card.length];

     Vector bon=new Vector();
     for(int i=0;i<card.length;i++)
     {
       int num=card[i];
       //花牌跳離
       if(num>=40)
         continue;
       if(!check[i])
       for(int j=i+1;j<card.length;j++)
       {
         if(card[j]==num && !check[j])
           for(int k=j+1;k<card.length;k++)
             if(card[k]==num && !check[k])
             {
               //存入碰的牌
               bon.addElement(new Integer(card[i]));
               check[i]=check[j]=check[k]=true;
               break;
             }
       }
     }
     return bon;
   }

   // 搜尋有幾組槓(並記錄所有槓的牌）
   public Vector searchCon(int card[],boolean clear)
   {
     if(clear)
       check=new boolean[card.length];
     Vector con=new Vector();
     for(int i=0;i<card.length;i++)
     {
       int num=card[i];
       //花牌跳離
       if(num>=40)
         continue;
       //找出-1槓偵測牌
       if(!check[i] && card[i]==-1)
       {
         //存入槓的牌
         con.addElement(new Integer(card[i-2]));
         check[i]=check[i-1]=check[i-2]=true;
         break;
       }
     }
     return con;
   }

   public void searchCard(int card[])
   {
     check=new boolean[card.length];
     conCard=searchCon(card,false);
     eatCard=searchEat(card,false);
     bonCard=searchBon(card,false);

     for(int i=0;i<card.length;i++)
     {
       if(!check[i])
       {
         tazCard=card[i];
         break;
       }
     }
   }

   void sort(Vector cards) {
     int n = cards.size();
     for (int i = 0; i < n - 1; i++)
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
